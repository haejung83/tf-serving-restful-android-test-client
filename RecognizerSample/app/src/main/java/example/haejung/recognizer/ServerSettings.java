package example.haejung.recognizer;

import android.support.annotation.NonNull;
import android.text.TextUtils;

/**
 * ServerSettings Class
 */
public class ServerSettings {
    private static final String CONST_PORT_SEPARATOR = ":";
    private static final String CONST_PATH_SEPARATOR = "/";
    private String mServerAddress;
    private String mAPIPath;
    private String mAbsolutePath;
    private int mServerPort;

    @NonNull
    public static ServerSettings createDefault() {
        // FIXME: It's not fixed yet.
        return new ServerSettings(
                "http://192.168.0.165",
                "v1/models/shinhan/versions/0:predict",
                8501);
    }

    public ServerSettings(String mServerAddress, String mAPIPath, int mServerPort) {
        this.mServerAddress = mServerAddress;
        this.mAPIPath = mAPIPath;
        this.mServerPort = mServerPort;
    }

    public String getServerAddress() {
        return mServerAddress;
    }

    public void setServerAddress(String mServerAddress) {
        this.mServerAddress = mServerAddress;
        this.mAbsolutePath = null;
    }

    public int getServerPort() {
        return mServerPort;
    }

    public void setServerPort(int mServerPort) {
        this.mServerPort = mServerPort;
        this.mAbsolutePath = null;
    }

    public String getAPIPath() {
        return mAPIPath;
    }

    public void setAPIPath(String mAPIPath) {
        this.mAPIPath = mAPIPath;
        this.mAbsolutePath = null;
    }

    private void buildAbsolutePath() {
        if (!TextUtils.isEmpty(mServerAddress)) {
            if (mServerAddress.endsWith(CONST_PATH_SEPARATOR))
                mServerAddress = mServerAddress.substring(
                        0,
                        mServerAddress.lastIndexOf(CONST_PATH_SEPARATOR));

            StringBuilder sb = new StringBuilder(mServerAddress);
            sb.append(CONST_PORT_SEPARATOR);
            sb.append(Integer.toString(mServerPort));
            if (!TextUtils.isEmpty(mAPIPath)) {
                sb.append(CONST_PATH_SEPARATOR);
                sb.append(mAPIPath);
            }
            mAbsolutePath = sb.toString();
        }
    }

    public String getAbsoluteUrl() {
        if (TextUtils.isEmpty(mAbsolutePath))
            buildAbsolutePath();

        return mAbsolutePath;
    }

}

