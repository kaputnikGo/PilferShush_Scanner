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
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
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
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;
import pilfershush.cityfreqs.com.pilfershush.assist.DeviceContainer;
import pilfershush.cityfreqs.com.pilfershush.assist.WriteProcessor;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "PilferShush";
    private static final boolean DEBUG = true;
    private static final boolean INIT_WRITE_FILES = true;
    // keep as internal switch
    private static final boolean WRITE_WAV = true;

    private static final int REQUEST_MULTIPLE_PERMISSIONS = 123;

    // dev internal version numbering
    public static final String VERSION = "2.0.30";

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

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private HeadsetIntentReceiver headsetReceiver;
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

        headsetReceiver = new HeadsetIntentReceiver();
        powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);

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

        // permissions ask:
        // check API version, above 23 permissions are asked at runtime
        // if API version < 23 (6.x) fallback is manifest.xml file permission declares
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {

            List<String> permissionsNeeded = new ArrayList<String>();
            final List<String> permissionsList = new ArrayList<String>();

            if (!addPermission(permissionsList, Manifest.permission.RECORD_AUDIO))
                permissionsNeeded.add(getResources().getString(R.string.perms_state_1));
            if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                permissionsNeeded.add(getResources().getString(R.string.perms_state_2));

            if (permissionsList.size() > 0) {
                if (permissionsNeeded.size() > 0) {
                    // Need Rationale
                    String message = getResources().getString(R.string.perms_state_3) + permissionsNeeded.get(0);
                    for (int i = 1; i < permissionsNeeded.size(); i++) {
                        message = message + ", " + permissionsNeeded.get(i);
                    }
                    showPermissionsDialog(message,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            permissionsList.toArray(new String[permissionsList.size()]),
                                            REQUEST_MULTIPLE_PERMISSIONS);
                                }
                            });
                    return;
                }
                ActivityCompat.requestPermissions(this,
                        permissionsList.toArray(new String[permissionsList.size()]),
                        REQUEST_MULTIPLE_PERMISSIONS);
                return;
            }
            else {
                // assume already runonce, has permissions
                initPilferShush();
            }

        }
        else {
            // pre API 23
            initPilferShush();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        // refocus app, ready for fresh scanner run
        toggleHeadset(false); // default state at init
        audioFocusCheck();
        pilferShushScanner.resumeLogWrite();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // backgrounded, stop recording, possible audio_focus loss due to telephony...
        unregisterReceiver(headsetReceiver);
        interruptRequestAudio();
        pilferShushScanner.closeLogWrite();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pilferShushScanner.closeLogWrite();
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

    private void showPermissionsDialog(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(getResources().getString(R.string.dialog_button_okay), okListener)
                .setNegativeButton(getResources().getString(R.string.dialog_button_cancel), null)
                .create()
                .show();
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();
                // Initial
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with results
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                }
                // Check for RECORD_AUDIO
                if (perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    // All Permissions Granted
                    initPilferShush();
                } else
                    {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.perms_state_4), Toast.LENGTH_SHORT)
                            .show();
                }
            }
            break;
            default:
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
        logger(getResources().getString(R.string.usb_state_1));
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while(deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            deviceContainer = new DeviceContainer(device);
            logger(getResources().getString(R.string.usb_state_2) + deviceContainer.toString());
            return true;
        }
        logger(getResources().getString(R.string.usb_state_3));
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
                                logger(getResources().getString(R.string.usb_state_4));
                            }
                            else {
                                usbManager.requestPermission(deviceContainer.getDevice(), permissionIntent);
                            }
                        }
                        else {
                            logger(getResources().getString(R.string.usb_state_5));
                        }
                    }
                    else {
                        // re-route audio to phone hardware
                        toggleHeadset(false);
                        logger(getResources().getString(R.string.usb_state_6));
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
            audioFocusCheck();
            initAudioFocusListener();
            populateMenuItems();
            reportInitialState();
        }
        else {
            mainScanLogger(getResources().getString(R.string.init_state_12), true);
            logger(getResources().getString(R.string.init_state_13));
        }
    }

    private void reportInitialState() {
        mainScanText.setText(getResources().getString(R.string.init_state_1) + VERSION);

        mainScanLogger("\n" + getResources().getString(R.string.init_state_2) + pilferShushScanner.getAudioCheckerReport(), false);

        mainScanLogger("\n" + getResources().getString(R.string.init_state_3), true);

        mainScanLogger("\n" + getResources().getString(R.string.init_state_4) + getResources().getString(R.string.init_state_5), false);

        mainScanLogger("\n" + getResources().getString(R.string.init_state_6) + Boolean.toString(INIT_WRITE_FILES), true);

        if (WRITE_WAV) {
            mainScanLogger("\n" + getResources().getString(R.string.init_state_7_1) +
                    pilferShushScanner.getSaveFileType() +
                    getResources().getString(R.string.init_state_7_2), false);
        }
        else {
            mainScanLogger("\n" + getResources().getString(R.string.init_state_8_1) +
                    pilferShushScanner.getSaveFileType() +
                    getResources().getString(R.string.init_state_8_2), false);
        }

        // run at init for awareness
        mainScanLogger("\n" + getResources().getString(R.string.init_state_9) + printFreeSize(), true);
        if (pilferShushScanner.cautionFreeSpace()) {
            // has under a minimum of 2048 bytes , pop a toast.
            cautionStorageSize();
        }

        mainScanLogger("\n" + getResources().getString(R.string.init_state_10_1) +
                getResources().getString(R.string.main_scanner_11) +
                getResources().getString(R.string.init_state_10_2), false);

        mainScanLogger("\n" + getResources().getString(R.string.init_state_11) + "\n", true);
    }

    private void populateMenuItems() {
        pollSpeedList = new String[6];
        pollSpeedList[0] = getResources().getString(R.string.polling_1_text);
        pollSpeedList[1] = getResources().getString(R.string.polling_2_text);
        pollSpeedList[2] = getResources().getString(R.string.polling_3_text);
        pollSpeedList[3] = getResources().getString(R.string.polling_4_text);
        pollSpeedList[4] = getResources().getString(R.string.polling_5_text);
        pollSpeedList[5] = getResources().getString(R.string.polling_default_text);

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

        dbLevel = new String[4];
        dbLevel[0] = getResources().getString(R.string.magnitude_80_text);
        dbLevel[1] = getResources().getString(R.string.magnitude_90_text);
        dbLevel[2] = getResources().getString(R.string.magnitude_93_text);
        dbLevel[3] = getResources().getString(R.string.magnitude_100_text);

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
                mainScanLogger(getResources().getString(R.string.option_dialog_1), true);
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
                        mainScanLogger(getResources().getString(R.string.option_dialog_2) + printUsedSize(), false);
                        break;
                    case 1:
                        mainScanLogger(getResources().getString(R.string.option_dialog_3), false);
                        pilferShushScanner.clearEmptyLogFiles();
                        break;
                    case 2:
                        mainScanLogger(getResources().getString(R.string.option_dialog_4), true);
                        pilferShushScanner.clearLogStorageFolder();
                        break;
                    case 3:
                        mainScanLogger(getResources().getString(R.string.option_dialog_5) + printFreeSize(), false);
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
                mainScanLogger(getResources().getString(R.string.option_dialog_6), false);
            }
        });
        dialogBuilder.setNegativeButton(R.string.dialog_write_file_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                pilferShushScanner.setWriteFiles(false);
                mainScanLogger(getResources().getString(R.string.option_dialog_7), false);
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
                // 1000, 2000, 3000, 4000, 5000, 6000 (default) ms
                pilferShushScanner.setPollingSpeed(AudioSettings.POLLING_DELAY[which]);
                mainScanLogger(getResources().getString(R.string.option_dialog_8) + AudioSettings.POLLING_DELAY[which], false);
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
                pilferShushScanner.setFrequencyStep(AudioSettings.FREQ_STEPS[which]);
                mainScanLogger(getResources().getString(R.string.option_dialog_9) + AudioSettings.FREQ_STEPS[which], false);
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
                        mainScanLogger(getResources().getString(R.string.option_dialog_10), false);
                        break;
                    case 1:
                        pilferShushScanner.setFreqMinMax(2);
                        mainScanLogger(getResources().getString(R.string.option_dialog_11), false);
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
                pilferShushScanner.setMinMagnitude(AudioSettings.MAGNITUDES[which]);
                mainScanLogger(getResources().getString(R.string.option_dialog_12) + AudioSettings.DECIBELS[which], false);
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
                mainScanLogger(getResources().getString(R.string.option_dialog_13) + AudioSettings.FFT_WINDOWS[which], false);
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

    private void interruptRequestAudio() {
        // system app requests audio focus, respond here
        mainScanLogger(getResources().getString(R.string.audiofocus_check_5), true);
        if (SCANNING) {
            toggleScanning();
        }
    }


    /********************************************************************/
/*
 * ACTION SCANS
 */
    private void toggleScanning() {
        // add check for mic/record ability
        if (pilferShushScanner.audioStateError()) {
            // no mic or audio record capabilities
            mainScanLogger(getResources().getString(R.string.init_state_17), true);
            return;
        }

        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        if (SCANNING) {
            SCANNING = false;
            timerHandler.removeCallbacks(timerRunnable);
            timerText.setText("timer - 00:00");
            stopScanner();
            if (wakeLock.isHeld()) {
                wakeLock.release(); // this
                wakeLock = null;
            }
        }
        else {
            SCANNING = true;
            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
            // clear any caution lines from previous session
            visualiserView.clearFrequencyCaution();
            runScanner();
            wakeLock.acquire();
        }
    }

    private void runScanner() {
        runScansButton.setText(getResources().getString(R.string.main_scanner_1));
        runScansButton.setBackgroundColor(Color.RED);
        mainScanLogger(getResources().getString(R.string.main_scanner_2), false);

        int audioNum = pilferShushScanner.getAudioRecordAppsNumber();
        if (audioNum > 0) {
            mainScanLogger(getResources().getString(R.string.main_scanner_3) + audioNum, true);
        }
        else {
            mainScanLogger(getResources().getString(R.string.main_scanner_4), false);
        }
        if (pilferShushScanner.hasAudioBeaconApps()) {
            mainScanLogger(pilferShushScanner.getAudioBeaconAppNumber()
                    + getResources().getString(R.string.main_scanner_5), true);
        }
        else {
            mainScanLogger(getResources().getString(R.string.main_scanner_6), false);
        }

        mainScanLogger(getResources().getString(R.string.main_scanner_7), false);
        if (pilferShushScanner.mainPollingCheck()) {
            mainScanLogger(getResources().getString(R.string.main_scanner_8), true);
        }
        else {
            mainScanLogger(getResources().getString(R.string.main_scanner_9), false);
        }
        pilferShushScanner.mainPollingStop();

        mainScanLogger(getResources().getString(R.string.main_scanner_10), false);
        pilferShushScanner.runAudioScanner();
    }

    private void stopScanner() {
        // FINISHED, determine type of signal
        pilferShushScanner.stopAudioScanner();
        runScansButton.setText(getResources().getString(R.string.main_scanner_11));
        runScansButton.setBackgroundColor(Color.LTGRAY);
        mainScanLogger(getResources().getString(R.string.main_scanner_12), false);

        if (pilferShushScanner.hasAudioScanSequence()) {
            mainScanLogger("\n" + getResources().getString(R.string.main_scanner_13) + "\n", true);

            // to main debug view candidate numbers for logic 1,0
            mainScanLogger(pilferShushScanner.getModFrequencyLogic(), true);

            // all captures to detailed view:
            pilferShushScanner.getFreqSeqLogicEntries();

            // simple report to main logger
            mainScanLogger(getResources().getString(R.string.main_scanner_14) + pilferShushScanner.getFrequencySequenceSize(), true);

            // output in order of capture sent to log file:
            writeLogger(getResources().getString(R.string.main_scanner_15));
            writeLogger(pilferShushScanner.getFrequencySequence());
        }
        else {
            mainScanLogger(getResources().getString(R.string.main_scanner_16), false);
        }
        // allow freq list processing above first, then
        pilferShushScanner.resetAudioScanner();

        mainScanLogger("\n" + getResources().getString(R.string.main_scanner_17) + "\n\n", false);
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
            entryLogger(getResources().getString(R.string.audio_apps_check_1), true);
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
            entryLogger(getResources().getString(R.string.user_apps_check_1), true);
        }
    }


    /********************************************************************/
/*
 * 	AUDIO
 */
    private void audioFocusCheck() {
        // this may not work as SDKs requesting focus may not get it cos we already have it?
        // also: getting MIC access does not require getting AUDIO_FOCUS
        entryLogger(getResources().getString(R.string.audiofocus_check_1), false);
        int result = audioManager.requestAudioFocus(audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            entryLogger(getResources().getString(R.string.audiofocus_check_2), false);
        }
        else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            entryLogger(getResources().getString(R.string.audiofocus_check_3), false);
        }
        else {
            entryLogger(getResources().getString(R.string.audiofocus_check_4), false);
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
            entryLogger(getResources().getString(R.string.mic_check_1), true);
            return;
        }

        if (MIC_CHECKING) {
            // currently running, stop it
            pilferShushScanner.micChecking(MIC_CHECKING = false);
            micCheckButton.setText(getResources().getString(R.string.mic_check_2));
            micCheckButton.setBackgroundColor(Color.LTGRAY);
        }
        else {
            // not running, start it
            if (pilferShushScanner.checkScanner()) {
                micCheckButton.setText(getResources().getString(R.string.mic_check_3));
                micCheckButton.setBackgroundColor(Color.RED);
                pilferShushScanner.micChecking(MIC_CHECKING = true);
            }
        }
    }

    private void togglePollingCheck() {
        if (MIC_CHECKING) {
            entryLogger(getResources().getString(R.string.poll_check_1), true);
            return;
        }
        if (POLLING) {
            pilferShushScanner.pollingCheck(POLLING = false);
            micPollingButton.setText(getResources().getString(R.string.poll_check_2));
            micPollingButton.setBackgroundColor(Color.LTGRAY);
        }
        else {
            pilferShushScanner.pollingCheck(POLLING = true);
            micPollingButton.setText(getResources().getString(R.string.poll_check_3));
            micPollingButton.setBackgroundColor(Color.RED);
        }
    }

    private void initAudioFocusListener() {
        audioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch(focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        // -1
                        // loss for unknown duration
                        focusText.setText(getResources().getString(R.string.audiofocus_1));
                        audioManager.abandonAudioFocus(audioFocusListener);
                        interruptRequestAudio();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // -2
                        // temporary loss ? API docs says a "transient loss"!
                        focusText.setText(getResources().getString(R.string.audiofocus_2));
                        interruptRequestAudio();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // -3
                        // loss to other audio source, this can duck for the short duration if it wants
                        focusText.setText(getResources().getString(R.string.audiofocus_3));
                        interruptRequestAudio();
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        // 0
                        // failed focus change request
                        focusText.setText(getResources().getString(R.string.audiofocus_4));
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        //case AudioManager.AUDIOFOCUS_REQUEST_GRANTED: <- duplicate int value...
                        // 1
                        // has gain, or request for gain, of unknown duration
                        focusText.setText(getResources().getString(R.string.audiofocus_5));
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        // 2
                        // temporary gain or request for gain, for short duration (ie. notification)
                        focusText.setText(getResources().getString(R.string.audiofocus_6));
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        // 3
                        // as above but with other background audio ducked for duration
                        focusText.setText(getResources().getString(R.string.audiofocus_7));
                        break;
                    default:
                        //
                        focusText.setText(getResources().getString(R.string.audiofocus_8));
                }
            }
        };
    }

    private class HeadsetIntentReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        mainScanLogger(getResources().getString(R.string.headset_state_1), false);
                        toggleHeadset(false);
                        break;
                    case 1:
                        mainScanLogger(getResources().getString(R.string.headset_state_2), false);
                        toggleHeadset(true);
                        break;
                    default:
                        mainScanLogger(getResources().getString(R.string.headset_state_3), false);
                }
            }
        }
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
