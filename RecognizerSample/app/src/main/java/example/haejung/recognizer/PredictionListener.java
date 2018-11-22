package example.haejung.recognizer;

import java.util.List;

/**
 * PredictionListener Interface
 */
public interface PredictionListener {

    /**
     * PredictionResultStatus Enumeration
     */
    enum PredictionResultStatus {
        RESULT_STATUS_ERROR(0, "result_status_error"),
        RESULT_STATUS_OK(1, "result_status_ok");

        private int mType;
        private String mStringType;

        PredictionResultStatus(int type, String stringType) {
            mType = type;
            mStringType = stringType;
        }

        public int getType() {
            return mType;
        }

        public String getStringType() {
            return mStringType;
        }
    }

    /**
     * An interface for receive data for prediction result
     *
     * @param predictionResult
     */
    void onReceivedPredictionResult(PredictionResultStatus predictionResultStatus,
                                    List<PredictionResult> predictionResult);

}

