package example.haejung.recognizer;

import android.content.Context;
import android.graphics.RectF;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static example.haejung.recognizer.PredictionListener.PredictionResultStatus.RESULT_STATUS_ERROR;
import static example.haejung.recognizer.PredictionListener.PredictionResultStatus.RESULT_STATUS_OK;

/**
 * RecognizerClientImpl Class (Using the bridge pattern)
 */
public class RecognizerClientImpl {
    private static final String TAG = RecognizerClientImpl.class.getSimpleName();
    private static final MediaType MEDIA_TYPE_JPG = MediaType.parse("image/jpeg");

    private Config mConfig;
    private PredictionListener mPredictionListener;
    private OkHttpClient client;
    private AtomicBoolean isRequested = new AtomicBoolean(false);

    RecognizerClientImpl(Context context) {
        client = new OkHttpClient();
        mConfig = new Config();
        mConfig.loadFromDisk(context);
    }

    public PredictionListener getPredictionListener() {
        return mPredictionListener;
    }

    public void setPredictionListener(PredictionListener mPredictionListener) {
        this.mPredictionListener = mPredictionListener;
    }

    public Config getConfig() {
        return mConfig;
    }

    public void setConfig(Config config) {
        this.mConfig = config;
    }

    public void predictOneShot(final byte[] image) {
        if (isRequested.compareAndSet(false, true)) {
            Log.i(TAG, "predictOneShot image length: " + image.length);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    requestPrediction(image);
                }
            }).start();
        } else {
            Log.i(TAG, "Ignore this frame cuz request before get response");
        }
    }

    public void predictOneShot(final String b64Encoded) {
        if (isRequested.compareAndSet(false, true)) {
            Log.i(TAG, "predictOneShot b64 encoded length: " + b64Encoded.length());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    requestPrediction(b64Encoded);
                }
            }).start();
        } else {
            Log.i(TAG, "Ignore this frame cuz request before get response");
        }
    }

    protected void requestPrediction(byte[] image) {
        RequestBody body = MultipartBody.create(MEDIA_TYPE_JPG, image);
        Request request = new Request.Builder()
                .url(mConfig.getParsedUrl())
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                processError();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                processResponse(response.body().string());
            }
        });
    }

    protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    protected RequestBody createRequestBodyWithB64EncodedImage(String b64Encoded) {
        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put("signature_name", "serving_default");

            JSONObject jsonB64WrapObject = new JSONObject();
            jsonB64WrapObject.put("b64", b64Encoded);
            JSONObject jsonInputsWrapObject = new JSONObject();
            jsonInputsWrapObject.put("inputs", jsonB64WrapObject);

            JSONArray jsonInstanceArray = new JSONArray();
            jsonInstanceArray.put(jsonInputsWrapObject);

            jsonRoot.put("instances", jsonInstanceArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String jsonRootString = jsonRoot.toString();
        return RequestBody.create(JSON, jsonRootString);
    }

    protected void requestPrediction(String b64encoded) {
        RequestBody body = createRequestBodyWithB64EncodedImage(b64encoded);
        Request request = new Request.Builder()
                .url(mConfig.getParsedUrl())
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                isRequested.set(false);
                processError();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                processResponse(response.body().string());
                isRequested.set(false);
            }
        });
    }

    protected void processError() {
        Log.e(TAG, "processError");
        if (mPredictionListener != null) {
            mPredictionListener.onReceivedPredictionResult(
                    RESULT_STATUS_ERROR,
                    null);
        }
    }

    protected void processResponse(final String response) {
        // Log.v(TAG, "processResponse: " + response);
        try {
            float thresholdScore = (float) mConfig.getThreshold() / 100.f;
            // Parse response
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray jsonPredictions = jsonResponse.getJSONArray("predictions");

            // Use only first item, ignore others
            int predictionsLength = jsonPredictions.length();
            List<PredictionResult> predictionResultList = new ArrayList<>();

            for (int nPredictionIndex = 0; nPredictionIndex < predictionsLength; nPredictionIndex++) {
                JSONObject jsonPredictionObject = jsonPredictions.getJSONObject(nPredictionIndex);
                JSONArray jsonDetectedScores = jsonPredictionObject.getJSONArray("detection_scores");
                JSONArray jsonDetectedClasses = jsonPredictionObject.getJSONArray("detection_classes");
                JSONArray jsonDetectedBoxes = jsonPredictionObject.getJSONArray("detection_boxes");

                List<Float> scores = new ArrayList<>();
                List<Integer> classes = new ArrayList<>();
                List<RectF> boxes = new ArrayList<>();

                int scoreLength = jsonDetectedScores.length();
                for (int nDetectionIndex = 0; nDetectionIndex < scoreLength; nDetectionIndex++) {

                    float score = (float) jsonDetectedScores.getDouble(nDetectionIndex);
                    if (score > thresholdScore) {
                        // Add to list
                        int classLabel = jsonDetectedClasses.getInt(nDetectionIndex);
                        JSONArray jsonDetectedBox = jsonDetectedBoxes.getJSONArray(nDetectionIndex);

                        scores.add(score);
                        classes.add(classLabel);
                        boxes.add(new RectF(
                                (float) jsonDetectedBox.getDouble(1), // This index is twisted
                                (float) jsonDetectedBox.getDouble(0),
                                (float) jsonDetectedBox.getDouble(3),
                                (float) jsonDetectedBox.getDouble(2))
                        );
                    } else {
                        // Break below than threshold
                        break;
                    }
                }
                predictionResultList.add(new PredictionResult(scores, classes, boxes));
            }

            if (mPredictionListener != null) {
                mPredictionListener.onReceivedPredictionResult(
                        RESULT_STATUS_OK,
                        predictionResultList);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
