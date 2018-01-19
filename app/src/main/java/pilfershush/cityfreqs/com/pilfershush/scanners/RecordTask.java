package pilfershush.cityfreqs.com.pilfershush.scanners;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.assist.WriteProcessor;
import pilfershush.cityfreqs.com.pilfershush.scanners.FreqDetector.RecordTaskListener;

public class RecordTask extends AsyncTask<Void, Integer, String> {
    private static final String TAG = "RecordTask";

    private byte[] bufferArray;
    //private double[] dArr;
    private RecordTaskListener recordTaskListener;
    private AudioRecord audioRecord;
    private AudioSettings audioSettings;
    private int bufferRead;
    private int freqStepper;
    private Integer[] tempBuffer;
    private ArrayList<Integer[]> bufferStorage;
    private HashMap<Integer, Integer> freqMap;
    private byte[] byteBuffer;


    public RecordTask(AudioSettings audioSettings, int freqStepper) {
        this.audioSettings = audioSettings;
        this.freqStepper = freqStepper;
        bufferArray = new byte[audioSettings.getBufferSize()];

        bufferStorage = new ArrayList<Integer[]>();

        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSettings.getAudioSource(),
                        audioSettings.getSampleRate(),
                        audioSettings.getChannel(),
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

    public boolean runCurrentBufferScan() {
        // get rid of audioRecord
        if (audioRecord != null) {
            audioRecord = null;
        }
        if (bufferStorage != null) {
            activityLogger("run Buffer Scan...");
            return magnitudeBufferScan(AudioSettings.DEFAULT_WINDOW_TYPE);
        }
        else {
            activityLogger("Buffer Scan storage null.");
            return false;
        }
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

    protected boolean hasFrequencyCountMap() {
        if (freqMap != null) {
            return freqMap.size() > 0;
        }
        return false;
    }

    protected int getFrequencyCountMapSize() {
        if (freqMap != null) {
            return freqMap.size();
        }
        return 0;
    }

    protected HashMap<Integer, Integer> getFrequencyCountMap() {
        return freqMap;
    }

    /*
    public byte[] getRecordBuffer() {
        return byteBuffer;
    }
    */

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
        if (isCancelled()) {
            // check
            logger("isCancelled check");
            return "isCancelled()";
        }
        // check audioRecord object first
        if ((audioRecord != null) || (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
            try {
                audioRecord.startRecording();
                logger("audioRecord started...");
                audioRecord.setPositionNotificationPeriod(audioSettings.getBufferSize() / 2);
                audioRecord.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
                    public void onMarkerReached(AudioRecord audioRecord) {
                        logger("marker reached");
                    }

                    public void onPeriodicNotification(AudioRecord audioRecord) {
                        magnitudeRecordScan(AudioSettings.DEFAULT_WINDOW_TYPE);
                        MainActivity.visualiserView.updateVisualiser(byteBuffer);
                    }
                });

                // check for a stop
                do {
                    bufferRead = audioRecord.read(bufferArray, 0, audioSettings.getBufferSize());
                    // TODO save audio buffer to non-header pcm file, add boolean switch here
                    try {
                        WriteProcessor.AUDIO_OUTPUT_STREAM.write(bufferArray, 0, audioSettings.getBufferSize());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        logger("AudioRecord write stream error.");
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
        if (bufferRead > 0) {
            int i;
            double[] dArr = new double[audioSettings.getBufferSize()];
            tempBuffer = new Integer[audioSettings.getBufferSize()];
            byteBuffer = new byte[audioSettings.getBufferSize()];

            for (i = 0; i < dArr.length; i++) {
                dArr[i] = (double)bufferArray[i];
                tempBuffer[i] = (int)bufferArray[i];
                byteBuffer[i] = (byte)bufferArray[i];
            }
            // default value set to 2
            dArr = windowArray(windowType, dArr);
            int candidateFreq = AudioSettings.DEFAULT_FREQUENCY_MIN;

            // this only sets the default as a double:
            double minMagnitude = (double)AudioSettings.DEFAULT_MAGNITUDE; // need a minimum here?

            while (candidateFreq <= AudioSettings.DEFAULT_FREQUENCY_MAX) {
                // look for any of our freqs here, increment by freqStepper
                // this will result in a found candidate for anything in our ranges...

                // set Goertzel up to look for candidate in dArr
                Goertzel goertzel = new Goertzel((float)audioSettings.getSampleRate(), (float)candidateFreq, dArr);
                goertzel.initGoertzel();

                // get its magnitude
                double candidateMag = goertzel.getOptimisedMagnitude();
                // check if above threshold
                if (candidateMag >= minMagnitude) {
                    // saved here for later analysis
                    bufferStorage.add(tempBuffer);
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

    @SuppressLint("UseSparseArrays")
    private boolean magnitudeBufferScan(int windowType) {
        // use existing bufferStorage array for scanning.
        // will result in same findings as magnitudeRecordScan()...

        if (bufferStorage != null) {
            activityLogger("Start buffer scanning in " + bufferStorage.size() + " buffers.");

            // bufferStorage is ArrayList of Integer arrays,
            // each Integer array *may* contain a binMod signal
            double[] dArr;
            freqMap = new HashMap<Integer, Integer>();

            //TODO
            // may want a maximum on this cos it could get big and ugly...
            for (Integer[] arrayInt : bufferStorage) {
                // in each array, scan for magnitude
                dArr = new double[arrayInt.length];
                int i;
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = (double)arrayInt[i];
                }
                //
                // default value set to 2
                dArr = windowArray(windowType, dArr);
                // end windowing
                // find a single good freq
                int candidateFreq = AudioSettings.DEFAULT_FREQUENCY_MIN;
                double minMagnitude = (double)AudioSettings.DEFAULT_MAGNITUDE; // need a minimum here?
                int centreFreq = 0;

                while (candidateFreq <= AudioSettings.DEFAULT_FREQUENCY_MAX) {
                    // look for any of our freqs here, increment by freqStepper,
                    // based upon checking each freq+freqStep == magnitude > DEFAULT_MAGNITUDE
                    // binMod signal will have multiples of 2 frequencies with possibly a clock as well.

                    // set Goertzel up to look for candidate in dArr
                    Goertzel goertzel = new Goertzel((float)audioSettings.getSampleRate(), (float)candidateFreq, dArr);
                    goertzel.initGoertzel();

                    // get its magnitude
                    double candidateMag = goertzel.getOptimisedMagnitude();
                    // check if above threshold
                    if (candidateMag >= minMagnitude) {
                        centreFreq = candidateFreq;
                    }
                    // next freq for loop
                    candidateFreq += freqStepper;
                }
                // check number of occurrences of our centreFreq and any possible binMod freqs
                int freq;
                // reset these:
                candidateFreq = AudioSettings.DEFAULT_FREQUENCY_MIN;
                minMagnitude = (double)AudioSettings.DEFAULT_MAGNITUDE;
                ArrayList<Integer> freqCounter = new ArrayList<Integer>();
                // range here may be too small..
                for (freq = centreFreq - AudioSettings.MAX_FREQ_STEP;
                     freq <= centreFreq + AudioSettings.MAX_FREQ_STEP;
                     freq += freqStepper) {
                    // set Goertzel up to count candidates in dArr
                    Goertzel goertzel = new Goertzel((float)audioSettings.getSampleRate(), (float)freq, dArr);
                    goertzel.initGoertzel();

                    // get its magnitude
                    double candidateMag = goertzel.getOptimisedMagnitude();
                    // set magnitude floor, raises it
                    // check if above threshold
                    if (candidateMag >= minMagnitude) {
                        // the freq has a magnitude,
                        // note it and then allow loop to continue
                        freqCounter.add(freq);
                    }
                }
                // store any finds for later analysis
                if (!freqCounter.isEmpty()) {
                    mapFrequencyCounts(freqCounter);
                }
            }
            // end bufferStorage loop thru
        }
        activityLogger("finished  Buffer Scan loop.");
        return true;
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
                for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (((0.355768d - (0.487396d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length))) +
                            (0.144232d * Math.cos((AudioSettings.PI4 * ((double) i)) / dArr.length))) -
                            (0.012604d * Math.cos((AudioSettings.PI6 * ((double) i)) / dArr.length)));
                }
                break;
        }
        return dArr;
    }


    private void mapFrequencyCounts(ArrayList<Integer> freqList) {
        // SparseIntArray is suggested...
        // this only counts, order of occurrence is not preserved.

        for (int freq : freqList) {
            if (freqMap.containsKey(freq)) {
                freqMap.put(freq, freqMap.get(freq) + 1);
            }
            else {
                freqMap.put(freq, 1);
            }
        }
    }

    /********************************************************************/

    private void activityLogger(String message) {
        MainActivity.logger(message);
    }

    private void logger(String message) {
        Log.d(TAG, message);
    }
}


