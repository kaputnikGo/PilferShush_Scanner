package cityfreqs.com.pilfershush.scanners;

import android.os.AsyncTask;

import java.util.ArrayList;

import cityfreqs.com.pilfershush.assist.AudioSettings;

public class FreqDetector {
    private RecordTask recordTask;
    private AudioSettings audioSettings;

    protected interface RecordTaskListener {
        void onFailure(String paramString);
        void onSuccess(int paramInt);
    }

    protected FreqDetector(AudioSettings audioSettings) {
        this.audioSettings = audioSettings;
    }

    /********************************************************************/

    protected void init() {
        recordTask = new RecordTask(audioSettings, audioSettings.getFreqStep(), audioSettings.getMagnitude());
    }

    protected void startRecording(RecordTaskListener recordTaskListener) {
        if (recordTask == null) {
            recordTask = new RecordTask(audioSettings, audioSettings.getFreqStep(), audioSettings.getMagnitude());
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

