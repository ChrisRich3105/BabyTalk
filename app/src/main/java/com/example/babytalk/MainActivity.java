package com.example.babytalk;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.anastr.speedviewlib.ProgressiveGauge;

public class MainActivity extends AppCompatActivity {
    // Initialize log meassage header
    private static final String LOG_TAG = "MonitorService";
    // Request code to perform phone calls
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Is monitoring currently switched on (service needs to be created
    private boolean isMonitoring=false;
    // old ringer mode
    private int oldRingerMode;
    // Is app correctly initialized?
    private boolean appIsInitialized = false;
    // Instantiate activate/stop button
    private Button bService;
    // Declare AudioManager
    private AudioManager audioManager;
    // Declare shared preferences
    private SharedPreferences prefs;
    // Decalre sound level receiver
    private Receiver soundLevelReceiver;
    // Declare timer for pause time
    private CountDownTimer timer = null;
    // Declare info message text view
    private TextView tvInfoMessage;
    // Declare progressive gauge
    ProgressiveGauge progressiveGauge;
    // Initialize sound limit
    private int soundLimitValue = 0;

    // Declare request code for audio permission
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    // Declare permissions string array
    private String [] permissions = {Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isPermissionGiven = false;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Log.v(LOG_TAG,"Permission is granted");
            isPermissionGiven =  true;
        } else {

            Log.v(LOG_TAG,"Permission is revoked");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE}, 1);
            isPermissionGiven =  false;
        }

        if (isPermissionGiven){
            // Set main activity view
            setContentView(R.layout.activity_main);
            // Find service button in main activity
            bService=(findViewById(R.id.bActivate));
            // Find information text view
            tvInfoMessage = findViewById(R.id.tvInformationMessage);
            // Set information text blank
            tvInfoMessage.setText("");
            // Instantiate audio manager from system service
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            // Instantiate shared preferences
            prefs = PreferenceManager.getDefaultSharedPreferences(this);
            // Find progressive gauge in main activity
            progressiveGauge= (ProgressiveGauge) findViewById(R.id.progressiveGauge);

            // Get the sound limit setting from the shared preferences
            int storedSoundLimit= prefs.getInt(getString(R.string.preference_soundlimit_key),3000);

            // Find the seek bar in main activity
            SeekBar seekBar = (SeekBar) findViewById(R.id.soundLimitSeekBar);
            // Set seekbar progress to the stored sound limit from preferences
            seekBar.setProgress(storedSoundLimit);
            // Get the set progress of the seekbar
            soundLimitValue=seekBar.getProgress();
            // Set max limit of the gauge
            progressiveGauge.setMaxSpeed((float)soundLimitValue);
            // Remove text above gauge
            progressiveGauge.setSpeedTextSize(0);

            // Register a listener when seekbar changes
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Get the set level
                    soundLimitValue=seekBar.getProgress();
                    // Set max of the gauge
                    progressiveGauge.setMaxSpeed((float)soundLimitValue);
                    // Set the sound limit in the preferences
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(getString(R.string.preference_soundlimit_key),soundLimitValue);
                    editor.commit();
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    seekBar.setSecondaryProgress(seekBar.getProgress());
                }
            });
            appIsInitialized = true;
        } else
            // Set blank screen
            setContentView(R.layout.blank);

    }
    @Override
    protected void onPause() {
        super.onPause();

        if(appIsInitialized) {
            // Unregister the sound level receiver
            unregisterReceiver(soundLevelReceiver);
            soundLevelReceiver = null;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(appIsInitialized) {
            // re-register the soundlevel receiver
            if (soundLevelReceiver == null) {
                soundLevelReceiver = new Receiver();
                registerReceiver(soundLevelReceiver, new IntentFilter(getResources().getString(R.string.broadcast_sound_level_URL)));
            }
            // check if service is running
            if (!isServiceRunning(MonitorService.class)) {
                // Start the monitoring service
                startService(new Intent(this, MonitorService.class));
                // Reset the isMonitoring flag
                isMonitoring = false;
                // Set service button to activate
                bService.setText("Start");
                bService.setBackgroundResource(R.drawable.activateshape);
            } else {
                if (MonitorService.getMonitoringState()) {
                    // Monitoring is already active
                    isMonitoring = true;
                    // set service button to stop
                    bService.setText("Stop");
                    bService.setBackgroundResource(R.drawable.stopshape);
                }
            }
        }
    }
    public void onDestroy() {
        super.onDestroy();
        // Should not be called from normal lifecycle but when Activity is killed I got an exception
        if(soundLevelReceiver!= null){
            // unregister soundlevel receiver
            unregisterReceiver(soundLevelReceiver);
            soundLevelReceiver=null;
        }
        if(!MonitorService.getMonitoringState()){
            // stop the service if it is running
            stopService(new Intent(this,MonitorService.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 1:
                // Create flag showing if all permissions were granted
                boolean isPerpermissionForAllGranted = false;
                // Loop though all permissions and check that they were granted
                if (grantResults.length > 0 && permissions.length==grantResults.length) {
                    for (int i = 0; i < permissions.length; i++){
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                            isPerpermissionForAllGranted=true;
                        }else{
                            // Reset flag
                            isPerpermissionForAllGranted=false;
                        }
                    }
                    // Debug message
                    Log.i(LOG_TAG, "Permissions Granted.");
                } else {
                    isPerpermissionForAllGranted=false;
                    Log.e(LOG_TAG, "Permissions Denied.");
                }
                if(isPerpermissionForAllGranted){
                    // Set main activity view
                    setContentView(R.layout.activity_main);
                    // Find service button in main activity
                    bService=(findViewById(R.id.bActivate));
                    // Find information text view
                    tvInfoMessage = findViewById(R.id.tvInformationMessage);
                    // Set information text blank
                    tvInfoMessage.setText("");
                    // Instantiate audio manager from system service
                    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    // Instantiate shared preferences
                    prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    // Find progressive gauge in main activity
                    progressiveGauge= (ProgressiveGauge) findViewById(R.id.progressiveGauge);

                    // Get the sound limit setting from the shared preferences
                    int storedSoundLimit= prefs.getInt(getString(R.string.preference_soundlimit_key),3000);

                    // Find the seek bar in main activity
                    SeekBar seekBar = (SeekBar) findViewById(R.id.soundLimitSeekBar);
                    // Set seekbar progress to the stored sound limit from preferences
                    seekBar.setProgress(storedSoundLimit);
                    // Get the set progress of the seekbar
                    soundLimitValue=seekBar.getProgress();
                    // Set max limit of the gauge
                    progressiveGauge.setMaxSpeed((float)soundLimitValue);
                    // Remove text above gauge
                    progressiveGauge.setSpeedTextSize(0);

                    // Register a listener when seekbar changes
                    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            // Get the set level
                            soundLimitValue=seekBar.getProgress();
                            // Set max of the gauge
                            progressiveGauge.setMaxSpeed((float)soundLimitValue);
                            // Set the sound limit in the preferences
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt(getString(R.string.preference_soundlimit_key),soundLimitValue);
                            editor.commit();
                        }
                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }
                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            seekBar.setSecondaryProgress(seekBar.getProgress());
                        }
                    });
                    appIsInitialized = true;
                }
                break;
        }

    }

    public void goToSettings(View view){
        // Change to settings activity
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    public void startMonitoring(View view){
        // Monitoring is started
        if(!isMonitoring && timer==null) {
            // Get the pause time from the preferences
            int pauseTime = prefs.getInt(getString(R.string.preference_pause_value_key), 30);
            // Check in preferences if pause time is activate
            boolean pauseActivated = prefs.getBoolean(getString(R.string.preference_pause_key), false);
            // Set service button to stop
            bService.setText("Stop");
            bService.setBackgroundResource(R.drawable.stopshape);

            // Debug message
            Log.i(LOG_TAG, "Service running - Pause" + String.valueOf(pauseActivated) + " Pause time: " + String.valueOf(pauseTime));
            if (pauseActivated) {
                // Pause is activated, let timer run
                timer = new CountDownTimer(pauseTime * 1000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        // Debug message
                        Log.i(LOG_TAG, "Pause time remaining: " + String.valueOf(millisUntilFinished / 1000) + "s");
                        // Set infromation message view
                        tvInfoMessage.setText("Timer: "+String.valueOf(millisUntilFinished / 1000) + "s");
                    }

                    public void onFinish() {
                        // Debug message
                        Log.i(LOG_TAG, "Pause time exceeded. Start monitoring.");
                        // Set information text
                        tvInfoMessage.setText("Monitoring \nactive");
                        // Start the monitoring
                        MonitorService.startMonitoring();
                        // Set flag
                        isMonitoring = true;
                        // Reset timer
                        timer=null;
                    }
                };
                // Start timer execution
                timer.start();
            }else{
                // Start monitoring directly when no pause time is activated
                MonitorService.startMonitoring();
                // Set flag
                isMonitoring = true;
                // Set information text
                bService.setText("Stop");
                bService.setBackgroundResource(R.drawable.stopshape);
            }

            // Check if phone should be put into silent mode
            if (prefs.getBoolean(getString(R.string.preference_silent_mode_key), false) == true) {
                // Reset oldRingerMode to an invalid value
                oldRingerMode = -1;
                // Check if special permission must be granted to set ringer mode
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notificationManager.isNotificationPolicyAccessGranted()) {
                    // Start activity to set permission
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivity(intent);
                } else {
                    // Set old ringer mode
                    oldRingerMode = audioManager.getRingerMode();
                    // Set ringer mode to SILENT
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                }
            }
        }else{
            // Stop the monitoring
            MonitorService.stopMonitoring();
            // Set flag
            isMonitoring=false;
            // Set service button to activate
            bService.setText("Activate");
            bService.setBackgroundResource(R.drawable.activateshape);
            // Set information text
            tvInfoMessage.setText("Monitoring\nstopped");
            // Check if phone was put in silent mode by this app
            if (prefs.getBoolean(getString(R.string.preference_silent_mode_key), false) == true && oldRingerMode != -1) {
                // Set to normal mode
                audioManager.setRingerMode(oldRingerMode);
            }
            // Check if timer is still instantiated
            if(timer!=null){
                // Cancel timer
                timer.cancel();
                // Set to null
                timer = null;
            }
        }
    }

    // According to https://stackoverflow.com/questions/3907713/how-to-send-and-receive-broadcast-message
    private class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // Only get the sound level when pause timer is not runnning
            if(timer==null){
                // Get sound level via broadcast
                double level = arg1.getExtras().getDouble("level");
                // Set the gauge level for main activity
                progressiveGauge.speedPercentTo((int)((level/progressiveGauge.getMaxSpeed())*100),100);
            }
        }
    }

    // https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
    private boolean isServiceRunning(Class<?> serviceClass) {
        // Instantiate activity manager
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        // Get runnning services
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            // Check if this is the service we are looking for
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}


