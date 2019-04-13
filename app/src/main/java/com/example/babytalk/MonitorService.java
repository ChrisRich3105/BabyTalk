package com.example.babytalk;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

public class MonitorService extends Service implements SensorEventListener {
    private static final String LOG_TAG = "MonitorService";
    private Thread backgroundThread;

    private boolean isRunning = false;
    private AudioRecorder audioRecorder = null;
    private boolean calling = false;

    private Sensor sensor;
    private SensorManager sensorManager;
    private double filteredAcceleration; // Just a PT1 filter


    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");

        monitorVoiceLevel();
        monitorAcceleration();

        return Service.START_STICKY;
    }
    private void monitorVoiceLevel(){
        audioRecorder=new AudioRecorder();
        audioRecorder.start();
        isRunning = true;
        backgroundThread=new Thread(new Runnable() {
            public void run() {
                Log.i(LOG_TAG, "Service running");
                while (isRunning){
                    try {
                        Thread.sleep(1000);
                        Log.i(LOG_TAG, "Current maximum amplitude " + audioRecorder.getMaxAmplitude());
                        // TODO readFromConfig
                        if(audioRecorder.getMaxAmplitude() > 10000 && !calling){
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

    public IBinder onBind(Intent arg0) {
        Log.i(LOG_TAG, "Service onBind");
        return null;
    }

    public void onDestroy() {
        isRunning = false;
        stopMonitorAcceleration();
        //backgroundThread.interrupt();
        audioRecorder.close();
        Log.i(LOG_TAG, "Service destroyed");
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
    private void performPhoneCall(){
        Intent phoneIntent = new Intent(Intent.ACTION_CALL,Uri.parse("tel:042747457"));
        // TODO readNumberfromConfig
        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG,"Permission missing");
            return;
        }
        startActivity(phoneIntent);
    }
}

