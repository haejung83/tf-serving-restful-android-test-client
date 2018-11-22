package example.haejung.recognizer;

import android.support.annotation.NonNull;

/**
 * RecognizerSettings Class
 */
public class RecognizerSettings {
    private int mPredictionInterval;
    private String mPredictionModelName;

    @NonNull
    public static RecognizerSettings createDefault() {
        return new RecognizerSettings(1000, null);
    }

    public RecognizerSettings() {
        // Nothing to do
    }

    public RecognizerSettings(int mPredictionInterval, String mPredictionModelName) {
        this.mPredictionInterval = mPredictionInterval;
        this.mPredictionModelName = mPredictionModelName;
    }

    public int getPredictionInterval() {
        return mPredictionInterval;
    }

    public void setPredictionInterval(int mPredictionInterval) {
        this.mPredictionInterval = mPredictionInterval;
    }

    public String getPredictionModelName() {
        return mPredictionModelName;
    }

    public void setPredictionModelName(String mPredictionModelName) {
        this.mPredictionModelName = mPredictionModelName;
    }
}

