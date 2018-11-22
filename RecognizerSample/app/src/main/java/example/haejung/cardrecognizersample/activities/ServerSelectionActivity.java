package example.haejung.cardrecognizersample.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import example.haejung.cardrecognizersample.R;
import example.haejung.recognizer.Config;

public class ServerSelectionActivity extends AppCompatActivity {
    private static final String TAG = ServerSelectionActivity.class.getSimpleName();
    private EditText editHost;
    private EditText editPort;
    private EditText editModelName;
    private EditText editModelVersion;
    private TextView textThresholdValue;
    private SeekBar seekbarThreshold;
    private Config config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_selection);

        mapConfig();
        bindingUI();
        setupUI();
    }

    private void mapConfig() {
        config = new Config();
        config.loadFromDisk(this);
    }

    // "http://192.168.0.165:8501/v1/models/shinhan/versions/0:predict"
    private void bindingUI() {
        editHost = findViewById(R.id.editHost);
        editPort = findViewById(R.id.editPort);
        editModelName = findViewById(R.id.editModelName);
        editModelVersion = findViewById(R.id.editModelVersion);
        textThresholdValue = findViewById(R.id.textThresholdValue);
        seekbarThreshold = findViewById(R.id.seekbarThreshold);

        seekbarThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.i(TAG, "onProgressChanged: " + String.valueOf(progress));
                textThresholdValue.setText(String.valueOf(progress * 5));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to do
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Nothing to do
            }
        });

        findViewById(R.id.btnLaunch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isValidConfig()) {
                    saveConfig();
                    launchCameraActivity();
                }
            }
        });
    }

    private void setupUI() {
        editHost.setText(config.getHost());
        editPort.setText(String.valueOf(config.getPort()));
        editModelName.setText(config.getModelName());
        if (config.getModelVersion() >= 0)
            editModelVersion.setText(String.valueOf(config.getModelVersion()));
        seekbarThreshold.setProgress(config.getThreshold() / 5);
    }

    private boolean isValidConfig() {
        try {
            if (TextUtils.isEmpty(editHost.getText()))
                return false;

            if (!TextUtils.isEmpty(editPort.getText()) && Integer.parseInt(editPort.getText().toString()) < 0)
                return false;

            if (TextUtils.isEmpty(editModelName.getText()))
                return false;

            if (!TextUtils.isEmpty(editModelVersion.getText()) && Integer.parseInt(editModelVersion.getText().toString()) < 0)
                return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void saveConfig() {
        config.setHost(editHost.getText().toString().trim());
        config.setPort(Integer.parseInt(editPort.getText().toString().trim()));
        config.setModelName(editModelName.getText().toString().trim());
        String modelVersion = editModelVersion.getText().toString().trim();
        if (modelVersion.length() > 0)
            config.setModelVersion(Integer.parseInt(modelVersion));
        else
            config.setModelVersion(-1);
        config.setThreshold(seekbarThreshold.getProgress() * 5);
        config.saveToDisk(ServerSelectionActivity.this);
    }

    private void launchCameraActivity() {
        Log.d(TAG, "Parsed URL: " + config.getParsedUrl());
        Intent intent = new Intent(ServerSelectionActivity.this, CameraActivity.class);
        startActivity(intent);
    }



}
