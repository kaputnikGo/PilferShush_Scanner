package pilfershush.cityfreqs.com.pilfershush;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.util.HashMap;
import java.util.Iterator;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.assist.DeviceContainer;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "PilferShush";
    private static final boolean DEBUG = true;

    private ViewSwitcher viewSwitcher;
    private boolean mainView;
    private static TextView debugText;
    private TextView focusText;
    private Button micCheckButton;
    private Button micPollingButton;
    private Button runScansButton;
    private Button debugViewButton;
    private Button mainViewButton;
    private TextView mainScanText;

    private String[] pollSpeedList;
    private String[] freqSteps;
    private String[] dbLevel;

    // USB
    private DeviceContainer deviceContainer;
    private UsbManager usbManager;

    private boolean output;
    private boolean checkAble;
    private boolean micChecking;
    private boolean polling;
    private boolean SCANNING;

    private PilferShushScanner pilferShushScanner;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener audioFocusListener;
    public static AudioVisualiserView visualiserView;

    private AlertDialog.Builder dialogBuilder;
    private AlertDialog alertDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewSwitcher = (ViewSwitcher) findViewById(R.id.main_view_switcher);
        mainView = true;

        pilferShushScanner = new PilferShushScanner();
        output = false;
        SCANNING = false;

        //MAIN VIEW
        runScansButton = (Button) findViewById(R.id.run_scans_button);
        runScansButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleScanning();
            }
        });
        mainScanText = (TextView) findViewById(R.id.main_scan_text);
        mainScanText.setTextColor(Color.parseColor("#00ff00"));
        mainScanText.setMovementMethod(new ScrollingMovementMethod());

        visualiserView = (AudioVisualiserView) findViewById(R.id.audio_visualiser_view);

        debugViewButton = (Button) findViewById(R.id.debug_view_button);
        debugViewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switchViews();
            }
        });

        // DEBUG VIEW
        micCheckButton = (Button) findViewById(R.id.mic_check_button);
        micCheckButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleMicCheck();
            }
        });
        micPollingButton = (Button) findViewById(R.id.mic_polling_button);
        micPollingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                togglePollingCheck();
            }
        });
        debugText = (TextView) findViewById(R.id.debug_text);
        debugText.setTextColor(Color.parseColor("#00ff00"));
        debugText.setMovementMethod(new ScrollingMovementMethod());
        debugText.setOnClickListener(new TextView.OnClickListener() {
            @Override
            public void onClick(View v) {
                debugText.setGravity(Gravity.NO_GRAVITY);
                debugText.setSoundEffectsEnabled(false); // no further click sounds
            }
        });
        focusText = (TextView) findViewById(R.id.focus_text);
        focusText.setTextColor(Color.parseColor("#ffff00")); // yellow

        mainViewButton = (Button) findViewById(R.id.main_view_button);
        mainViewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switchViews();
            }
        });


        // permissions ask:
        // check API version, above 23 permissions are asked at runtime
        // if API version < 23 (6.x) fallback is manifest.xml file permission declares
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_DENIED) {
                requestRecordAudioPermission();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_DENIED) {
                    requestExtStorageWritePermission();
                    // not a vital permission
                }
            }
            else {
                // assume already runonce, has permissions
                initPilferShush();
            }
        }
        else {
            initPilferShush();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        // refocus app, ready for fresh scanner run
    }

    @Override
    protected void onPause() {
        super.onPause();
        //TODO
        // sort out things here, clean up and ready for a possible restart/refocus
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pilferShushScanner.onDestroy();
    }


    /********************************************************************/
// need to be able to have main view that is simple,
// then a switch for the debug view.
    private void switchViews() {
        if (mainView) {
            viewSwitcher.showNext();
            mainView = false;
        }
        else {
            viewSwitcher.showPrevious();
            mainView = true;
        }
    }


    /********************************************************************/
/*
 * MENU
 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_settings:
                changePollingSpeed();
                return true;
            case R.id.action_audio_scan_settings:
                changeAudioScanSettings();
                return true;
            case R.id.action_audio_beacons:
                hasAudioBeaconAppsList();
                return true;
            case R.id.action_override_scan:
                hasUserAppsList();
                return true;
            case R.id.action_write_file:
                changeWriteFile();
                return true;
            default:
                // do not consume the action
                return super.onOptionsItemSelected(item);

        }
    }

    /********************************************************************/
/*
 * 	INIT
 */
    private void requestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }
    private void requestExtStorageWritePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == 1) {
            // If request is cancelled, the result arrays are empty.
            // case:1 == RECORD_AUDIO permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                entryLogger("App record audio permission granted.", false);
                initPilferShush();
            }
            else {
                entryLogger("App record audio permission denied.", true);
                // no point in running the app ?
                // have a non recording state, just an app checker?
                finish();
            }
        }
        else if (requestCode == 2) {
            // If request is cancelled, the result arrays are empty.
            // case:2 == WRITE_EXTERNAL_STORAGE permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                entryLogger("Write external storage permission granted.", false);
            }
            else {
                entryLogger("Write external storage permission denied, using internal.", true);
                // fall back to internal
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean scanUsbDevices() {
        // search for any attached usb devices that we can read properties,
        // create a DeviceContainer for them.
        // add a listener service in case user unplugs usb and audio gets re-routed (fdbk)
        //TODO
        logger("checking usb host for audio device(s).");
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        boolean found = false;
        //int i = 0;
        //deviceContainer = new DeviceContainer();

        while(deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            deviceContainer = new DeviceContainer(device);
            found = true;
            logger("USB: " + deviceContainer.toString());
            //i++;
        }
        if (!found) logger("usb device(s) not found.");
        // clean up
        // check if audio compliant device, then return
        return found;
    }

    private void initPilferShush() {
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if (pilferShushScanner.initScanner(this, scanUsbDevices())) {
            checkAble = pilferShushScanner.checkScanner();
            micChecking = false;
            toggleHeadset(output);
            quickAudioFocusCheck();
            initAudioFocusListener();
            populateMenuItems();
            reportInitialState();
        }
        else {
            mainScanLogger("PilferShush init failed.", true);
            logger("Failed to init audio device.");
        }
    }

    private void reportInitialState() {
        mainScanText.setText("PilferShush scanner ready to scan for:");
        mainScanLogger(AudioSettings.DEFAULT_FREQUENCY_MIN + "+ Hz at "
                + AudioSettings.DEFAULT_FREQ_STEP + "Hz steps, above 100 dB.", false);

        mainScanLogger("\nSettings can be changed via the Options menu.", false);
        mainScanLogger("\nThe Detailed View has logging and more information from scans. " +
                "It also has a continuous Mic check and intermittent polling check " +
                "to look for other apps using the microphone.", false);

        mainScanLogger("\nPress 'Run Scanner' button to start and stop scanning for audio.", false);
        mainScanLogger("\nDO NOT RUN SCANNER FOR A LONG TIME.\n", true);
    }

    private void populateMenuItems() {
        pollSpeedList = new String[4];
        pollSpeedList[0] = getResources().getString(R.string.polling_1_text);
        pollSpeedList[1] = getResources().getString(R.string.polling_2_text);
        pollSpeedList[2] = getResources().getString(R.string.polling_3_text);
        pollSpeedList[3] = getResources().getString(R.string.polling_default_text);

        freqSteps = new String[5];
        freqSteps[0] = getResources().getString(R.string.freq_step_10_text);
        freqSteps[1] = getResources().getString(R.string.freq_step_25_text);
        freqSteps[2] = getResources().getString(R.string.freq_step_50_text);
        freqSteps[3] = getResources().getString(R.string.freq_step_75_text);
        freqSteps[4] = getResources().getString(R.string.freq_step_100_text);

        dbLevel = new String[5];
        dbLevel[0] = getResources().getString(R.string.magnitude_50_text);
        dbLevel[1] = getResources().getString(R.string.magnitude_70_text);
        dbLevel[2] = getResources().getString(R.string.magnitude_80_text);
        dbLevel[3] = getResources().getString(R.string.magnitude_90_text);
        dbLevel[4] = getResources().getString(R.string.magnitude_100_text);
    }

    private void changeWriteFile() {
        // flip current value
        dialogBuilder = new AlertDialog.Builder(this);

        dialogBuilder.setTitle(R.string.dialog_write_file);
        dialogBuilder.setPositiveButton(R.string.dialog_write_file_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                pilferShushScanner.setWriteFileSwitch(true);
                mainScanLogger("Write to file enabled.", false);
            }
        });
        dialogBuilder.setNegativeButton(R.string.dialog_write_file_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                pilferShushScanner.setWriteFileSwitch(false);
                mainScanLogger("Write to file disabled.", false);
            }
        });

        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void changePollingSpeed() {
        // set the interval delay for polling,
        // make it a radio button list of presets, or slow, med, fast
        // limits are: 1000 and 6000 (1 sec and 6 sec) ??

        if (polling) {
            // stop it
            togglePollingCheck();
        }

        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(pollSpeedList, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                switch(which) {
                    case 0:
                        pilferShushScanner.setPollingSpeed(AudioSettings.SHORT_DELAY);
                        break;
                    case 1:
                        pilferShushScanner.setPollingSpeed(AudioSettings.SEC_2_DELAY);
                        break;
                    case 2:
                        pilferShushScanner.setPollingSpeed(AudioSettings.SEC_3_DELAY);
                        break;
                    case 3:
                    default:
                        pilferShushScanner.setPollingSpeed(AudioSettings.LONG_DELAY);
                        break;
                }
            }
        });
        dialogBuilder.setTitle(R.string.dialog_polling);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void changeAudioScanSettings() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(freqSteps, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                switch(which) {
                    case 0:
                        pilferShushScanner.setFrequencyStep(AudioSettings.FREQ_STEP_10);
                        break;
                    case 1:
                        pilferShushScanner.setFrequencyStep(AudioSettings.FREQ_STEP_25);
                        break;
                    case 2:
                        pilferShushScanner.setFrequencyStep(AudioSettings.FREQ_STEP_50);
                        break;
                    case 3:
                        pilferShushScanner.setFrequencyStep(AudioSettings.FREQ_STEP_75);
                        break;
                    case 4:
                        pilferShushScanner.setFrequencyStep(AudioSettings.MAX_FREQ_STEP);
                        break;
                    default:
                        pilferShushScanner.setFrequencyStep(AudioSettings.DEFAULT_FREQ_STEP);
                }
            }
        });

        dialogBuilder.setTitle(R.string.dialog_freq_step);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }


    /********************************************************************/
/*
 * ACTION SCANS
 */
    private void toggleScanning() {
        logger("Scanning button pressed");
        if (SCANNING) {
            SCANNING = false;
            stopScanner();
        }
        else {
            SCANNING = true;
            runScanner();
        }
    }

    private void runScanner() {
        runScansButton.setText("SCANNING...");
        runScansButton.setBackgroundColor(Color.RED);

        mainScanLogger("Running scans on user installed apps...", false);

        // two diff methods of doing same thing... lols
        int audioNum = pilferShushScanner.getAudioRecordAppsNumber();
        if (audioNum > 0) {
            mainScanLogger("Record audio apps found: " + audioNum, true);
        }
        else {
            mainScanLogger("No record audio apps found.", false);
        }
        if (pilferShushScanner.hasAudioBeaconApps()) {
            mainScanLogger(pilferShushScanner.getAudioBeaconAppNumber()
                    + " Audio Beacon SDKs detected.", true);
        }
        else {
            mainScanLogger("No Audio Beacon SDKs detected.", false);
        }

        mainScanLogger("Microphone check...", false);
        if (pilferShushScanner.mainPollingCheck()) {
            mainScanLogger("Microphone use detected.", true);
        }
        else {
            mainScanLogger("No microphone use detected.", false);
        }
        pilferShushScanner.mainPollingStop();

        mainScanLogger("Listening for near-ultra high audio...", false);
        pilferShushScanner.runAudioScanner();
    }

    private void stopScanner() {
        // FINISHED, determine type of signal
        pilferShushScanner.stopAudioScanner();
        runScansButton.setText("Run Scanner");
        runScansButton.setBackgroundColor(Color.LTGRAY);

        mainScanLogger("Stop listening for audio.", false);

        if (pilferShushScanner.hasAudioScanSequence()) {
            mainScanLogger("Detected audio beacon modulated signal: \n", true);
            mainScanLogger(pilferShushScanner.getModFrequencyLogic(), true);

            // a debug of all modfreq captures
            //mainScanLogger(pilferShushScanner.getFreqSeqLogicEntries(), true);
            // a debug, output in order of capture:
            //mainScanLogger(pilferShushScanner.getFrequencySequence(), true);

            // TODO - this buffer scan
            if (pilferShushScanner.hasBufferStorage()) {
                Toast scanToast = Toast.makeText(this, "Processing buffer scan data...", Toast.LENGTH_LONG);
                scanToast.show();

                mainScanLogger("Running scans on captured signals...", false);
                if (pilferShushScanner.runBufferScanner()) {
                    mainScanLogger("Found buffer scan data:", true);
                    // do something with it...
                    mainScanLogger(pilferShushScanner.getBufferScanReport(), true);
                }
                scanToast.cancel();
            }
        }
        else {
            mainScanLogger("No detected audio beacon modulated signals.", false);
        }

        if (pilferShushScanner.hasAudioScanCharSequence()) {
            mainScanLogger("Detected audio beacon alphabet signal: \n", true);
            mainScanLogger(pilferShushScanner.getAudioScanCharSequence(), true);
        }
        else {
            mainScanLogger("No detected audio beacon alphabet signals.", false);
        }
        pilferShushScanner.stopBufferScanner();
        mainScanLogger("\n[>-:end of scan:-<]\n\n", false);
    }

    private void hasAudioBeaconAppsList() {
        String[] appNames = pilferShushScanner.getAudioBeaconAppList();

        if (appNames != null && appNames.length > 0) {
            // proceed to list
            dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setItems(appNames, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int which) {
                    // index position of clicked app name
                    pilferShushScanner.listBeaconDetails(which);
                }
            });
            dialogBuilder.setTitle(R.string.dialog_audio_beacon_apps);
            alertDialog = dialogBuilder.create();
            alertDialog.show();
        }
        else {
            // none found, inform user
            entryLogger("NO AUDIO BEACON APPS FOUND.", true);
        }
    }

    private void hasUserAppsList() {
        String[] appNames = pilferShushScanner.getScanAppList();

        if (appNames != null && appNames.length > 0) {
            dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setItems(appNames, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int which) {
                    // index position of clicked app name
                    pilferShushScanner.listScanDetails(which);
                }
            });
            dialogBuilder.setTitle(R.string.dialog_override_scan_apps);
            alertDialog = dialogBuilder.create();
            alertDialog.show();
        }
        entryLogger("NO USER APPS FOUND FOR OVERRIDE SCAN.", true);

    }


    /********************************************************************/
/*
 * 	AUDIO
 */
    private void quickAudioFocusCheck() {
        // this may not work as SDKs requesting focus may not get it cos we already have it?

        entryLogger("AudioFocus check...", false);
        int result = audioManager.requestAudioFocus(audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            entryLogger("AudioFocus request granted.", false);
        }
        else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            entryLogger("AudioFocus request failed.", false);
        }
        else {
            entryLogger("AudioFocus unknown.", false);
        }
    }

    private void toggleHeadset(boolean output) {
        // if no headset, mute the audio output
        if (output) {
            // volume to 50%
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2,
                    AudioManager.FLAG_SHOW_UI);
        }
        else {
            // volume to 0
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    0,
                    AudioManager.FLAG_SHOW_UI);
        }
    }

    private void toggleMicCheck() {
        if (polling) {
            // do not do this as well
            entryLogger("DO NOT CHECK WHEN POLLING", true);
            return;
        }

        if (micChecking) {
            // currently running, stop it
            pilferShushScanner.micChecking(micChecking = false);
            micCheckButton.setText("MICROPHONE CHECK");
            micCheckButton.setBackgroundColor(Color.LTGRAY);
        }
        else {
            // not running, start it
            if (checkAble) {
                micCheckButton.setText("CHECKING...");
                micCheckButton.setBackgroundColor(Color.RED);
                pilferShushScanner.micChecking(micChecking = true);
            }
        }
    }

    private void togglePollingCheck() {
        if (micChecking) {
            // do not do this as well
            entryLogger("DO NOT POLL WHEN MIC CHECKING", true);
            return;
        }
        if (polling) {
            pilferShushScanner.pollingCheck(polling = false);
            micPollingButton.setText("POLLING CHECK");
            micPollingButton.setBackgroundColor(Color.LTGRAY);
        }
        else {
            pilferShushScanner.pollingCheck(polling = true);
            micPollingButton.setText("POLLING...");
            micPollingButton.setBackgroundColor(Color.RED);
        }
    }

    private void initAudioFocusListener() {
        //Audio Focus Listener: STATE
        //focusText.setText("Audio Focus Listener: running.");
        //TODO
        // proper notifications sent to audioManger and UI...

        // eg. use Do/While loop in onAudioFocusChange() method:
        // do (if(focusChange == 1) runnable listener @ 60ms)
        // while (focusChange != -1)

        audioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch(focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        // -1
                        // loss for unknown duration
                        focusText.setText("Audio Focus Listener: LOSS.");
                        audioManager.abandonAudioFocus(audioFocusListener);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // -2
                        // temporary loss ? API docs says a "transient loss"!
                        focusText.setText("Audio Focus Listener: LOSS_TRANSIENT.");
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // -3
                        // loss to other audio source, this can duck for the short duration if it wants
                        focusText.setText("Audio Focus Listener: LOSS_TRANSIENT_DUCK.");
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        // 0
                        // failed focus change request
                        focusText.setText("Audio Focus Listener: REQUEST_FAIL.");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        //case AudioManager.AUDIOFOCUS_REQUEST_GRANTED: <- duplicate int value...
                        // 1
                        // has gain, or request for gain, of unknown duration
                        focusText.setText("Audio Focus Listener: GAIN.");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        // 2
                        // temporary gain or request for gain, for short duration (ie. notification)
                        focusText.setText("Audio Focus Listener: GAIN_TRANSIENT.");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        // 3
                        // as above but with other background audio ducked for duration
                        focusText.setText("Audio Focus Listener: GAIN_TRANSIENT_DUCK.");
                        break;
                    default:
                        //
                        focusText.setText("Audio Focus Listener: UNKNOWN STATE.");
                }
            }
        };
    }

    /********************************************************************/
/*
 * 	LOGGER
 */
    private void mainScanLogger(String entry, boolean caution) {
        // this prints to MainView.log
        int start = mainScanText.getText().length();
        mainScanText.append("\n" + entry);
        int end = mainScanText.getText().length();
        Spannable spannableText = (Spannable) mainScanText.getText();
        if (caution) {
            spannableText.setSpan(new ForegroundColorSpan(Color.YELLOW), start, end, 0);
        }
        else {
            spannableText.setSpan(new ForegroundColorSpan(Color.GREEN), start, end, 0);
        }
    }

    protected static void entryLogger(String entry, boolean caution) {
        // this prints to console.log and DetailedView.log
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

    public static void logger(String message) {
        if (DEBUG) {
            debugText.append("\n" + TAG + ": " + message);
            Log.d(TAG, message);
        }
    }
}
