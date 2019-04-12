package com.example.babytalk;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private boolean isMonitoring=false;
    private Button bService;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bService=(findViewById(R.id.bService));

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    public void goToSettings(View view){
        startActivity(new Intent(MainActivity.this, Configuration.class));
    }

    public void startMonitoring(View view){
        if(!isMonitoring) {
            startService(new Intent(this, MonitorService.class));
            isMonitoring=true;
            bService.setText("Stop monitoring ...");
            // TODO Check if Service started successfully
        }else{
            stopService(new Intent(this,MonitorService.class));
            isMonitoring=false;
            bService.setText("Start monitoring ...");
        }
    }
}
