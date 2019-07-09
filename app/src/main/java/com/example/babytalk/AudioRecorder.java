package com.example.babytalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/*
     * Thread to manage live recording/playback of voice input from the device's microphone adapted to own requirements
     * Source adapted from https://stackoverflow.com/questions/6959930/android-need-to-record-mic-input
     */
    public class AudioRecorder extends Thread
    {
        private static final int MOVING_AVG_SIZE=100;
        private boolean stopped = false; // start and stop thread
        private AudioRecord recorder = null; // Audio recorder
        private short[][]   buffers  = new short[256][160]; // init buffer
        private int ix = 0; // buffer counter
        private int maxAmplitude    = 0; // the maximum amplitude measured since the last reset
        private int publicAvg = 0;  // the value that is passed to the main activity as current sound level

        /**
         * Give the thread high priority so that it's not canceled unexpectedly, and start it
         */
        protected AudioRecorder()
        {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        }

        @Override
        public void run()
        {
            Log.i("Audio", "Running Audio Thread");
            /*
             * Initialize buffer to hold continuously recorded audio data, start recording, and start
             * playback.
             */
            try
            {
                int N = AudioRecord.getMinBufferSize(8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT); // Get buffer size
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, N*10); // Set enconding and buffer size
                recorder.startRecording(); // Start recording

                /*
                 * Loops until something outside of this thread stops it.
                 * Reads the data from the recorder and writes it to the audio track for playback.
                 */
                while(!stopped)
                {
                    // init and buffer
                    short[] buffer = buffers[ix++ % buffers.length];
                    List<Short> movingAvg = new ArrayList<>();
                    recorder.read(buffer,0,buffer.length);

                    // go through the buffer
                    for (short s : buffer)
                    {
                        movingAvg.add(s); // add samples to moving-avg list
                        if(movingAvg.size()>MOVING_AVG_SIZE){
                            movingAvg.remove(0); // first element
                            // build sum here
                            int currentAvg=0; // what is the current signal-average
                            for(short sample : movingAvg)
                                currentAvg += Math.abs(sample); // sum absolute values
                            publicAvg =currentAvg/MOVING_AVG_SIZE; // Set the value which is passed to the main activity
                            if (publicAvg > maxAmplitude) // if bigger than current maximum amplitude
                            {
                                maxAmplitude = publicAvg; // set new max. amplitude
                            }
                        }

                    }
                }
            }
            catch(Throwable x)
            {
                Log.e("Audio", "Error reading voice audio", x);
            }
            /*
             * Free thread's resources after the loop completes so that it can be run again
             */
            finally
            {
                recorder.stop();
                recorder.release();
            }
        }

        /**
         * Called from outside of the thread in order to stop the recording/playback loop
         */
        protected void close()
        {
            stopped = true;
        }

        /**
         * Get the current sliding averageAmplitude
         */
        public double getCurrentAmplitudeAvg() {
            return publicAvg;
        }

        /**
         * Set the maximumAmplitude back to 0
         */
        public void setMaxAmplitudeZero(){ maxAmplitude=0; }

        /**
         * Get the maximumAmplitude
         */
        public double getMaxAmplitude(){ return maxAmplitude; }

    }