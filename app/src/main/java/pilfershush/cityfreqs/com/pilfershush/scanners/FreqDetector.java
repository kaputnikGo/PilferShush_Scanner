package pilfershush.cityfreqs.com.pilfershush.scanners;

import java.util.ArrayList;
import java.util.HashMap;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

import android.os.AsyncTask;

public class FreqDetector {
    private RecordTask recordTask;
    private AudioSettings audioSettings;
    private int frequencyStepper;

    protected interface RecordTaskListener {
        void onFailure(String paramString);
        void onSuccess(int paramInt);
    }

    protected FreqDetector(AudioSettings audioSettings) {
        this.audioSettings = audioSettings;
    }

    /********************************************************************/

    protected void init(int frequencyStepper) {
        this.frequencyStepper = frequencyStepper;
        recordTask = new RecordTask(audioSettings, frequencyStepper);
    }

    protected void startRecording(RecordTaskListener recordTaskListener) {
        if (recordTask == null) {
            recordTask = new RecordTask(audioSettings, frequencyStepper);
        }
        startRecordTaskListener(recordTaskListener);
    }

    private void startRecordTaskListener(RecordTaskListener recordTaskListener) {
        if (recordTask.getStatus() == AsyncTask.Status.RUNNING) {
            //
        }
        else {
            recordTask.setOnResultsListener(recordTaskListener);
            recordTask.execute(new Void[0]);
        }
    }

    public RecordTask getRecordTask() {
        return recordTask;
    }

    /********************************************************************/

    protected boolean runBufferScanner(int frequencyStepper) {
        this.frequencyStepper = frequencyStepper;
        int countSize = 0;
        //recordTask = new RecordTask(audioSettings, frequencyStepper);
        if (recordTask.runCurrentBufferScan()) {
            // can scan..
            countSize = recordTask.getFrequencyCountMapSize();
        }

        if (countSize > 0) {
            return true;
        }
        return false;
    }

    protected HashMap<Integer, Integer> getFrequencyCountMap() {
        return recordTask.getFrequencyCountMap();
    }

    protected boolean hasBufferStorage() {
        if (recordTask != null) {
            return recordTask.hasBufferStorage();
        }
        return false;
    }

    protected ArrayList<Integer[]> getBufferStorage() {
        if (recordTask != null) {
            return recordTask.getBufferStorage();
        }
        return null;
    }

    /********************************************************************/
    protected void stopRecording() {
        if (recordTask != null) {
            recordTask.cancel(true);
        }
    }

    protected void cleanup() {
        if (recordTask != null) recordTask = null;
    }
}

