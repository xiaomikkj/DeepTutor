package com.deeptutor.mobile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int REQ_RECORD_AUDIO = 4100;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Microphone: request once at startup. Capacitor's
        // BridgeWebChromeClient grants the WebView's getUserMedia request only
        // when RECORD_AUDIO is already granted, so the DeepTutor web app's
        // voice input (useVoiceRecorder → /api/v1/voice/stt) works on Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[] {Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            }
        }
    }
}
