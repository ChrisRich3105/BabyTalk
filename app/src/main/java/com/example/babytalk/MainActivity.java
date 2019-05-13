package com.example.babytalk;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "MonitorService";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private boolean isMonitoring=false;
    private Button bService;
    private AudioManager audioManager;
    private SharedPreferences prefs;
    private Receiver soundLevelReceiver;
    private CountDownTimer timer = null;
    private TextView tvSoundLevel;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bService=(findViewById(R.id.bActivate));
        tvSoundLevel = findViewById(R.id.tvSoundLevel);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        requestCallPermission();
        // TODO got error for the first time granting it/same structure as telephone
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

    }
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(soundLevelReceiver);
        soundLevelReceiver=null;
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(soundLevelReceiver==null){
            soundLevelReceiver =  new Receiver();
            registerReceiver(soundLevelReceiver, new IntentFilter(getResources().getString(R.string.broadcast_sound_level_URL)));
        }
        // check if service is running
        if(!isServiceRunning(MonitorService.class)){
            startService(new Intent(this, MonitorService.class));
            isMonitoring=false;
            bService.setText("Start");
            bService.setBackgroundResource(R.drawable.activateshape);
        }else{
            if(MonitorService.getMonitoringState()){
                isMonitoring=true;
                bService.setText("Stop");
                bService.setBackgroundResource(R.drawable.stopshape);
            }
        }
    }
    public void onDestroy() {
        super.onDestroy();
        if(soundLevelReceiver!= null){ // Should not be called from normal lifecycle but when Activity is killed I got an exception
            unregisterReceiver(soundLevelReceiver);
            soundLevelReceiver=null;
        }
        if(!MonitorService.getMonitoringState()){
            stopService(new Intent(this,MonitorService.class));
        }
    }

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

    public void goToSettings(View view){
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    public void startMonitoring(View view){

        if(!isMonitoring && timer==null) {
            int pauseTime = prefs.getInt(getString(R.string.preference_pause_value_key), 0);
            boolean pauseActivated = prefs.getBoolean(getString(R.string.preference_pause_key), false);
            bService.setText("Stop");
            bService.setBackgroundResource(R.drawable.stopshape);

            Log.i(LOG_TAG, "Service running - Pause" + String.valueOf(pauseActivated) + " Pause time: " + String.valueOf(pauseTime));
            if (pauseActivated) {

                timer = new CountDownTimer(pauseTime * 1000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        Log.i(LOG_TAG, "Pause exceeded: " + String.valueOf(millisUntilFinished / 1000));
                        tvSoundLevel.setText("Timer: "+String.valueOf(millisUntilFinished / 1000));
                    }

                    public void onFinish() {
                        Log.i(LOG_TAG, "Pause time exceeded. Start monitoring.");
                        MonitorService.startMonitoring();
                        isMonitoring = true;
                        timer=null;
                    }
                };
                timer.start();
            }else{
                MonitorService.startMonitoring();
                isMonitoring = true;
                bService.setText("Stop");
                bService.setBackgroundResource(R.drawable.stopshape);
            }

            if (prefs.getBoolean(getString(R.string.preference_silent_mode_key), false) == true) {
                // set to silent mode
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            }
        }else{
            MonitorService.stopMonitoring();
            isMonitoring=false;
            bService.setText("Activate");
            bService.setBackgroundResource(R.drawable.activateshape);
            if (prefs.getBoolean(getString(R.string.preference_silent_mode_key), false) == true) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
            if(timer!=null){
                timer.cancel();
                timer = null;
            }
        }
    }

    // Permission in manifest is not enough for calling
    public void requestCallPermission(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CALL_PHONE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // request the permission
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CALL_PHONE},PERMISSION_REQUEST_CODE);
            }
        }
    }
    // According to https://stackoverflow.com/questions/3907713/how-to-send-and-receive-broadcast-message
    private class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if(timer==null){
            double level = arg1.getExtras().getDouble("level");
            tvSoundLevel.setText(String.valueOf(level));
            }
        }
    }

    // https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}


