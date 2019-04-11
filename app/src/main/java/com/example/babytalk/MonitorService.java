package com.example.babytalk;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class MonitorService extends Service {
    private static final String LOG_TAG = "MonitorService";

    private boolean isRunning = false;
    private AudioRecorder audioRecorder = null;


    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");
        audioRecorder=new AudioRecorder();
        audioRecorder.start();

        isRunning = true;
        new Thread(new Runnable() {
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
        }).start();
        return Service.START_STICKY;
    }

    public IBinder onBind(Intent arg0) {
        Log.i(LOG_TAG, "Service onBind");
        return null;
    }

    public void onDestroy() {
        isRunning = false;
        audioRecorder.close();
        Log.i(LOG_TAG, "Service destroyed");
    }
}

