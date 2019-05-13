package com.example.babytalk;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.RemoteViews;

import java.lang.reflect.Method;

public class MonitorService extends Service implements SensorEventListener {
    // TODO comment variables and classes
    private static final String LOG_TAG = "MonitorService";
    private static final int ONGOING_NOTIFICATION_ID = 100;
    private static final String CHANNEL_ID = "100";
    private static final String CHANNEL_NAME = "BabyTalk";

    private static boolean monitoringActive=false;
    private static AudioRecorder audioRecorder = null;
    private static MonitorService cService = null;
    private static SharedPreferences prefs;
    private static CountDownTimer timer;

    private Thread backgroundThread;
    private boolean isRunning = false;
    private boolean calling = false;


    private Sensor sensor;
    private SensorManager sensorManager;
    private double filteredAcceleration; // Just a PT1 filter


    // read phone state
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private AudioManager audioManager;

    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        cService = this;

        monitorAcceleration();
        addPhoneStateListener();
        monitorVoiceLevel();

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

    private void addPhoneStateListener() {
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // TODO handle incoming calls

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                Log.i(LOG_TAG, "onCallStateChanged");
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    Log.i(LOG_TAG, "onCallStateChanged - IDLE");
                    try {
                        Thread.sleep(500); // Tone from hanging should not trigger a scond call
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    audioRecorder.setMaxAmplitudeZero();
                    calling = false;
                    audioManager.setSpeakerphoneOn(false); // to have the timeout with next cycle

                } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    Log.i(LOG_TAG, "onCallStateChanged - CALL_STATE_OFFHOOK");
                    if (prefs.getBoolean(getString(R.string.preference_speakerphone_key), false) == true) {
                        try {
                            Thread.sleep(1000); // There is a notification tone after that on my mobile - suppress first second
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        audioManager.setSpeakerphoneOn(true);
                        // TODO? set loudspeaker level to zero that the noise is not transfered to the baby or should talking be possible?
                    }
                } else if (state == TelephonyManager.CALL_STATE_RINGING) {
                    Log.i(LOG_TAG, "onCallStateChanged - CALL_STATE_RINGING");
                    // TODO maybe hang up on incoming calls as option
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void monitorVoiceLevel() {
        audioRecorder = new AudioRecorder();
        audioRecorder.start();
        isRunning = true;

        final int motionTriggerLevel = prefs.getInt(getString(R.string.preference_motion_value_key), 0);
        final boolean motionTriggerActivated = prefs.getBoolean(getString(R.string.preference_motion_key), false);

        backgroundThread = new Thread(new Runnable() {
            public void run() {
                while (isRunning) {
                    try {
                        Thread.sleep(200);
                        // Broadcast current noise level
                        Log.i(LOG_TAG, "Current maximum amplitude " + audioRecorder.getMaxAmplitude());
                        Intent intent=new Intent();
                        intent.setAction(getResources().getString(R.string.broadcast_sound_level_URL));
                        intent.putExtra("level", audioRecorder.getCurrentAmplitudeAvg());
                        sendBroadcast(intent);

                        /* TODO readFromConfig - comment CRE: not read noise level setting from config but from main activity, as it is not included in the preferences page but it´s kind of a "live-setting" */
                        if(monitoringActive){
                            if (((audioRecorder.getMaxAmplitude() > 3000)
                                    || (motionTriggerActivated && (getMotion() > 0.2 + ((double) motionTriggerLevel / 50)))) && !calling) {
                                Log.i(LOG_TAG, "Perform call");
                                calling = true;
                                performPhoneCall();
                            }
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

    private void monitorAcceleration() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopMonitorAcceleration() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            //Log.i(LOG_TAG,"AccX: "+event.values[0]);
            //Log.i(LOG_TAG,"AccY: "+event.values[1]);
            //Log.i(LOG_TAG,"AccZ: "+event.values[2]);
            // Calculate vectorsum
            double accelerationSum = Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            filteredAcceleration = 0.95 * filteredAcceleration + 0.05 * accelerationSum; // No idea about timeconstant yet
            Log.i(LOG_TAG, "Acceleration: " + filteredAcceleration);
        }
    }

    private double getMotion() {
        return filteredAcceleration;
    }

    private void performPhoneCall() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String phoneNumber = prefs.getString(getString(R.string.preference_phonenumber_key), null);
        Log.i(LOG_TAG, "Phone number: " + phoneNumber);

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
                //TODO add Toast/Info
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
            phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG, "Permission missing");
                return;
            }
            startActivity(phoneIntent);
        }

    }
    /********  static functions ***************/
    protected static void startMonitoring(){

        int pauseTime = prefs.getInt(cService.getString(R.string.preference_pause_value_key), 0);
        boolean pauseActivated = prefs.getBoolean(cService.getString(R.string.preference_pause_key), false);
        Log.i(LOG_TAG, "Service running - Pause" + String.valueOf(pauseActivated) + " Pause time: " + String.valueOf(pauseTime));
        if (pauseActivated){

            timer=new CountDownTimer(pauseTime*1000, 1000) {

                public void onTick(long millisUntilFinished) {
                    // TODO maybe add a timer showing the pause time on the main activity? Broadcast timer here or make static
                    Log.i(LOG_TAG, "Pause exceeded: "+String.valueOf(millisUntilFinished/1000));
                }

                public void onFinish() {
                    Log.i(LOG_TAG, "Pause time exceeded. Start monitoring.");

                    audioRecorder.setMaxAmplitudeZero();
                    addNotificationForeground();
                    monitoringActive=true;
                }
            };
            timer.start();

        }else{
            audioRecorder.setMaxAmplitudeZero();
            addNotificationForeground();
            monitoringActive=true;
        }
    }
    protected static void stopMonitoring(){
        cancelNotificationForeground();
        timer.cancel();
        monitoringActive=false;
    }

    // start service in foreground with a notification so it is not killed when memory is needed
    // https://stackoverflow.com/questions/44913884/android-notification-not-showing-on-api-26
    public static void addNotificationForeground() {
        NotificationManager notificationManager = (NotificationManager) cService.getSystemService(Context.NOTIFICATION_SERVICE);

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
    public static void cancelNotificationForeground() {
        cService.stopForeground(true);
    }

    public static boolean getMonitoringState(){
        return monitoringActive;
    }
}

