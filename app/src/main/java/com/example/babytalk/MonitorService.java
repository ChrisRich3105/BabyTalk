package com.example.babytalk;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.RemoteViews;

public class MonitorService extends Service implements SensorEventListener {
    // TODO comment variables and classes
    private static final String LOG_TAG = "MonitorService";
    private static final int ONGOING_NOTIFICATION_ID = 100;
    private static final String CHANNEL_ID = "100";
    private static final String CHANNEL_NAME = "BabyTalk";

    private Thread backgroundThread;
    private boolean isRunning = false;
    private AudioRecorder audioRecorder = null;
    private boolean calling = false;

    private Sensor sensor;
    private SensorManager sensorManager;
    private double filteredAcceleration; // Just a PT1 filter
    private SharedPreferences prefs;

    // read phone state
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        addNotificationForeground();
        monitorVoiceLevel();
        monitorAcceleration();
        addPhoneStateListener();

        return Service.START_STICKY;
    }

    public IBinder onBind(Intent arg0) {
        Log.i(LOG_TAG, "Service onBind");
        return null;
    }

    public void onDestroy() {
        isRunning = false;
        stopMonitorAcceleration();
        audioRecorder.close();
        Log.i(LOG_TAG, "Service destroyed");
    }

    // start service in foreground with a notification so it is not killed when memory is needed
    // https://stackoverflow.com/questions/44913884/android-notification-not-showing-on-api-26
    public void addNotificationForeground(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }

        // Create an Intent to get back to own activity https://developer.android.com/training/notify-user/navigation
        Intent resultIntent = new Intent(this, MainActivity.class);
        // Create the TaskStackBuilder and add the intent, which inflates the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        // Get the PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(getResources().getString(R.string.notification_title))
                .setContentText(getResources().getString(R.string.notification_message))
                .setVibrate(new long[]{100, 250})
                .setLights(Color.YELLOW, 500, 5000)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentIntent(resultPendingIntent);

        startForeground(ONGOING_NOTIFICATION_ID, mBuilder.build());
    }

    private void addPhoneStateListener(){
        telephonyManager=(TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        phoneStateListener=new PhoneStateListener(){
            @Override public void onCallStateChanged(int state, String incomingNumber){
                Log.i(LOG_TAG,"onCallStateChanged");
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    Log.i(LOG_TAG,"onCallStateChanged - IDLE");
                    audioRecorder.setMaxAmplitudeZero();
                    calling=false;
                    audioManager.setSpeakerphoneOn(false); // to have the timeout with next cycle
                    try {
                        Thread.sleep(1000); // There is a notification tone after that on my mobile - suppress first second
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else if( state == TelephonyManager.CALL_STATE_OFFHOOK){
                    Log.i(LOG_TAG,"onCallStateChanged - CALL_STATE_OFFHOOK");
                    if(prefs.getBoolean(getString(R.string.preference_speakerphone_key),false)== true){
                        try {
                            Thread.sleep(2000); // There is a notification tone after that on my mobile - suppress first second
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        audioManager.setSpeakerphoneOn(true);
                        // TODO set loudspeaker level to zero that the noise is not transfered to the baby or should talking be possible?
                    }
                }else if(state == TelephonyManager.CALL_STATE_RINGING){
                    Log.i(LOG_TAG,"onCallStateChanged - CALL_STATE_RINGING");
                }
            }
        };
        telephonyManager.listen(phoneStateListener,PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void monitorVoiceLevel(){
        audioRecorder=new AudioRecorder();
        audioRecorder.start();
        isRunning = true;

        final int motionTriggerLevel = prefs.getInt(getString(R.string.preference_motion_value_key), 0);
        final int pauseTime = prefs.getInt(getString(R.string.preference_pause_value_key), 0);
        final boolean pauseActivated = prefs.getBoolean(getString(R.string.preference_pause_key),false);
        final boolean motionTriggerActivated = prefs.getBoolean(getString(R.string.preference_motion_key),false);

        backgroundThread=new Thread(new Runnable() {
            public void run() {
                Log.i(LOG_TAG, "Service running");
                if (pauseActivated)
                    try {
                        Thread.sleep(pauseTime * 1000);
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "Thread InterruptedException");
                    }
                // TODO maybe add a timer showing the pause time on the main activity?
                Log.i(LOG_TAG, "Pause time exceeded. Start monitoring.");
                while (isRunning){
                    try {
                        Thread.sleep(1000);
                        Log.i(LOG_TAG, "Current maximum amplitude " + audioRecorder.getMaxAmplitude());
                        /* TODO readFromConfig - comment CRE: not read noise level setting from config but from main activity, as it is not included in the preferences page but it´s kind of a "live-setting" */
                        if( ((audioRecorder.getMaxAmplitude() > 10000)
                                || (motionTriggerActivated && (getMotion() > 0.2 + ((double)motionTriggerLevel / 50)))) && !calling){
                            Log.i(LOG_TAG, "Perform call");
                            calling = true;
                            performPhoneCall();
                        }
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "Thread InterruptedException");
                    }
                }
                stopSelf();
            }
        });
        backgroundThread.start();
    }

    public void monitorAcceleration(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stopMonitorAcceleration(){
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType()==Sensor.TYPE_LINEAR_ACCELERATION){
            //Log.i(LOG_TAG,"AccX: "+event.values[0]);
            //Log.i(LOG_TAG,"AccY: "+event.values[1]);
            //Log.i(LOG_TAG,"AccZ: "+event.values[2]);
            // Calculate vectorsum
            double accelerationSum = Math.sqrt(event.values[0]*event.values[0]+event.values[1]*event.values[1]+event.values[2]*event.values[2]);
            filteredAcceleration = 0.95 * filteredAcceleration + 0.05 * accelerationSum; // No idea about timeconstant yet
            Log.i(LOG_TAG,"Acceleration: "+filteredAcceleration);
        }
    }
    private double getMotion(){
        return filteredAcceleration;
    }

    private void performPhoneCall(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String phoneNumber = prefs.getString(getString(R.string.preference_phonenumber_key), null);
        Log.i(LOG_TAG,"Phone number: "+phoneNumber);
        Intent phoneIntent = new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+phoneNumber));
        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG,"Permission missing");
            return;
        }
        startActivity(phoneIntent);
    }
}

