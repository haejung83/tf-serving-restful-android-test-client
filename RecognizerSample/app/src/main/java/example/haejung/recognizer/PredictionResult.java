package example.haejung.recognizer;

import android.graphics.RectF;

import java.util.List;

/**
 * PredictionResult Class
 * Containing result of prediction
 */
public class PredictionResult {
    public List<Float> mScores;
    public List<Integer> mClasses;
    public List<RectF> mBoundingBoxes;

    public PredictionResult() {
    }

    public PredictionResult(List<Float> mScores, List<Integer> mClasses, List<RectF> mBoundingBoxes) {
        this.mScores = mScores;
        this.mClasses = mClasses;
        this.mBoundingBoxes = mBoundingBoxes;
    }
}

