package pilfershush.cityfreqs.com.pilfershush.scanners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.annotation.SuppressLint;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class ProcessAudio {
    private String tempSeq = new String();
    private String startBit = "A";
    private String stopBit = "a";
    private Entry<Integer, Integer> logicZero;
    private Entry<Integer, Integer> logicOne;
    private List<Map.Entry<Integer,Integer>> entries;

    /********************************************************************/

    public boolean checkFrequency(int freq) {
        if ((freq >= AudioSettings.DEFAULT_FREQUENCY_MIN) &&
                (freq <= AudioSettings.DEFAULT_FREQUENCY_MAX)) {
            // check if its a good number?
            return checkFrequencyDivisor(freq);
        }
        return false;
    }

    public boolean hasFreqSequenceDuplicates(ArrayList<Integer> freqList) {
        return checkSequenceDuplicates(freqList);
    }

    public String processFreqChar(int candidateFreq) {
        String str = processSingleChar(getCorrespondingChar(candidateFreq));
        if ((str != null) && (!str.equals(""))) {
            // reset
            return str;
        }
        return null;
    }

    public void resetSequences() {
        tempSeq = new String();
    }

    public String getLogicEntries() {
        String logicEntries = "";
        for (Map.Entry<Integer, Integer> e : entries) {
            logicEntries += "Freq: " + e.getKey() + " : " + e.getValue() + "\n";
        }
        return logicEntries;
    }

    public String getLogicZero() {
        if (logicZero != null) {
            return "Freq 0: " + logicZero.getKey() + " : " + logicZero.getValue();
        }
        else {
            return "Freq 0: not found";
        }
    }

    public String getLogicOne() {
        if (logicOne != null) {
            return "Freq 1: " + logicOne.getKey() + " : " + logicOne.getValue();
        }
        else {
            return "Freq 1: not found";
        }
    }

    /********************************************************************/

    private boolean checkFrequencyDivisor(int freq) {
        return freq % AudioSettings.FREQ_DIVISOR == 0;
    }

    @SuppressLint("UseSparseArrays")
    private boolean checkSequenceDuplicates(ArrayList<Integer> freqList) {
        // ideally looking for two freqs that are logic 0 and logic 1
        // they may/should be separated by 100Hz

        // N.B. we are not timing the audio scan to stop after 32 bits received...

        // break for no audio:
        if (freqList.isEmpty()) {
            debugProcessAudio("freqList is empty.");
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
                debugProcessAudio("Freq: " + e.getKey() + " : " + e.getValue());
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



    //TODO need to add the freqStep, not the default 75 hertz...
    // these may not correspond to anything...
    private String getCorrespondingChar(int freq) {
        switch (freq) {
            case 18000:
                return "A";
            case 18075:
                return "B";
            case 18150:
                return "C";
            case 18225:
                return "D";
            case 18300:
                return "E";
            case 18375:
                return "F";
            case 18450:
                return "G";
            case 18525:
                return "H";
            case 18600:
                return "I";
            case 18675:
                return "J";
            case 18750:
                return "K";
            case 18825:
                return "L";
            case 18900:
                return "M";
            case 18975:
                return "N";
            case 19050:
                return "O";
            case 19125:
                return "P";
            case 19200:
                return "Q";
            case 19275:
                return "R";
            case 19350:
                return "S";
            case 19425:
                return "T";
            case 19500:
                return "U";
            case 19575:
                return "V";
            case 19650:
                return "W";
            case 19725:
                return "X";
            case 19800:
                return "Y";
            case 19875:
                return "Z";
            case 19950:
                return "a";
            default:
                return null;
        }
    }

    // send single char to MainActivity
    //TODO
    // this logic for seq order not useful here,
    // need to be more flexible for out of seq chars (ie not alphabetical, etc)

    private String processSingleChar(String candidateChar) {
        if (candidateChar == null || candidateChar == "") {
            return null;
        }
        // START BIT
        if (candidateChar.compareTo(startBit) == 0) {
            if (tempSeq.indexOf(startBit) != 0) {
                tempSeq += startBit;
                //MainActivity.logger(TAG, "found startBit.");
                // we have a startBit char
                return startBit;
            }
            else {
                // we have a char
                return null;
            }
        }
        //STOP BIT
        else if (candidateChar.compareTo(stopBit) == 0) {
            if (tempSeq.contains(startBit)) {
                tempSeq += stopBit;
                //MainActivity.logger(TAG, "found stopBit: tempSeq is: " + tempSeq);
                tempSeq = new String();
                return stopBit;
            }
            else {
                // may have received it before complete payload...
                return null;
            }
        }
        //PAYLOAD
        else if (tempSeq.indexOf(startBit) == 0) {
            if (!tempSeq.contains(candidateChar)) {
                if (candidateChar.compareTo(tempSeq.substring(tempSeq.length() - 1)) > 0) {
                    // check if alphabetical
                    tempSeq += candidateChar;
                    return candidateChar;
                }
                else {
                    // out of alphabetical order
                    return null;
                }
            }
            else {
                // we have dupe char?
                return null;
            }
        }
        //CATCH AND DEFAULT
        else {
            if (tempSeq.length() >= AudioSettings.MAX_SEQUENCE_LENGTH) {
                //MainActivity.logger(TAG, "tempSeq is filled.");
                return null;
            }
            // we have a char out of sequence
            return null;
        }
    }

    private void debugProcessAudio(String message) {
        MainActivity.logger(message);
    }
}

