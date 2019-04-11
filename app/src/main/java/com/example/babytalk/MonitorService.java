package com.example.babytalk;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.IOException;

public class MonitorService extends Service {
    private static final String LOG_TAG = "MonitorService";
    private static String fileName = null;

    private boolean isRunning = false;

    private MediaRecorder recorder = null;
    private MediaPlayer player = null;

    private int minSize;

    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(LOG_TAG, "Service onStart");

        // Record to the external cache directory for visibility
        fileName = getExternalCacheDir().getAbsolutePath();
        fileName += "/audiorecordtest.3gp";


       // minSize= AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        isRunning = true;
        new Thread(new Runnable() {
            public void run() {
                Log.i(LOG_TAG, "Service running");
                while (isRunning){
                    try {
                        startRecording();
                        Thread.sleep(1000);
                        Log.i(LOG_TAG, "Current amplitude " + getAmplitude());
                        stopRecording();
                        Thread.sleep(3000);

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
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }

        Log.i(LOG_TAG, "Service destroyed");
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        recorder.start();
    }

    private void stopRecording() {
        recorder.stop();
        recorder.release();
        recorder = null;
    }


    public double getAmplitude() {
       /* short[] buffer = new short[minSize];
        ar.startRecording();
        //Thread.sleep(20);
        // TODO Set a delay for recording
        ar.read(buffer, 0, minSize);
        ar.stop();
        int max = 0;
        for (short s : buffer)
        {
            if (Math.abs(s) > max)
            {
                max = Math.abs(s);
            }
        }*/
        /*player = new MediaPlayer();
        try {
            player.setDataSource(fileName);
            player.prepare();
            player.start();

        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }*/
        // TODO get maximum amplitude
        return recorder.getMaxAmplitude();
        //return player.getDuration();
    }
}

