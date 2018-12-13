package cityfreqs.com.pilfershush.scanners;

import android.os.AsyncTask;
import android.os.Bundle;

import java.util.ArrayList;

public class FreqDetector {
    private RecordTask recordTask;
    private Bundle audioBundle;

    protected interface RecordTaskListener {
        void onFailure(String paramString);
        void onSuccess(int paramInt, int magnitude);
    }

    protected FreqDetector(Bundle audioBundle) {
        this.audioBundle = audioBundle;
    }

    /********************************************************************/

    protected void init() {
        recordTask = new RecordTask(audioBundle);
    }

    protected void startRecording(RecordTaskListener recordTaskListener) {
        if (recordTask == null) {
            recordTask = new RecordTask(audioBundle);
        }
        startRecordTaskListener(recordTaskListener);
    }

    private void startRecordTaskListener(RecordTaskListener recordTaskListener) {
        if (recordTask.getStatus() != AsyncTask.Status.RUNNING) {
            recordTask.setOnResultsListener(recordTaskListener);
            recordTask.execute();
        }
    }

    protected boolean hasBufferStorage() {
        return (recordTask != null && recordTask.hasBufferStorage());
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

