package cityfreqs.com.pilfershush;

import android.content.Context;
import android.os.Bundle;

import cityfreqs.com.pilfershush.assist.AudioChecker;
import cityfreqs.com.pilfershush.assist.AudioSettings;
import cityfreqs.com.pilfershush.assist.BackgroundChecker;
import cityfreqs.com.pilfershush.assist.WriteProcessor;
import cityfreqs.com.pilfershush.scanners.AudioScanner;

class PilferShushScanner {
    private Context context;
    private Bundle audioBundle;
    private BackgroundChecker backgroundChecker;
    private AudioChecker audioChecker;
    private AudioScanner audioScanner;
    private WriteProcessor writeProcessor;

    private int scanBufferSize;

    public PilferShushScanner(Context context, AudioChecker audioChecker) {
        this.context = context;
        this.audioChecker = audioChecker;
        this.audioBundle = audioChecker.getAudioBundle();
    }

    void onDestroy() {
        //
    }

    /********************************************************************/
/*
*
*/
    boolean initScanner() {
        scanBufferSize = 0;

        entryLogger(context.getString(R.string.audio_check_pre_1), false);
        if (audioChecker.determineRecordAudioType()) {
            entryLogger(getAudioCheckerReport(), false);
            // get output settings here.
            entryLogger(context.getString(R.string.audio_check_pre_2), false);
            if (!audioChecker.determineOutputAudioType()) {
                // have a setup error getting the audio for output
                entryLogger(context.getString(R.string.audio_check_pre_3), true);
            }
            writeProcessor = new WriteProcessor(context, audioBundle);
            audioScanner = new AudioScanner(context, audioBundle);
            initBackgroundChecks();
            return true;
        }
        return false;
    }

    void updateAudioBundle(Bundle audioBundle) {
        this.audioBundle = audioBundle;
    }

    /*
    Bundle getAudioBundle() {
        return audioBundle;
    }
    */

    String getSaveFileType() {
        return audioChecker.saveFormatToString();
    }

    void setWriteFiles(boolean writeFiles) {
        audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[19], writeFiles);
    }

    boolean canWriteFiles() {
        return audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[19]);
    }

    long getLogStorageSize() {
        return writeProcessor.getStorageSize();
    }

    long getFreeStorageSize() {
        return writeProcessor.getFreeStorageSpace();
    }

    boolean cautionFreeSpace() {
        return writeProcessor.cautionFreeSpace();
    }

    void clearLogStorageFolder() {
        writeProcessor.deleteStorageFiles();
    }

    String getAudioCheckerReport() {
        return ("audio record format: "
                + audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]) +
                ", " + audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]) +
                ", " + audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[3]) +
                ", " + audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[2]) +
                ", " + audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[0]));
    }

    boolean checkScanner() {
        return audioChecker.checkAudioRecord();
    }

    void setFrequencyStep(int freqStep) {

        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[16], freqStep);
        entryLogger(context.getString(R.string.option_set_2) + freqStep, false);
    }

    void setMagnitude(double magnitude) {
        audioScanner.setMagnitude(magnitude);
        entryLogger(context.getString(R.string.option_set_3) + magnitude, false);
    }

    void setFFTWindowType(int userWindow) {
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[18], userWindow);
    }

    void runAudioScanner() {
        entryLogger(context.getString(R.string.main_scanner_18), false);
        scanBufferSize = 0;
        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[19])) {
            if (!writeProcessor.prepareWriteAudioFile()) {
                entryLogger(context.getString(R.string.init_state_15), true);
            }
        }
        audioScanner.runAudioScanner();
    }

    void stopAudioScanner() {
        if (audioScanner != null) {
            entryLogger(context.getString(R.string.main_scanner_19), false);
            // below nulls the recordTask...
            audioScanner.stopAudioScanner();
            writeProcessor.audioFileConvert();

            if (audioScanner.canProcessBufferStorage()) {
                scanBufferSize = audioScanner.getSizeBufferStorage();
                entryLogger(context.getString(R.string.main_scanner_20) + scanBufferSize, false);
            }
            else {
                entryLogger(context.getString(R.string.main_scanner_21), false);
            }
        }
    }

    void resetAudioScanner() {
        audioScanner.resetAudioScanner();
    }


    /********************************************************************/

    int getAudioRecordAppsNumber() {
        return backgroundChecker.getUserRecordNumApps();
    }

    boolean hasAudioBeaconApps() {
        return backgroundChecker.checkAudioBeaconApps();
    }

    /*
    String displayAudioSdkList() {
        return backgroundChecker.displayAudioSdkNames();
    }
    */

    int getAudioBeaconAppNumber() {
        return backgroundChecker.getAudioBeaconAppNames().length;
    }

    String[] getAudioBeaconAppList() {
        return backgroundChecker.getAudioBeaconAppNames();
    }

    String[] getScanAppList() {
        return backgroundChecker.getOverrideScanAppNames();
    }

    /*
    void listBeaconDetails(int appNumber) {
        listAppAudioBeaconDetails(appNumber);
    }
    */

    void listScanDetails(int appNumber) {
        listAppOverrideScanDetails(appNumber);
    }

    boolean hasAudioScanSequence() {
        return audioScanner.hasFrequencySequence();
    }

    int getFrequencySequenceSize() {
        return audioScanner.getFrequencySequenceSize();
    }

    String getModFrequencyLogic() {
        return context.getString(R.string.audio_scan_1) + "\n" + audioScanner.getFrequencySequenceLogic();
    }

    String getFreqSeqLogicEntries() {
        return audioScanner.getFreqSeqLogicEntries();
    }

    /********************************************************************/

    private void initBackgroundChecks() {
        backgroundChecker = new BackgroundChecker(context, false);
        if (backgroundChecker.initChecker(context.getPackageManager())) {
            // is good
            auditBackgroundChecks();
        }
        else {
            // is bad
            MainActivity.logger(context.getString(R.string.background_scan_1));
        }
    }

/*
* 	CHECKS
*/
    private void auditBackgroundChecks() {
        // is good
        MainActivity.logger(context.getString(R.string.background_scan_2) + "\n");
        backgroundChecker.runChecker();

        MainActivity.logger(context.getString(R.string.background_scan_3) + backgroundChecker.getUserRecordNumApps() + "\n");

        backgroundChecker.audioAppEntryLog();
    }

    /*
    private void listAppAudioBeaconDetails(int selectedIndex) {
        if (backgroundChecker.getAudioBeaconAppEntry(selectedIndex).checkBeaconServiceNames()) {
            entryLogger(context.getString(R.string.background_scan_4)
                    + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getActivityName()
                    + ": " + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconServiceNamesNum(), true);

            logAppEntryInfo(backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconServiceNames());
        }
        // add a call for any receiver names too
        if (backgroundChecker.getAudioBeaconAppEntry(selectedIndex).checkBeaconReceiverNames()) {
            entryLogger(context.getString(R.string.background_scan_5)
                    + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getActivityName()
                    + ": " + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconReceiverNamesNum(), true);

            logAppEntryInfo(backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconReceiverNames());
        }
    }
    */

    private void listAppOverrideScanDetails(int selectedIndex) {
        // check for receivers too?
        entryLogger(context.getString(R.string.background_scan_6)
                + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getActivityName()
                + ": " + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServicesNum(), true);

        if (backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServicesNum() > 0) {
            logAppEntryInfo(backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServiceNames());
        }

        entryLogger(context.getString(R.string.background_scan_7)
                + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getActivityName()
                + ": " + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiversNum(), true);

        if (backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiversNum() > 0) {
            logAppEntryInfo(backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiverNames());
        }
    }

    private void logAppEntryInfo(String[] appEntryInfoList) {
        entryLogger("\n" + context.getString(R.string.background_scan_8) + "\n", false);
        for (String entry : appEntryInfoList) {
            entryLogger(entry + "\n", false);
        }
    }

    private static void entryLogger(String entry, boolean caution) {
        MainActivity.entryLogger(entry, caution);
    }
}

