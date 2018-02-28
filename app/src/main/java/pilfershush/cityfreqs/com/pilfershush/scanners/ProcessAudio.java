package pilfershush.cityfreqs.com.pilfershush.scanners;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.R;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class ProcessAudio {
    private Context context;
    private Entry<Integer, Integer> logicZero;
    private Entry<Integer, Integer> logicOne;
    private List<Map.Entry<Integer,Integer>> entries;

    public ProcessAudio(Context context) {
        this.context = context;
    }

    /********************************************************************/

    public boolean checkFrequency(int freq) {
        return ((freq >= AudioSettings.DEFAULT_FREQUENCY_MIN) &&
                (freq <= AudioSettings.DEFAULT_FREQUENCY_MAX));
    }

    public boolean hasFreqSequenceDuplicates(ArrayList<Integer> freqList) {
        return checkSequenceDuplicates(freqList);
    }

    public String getLogicEntries() {
        String logicEntries = "";
        for (Map.Entry<Integer, Integer> e : entries) {
            logicEntries += context.getString(R.string.process_audio_1) + e.getKey() + " : " + e.getValue() + "\n";
        }
        return logicEntries;
    }

    public String getLogicZero() {
        if (logicZero != null) {
            return context.getString(R.string.process_audio_2) + logicZero.getKey() + " : " + logicZero.getValue();
        }
        else {
            return context.getString(R.string.process_audio_3);
        }
    }

    public String getLogicOne() {
        if (logicOne != null) {
            return context.getString(R.string.process_audio_4) + logicOne.getKey() + " : " + logicOne.getValue();
        }
        else {
            return context.getString(R.string.process_audio_5);
        }
    }

    /********************************************************************/

    /*
    private boolean checkFrequencyDivisor(int freq) {
        return freq % AudioSettings.getFreqStep() == 0;
    }
    */

    @SuppressLint("UseSparseArrays")
    private boolean checkSequenceDuplicates(ArrayList<Integer> freqList) {
        // ideally looking for two freqs that are logic 0 and logic 1
        // they may/should be separated by 100Hz

        // N.B. we are not timing the audio scan to stop after 32 bits received...

        // break for no audio:
        if (freqList.isEmpty()) {
            debugProcessAudio(context.getString(R.string.process_audio_6));
            return false;
        }

        // sort into list of frequencies and number of occurrences:
        // freqMap<int frequency, int count>
        HashMap<Integer, Integer> freqMap = new HashMap<Integer, Integer>();
        for (int freq : freqList) {
            if (freqMap.containsKey(freq)) {
                freqMap.put(freq, freqMap.get(freq) + 1);
            }
            else {
                freqMap.put(freq, 1);
            }
        }

        // sort into list with highest values order
        entries = new ArrayList<Map.Entry<Integer,Integer>>(freqMap.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<Integer, Integer>>() {
            public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });

        // top 2 entries are of interest?
        if (entries != null && !entries.isEmpty()) {
            // this is proving more useful for determining and debug
            for (Map.Entry<Integer, Integer> e : entries) {
                debugProcessAudio(context.getString(R.string.process_audio_1) + e.getKey() + " : " + e.getValue());
            }

            // we need at least two entries for this:
            if (entries.get(0) != null) {
                logicZero = entries.get(0);
                if (entries.size() >= 2) {
                    if (entries.get(1) != null) {
                        logicOne = entries.get(1);
                        return true;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void debugProcessAudio(String message) {
        MainActivity.logger(message);
    }
}

