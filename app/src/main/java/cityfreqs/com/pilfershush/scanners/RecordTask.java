package cityfreqs.com.pilfershush.scanners;

import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import cityfreqs.com.pilfershush.MainActivity;
import cityfreqs.com.pilfershush.assist.AudioSettings;
import cityfreqs.com.pilfershush.assist.WriteProcessor;
import cityfreqs.com.pilfershush.scanners.FreqDetector.RecordTaskListener;

import static android.os.Process.THREAD_PRIORITY_AUDIO;

public class RecordTask extends AsyncTask<Void, Integer, String> {
    private static final String TAG = "RecordTask";

    private short[] bufferArray; // (shorts do not make a byte)
    private RecordTaskListener recordTaskListener;
    private AudioRecord audioRecord;
    private Bundle audioBundle;
    private int bufferRead;
    private ArrayList<Integer[]> bufferStorage;


    public RecordTask(Bundle audioBundle) {
        this.audioBundle = audioBundle;
        bufferArray = new short[audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4])];
        bufferStorage = new ArrayList<>();

        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[0]),
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]),
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[2]),
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[3]),
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]));

                logger("RecordTask ready.");
            }
            catch (Exception ex) {
                ex.printStackTrace();
                logger("RecordTask failed.");
            }
        }
    }

    void setOnResultsListener(RecordTaskListener recordTaskListener) {
        this.recordTaskListener = recordTaskListener;
    }

    /********************************************************************/

    boolean hasBufferStorage() {
        return (bufferStorage != null && !bufferStorage.isEmpty());
    }

    ArrayList<Integer[]> getBufferStorage() {
        return bufferStorage;
    }

    /********************************************************************/

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... paramArgs) {
        if (recordTaskListener == null) {
            logger("onProgress listener null.");
            return;
        }

        if (paramArgs[0] != null) {
            recordTaskListener.onSuccess(paramArgs[0], paramArgs[1]);
        }
        else {
            recordTaskListener.onFailure("RecordTaskListener failed, no params.");
            logger("listener onFailure.");
        }
    }

    @Override
    protected String doInBackground(Void... paramArgs) {
        android.os.Process.setThreadPriority(THREAD_PRIORITY_AUDIO);

        if (isCancelled()) {
            // check
            logger("isCancelled check");
            return "isCancelled()";
        }
        // check audioRecord object first
        if (audioRecord != null) {
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord.startRecording();
                    logger("audioRecord started...");
                    audioRecord.setPositionNotificationPeriod(audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]));// / 2);
                    audioRecord.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
                        public void onMarkerReached(AudioRecord audioRecord) {
                            logger("marker reached");
                        }

                        public void onPeriodicNotification(AudioRecord audioRecord) {
                            magnitudeRecordScan(audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[13]));

                            MainActivity.visualiserView.updateVisualiser(bufferArray); //byteBuffer
                        }
                    });

                    do {
                        bufferRead = audioRecord.read(bufferArray, 0, audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]));
                        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[14])) {
                            WriteProcessor.writeAudioFile(bufferArray, bufferRead);
                        }
                    } while (!isCancelled());
                } catch (IllegalStateException exState) {
                    exState.printStackTrace();
                    logger("AudioRecord start recording failed.");
                }
            }
        }
        return "RecordTask finished";
    }

    @Override
    protected void onPostExecute(String paramString) {
        logger("Post execute: " + paramString);
    }

    @Override
    protected void onCancelled() {
        logger("onCancelled called.");
        bufferRead = 0;
        try {
            if (WriteProcessor.AUDIO_OUTPUT_STREAM != null) {
                WriteProcessor.AUDIO_OUTPUT_STREAM.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            logger("onCancelled write stream close error.");
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            logger("audioRecord stop and release.");
        }
        else {
            logger("audioRecord is null.");
        }
    }

    /********************************************************************/

    private void magnitudeRecordScan(int windowType) {
        // TODO need to add diff version of scanning, not freqStepper version
        // https://github.com/lucns/Android-Audio-Sample/tree/master/main/java/com/sample
        // https://github.com/bewantbe/audio-analyzer-for-android

        int bufferSize;
        double[] recordScan;
        int candidateFreq;
        Integer[] tempBuffer;

        if (bufferRead > 0) {
            bufferSize = audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]);

            recordScan = new double[bufferSize]; // working array
            tempBuffer = new Integer[bufferSize]; // for bufferStorage scans

            for (int i = 0; i < recordScan.length; i++) {
                recordScan[i] = (double)bufferArray[i];
                tempBuffer[i] = (int)bufferArray[i];
            }

            // default value set to 2
            recordScan = windowArray(windowType, recordScan);
            candidateFreq = audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[9]);
            Goertzel goertzel;
            double candidateMag;

            while (candidateFreq <= audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[10])) {
                // look for any of our freqs here, increment by freqStepper
                // this will result in a found candidate for anything in our ranges...
                goertzel = new Goertzel((float)audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]), (float)candidateFreq, recordScan);
                goertzel.initGoertzel();
                // get its magnitude
                candidateMag = goertzel.getOptimisedMagnitude();
                // check if above threshold
                if (candidateMag >= audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[12])) {
                    // saved here for later analysis
                    bufferStorage.add(tempBuffer);
                    // draw on view
                    publishProgress(candidateFreq, (int)candidateMag);
                }
                // next freq for loop
                candidateFreq += audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[11]);
            }
        }
        else {
            logger("bufferRead empty");
        }
    }

    /********************************************************************/

    private double[] windowArray(int windowType, double[] dArr) {
        int i;
        // default value set to 2
        switch (windowType) {
            case 1:
                // Hann(ing) Window
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (0.5d - (0.5d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length)));
                }
                break;
            case 2:
                // Blackman Window
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * ((0.42659d - (0.49659d * Math.cos((AudioSettings.PI2 *
                            ((double) i)) / dArr.length))) + (0.076849d * Math.cos((AudioSettings.PI4 * ((double) i)) / dArr.length)));
                }
                break;
            case 3:
                // Hamming Window
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (0.54d - (0.46d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length)));
                }
                break;
            case 4:
                // Nuttall Window
                // NOTE:: a0 = 0.3635819, a1 = 0.4891775, a2 = 0.1365995, a3 = 0.0106411
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (((0.355768d - (0.487396d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length))) +
                            (0.144232d * Math.cos((AudioSettings.PI4 * ((double) i)) / dArr.length))) -
                            (0.012604d * Math.cos((AudioSettings.PI6 * ((double) i)) / dArr.length)));
                }
                break;
            case 5:
                // Blackman-Nuttall window
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (((0.3635819d - (0.4891775d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length))) +
                            (0.1365995d * Math.cos((AudioSettings.PI4 * ((double) i)) / dArr.length))) -
                            (0.0106411d * Math.cos((AudioSettings.PI6 * ((double) i)) / dArr.length)));
                }
                break;
        }
        return dArr;
    }

    /********************************************************************/
/*
    private void activityLogger(String message) {
        MainActivity.logger(message);
    }
  */

    private void logger(String message) {
        Log.d(TAG, message);
    }
}

