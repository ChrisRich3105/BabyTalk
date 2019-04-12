package com.example.babytalk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

public class MonitorService extends Service implements SensorEventListener {
    private static final String LOG_TAG = "MonitorService";
    private Thread backgroundThread;

    private boolean isRunning = false;
    private AudioRecorder audioRecorder = null;

    private Sensor sensor;
    private SensorManager sensorManager;


    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");
        audioRecorder=new AudioRecorder();
        audioRecorder.start();

        monitorAcceleration();

        isRunning = true;
        backgroundThread=new Thread(new Runnable() {
            public void run() {
                Log.i(LOG_TAG, "Service running");
                while (isRunning){
                    try {
                        Thread.sleep(1000);
                        Log.i(LOG_TAG, "Current maximum amplitude " + audioRecorder.getMaxAmplitude());
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "Thread InterruptedException");
                    }
                }
                stopSelf();
            }
        });
        backgroundThread.start();
        return Service.START_STICKY;
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
            Log.i(LOG_TAG,"AccX: "+event.values[0]);
            Log.i(LOG_TAG,"AccY: "+event.values[1]);
            Log.i(LOG_TAG,"AccZ: "+event.values[2]);
        }
    }
}

