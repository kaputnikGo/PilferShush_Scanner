package cityfreqs.com.pilfershush.scanners;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cityfreqs.com.pilfershush.MainActivity;
import cityfreqs.com.pilfershush.R;
import cityfreqs.com.pilfershush.assist.AudioSettings;

public class ProcessAudio {
    private Context context;
    private Entry<Integer, Integer> logicZero;
    private Entry<Integer, Integer> logicOne;
    private List<Map.Entry<Integer,Integer>> entries;

    public ProcessAudio(Context context) {
        this.context = context;
    }

    /********************************************************************/

    boolean checkFrequency(int freq) {
        return ((freq >= AudioSettings.DEFAULT_FREQUENCY_MIN) &&
                (freq <= AudioSettings.DEFAULT_FREQUENCY_MAX));
    }

    boolean hasFreqSequenceDuplicates(ArrayList<Integer> freqList) {
        return checkSequenceDuplicates(freqList);
    }

    String getLogicEntries() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Integer, Integer> e : entries) {
            sb.append(context.getString(R.string.process_audio_1))
                    .append(e.getKey())
                    .append(" : ")
                    .append(e.getValue())
                    .append("\n");
        }
        return sb.toString();
    }

    String getLogicZero() {
        if (logicZero != null) {
            return context.getString(R.string.process_audio_2) + logicZero.getKey() + " : " + logicZero.getValue();
        }
        else {
            return context.getString(R.string.process_audio_3);
        }
    }

    String getLogicOne() {
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
        // looking for two prominent freqs that may be thought of as logic 0 and logic 1
        // break for no audio:
        if (freqList.isEmpty()) {
            entryLogger(context.getString(R.string.process_audio_6), true);
            return false;
        }

        // sort into list of frequencies and number of occurrences:
        // freqMap<int frequency, int count>
        HashMap<Integer, Integer> freqMap = new HashMap<>();
        for (int freq : freqList) {
            if (freqMap.containsKey(freq)) {
                freqMap.put(freq, freqMap.get(freq) + 1);
            }
            else {
                freqMap.put(freq, 1);
            }
        }

        // sort into list with highest values order
        entries = new ArrayList<>(freqMap.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<Integer, Integer>>() {
            public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });

        // top 2 entries are of interest?
        if (entries != null && !entries.isEmpty()) {
            // this is proving more useful for determining and debug
            for (Map.Entry<Integer, Integer> e : entries) {
                entryLogger(context.getString(R.string.process_audio_1) + e.getKey() + " : " + e.getValue(), false);
            }

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

    private void entryLogger(String entry, boolean caution) {
        MainActivity.entryLogger(entry, caution);
    }
}

