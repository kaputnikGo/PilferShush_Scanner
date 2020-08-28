package cityfreqs.com.pilfershush.scanners;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import java.util.ArrayList;

import cityfreqs.com.pilfershush.MainActivity;
import cityfreqs.com.pilfershush.R;
import cityfreqs.com.pilfershush.assist.AudioSettings;
import cityfreqs.com.pilfershush.scanners.FreqDetector.RecordTaskListener;

public class AudioScanner {
    //TODO make this the service

    private Context context;
    private FreqDetector freqDetector;
    private ProcessAudio processAudio;
    private Bundle audioBundle;

    private ArrayList<Integer> frequencySequence;
    private ArrayList<Integer[]> bufferStorage;

    public AudioScanner(Context context, Bundle audioBundle) {
        this.context = context;
        this.audioBundle = audioBundle;



        freqDetector = new FreqDetector(this.audioBundle);
        freqDetector.init();
        processAudio = new ProcessAudio(context);
        resetAudioScanner();
    }

    public void resetAudioScanner() {
        frequencySequence = new ArrayList<>();
        bufferStorage = new ArrayList<>();
    }

    public void setMagnitude(double magnitude) {
        audioBundle.putDouble(AudioSettings.AUDIO_BUNDLE_KEYS[12], magnitude);
    }

    public void runAudioScanner() {
        freqDetector.startRecording(new RecordTaskListener() {
            public void onSuccess(int value, int magnitude) {
                if (processAudio.checkFrequency(value)) {
                    frequencySequence.add(value);
                    MainActivity.visualiserView.frequencyCaution(value, magnitude);
                }
            }

            public void onFailure(String paramString) {
                entryLogger(context.getString(R.string.audio_scan_2) + paramString, true);
            }
        });
    }

    public void stopAudioScanner() {
        try {
            if (freqDetector.hasBufferStorage()) {
                // this can return a null
                bufferStorage = freqDetector.getBufferStorage();
            }
            freqDetector.stopRecording();
            freqDetector.cleanup();
        }
        catch (Exception ex) {
            entryLogger(context.getString(R.string.audio_scan_3), false);
        }
    }

    public boolean canProcessBufferStorage() {
        return bufferStorage != null;
    }

    public int getSizeBufferStorage() {
        if (bufferStorage != null) {
            return bufferStorage.size();
        }
        else
            return 0;
    }

    /********************************************************************/

    public boolean hasFrequencySequence() {
        return processAudio.hasFreqSequenceDuplicates(frequencySequence);
    }

    public int getFrequencySequenceSize() {
        if (frequencySequence != null) {
            return frequencySequence.size();
        }
        return 0;
    }

    public String getFrequencySequenceLogic() {
        return  processAudio.getLogicZero() + "\n" + processAudio.getLogicOne() + "\n";
    }

    public String getFreqSeqLogicEntries() {
        return processAudio.getLogicEntries();
    }

    /*
    public ArrayList<Integer> getFreqSequence() {
        return frequencySequence;
    }
    */
    private void entryLogger(String entry, boolean caution) {
        TextView debugText = ((Activity)context).findViewById(R.id.debug_text);
        int start = debugText.getText().length();
        debugText.append("\n" + entry);
        int end = debugText.getText().length();
        Spannable spannableText = (Spannable) debugText.getText();
        if (caution) {
            spannableText.setSpan(new ForegroundColorSpan(Color.YELLOW), start, end, 0);
        }
        else {
            spannableText.setSpan(new ForegroundColorSpan(Color.GREEN), start, end, 0);
        }
    }
}

