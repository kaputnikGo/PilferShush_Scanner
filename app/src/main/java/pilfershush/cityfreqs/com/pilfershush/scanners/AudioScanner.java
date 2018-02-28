package pilfershush.cityfreqs.com.pilfershush.scanners;

import android.content.Context;

import java.util.ArrayList;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.R;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.scanners.FreqDetector.RecordTaskListener;

public class AudioScanner {
    private Context context;
    private FreqDetector freqDetector;
    private ProcessAudio processAudio;
    private AudioSettings audioSettings;
    public boolean audioDetected;
    private int freqStep;

    private ArrayList<Integer> frequencySequence;
    private ArrayList<Integer[]> bufferStorage;

    public AudioScanner(Context context, AudioSettings audioSettings) {
        this.context = context;
        this.audioSettings = audioSettings;
        freqStep = AudioSettings.DEFAULT_FREQ_STEP;

        freqDetector = new FreqDetector(this.audioSettings);
        freqDetector.init(freqStep, AudioSettings.DEFAULT_MAGNITUDE);
        processAudio = new ProcessAudio(context);
        resetAudioScanner();
    }

    public void resetAudioScanner() {
        frequencySequence = new ArrayList<Integer>();
        bufferStorage = new ArrayList<Integer[]>();
        audioDetected = false;
    }

    public void setMinMagnitude(double magnitude) {
        freqDetector.setMagnitude(magnitude);
    }

    public void runAudioScanner() {
        freqDetector.startRecording(new RecordTaskListener() {
            public void onSuccess(int value) {
                if (processAudio.checkFrequency(value)) {
                    frequencySequence.add(value);
                    MainActivity.visualiserView.frequencyCaution(value);
                }
            }

            public void onFailure(String paramString) {
                MainActivity.logger(context.getString(R.string.audio_scan_2) + paramString);
            }
        });
    }

    public void stopAudioScanner() {
        try {
            if (hasBufferStorage()) {
                // this can return a null
                bufferStorage = freqDetector.getBufferStorage();
            }
            freqDetector.stopRecording();
            freqDetector.cleanup();
        }
        catch (Exception ex) {
            MainActivity.logger(context.getString(R.string.audio_scan_3));
        }
    }

    public boolean hasBufferStorage() {
        return freqDetector.hasBufferStorage();
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

    public ArrayList<Integer> getFreqSequence() {
        return frequencySequence;
    }
}

