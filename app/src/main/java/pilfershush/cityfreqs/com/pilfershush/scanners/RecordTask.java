package pilfershush.cityfreqs.com.pilfershush.scanners;

import android.media.AudioRecord;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.assist.WriteProcessor;
import pilfershush.cityfreqs.com.pilfershush.scanners.FreqDetector.RecordTaskListener;

import static android.os.Process.THREAD_PRIORITY_AUDIO;

public class RecordTask extends AsyncTask<Void, Integer, String> {
    private static final String TAG = "RecordTask";

    private short[] bufferArray; // (shorts do not make a byte)
    private double[] recordScan;
    private RecordTaskListener recordTaskListener;
    private AudioRecord audioRecord;
    private AudioSettings audioSettings;
    private int bufferRead;
    private double minMagnitude;
    private int freqStepper;
    private int candidateFreq;
    private Integer[] tempBuffer;
    private ArrayList<Integer[]> bufferStorage;


    public RecordTask(AudioSettings audioSettings, int freqStepper, double magnitude) {
        this.audioSettings = audioSettings;
        this.freqStepper = freqStepper;
        minMagnitude = magnitude;
        bufferArray = new short[audioSettings.getBufferSize()];
        bufferStorage = new ArrayList<Integer[]>();

        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSettings.getAudioSource(),
                        audioSettings.getSampleRate(),
                        audioSettings.getChannelConfig(),
                        audioSettings.getEncoding(),
                        audioSettings.getBufferSize());

                logger("RecordTask ready.");
            }
            catch (Exception ex) {
                ex.printStackTrace();
                logger("RecordTask failed.");
            }
        }
    }

    public void setOnResultsListener(RecordTaskListener recordTaskListener) {
        this.recordTaskListener = recordTaskListener;
    }

    /********************************************************************/

    protected boolean hasBufferStorage() {
        if (bufferStorage != null) {
            return !bufferStorage.isEmpty();
        }
        return false;
    }

    protected ArrayList<Integer[]> getBufferStorage() {
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
            recordTaskListener.onSuccess(paramArgs[0].intValue());
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
        // TODO getting a couple of RecordThread: buffer overflow warnings etc in adb at start of record

        if ((audioRecord != null) || (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
            try {
                audioRecord.startRecording();
                logger("audioRecord started...");
                audioRecord.setPositionNotificationPeriod(audioSettings.getBufferSize());// / 2);
                audioRecord.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
                    public void onMarkerReached(AudioRecord audioRecord) {
                        logger("marker reached");
                    }

                    public void onPeriodicNotification(AudioRecord audioRecord) {
                        magnitudeRecordScan(audioSettings.getUserWindowType());
                        MainActivity.visualiserView.updateVisualiser(bufferArray); //byteBuffer
                    }
                });

                do {
                    bufferRead = audioRecord.read(bufferArray, 0, audioSettings.getBufferSize());
                    // not proper wav yet
                    if (audioSettings.getWriteFiles()) {
                        WriteProcessor.writeAudioFile(bufferArray, bufferRead);
                    }
                } while (!isCancelled());
            }
            catch (IllegalStateException exState) {
                exState.printStackTrace();
                logger("AudioRecord start recording failed.");
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
        // TODO many type conversions (x2)
        // need to add diff version of scanning, not freqStepper version
        int bufferSize;
        if (bufferRead > 0) {
            bufferSize = audioSettings.getBufferSize();

            recordScan = new double[bufferSize]; // working array
            tempBuffer = new Integer[bufferSize]; // for bufferStorage scans

            for (int i = 0; i < recordScan.length; i++) {
                recordScan[i] = (double)bufferArray[i];
                tempBuffer[i] = (int)bufferArray[i];
            }

            // default value set to 2
            recordScan = windowArray(windowType, recordScan);
            candidateFreq = audioSettings.getMinFreq();
            Goertzel goertzel;
            double candidateMag;

            while (candidateFreq <= audioSettings.getMaxFreq()) {
                // look for any of our freqs here, increment by freqStepper
                // this will result in a found candidate for anything in our ranges...
                goertzel = new Goertzel((float)audioSettings.getSampleRate(), (float)candidateFreq, recordScan);
                goertzel.initGoertzel();
                // get its magnitude
                candidateMag = goertzel.getOptimisedMagnitude();
                // check if above threshold
                if (candidateMag >= minMagnitude) {
                    // saved here for later analysis
                    bufferStorage.add(tempBuffer);
                    // draw on view
                    publishProgress(new Integer[]{Integer.valueOf(candidateFreq)});
                }
                // next freq for loop
                candidateFreq += freqStepper;
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

