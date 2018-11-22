package example.haejung.cardrecognizersample.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import example.haejung.cardrecognizersample.utils.PixelConverter;
import example.haejung.recognizer.PredictionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BoundingBoxDrawView extends View {
    private static final String TAG = BoundingBoxDrawView.class.getSimpleName();
    private static final int DRAW_TEXT_SIZE = 12; // DP

    private Paint paint;
    private Rect clippingArea;
    private List<PredictionResult> predictionResultList;
    private Map<Integer, String> labelMap;
    private List<Integer> colorMap;
    private int drawTextPixelSize;

    public BoundingBoxDrawView(Context context) {
        super(context);
        initVariables();
    }

    public BoundingBoxDrawView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initVariables();
    }

    public BoundingBoxDrawView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVariables();
    }

    public BoundingBoxDrawView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initVariables();
    }

    private void initVariables() {
        buildColorMap();

        drawTextPixelSize = (int) PixelConverter.convertDpToPixel(DRAW_TEXT_SIZE, getContext());

        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setTextSize(drawTextPixelSize);
    }

    private void buildColorMap() {
        colorMap = new ArrayList<>();
        colorMap.add(Color.YELLOW);
        colorMap.add(Color.RED);
        colorMap.add(Color.GREEN);
        colorMap.add(Color.MAGENTA);
        colorMap.add(Color.BLUE);
        colorMap.add(Color.CYAN);
    }

    public void setClippingArea(Rect clippingArea) {
        this.clippingArea = new Rect(
                clippingArea.left,
                clippingArea.top,
                clippingArea.left + clippingArea.right,
                clippingArea.top + clippingArea.bottom
        );
        invalidate();
    }

    public void setPredictionResult(List<PredictionResult> predictionResultList) {
        this.predictionResultList = predictionResultList;
        invalidate();
    }

    public void setLabelMap(Map<Integer, String> labelMap) {
        this.labelMap = labelMap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Log.v(TAG, "onDraw");
        drawClippingArea(canvas);
        drawBoundingBoxWithText(canvas);
    }

    private void drawClippingArea(Canvas canvas) {
        if (clippingArea != null) {
            paint.setColor(Color.RED);
            canvas.drawRect(clippingArea, paint);
            canvas.drawText("Clipping Area", clippingArea.left, clippingArea.top - 5, paint);
        }
    }

    private void drawBoundingBoxWithText(Canvas canvas) {
        if (predictionResultList == null)
            return;

        int clipWidth = clippingArea.right - clippingArea.left;
        int clipHeight = clippingArea.bottom - clippingArea.top;

        for (PredictionResult result : predictionResultList) {
            for (int nClassIndex = 0; nClassIndex < result.mClasses.size(); nClassIndex++) {
                float score = result.mScores.get(nClassIndex) * 100.f;
                int classIndex = result.mClasses.get(nClassIndex);
                RectF box = result.mBoundingBoxes.get(nClassIndex);

                int nColorIndex = classIndex % colorMap.size();
                paint.setColor(colorMap.get(nColorIndex));

                RectF calculatedRect = new RectF(
                        clipWidth * box.left + clippingArea.left,
                        clipHeight * box.top + clippingArea.top,
                        clipWidth * box.right + clippingArea.left,
                        clipHeight * box.bottom + clippingArea.top
                );
                String label = (labelMap != null) ? labelMap.get(classIndex) : String.valueOf(classIndex);
                String markingText = String.format("%s (%3.1f%s)", label, score, "%");
                canvas.drawRect(calculatedRect, paint);
                canvas.drawText(markingText, calculatedRect.left, calculatedRect.top - 5, paint);
                canvas.drawText(markingText, drawTextPixelSize * 3, drawTextPixelSize * (nClassIndex + 3), paint);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

}
