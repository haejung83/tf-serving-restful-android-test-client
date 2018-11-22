package example.haejung.recognizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class Config {
    private static final String PreferenceName = "config";

    private String mHost;
    private int mPort;
    private String mModelName;
    private int mModelVersion;
    private int mThreshold;

    public Config() {
    }

    public String getHost() {
        return mHost;
    }

    public void setHost(String mHost) {
        this.mHost = mHost;
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int mPort) {
        this.mPort = mPort;
    }

    public String getModelName() {
        return mModelName;
    }

    public void setModelName(String mModelName) {
        this.mModelName = mModelName;
    }

    public int getModelVersion() {
        return mModelVersion;
    }

    public void setModelVersion(int mModelVersion) {
        this.mModelVersion = mModelVersion;
    }

    public int getThreshold() {
        return mThreshold;
    }

    public void setThreshold(int mThreshold) {
        this.mThreshold = mThreshold;
    }

    public void saveToDisk(Context context) {
        // Save to preference
        SharedPreferences.Editor editor = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE).edit();
        editor.putString("host", mHost);
        editor.putInt("port", mPort);
        editor.putString("model_name", mModelName);
        editor.putInt("model_version", mModelVersion);
        editor.putInt("threshold", mThreshold);
        editor.commit();
    }

    public void loadFromDisk(Context context) {
        // Load from preference
        SharedPreferences preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE);
        mHost = preferences.getString("host", "192.168.0.165");
        mPort = preferences.getInt("port", 8501);
        mModelName = preferences.getString("model_name", "half_plus_two");
        mModelVersion = preferences.getInt("model_version", -1);
        mThreshold = preferences.getInt("threshold", 50);
    }

    public String getParsedUrl() {
        Uri.Builder builder = Uri.parse("http://" + mHost + ":" + String.valueOf(mPort))
                .buildUpon()
                .appendPath("v1")
                .appendPath("models")
                .appendPath(mModelName);
        if(mModelVersion >= 0)
            builder.appendPath("versions").appendPath(String.valueOf(mModelVersion));

        return builder.build().toString().trim() + ":predict";
    }

}
