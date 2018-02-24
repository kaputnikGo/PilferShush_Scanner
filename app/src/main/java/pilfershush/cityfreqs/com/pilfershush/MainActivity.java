package pilfershush.cityfreqs.com.pilfershush;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.util.HashMap;
import java.util.Iterator;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.assist.DeviceContainer;
import pilfershush.cityfreqs.com.pilfershush.assist.WriteProcessor;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "PilferShush";
    private static final boolean DEBUG = true;
    private static final boolean INIT_WRITE_FILES = true;
    // not until propered
    private static final boolean WRITE_WAV = false;


    // dev internal version numbering
    public static final String VERSION = "2.0.23";

    private ViewSwitcher viewSwitcher;
    private boolean mainView;
    private static TextView debugText;
    private TextView timerText;
    private long startTime;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private TextView focusText;
    private Button micCheckButton;
    private Button micPollingButton;
    private Button runScansButton;
    private Button debugViewButton;
    private Button mainViewButton;
    private TextView mainScanText;

    private String[] pollSpeedList;
    private String[] freqSteps;
    private String[] freqRanges;
    private String[] windowTypes;
    private String[] dbLevel;
    private String[] storageAdmins;

    // USB
    private static final String ACTION_USB_PERMISSION = "pilfershush.USB_PERMISSION";
    private PendingIntent permissionIntent;
    private DeviceContainer deviceContainer;
    private UsbManager usbManager;

    private boolean MIC_CHECKING;
    private boolean POLLING;
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
        mainScanText.setGravity(Gravity.BOTTOM);

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

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

        // permissions ask:
        // check API version, above 23 permissions are asked at runtime
        // if API version < 23 (6.x) fallback is manifest.xml file permission declares
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_DENIED) {
                requestRecordAudioPermission();
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
        //TODO
        pilferShushScanner.resumeLogWrite();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //TODO
        // sort out things here, clean up and ready for a possible restart/refocus
        pilferShushScanner.closeLogWrite();
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
            case R.id.action_audio_scan_range:
                changeAudioScanRange();
                return true;
            case R.id.action_sensitivity_settings:
                changeSensitivitySettings();
                return true;
            case R.id.action_fft_window_settings:
                changeFFTWindowSettings();
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
            case R.id.action_session_name:
                setSessionName();
                return true;
            case R.id.action_storage_admin:
                performStorageAdmin();
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


/*
* USB device - need replacement daughterboard before implementing this : S5-dev
*/
    // https://source.android.com/devices/audio/usb.html
    // http://developer.android.com/guide/topics/connectivity/usb/host.html

	/*
	private boolean getIntentUsbDevice(Intent intent) {
		// the device_filter.xml has a hardcoded usb device declaration
		// update it to the SDR when it gets here...
		deviceContainer = new DeviceContainer();
		deviceContainer.setUsbDevice((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
		return (deviceContainer.hasDevice() != false);
	}
	*/

    private boolean scanUsbDevices() {
        //TODO
        logger("checking usb host for audio device.");
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while(deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            deviceContainer = new DeviceContainer(device);
            logger("USB: " + deviceContainer.toString());
            return true;
        }
        logger("usb audio device not found, using internal.");
        return false;
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if ((deviceContainer != null) && (deviceContainer.hasDevice()))  {
                            //call method to set up device communication
                            if (usbManager.hasPermission(deviceContainer.getDevice())) {
                                // re-route audio to usb audio device
                                toggleHeadset(true);
                                logger(" has permission for USB device.");
                            }
                            else {
                                usbManager.requestPermission(deviceContainer.getDevice(), permissionIntent);
                            }
                        }
                        else {
                            logger(" no USB deviceContainer / device.");
                        }
                    }
                    else {
                        // re-route audio to phone hardware
                        toggleHeadset(false);
                        logger(" permission denied for USB device.");
                    }
                }
            }
        }
    };

    private void initPilferShush() {
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        timerText = (TextView) findViewById(R.id.timer_text);
        // on screen timer
        timerHandler = new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int)(millis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                timerText.setText(String.format("timer - %02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 500);
            }
        };

        if (pilferShushScanner.initScanner(this, scanUsbDevices(), getResources().getString(R.string.session_default_name), INIT_WRITE_FILES, WRITE_WAV)) {
            pilferShushScanner.checkScanner();
            MIC_CHECKING = false;
            toggleHeadset(false); // default state at init
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
        mainScanText.setText("PilferShush scanner version " + VERSION);

        mainScanLogger("\nUsing " + pilferShushScanner.getAudioCheckerReport(), false);

        mainScanLogger("\nSettings can be changed via the Options menu.", true);

        mainScanLogger("\nThe Detailed View has logging and more information from scans. " +
                "It also has a continuous Mic check and intermittent polling check " +
                "to look for other apps using the microphone.", false);

        mainScanLogger("\nInit: save log output and audio to files: " + Boolean.toString(INIT_WRITE_FILES), true);

        if (WRITE_WAV) {
            mainScanLogger("\nWav audio: " + pilferShushScanner.getSaveFileType() + ", little-endian.", false);
        }
        else {
            mainScanLogger("\nRaw audio: " + pilferShushScanner.getSaveFileType() + ", big-endian.", false);
        }

        // run at init for awareness
        mainScanLogger("\nFree storage space: " + printFreeSize(), true);
        if (pilferShushScanner.cautionFreeSpace()) {
            // has under a minimum of 2048 bytes , pop a toast.
            cautionStorageSize();
        }

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

        freqRanges = new String[2];
        freqRanges[0] = getResources().getString(R.string.freq_range_one);
        freqRanges[1] = getResources().getString(R.string.freq_range_two);

        windowTypes = new String[5];
        windowTypes[0] = getResources().getString(R.string.dialog_window_fft_1);
        windowTypes[1] = getResources().getString(R.string.dialog_window_fft_2);
        windowTypes[2] = getResources().getString(R.string.dialog_window_fft_3);
        windowTypes[3] = getResources().getString(R.string.dialog_window_fft_4);
        windowTypes[4] = getResources().getString(R.string.dialog_window_fft_5);

        dbLevel = new String[3];
        dbLevel[0] = getResources().getString(R.string.magnitude_80_text);
        dbLevel[1] = getResources().getString(R.string.magnitude_90_text);
        dbLevel[2] = getResources().getString(R.string.magnitude_100_text);

        storageAdmins = new String[4];
        storageAdmins[0] = getResources().getString(R.string.dialog_storage_size);
        storageAdmins[1] = getResources().getString(R.string.dialog_delete_empty_logs);
        storageAdmins[2] = getResources().getString(R.string.dialog_delete_all_files);
        storageAdmins[3] = getResources().getString(R.string.dialog_free_storage_size);
    }

    private void cautionStorageSize() {
        dialogBuilder = new AlertDialog.Builder(this);

        dialogBuilder.setTitle(R.string.dialog_storage_warn_title);
        dialogBuilder.setMessage(R.string.dialog_storage_warn_text);
        dialogBuilder.setPositiveButton(R.string.dialog_button_okay, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mainScanLogger("Limited storage space found.", true);
            }
        });

        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void setSessionName() {
        dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View inputView = inflater.inflate(R.layout.session_form, null);
        dialogBuilder.setView(inputView);
        final EditText userInput = (EditText) inputView.findViewById(R.id.session_input);

        dialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_button_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // reset WriteProcessor via scanner
                        pilferShushScanner.renameSessionWrites(userInput.getText().toString());
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // dismissed
                        alertDialog.cancel();
                    }
                });
        alertDialog = dialogBuilder.create();
        alertDialog.show();

    }

    private void performStorageAdmin() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(storageAdmins, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                switch(which) {
                    case 0:
                        mainScanLogger("Log storage size: " + printUsedSize(), false);
                        break;
                    case 1:
                        mainScanLogger("Delete empty log files.", false);
                        pilferShushScanner.clearEmptyLogFiles();
                        break;
                    case 2:
                        mainScanLogger("Delete ALL log files.", true);
                        pilferShushScanner.clearLogStorageFolder();
                        break;
                    case 3:
                        mainScanLogger("Free storage space: " + printFreeSize(), false);
                        break;
                    default:
                        // do nothing, catch dismisses
                        break;
                }
            }
        });
        dialogBuilder.setTitle(R.string.action_storage_admin);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void changeWriteFile() {
        // flip current value
        dialogBuilder = new AlertDialog.Builder(this);

        dialogBuilder.setTitle(R.string.dialog_write_file);
        dialogBuilder.setMessage(R.string.dialog_write_message);
        dialogBuilder.setPositiveButton(R.string.dialog_write_file_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                pilferShushScanner.setWriteFiles(true);
                mainScanLogger("Write to file enabled.", false);
            }
        });
        dialogBuilder.setNegativeButton(R.string.dialog_write_file_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                pilferShushScanner.setWriteFiles(false);
                mainScanLogger("Write to file disabled.", false);
            }
        });

        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void changePollingSpeed() {
        // set the interval delay for polling,
        if (POLLING) {
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

    private void changeAudioScanRange() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(freqRanges, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                switch(which) {
                    case 0:
                        pilferShushScanner.setFreqMinMax(1);
                        mainScanLogger("Frequency range set 18kHz - 21kHz.", false);
                        break;
                    case 1:
                        pilferShushScanner.setFreqMinMax(2);
                        mainScanLogger("Frequency range set 19kHz - 22kHz.", false);
                }
            }
        });
        dialogBuilder.setTitle(R.string.dialog_freq_range);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }


    private void changeSensitivitySettings() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(dbLevel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                switch(which) {
                    case 0:
                        pilferShushScanner.setMinMagnitude(AudioSettings.MAGNITUDE_80);
                        break;
                    case 1:
                        pilferShushScanner.setMinMagnitude(AudioSettings.MAGNITUDE_90);
                        break;
                    case 2:
                        pilferShushScanner.setMinMagnitude(AudioSettings.MAGNITUDE_100);
                        break;
                    default:
                        pilferShushScanner.setMinMagnitude(AudioSettings.DEFAULT_MAGNITUDE);
                }
            }
        });
        dialogBuilder.setTitle(R.string.dialog_sensitivity_text);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void changeFFTWindowSettings() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(windowTypes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                // numerical values from 1-5
                pilferShushScanner.setFFTWindowType(which + 1);
                mainScanLogger("FFT Window set to: " + AudioSettings.FFT_WINDOWS[which], false);
            }
        });
        dialogBuilder.setTitle(R.string.dialog_window_text);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }


    private String printUsedSize() {
        return android.text.format.Formatter.formatShortFileSize(this, pilferShushScanner.getLogStorageSize());
    }

    private String printFreeSize() {
        return android.text.format.Formatter.formatShortFileSize(this, pilferShushScanner.getFreeStorageSize());
    }


    /********************************************************************/
/*
 * ACTION SCANS
 */
    private void toggleScanning() {
        logger("Scanning button pressed");
        if (SCANNING) {
            SCANNING = false;
            timerHandler.removeCallbacks(timerRunnable);
            timerText.setText("timer - 00:00");
            stopScanner();
        }
        else {
            SCANNING = true;
            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
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
            mainScanLogger("Detected audio beacon signal: \n", true);
            mainScanLogger(pilferShushScanner.getModFrequencyLogic(), true);

            // all modfreq captures
            mainScanLogger("All freq logic entries: \n", true);
            mainScanLogger(pilferShushScanner.getFreqSeqLogicEntries(), true);

            // a debug, output in order of capture:
            writeLogger("Original sequence as transmitted:");
            writeLogger(pilferShushScanner.getFrequencySequence());
        }
        else {
            mainScanLogger("No detected audio beacon signals.", false);
        }
        // allow freq list processing above first, then
        pilferShushScanner.resetAudioScanner();

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
        else {
            entryLogger("NO USER APPS FOUND FOR OVERRIDE SCAN.", true);
        }
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

    private void toggleHeadset(boolean hasHeadset) {
        // if no headset, mute the audio output
        if (hasHeadset) {
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
        if (POLLING) {
            // do not do this as well
            entryLogger("DO NOT CHECK WHEN POLLING", true);
            return;
        }

        if (MIC_CHECKING) {
            // currently running, stop it
            pilferShushScanner.micChecking(MIC_CHECKING = false);
            micCheckButton.setText("MICROPHONE CHECK");
            micCheckButton.setBackgroundColor(Color.LTGRAY);
        }
        else {
            // not running, start it
            if (pilferShushScanner.checkScanner()) {
                micCheckButton.setText("CHECKING...");
                micCheckButton.setBackgroundColor(Color.RED);
                pilferShushScanner.micChecking(MIC_CHECKING = true);
            }
        }
    }

    private void togglePollingCheck() {
        if (MIC_CHECKING) {
            // do not do this as well
            entryLogger("DO NOT POLL WHEN MIC CHECKING", true);
            return;
        }
        if (POLLING) {
            pilferShushScanner.pollingCheck(POLLING = false);
            micPollingButton.setText("POLLING CHECK");
            micPollingButton.setBackgroundColor(Color.LTGRAY);
        }
        else {
            pilferShushScanner.pollingCheck(POLLING = true);
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

    private void writeLogger(String text) {
        // can create empty text files as no n-uhf data to save.
        if (pilferShushScanner.getWriteFiles()) {
            WriteProcessor.writeLogFile(text);
        }
    }

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
        // this prints to console.log and DetailedView.log, and save to text file
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
