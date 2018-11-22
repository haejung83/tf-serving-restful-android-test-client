package example.haejung.recognizer;

import android.content.Context;

/**
 * RecognizerClient Class
 * Provide some function to recognize(predict)
 */
public class RecognizerClient {

    private RecognizerClientImpl mImpl;

    public RecognizerClient(Context context) {
        mImpl = new RecognizerClientImpl(context);
    }

    public PredictionListener getPredictionListener() {
        return mImpl.getPredictionListener();
    }

    public void setPredictionListener(PredictionListener predictionListener) {
        mImpl.setPredictionListener(predictionListener);
    }

    public Config getConfig() {
        return mImpl.getConfig();
    }

    public void setConfig(Config config) {
        mImpl.setConfig(config);
    }

    public void predictOneShot(byte[] image) {
        mImpl.predictOneShot(image);
    }

    public void predictOneShot(String b64Encoded) {
        mImpl.predictOneShot(b64Encoded);
    }

}

