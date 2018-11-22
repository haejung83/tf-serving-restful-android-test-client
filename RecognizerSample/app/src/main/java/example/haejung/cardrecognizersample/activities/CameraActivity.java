package example.haejung.cardrecognizersample.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import example.haejung.cardrecognizersample.R;
import example.haejung.cardrecognizersample.utils.B64Utils;
import example.haejung.cardrecognizersample.views.AutoFitTextureView;
import example.haejung.cardrecognizersample.views.BoundingBoxDrawView;
import example.haejung.recognizer.PredictionListener;
import example.haejung.recognizer.PredictionResult;
import example.haejung.recognizer.RecognizerClient;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// Referenced from Android Sample Github
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = CameraActivity.class.getSimpleName();

    private static final String HANDLE_THREAD_NAME = "CameraBackground";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final int MAX_PREVIEW_WIDTH = 2960;
    private static final int MAX_PREVIEW_HEIGHT = 1440;

    private static final long MAX_INTERVAL_TO_PREDICT = 500; // 1000ms
    private static final int TARGET_RESIZED_IMAGE_WIDTH = 400;
    private static final float TARGET_CROPPED_IMAGE_ASPECT_RATIO = 4.f / 3.f;

    private boolean runPredictor = false;
    private boolean checkedPermissions = false;

    private BoundingBoxDrawView boundingBoxDrawView;
    private AutoFitTextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Size previewSize;
    private String cameraId;

    private final Object lock = new Object();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    // RecognizerClient
    private RecognizerClient mRecognizerClient;

    // Prediction a frame from the preview stream.
    private long mOldTimeMills = 0;
    private long mNewTimeMills = 0;
    private boolean mIsTimeoutToPredict = false;

    // Resize and crop captured image
    private Rect cropRect;
    private Size resizeSize;

    // Clipping Area for UI
    private Rect clippingRect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bindingViews();
        initializeRecognizerClient();
        loadAndSetLabelMap();
    }

    private void bindingViews() {
        boundingBoxDrawView = findViewById(R.id.boundingBoxDrawView);
        textureView = findViewById(R.id.textureViewCamera);

        Display display = getWindowManager().getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);
        Log.v(TAG, "Display Size: " + displaySize.x + ", " + displaySize.y);
    }

    private void initializeRecognizerClient() {
        if (mRecognizerClient == null) {
            mRecognizerClient = new RecognizerClient(CameraActivity.this);
            mRecognizerClient.setPredictionListener(mPredictionListener);
        }
    }

    private void loadAndSetLabelMap() {
        try {
            InputStream is = getAssets().open("label_map.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            Map<Integer, String> labelMap = new HashMap<>();
            JSONObject jsonLabelMap = new JSONObject(new String(buffer, "UTF-8"));
            Iterator<String> labelKeys = jsonLabelMap.keys();

            while (labelKeys.hasNext()) {
                String key = labelKeys.next();
                String value = jsonLabelMap.getString(key);
                labelMap.put(Integer.parseInt(key), value);
            }

            boundingBoxDrawView.setLabelMap(labelMap);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onStop() {
        closeCamera();
        stopBackgroundThread();
        super.onStop();
    }

    private void calculateCropRectAndSize(float width, float height) {
        int resizeHeight = (int) (height / (width / (float) TARGET_RESIZED_IMAGE_WIDTH));

        float viewAspectRatio = (width > height) ? width / height : height / width;
        float previewAspectRatio = (float) previewSize.getWidth() / (float) previewSize.getHeight();
        if (viewAspectRatio > previewAspectRatio) {
            float resizeScale = previewAspectRatio / viewAspectRatio;
            resizeHeight = (int) (resizeHeight * resizeScale);
        }

        resizeSize = new Size(TARGET_RESIZED_IMAGE_WIDTH, resizeHeight);

        int targetHeight = (int) ((float) resizeSize.getWidth() / (TARGET_CROPPED_IMAGE_ASPECT_RATIO));
        int targetY = (int) (((float) resizeSize.getHeight() / 2.f) - (targetHeight / 2.f));
        cropRect = new Rect(0, targetY, resizeSize.getWidth(), targetHeight);

        Log.v(TAG, "Target Resize Image Size: " + resizeSize.getWidth() + ", " + resizeSize.getHeight());
        Log.v(TAG, "Target Cropped Image Rect: "
                + cropRect.left + ", "
                + cropRect.top + ", "
                + cropRect.right + ", "
                + cropRect.bottom
        );
    }

    private void calculateUIClippingArea(float width, float height) {
        int targetHeight = (int) (width / (TARGET_CROPPED_IMAGE_ASPECT_RATIO));
        int targetY = (int) ((height / 2.f) - (targetHeight / 2.0f));
        clippingRect = new Rect(0, targetY, (int) width, targetHeight);
        Log.v(TAG, "UI Clipping Rect: "
                + clippingRect.left + ", "
                + clippingRect.top + ", "
                + clippingRect.right + ", "
                + clippingRect.bottom
        );
    }

    // SurfaceTextureListener
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    Log.d(TAG, "onSurfaceTextureAvailable: " + width + ", " + height);
                    openCamera(width, height);
                    calculateCropRectAndSize(width, height);
                    calculateUIClippingArea(width, height);
                    boundingBoxDrawView.setClippingArea(clippingRect);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                    Log.d(TAG, "onSurfaceTextureSizeChanged: " + width + ", " + height);
                    configureTransform(width, height);
                    calculateCropRectAndSize(width, height);
                    calculateUIClippingArea(width, height);
                    boundingBoxDrawView.setClippingArea(clippingRect);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    Log.d(TAG, "onSurfaceTextureDestroyed");
                    cropRect = null;
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                    // Nothing to do
                }
            };

    // Camera State Callback
    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {

                @Override
                public void onOpened(@NonNull CameraDevice currentCameraDevice) {
                    Log.d(TAG, "onOpened");
                    // This method is called when the camera is opened.  We start camera preview here.
                    cameraOpenCloseLock.release();
                    cameraDevice = currentCameraDevice;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice currentCameraDevice) {
                    Log.d(TAG, "onDisconnected");
                    cameraOpenCloseLock.release();
                    currentCameraDevice.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice currentCameraDevice, int error) {
                    Log.d(TAG, "onError");
                    cameraOpenCloseLock.release();
                    currentCameraDevice.close();
                    cameraDevice = null;
                    finish();
                }
            };

    // Camera Capture Callback
    private CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureProgressed(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) {
                }
            };


    // Calculate optimal size for camera preview
    private static Size chooseOptimalSize(
            Size[] choices,
            int textureViewWidth,
            int textureViewHeight,
            int maxWidth,
            int maxHeight,
            Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            Log.v(TAG, "Option: " + option.getWidth() + ", " + option.getHeight());
            if (option.getWidth() <= maxWidth
                    && option.getHeight() <= maxHeight
                    && option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

                // Find out if we need to swap dimension to get the preview size relative to sensor
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();

                /* Orientation of the camera sensor */
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                previewSize = chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth,
                        rotatedPreviewHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        largest);

                Log.d(TAG, "PreviewSize: " + previewSize.getWidth() + ", " + previewSize.getHeight());

                // We fit the aspect ratio of TextureView to the size of preview we picked.
//                int orientation = getResources().getConfiguration().orientation;
//                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
//                } else {
//                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
//                }

                this.cameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Toast.makeText(CameraActivity.this, "Camera Open Error", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            String packageName = getPackageName();
            PackageInfo info = getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void openCamera(int width, int height) {
        if (!checkedPermissions && !allPermissionsGranted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
            }
            return;
        } else {
            checkedPermissions = true;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please, Check a permission for camera", Toast.LENGTH_SHORT).show();
                return;
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                Log.d(TAG, "closeCamera");
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        synchronized (lock) {
            runPredictor = true;
        }
        backgroundHandler.post(periodicProcessFrame);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        try {
            backgroundHandler = null;
            //backgroundThread.quitSafely();
            backgroundThread.quit();
            backgroundThread.join();
            backgroundThread = null;
            synchronized (lock) {
                runPredictor = false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Takes photo periodically.
     */
    private Runnable periodicProcessFrame =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (runPredictor) {
                            processFrame();
                        }
                    }
                    if (backgroundHandler != null)
                        backgroundHandler.post(periodicProcessFrame);
                }
            };

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(
                                    CameraActivity.this,
                                    "Camera: Failed to configure",
                                    Toast.LENGTH_SHORT).show();
                        }
                    },
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == previewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());

            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else {
            float stretchY = (bufferRect.height() / bufferRect.width()) / (viewRect.height() / viewRect.width());
            matrix.postScale(1.0f, stretchY, centerX, centerY);
            if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }
        }

        textureView.setTransform(matrix);
    }

    /**
     * Calculate a status of timeout for making interval
     *
     * @return
     */
    private boolean isTimeoutToPredict() {
        mNewTimeMills = System.currentTimeMillis();

        if (mNewTimeMills - mOldTimeMills > MAX_INTERVAL_TO_PREDICT) {
            mIsTimeoutToPredict = true;
            mOldTimeMills = mNewTimeMills;
        } else
            mIsTimeoutToPredict = false;

        return mIsTimeoutToPredict;
    }

    private boolean hasResponseError = false;

    /**
     * A listener for receiving the result of prediction as requested.
     */
    private PredictionListener mPredictionListener = new PredictionListener() {
        @Override
        public void onReceivedPredictionResult(PredictionResultStatus predictionResultStatus, List<PredictionResult> predictionResult) {
            switch (predictionResultStatus) {
                case RESULT_STATUS_OK:
                    Log.v(TAG, "Result OK [Size]: " + predictionResult.size());
                    if (hasResponseError) {
                        hasResponseError = false;
                        showToast("Result Ok - Recovered!");
                    }

                    if (!predictionResult.isEmpty()) {
                        boundingBoxDrawView.setPredictionResult(predictionResult);
                    }
                    break;
                case RESULT_STATUS_ERROR:
                    Log.e(TAG, "Result Error");
                    if (!hasResponseError) {
                        hasResponseError = true;
                        showToast("Result Error!");
                    }
                default:
                    break;
            }
        }
    };

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CameraActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    int nSaveCount = 0;

    private void processFrame() {
        if (!isTimeoutToPredict())
            return;

        if (cameraDevice == null) {
            Log.e(TAG, "Invalid camera context.");
            return;
        }

        try {
            // Get a frame from the TextureView
            Bitmap bitmap = textureView.getBitmap(resizeSize.getWidth(), resizeSize.getHeight());
            Bitmap cropBitmap = Bitmap.createBitmap(bitmap,
                    cropRect.left,
                    cropRect.top,
                    cropRect.right,
                    cropRect.bottom);

            if (++nSaveCount == 5) {
                saveBitmaptoJpeg(bitmap, "crop_image", "save");
            }

            // Log.v(TAG, "Bitmap: " + cropBitmap.getWidth() + ", " + cropBitmap.getHeight());

            mRecognizerClient.predictOneShot(
                    B64Utils.encodeToBase64(
                            cropBitmap,
                            Bitmap.CompressFormat.JPEG,
                            50)
            );

            // Recycling
            bitmap.recycle();
            cropBitmap.recycle();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }


    // TEST
    public static void saveBitmaptoJpeg(Bitmap bitmap, String folder, String name) {
        String ex_storage = Environment.getExternalStorageDirectory().getAbsolutePath();
        String foler_name = "/" + folder + "/";
        String file_name = name + ".jpg";
        String string_path = ex_storage + foler_name;
        Log.d(TAG, "saveBitmapToJepg: " + string_path);
        File file_path;
        try {
            file_path = new File(string_path);
            if (!file_path.isDirectory()) {
                file_path.mkdirs();
            }
            FileOutputStream out = new FileOutputStream(string_path + file_name);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.close();
        } catch (FileNotFoundException exception) {
            Log.e("FileNotFoundException", exception.getMessage());
        } catch (IOException exception) {
            Log.e("IOException", exception.getMessage());
        }
    }

}
