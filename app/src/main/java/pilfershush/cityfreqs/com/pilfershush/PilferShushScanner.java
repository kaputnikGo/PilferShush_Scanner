package pilfershush.cityfreqs.com.pilfershush;

import android.content.Context;

import pilfershush.cityfreqs.com.pilfershush.assist.WriteProcessor;
import pilfershush.cityfreqs.com.pilfershush.scanners.AudioScanner;

public class PilferShushScanner {
    private Context context;
    private BackgroundChecker backgroundChecker;
    private AudioChecker audioChecker;
    private AudioScanner audioScanner;
    private WriteProcessor writeProcessor;
    private int scanBufferSize;
    private String bufferScanReport;
    public static boolean WRITE_FILE = false;

    protected void onDestroy() {
        audioChecker.destroy();
        backgroundChecker.destroy();
    }

    /********************************************************************/
/*
*
*/
    protected boolean initScanner(Context context, boolean hasUSB) {
        this.context = context;
        scanBufferSize = 0;
        audioChecker = new AudioChecker();
        writeProcessor = new WriteProcessor(context, "capture");
        // writes txt file to same location as audio records.
        // write init checks then close the file.
        // called again at runScanner.
        writeProcessor.prepareLogToFile();

        if (audioChecker.determineInternalAudioType()) {
            entryLogger(audioChecker.getAudioSettings().toString(), false);
            audioScanner = new AudioScanner(audioChecker.getAudioSettings());
            // get internalAudio settings here.
            initBackgroundChecks();
            return true;
        }
        // TODO wont yet run usb audio, no return true, background checks...
        if(hasUSB) {
            if (audioChecker.determineUsbAudioType(hasUSB)) {
                MainActivity.logger("has usb audio type.");
            }
            else {
                MainActivity.logger("no usb audio type found.");
            }
        }
        return false;
    }

    protected void closeLogWrites() {
        writeProcessor.closeLogFile();
    }

    protected void resumeLogWrites() {
        writeProcessor.prepareLogToFile();
    }

    protected boolean checkScanner() {
        return audioChecker.checkAudioRecord();
    }

    protected void setPollingSpeed(int delayMS) {
        audioChecker.setPollingSpeed(delayMS);
        entryLogger("Polling speed set to " + delayMS + " ms.", false);
    }

    protected void micChecking(boolean checking) {
        if (checking) {
            audioChecker.checkAudioBufferState();

            //TODO
            // this function is of concern...
            backgroundChecker.auditLogAsync();
        }
        else {
            audioChecker.stopAllAudio();
        }
    }

    protected void pollingCheck(boolean polling) {
        if (polling) {
            if (audioChecker.pollAudioCheckerInit()) {
                audioChecker.pollAudioCheckerStart();
            }
        }
        else {
            audioChecker.finishPollChecker();
        }
    }

    protected void setFrequencyStep(int step) {
        audioScanner.setFreqStep(step);
        entryLogger("FreqStep changed to: " + step, false);
    }

    protected void setMinMagnitude(double magnitude) {
        audioScanner.setMinMagnitude(magnitude);
        entryLogger("Magnitude level set: " + magnitude, false);
    }

    protected void runAudioScanner() {
        entryLogger("AudioScanning start...", false);
        scanBufferSize = 0;
        writeProcessor.prepareWriteToFile();
        audioScanner.runAudioScanner();
    }

    protected void setWriteFileSwitch(boolean userChoice) {
        WRITE_FILE = userChoice;
    }


    protected void stopAudioScanner() {
        if (audioScanner != null) {
            entryLogger("AudioScanning stop.", false);
            // below nulls the recordTask...
            audioScanner.stopAudioScanner();

            if (audioScanner.canProcessBufferStorage()) {
                scanBufferSize = audioScanner.getSizeBufferStorage();
                entryLogger("BufferStorage size: " + scanBufferSize, false);
            }
            else {
                entryLogger("BufferStorage: FALSE", false);
            }
        }
    }

    protected boolean hasBufferStorage() {
        return scanBufferSize > 0;
    }

    protected boolean runBufferScanner() {
        if (audioScanner.runBufferScanner()) {
            entryLogger("Buffer Scan found data.", true);
            bufferScanReport = "";
            audioScanner.storeBufferScanMap();
            // do something with it
            // process Audio has useful methods...
            if (audioScanner.processBufferScanMap()) {
                bufferScanReport = "Buffer Scan data: \n" + audioScanner.getLogicEntries();
                entryLogger(bufferScanReport, true);
                return true;
            }
        }
        else {
            entryLogger("BufferScan nothing found.", false);
        }
        return false;
    }

    protected String getBufferScanReport() {
        return bufferScanReport;
    }

    protected void stopBufferScanner() {
        audioScanner.stopBufferScanner();
    }

    /********************************************************************/

    protected int getAudioRecordAppsNumber() {
        return backgroundChecker.getUserRecordNumApps();
    }

    protected boolean hasAudioBeaconApps() {
        return backgroundChecker.checkAudioBeaconApps();
    }

    protected int getAudioBeaconAppNumber() {
        return backgroundChecker.getAudioBeaconAppNames().length;
    }

    protected String[] getAudioBeaconAppList() {
        return backgroundChecker.getAudioBeaconAppNames();
    }

    protected String[] getScanAppList() {
        return backgroundChecker.getOverrideScanAppNames();
    }

    protected void listBeaconDetails(int appNumber) {
        listAppAudioBeaconDetails(appNumber);
    }

    protected void listScanDetails(int appNumber) {
        listAppOverrideScanDetails(appNumber);
    }

    protected boolean mainPollingCheck() {
        boolean detected = false;
        setPollingSpeed(100);
        if (audioChecker.pollAudioCheckerInit()) {
            audioChecker.pollAudioCheckerStart();
            detected = audioChecker.getDetected();
        }
        return detected;
    }

    protected void mainPollingStop() {
        audioChecker.finishPollChecker();
    }

    protected boolean hasAudioScanSequence() {
        return audioScanner.hasFrequencySequence();
    }

    protected String getModFrequencyLogic() {
        return audioScanner.getFrequencySequenceLogic();
    }

    // MainActivity.stopScanner() debug type outputs
    // currently rem'd out
    protected String getFrequencySequence() {
        // get original sequence as transmitted...
        String sequence = "";
        for (Integer freq : audioScanner.getFreqSequence()) {
            sequence += freq.toString();
            // add a space
            sequence += " ";
        }
        return sequence;
    }

    protected String getFreqSeqLogicEntries() {
        return audioScanner.getFreqSeqLogicEntries();
    }

    /********************************************************************/

    private void initBackgroundChecks() {
        backgroundChecker = new BackgroundChecker();
        if (backgroundChecker.initChecker(context.getPackageManager())) {
            // is good
            auditBackgroundChecks();
        }
        else {
            // is bad
            MainActivity.logger("is broke");
        }
    }

/*
* 	CHECKS
*/
    private void auditBackgroundChecks() {
        // is good
        MainActivity.logger("run background checks...\n");
        backgroundChecker.runChecker();

        MainActivity.logger("USER APPs with RECORD AUDIO: "
                + backgroundChecker.getUserRecordNumApps() + "\n");

        backgroundChecker.audioAppEntryLog();
    }

    private void listAppAudioBeaconDetails(int selectedIndex) {
        if (backgroundChecker.getAudioBeaconAppEntry(selectedIndex).checkBeaconServiceNames()) {
            entryLogger("Found audio beacon services for "
                    + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getActivityName()
                    + ": " + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconServiceNamesNum(), true);

            logAppEntryInfo(backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconServiceNames());
        }
        //TODO
        // add a call for any receiver names too
        if (backgroundChecker.getAudioBeaconAppEntry(selectedIndex).checkBeaconReceiverNames()) {
            entryLogger("Found audio beacon receivers for "
                    + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getActivityName()
                    + ": " + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconReceiverNamesNum(), true);

            logAppEntryInfo(backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconReceiverNames());
        }
    }

    private void listAppOverrideScanDetails(int selectedIndex) {
        // check for receivers too?
        entryLogger("Found User App services for "
                + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getActivityName()
                + ": " + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServicesNum(), true);

        if (backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServicesNum() > 0) {
            logAppEntryInfo(backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServiceNames());
        }

        entryLogger("Found User App receivers for "
                + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getActivityName()
                + ": " + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiversNum(), true);

        if (backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiversNum() > 0) {
            logAppEntryInfo(backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiverNames());
        }
    }

    private void logAppEntryInfo(String[] appEntryInfoList) {
        entryLogger("\nAppEntry list: \n", false);
        for (int i = 0; i < appEntryInfoList.length; i++) {
            entryLogger(appEntryInfoList[i] + "\n", false);
        }
    }

    private static void entryLogger(String entry, boolean caution) {
        MainActivity.entryLogger(entry, caution);
    }
}

