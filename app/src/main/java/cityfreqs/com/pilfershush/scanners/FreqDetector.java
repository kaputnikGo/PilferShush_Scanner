package cityfreqs.com.pilfershush.scanners;

import android.os.AsyncTask;
import android.os.Bundle;

import java.util.ArrayList;

class FreqDetector {
    private RecordTask recordTask;
    private Bundle audioBundle;

    protected interface RecordTaskListener {
        void onFailure(String paramString);
        void onSuccess(int paramInt, int magnitude);
    }

    FreqDetector(Bundle audioBundle) {
        this.audioBundle = audioBundle;
    }

    /********************************************************************/

    void init() {
        recordTask = new RecordTask(audioBundle);
    }

    void startRecording(RecordTaskListener recordTaskListener) {
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

    boolean hasBufferStorage() {
        return (recordTask != null && recordTask.hasBufferStorage());
    }


    ArrayList<Integer[]> getBufferStorage() {
        if (recordTask != null) {
            return recordTask.getBufferStorage();
        }
        return null;
    }

    /********************************************************************/
    void stopRecording() {
        if (recordTask != null) {
            recordTask.cancel(true);
        }
    }

    void cleanup() {
        if (recordTask != null) recordTask = null;
    }
}

