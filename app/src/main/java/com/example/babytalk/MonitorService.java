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
import android.content.BroadcastReceiver;
import android.widget.Toast;

import java.lang.reflect.Method;

/*
 * Background service that is responsible for monitoring voice level and motion of the child and to trigger a phonecall according to the configuration.
 * Additionally it passes the current preprocessed audio-level to the main activity to adjust the sensitivity accordingly.
 */

public class MonitorService extends Service implements SensorEventListener {

    private static final String LOG_TAG = "MonitorService"; // LOG-Tag

    private static final int ONGOING_NOTIFICATION_ID = 100; // Notification ID
    private static final String CHANNEL_ID = "100"; // Notification Channel-ID
    private static final String CHANNEL_NAME = "BabyTalk"; // Notification Channel-Name

    private static boolean monitoringActive=false; // Is monitoring currently active
    private static AudioRecorder audioRecorder = null; // Audio recorder
    private static MonitorService cService = null; // Static reference to currently created monitor service
    private static SharedPreferences prefs; // Access the app configuration

    private Thread backgroundThread; // Thread for monitoring the current Audio-Level while staying responsive
    private boolean isRunning = false; // is monitoring with thread running
    private boolean calling = false; // Is a phonecall performed


    // Sensor for getting the current acceleration
    private Sensor sensor;
    private SensorManager sensorManager;
    private double filteredAcceleration; // Just a PT1 filter

    // read phone state - is call performed, was it answered
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    // Set audio settings
    private AudioManager audioManager;

    /**
     *  Service is started
     */
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        cService = this; // Set current background service - only one possible

        monitorAcceleration(); // monitor movement
        addPhoneStateListener(); // monitor Call-State
        monitorVoiceandAccLevel(); // monitor voice and acceleration level

        return Service.START_STICKY; // Service would be recreated if killed having to less memory (as it is a background service this should not be the case)
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(LOG_TAG, "Service onBind");
        return null;
    }
    /**
     *  Service is destroyed
     */
    public void onDestroy() {
        isRunning = false; // stop monitoring Audio-levels
        stopMonitorAcceleration(); // acceleration is not monitored any more
        if (audioRecorder != null)
            audioRecorder.close(); // Free resources audio recorder

        Log.i(LOG_TAG, "Service destroyed");
    }

    /**
     *  Monitor the current call state. If call was performed, go back to monitoring. Set mobile phone to speakerphone when somebody picked up.
     */
    private void addPhoneStateListener() {
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                Log.i(LOG_TAG, "onCallStateChanged");
                if (state == TelephonyManager.CALL_STATE_IDLE) { // Call is finished
                    Log.i(LOG_TAG, "onCallStateChanged - IDLE");
                    try {
                        Thread.sleep(5000); // Tone from hanging should not trigger a scond call
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    audioRecorder.setMaxAmplitudeZero(); // Go back to new monitoring
                    calling = false; // Call is over
                    audioManager.setSpeakerphoneOn(false); // Speakerphone to false again

                } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) { // Phone picked up
                    Log.i(LOG_TAG, "onCallStateChanged - CALL_STATE_OFFHOOK");
                    if (prefs.getBoolean(getString(R.string.preference_speakerphone_key), false) == true) { // If phone should go speakerphone
                        try {
                            Thread.sleep(1000); // There is a notification tone after that on my mobile - suppress first second
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        audioManager.setSpeakerphoneOn(true); // microphone is more sensitve and talking to the baby is possible
                        // TODO set loudspeaker level to zero that the noise is not transfered to the baby or should talking be possible?
                    }
                } else if (state == TelephonyManager.CALL_STATE_RINGING) {
                    Log.i(LOG_TAG, "onCallStateChanged - CALL_STATE_RINGING");
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE); // listen to call state
    }

    /**
     *  Start to monitor the current voice level
     */
    private void monitorVoiceandAccLevel() {
        audioRecorder = new AudioRecorder();
        audioRecorder.start(); // Start audio recorder
        isRunning = true;


        // create thread for monitoring babycrying and motion
        backgroundThread = new Thread(new Runnable() {
            public void run() {
                while (isRunning) {
                    try {
                        // get motion configuration and limits
                        final int motionTriggerLevel = prefs.getInt(getString(R.string.preference_motion_value_key), 0);
                        final boolean motionTriggerActivated = prefs.getBoolean(getString(R.string.preference_motion_key), false);
                        Thread.sleep(200); // reduce frequency to save power
                        // Broadcast current noise level
                        Log.i(LOG_TAG, "Current maximum amplitude " + (int)audioRecorder.getCurrentAmplitudeAvg() + " Sound limit: " + prefs.getInt(getString(R.string.preference_soundlimit_key),3000));
                        Log.i(LOG_TAG, "Current acceleration " + getMotion() + " Accel limit: " + (0.2 + ((double) motionTriggerLevel / 25)));

                        // send out current noise level via a broadcast to display on main activity
                        Intent intent=new Intent();
                        intent.setAction(getResources().getString(R.string.broadcast_sound_level_URL));
                        intent.putExtra("level", audioRecorder.getCurrentAmplitudeAvg());
                        sendBroadcast(intent);

                        if(monitoringActive){ // When activated
                            if ((((int)audioRecorder.getCurrentAmplitudeAvg() > prefs.getInt(getString(R.string.preference_soundlimit_key),3000)) // audio level exceeded
                                    || (motionTriggerActivated && (getMotion() > 0.2 + ((double) motionTriggerLevel / 25)))) && !calling) { // motion level exceeded
                                Log.i(LOG_TAG, "Perform call");
                                calling = true;
                                performPhoneCall(); // Call mummy/daddy
                            }
                        }
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "Thread InterruptedException");
                    }
                }
                stopSelf(); // free resources
            }
        });
        backgroundThread.start();
    }
    /**
     *  Start to monitor the current motion level
     */
    private void monitorAcceleration() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE); // get sensor-manager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION); // get linear acc
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL); // register listener
    }

    /**
     *  unregister sensor listener
     */
    private void stopMonitorAcceleration() {
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
    }
    /**
     *  Sensor listener method that has to be overwritten
     */
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }
    /**
     *  If sensor data changes
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            //Log.i(LOG_TAG,"AccX: "+event.values[0]);
            //Log.i(LOG_TAG,"AccY: "+event.values[1]);
            //Log.i(LOG_TAG,"AccZ: "+event.values[2]);
            // Calculate vectorsum
            double accelerationSum = Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            filteredAcceleration = 0.95 * filteredAcceleration + 0.05 * accelerationSum; // No idea about timeconstant yet
        }
    }

    /**
     *  Get current motion level
     */
    private double getMotion() {
        return filteredAcceleration;
    }
    /**
     *  Perform the phonecall
     */
    private void performPhoneCall() {

        String phoneNumber = prefs.getString(getString(R.string.preference_phonenumber_key), null);
        Log.i(LOG_TAG, "Phone number: " + phoneNumber);

        // Perform video call
        //TODO try with skype
        if (prefs.getBoolean(getString(R.string.preference_video_call_key), false) == true) {
            // perform video call over whats app from https://stackoverflow.com/questions/51070748/place-a-whatsapp-video-call
            /*
            Cursor cursor = getContentResolver ()
                    .query (
                            ContactsContract.Data.CONTENT_URI,
                            new String [] { ContactsContract.Data._ID },
                            ContactsContract.RawContacts.ACCOUNT_TYPE + " = 'com.whatsapp' " +
                                  //  "AND " + ContactsContract.Data.MIMETYPE + " = 'vnd.android.cursor.item/vnd.com.whatsapp.video.call' " +
                                    "AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE '%" + phoneNumber + "%'",
                            null,
                            ContactsContract.Contacts.DISPLAY_NAME
                    );

            if (cursor != null) {
                long id = 0;
                if(cursor.moveToNext()){
                    id = cursor.getLong (cursor.getColumnIndex (ContactsContract.Data._ID));
                    if (!cursor.isClosed ()) {
                        cursor.close ();
                    }
                    Log.i(LOG_TAG, "Found id: "+ String.valueOf(id));
                }else{
                    Log.i(LOG_TAG, "Can't move to first");
                }
                Log.i(LOG_TAG, "CALL WA");
                Intent phoneIntent = new Intent ();
                phoneIntent.setAction (Intent.ACTION_VIEW);
                phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                phoneIntent.setDataAndType (Uri.parse ("content://com.android.contacts/data/" + id), "vnd.android.cursor.item/vnd.com.whatsapp.voip.call");
                phoneIntent.setPackage ("com.whatsapp");

                startActivity (phoneIntent);
            }else{

                Log.i(LOG_TAG, "Nothing found in cursor");
            }*/
            Intent phoneIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
            phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            phoneIntent.putExtra("videocall", true);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG, "Permission missing");
                return;
            }
            startActivity(phoneIntent);

        } else {
            // perform standard call over whats app
            Intent phoneIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
            phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Go back to old screen state
            phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND); // Started from background service
            // Check permission again if not granted in the beginning
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG, "Permission missing");
                Toast.makeText(this, "Permission missing!", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(phoneIntent);
        }

    }
    /***************************  static functions ***********************************/
    /**
     *  Activate monitoring of voice and motion level from MainActivity
     */
    protected static void startMonitoring(){
            audioRecorder.setMaxAmplitudeZero();
            addNotificationForeground();
            monitoringActive=true;
    }
    /**
     *  Stop monitoring of voice and motion level from MainActivity
     */
    protected static void stopMonitoring(){
        cancelNotificationForeground();
        monitoringActive=false;
    }

    /**
     *  Activate monitoring of voice and motion level from MainActivity
     *  Start service in foreground with a notification so it is not killed when memory is needed
     *   https://stackoverflow.com/questions/44913884/android-notification-not-showing-on-api-26
     */
    public static void addNotificationForeground() {
        NotificationManager notificationManager = (NotificationManager) cService.getSystemService(Context.NOTIFICATION_SERVICE);

        // To perform on legacy phones
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }

        // Create an Intent to get back to own activity https://developer.android.com/training/notify-user/navigation
        Intent resultIntent = new Intent(cService, MainActivity.class);
        // Create the TaskStackBuilder and add the intent, which inflates the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(cService);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        // Get the PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cService, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(cService.getResources().getString(R.string.notification_title))
                .setContentText(cService.getResources().getString(R.string.notification_message))
                .setVibrate(new long[]{100, 250})
                .setLights(Color.YELLOW, 500, 5000)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(cService, R.color.colorPrimary))
                .setContentIntent(resultPendingIntent);

        cService.startForeground(ONGOING_NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * Remove notification when baby monitor is not active
     */
    public static void cancelNotificationForeground() {
        cService.stopForeground(true);
    }
    /**
     * Is monitoring currently active
     */
    public static boolean getMonitoringState(){
        return monitoringActive;
    }

}

