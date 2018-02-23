package pilfershush.cityfreqs.com.pilfershush.scanners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.scanners.FreqDetector.RecordTaskListener;

public class AudioScanner {
    private FreqDetector freqDetector;
    private ProcessAudio processAudio;
    private AudioSettings audioSettings;
    public boolean audioDetected;
    private int freqStep;
    //private double magnitude;

    private ArrayList<Integer> frequencySequence;
    private ArrayList<Integer[]> bufferStorage;
    private HashMap<Integer, Integer> freqMap;
    private ArrayList<Map.Entry<Integer,Integer>> mapEntries;
    private Entry<Integer, Integer> logicZero;
    private Entry<Integer, Integer> logicOne;

    public AudioScanner(AudioSettings audioSettings) {
        this.audioSettings = audioSettings;
        freqStep = AudioSettings.DEFAULT_FREQ_STEP;

        freqDetector = new FreqDetector(this.audioSettings);
        freqDetector.init(freqStep, AudioSettings.DEFAULT_MAGNITUDE);
        processAudio = new ProcessAudio();
        resetAudioScanner();
    }

    public void resetAudioScanner() {
        frequencySequence = new ArrayList<Integer>();
        bufferStorage = new ArrayList<Integer[]>();
        audioDetected = false;
    }

    public void setFreqStep(int freqStep) {
        if (freqStep > AudioSettings.MIN_FREQ_STEP ||
                freqStep < AudioSettings.MAX_FREQ_STEP) {
            this.freqStep = freqStep;
        }
        else {
            // is a default...
            this.freqStep = AudioSettings.DEFAULT_FREQ_STEP;
        }
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
                MainActivity.logger("AudioScanner run failed: " + paramString);
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
        }
        catch (Exception ex) {
            MainActivity.logger("Stop AudioScanner failed.");
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

    //TODO
    // secondary scans using the bufferStorage, ideally looking for binMods.
    public boolean runBufferScanner() {
        //
        return freqDetector.runBufferScanner(frequencySequence);
    }

    public void storeBufferScanMap() {
        freqMap = freqDetector.getFrequencyCountMap();
    }

    public void stopBufferScanner() {
        // this will null RecordTask
        freqDetector.stopRecording();
        freqDetector.cleanup();
        resetAudioScanner();
    }

    // this can take time...
    public boolean processBufferScanMap() {
        if (freqMap != null) {
            if (freqMap.size() > 0 ) {
                // sort into list with highest values order
                mapEntries = new ArrayList<Map.Entry<Integer,Integer>>(freqMap.entrySet());

                Collections.sort(mapEntries, new Comparator<Entry<Integer, Integer>>() {
                    public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) {
                        return b.getValue().compareTo(a.getValue());
                    }
                });

                // top 2 entries are of interest?
                if (mapEntries != null && !mapEntries.isEmpty()) {
                    // we need at least two entries for this:
                    if (mapEntries.get(0) != null) {
                        logicZero = mapEntries.get(0);
                        if (mapEntries.size() >= 2) {
                            if (mapEntries.get(1) != null) {
                                logicOne = mapEntries.get(1);
                                return true;
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String getLogicEntries() {
        String report = "";
        if (logicZero != null) {
            report = "Freq 0: " + logicZero.getKey() + " : " + logicZero.getValue();
        }
        else {
            report = "Freq 0: not found";
        }

        if (logicOne != null) {
            report += "\nFreq 1: " + logicOne.getKey() + " : " + logicOne.getValue();
        }
        else {
            report += "\nFreq 1: not found";
        }
        return report;
    }

    /********************************************************************/

    public boolean hasFrequencySequence() {
        return processAudio.hasFreqSequenceDuplicates(frequencySequence);
    }

    public String getFrequencySequenceLogic() {
        return "Found possible binary logic: \n" + processAudio.getLogicZero()
                + "\n" + processAudio.getLogicOne() + "\n";
    }

    public String getFreqSeqLogicEntries() {
        return processAudio.getLogicEntries();
    }

    public ArrayList<Integer> getFreqSequence() {
        return frequencySequence;
    }
}

