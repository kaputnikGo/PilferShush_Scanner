package cityfreqs.com.pilfershush;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cityfreqs.com.pilfershush.assist.AudioChecker;
import cityfreqs.com.pilfershush.assist.AudioSettings;

import static cityfreqs.com.pilfershush.assist.WriteProcessor.MINIMUM_STORAGE_SIZE_BYTES;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "PilferShush";
    private static final boolean DEBUG = true;

    private static final int REQUEST_MULTIPLE_PERMISSIONS = 123;

    public static final String VERSION = "4.0.0";

    // TODO fix the STATE nightmare of the scanner and the audioBundle
    private ViewSwitcher viewSwitcher;
    private boolean mainView;
    private static TextView debugText;
    private TextView timerText;
    private long startTime;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private TextView focusText;

    private ToggleButton runScansButton;
    private TextView mainScanText;

    private String[] freqSteps;
    private String[] freqRanges;
    private String[] windowTypes;
    private String[] dbLevel;
    private String[] storageAdmins;

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

    private Bundle audioBundle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewSwitcher = findViewById(R.id.main_view_switcher);
        mainView = true;

        headsetReceiver = new HeadsetIntentReceiver();
        powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);

        SCANNING = false;

        //MAIN VIEW
        runScansButton = findViewById(R.id.run_scans_button);
        runScansButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    runScanner();
                }
                else {
                    stopScanner();
                }
            }
        });

        mainScanText = findViewById(R.id.main_scan_text);
        mainScanText.setTextColor(Color.parseColor("#00ff00"));
        mainScanText.setMovementMethod(new ScrollingMovementMethod());
        mainScanText.setGravity(Gravity.BOTTOM);

        visualiserView = findViewById(R.id.audio_visualiser_view);

        Button debugViewButton = findViewById(R.id.debug_view_button);
        debugViewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switchViews();
            }
        });

        // DEBUG VIEW
        debugText = findViewById(R.id.debug_text);
        debugText.setTextColor(Color.parseColor("#00ff00"));
        debugText.setMovementMethod(new ScrollingMovementMethod());
        debugText.setOnClickListener(new TextView.OnClickListener() {
            @Override
            public void onClick(View v) {
                debugText.setSoundEffectsEnabled(false); // no further click sounds
            }
        });
        focusText = findViewById(R.id.focus_text);
        focusText.setTextColor(Color.parseColor("#ffff00")); // yellow

        Button mainViewButton = findViewById(R.id.main_view_button);
        mainViewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switchViews();
            }
        });

        // permissions ask:
        // check API version, above 23 permissions are asked at runtime
        // if API version < 23 (6.x) fallback is manifest.xml file permission declares
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {

            List<String> permissionsNeeded = new ArrayList<>();
            final List<String> permissionsList = new ArrayList<>();

            if (!addPermission(permissionsList, Manifest.permission.RECORD_AUDIO))
                permissionsNeeded.add(getResources().getString(R.string.perms_state_1));
            if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                permissionsNeeded.add(getResources().getString(R.string.perms_state_2));

            if (permissionsList.size() > 0) {
                if (permissionsNeeded.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(getResources().getString(R.string.perms_state_3));
                    sb.append(permissionsNeeded.get(0));
                    for (int i = 1; i < permissionsNeeded.size(); i++) {
                        sb.append(", ");
                        sb.append(permissionsNeeded.get(i));
                    }
                    showPermissionsDialog(sb.toString(),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            permissionsList.toArray(new String[0]),
                                            REQUEST_MULTIPLE_PERMISSIONS);
                                }
                            });
                    return;
                }
                ActivityCompat.requestPermissions(this,
                        permissionsList.toArray(new String[0]),
                        REQUEST_MULTIPLE_PERMISSIONS);
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
        int status = audioFocusCheck();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // backgrounded, stop recording, possible audio_focus loss due to telephony...
        unregisterReceiver(headsetReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // called from back button press
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pilferShushScanner.onDestroy();
    }


    /********************************************************************/
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
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
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
            case R.id.action_write_file:
                changeWriteFile();
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
            return (ActivityCompat.shouldShowRequestPermissionRationale(this, permission));
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
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

    private void initPilferShush() {
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        timerText = findViewById(R.id.timer_text);
        // on screen timer
        timerHandler = new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int)(millis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                timerText.setText(String.format(Locale.getDefault(), "timer - %02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 500);
            }
        };

        // apply audio checker settings to bundle for services
        audioBundle = new Bundle();
        audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[7], false);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[8], AudioSettings.JAMMER_TYPE_TEST);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[9], AudioSettings.CARRIER_TEST_FREQUENCY);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[10], AudioSettings.DEFAULT_RANGE_DRIFT_LIMIT);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[11], AudioSettings.MINIMUM_DRIFT_LIMIT);
        // set defaults
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[18], AudioSettings.DEFAULT_WINDOW_TYPE);
        audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[19], true); //write audio files for scanner
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[16], AudioSettings.DEFAULT_FREQ_STEP);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[17], AudioSettings.DEFAULT_MAGNITUDE);
        setFreqMinMax(1); // NUHF freq range

        AudioChecker audioChecker = new AudioChecker(this, audioBundle);
        pilferShushScanner = new PilferShushScanner(this, audioChecker);

        if (pilferShushScanner.initScanner()) {
            // get latest bundle updates bundle
            audioBundle = audioChecker.getAudioBundle();

            pilferShushScanner.checkScanner();

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
        String startText = getResources().getString(R.string.init_state_1) + VERSION;
        mainScanText.setText(startText);
        mainScanLogger("\n" + getResources().getString(R.string.init_state_2) + pilferShushScanner.getAudioCheckerReport(), false);
        mainScanLogger("\n" + getResources().getString(R.string.init_state_3), true);
        mainScanLogger("\n" + getResources().getString(R.string.init_state_4), false);
        mainScanLogger("\n" + getResources().getString(R.string.init_state_6) + pilferShushScanner.canWriteFiles(), false);

        if (pilferShushScanner.canWriteFiles()) {
            mainScanLogger(getResources().getString(R.string.init_state_7_1) +
                    pilferShushScanner.getSaveFileType() +
                    getResources().getString(R.string.init_state_7_2), false);
        }
        else {
            // not using raw file saves
            mainScanLogger("\n" + getResources().getString(R.string.init_state_8_1) +
                    pilferShushScanner.getSaveFileType() +
                    getResources().getString(R.string.init_state_8_2), false);
        }

        // run at init for awareness
        mainScanLogger(getResources().getString(R.string.init_state_9) + printFreeSize(), true);

        int storageSize = pilferShushScanner.cautionFreeSpace();
        if (storageSize <= MINIMUM_STORAGE_SIZE_BYTES) {
            // has under a minimum of 2048 bytes , pop a toast.
            if (storageSize == 0 ) {
                // have no ext storage or some error maybe
                Log.d(TAG, " Storage size reported as 0 bytes in size.");
                mainScanLogger("Storage size reported as 0 bytes in size.", true);
            }
            else {
                cautionStorageSize();
            }
        }

        mainScanLogger("\n" + getResources().getString(R.string.init_state_10_1) +
                getResources().getString(R.string.main_scanner_11) +
                getResources().getString(R.string.init_state_10_2), false);

        mainScanLogger(getResources().getString(R.string.init_state_11) + "\n", true);

        if (DEBUG) Log.d(TAG, "audioBundle: " + bundlePrint(audioBundle));
    }

    private String bundlePrint(Bundle b) {
        // debug printout to check vars in audioBundle
        if (b == null) {
            return "";
        }
        Bundle bundle = new Bundle();
        bundle.putAll(b);
        return bundle.toString();
    }


    private void populateMenuItems() {
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

        dbLevel = new String[7];
        dbLevel[0] = getResources().getString(R.string.magnitude_50_text);
        dbLevel[1] = getResources().getString(R.string.magnitude_60_text);
        dbLevel[2] = getResources().getString(R.string.magnitude_70_text);
        dbLevel[3] = getResources().getString(R.string.magnitude_80_text);
        dbLevel[4] = getResources().getString(R.string.magnitude_90_text);
        dbLevel[5] = getResources().getString(R.string.magnitude_93_text);
        dbLevel[6] = getResources().getString(R.string.magnitude_100_text);

        storageAdmins = new String[3];
        storageAdmins[0] = getResources().getString(R.string.dialog_storage_size);
        storageAdmins[1] = getResources().getString(R.string.dialog_delete_all_files);
        storageAdmins[2] = getResources().getString(R.string.dialog_free_storage_size);
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

    private void performStorageAdmin() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(storageAdmins, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                switch(which) {
                    case 0:
                        mainScanLogger(getResources().getString(R.string.option_dialog_2) + printUsedSize(), false);
                        break;
                    case 1:
                        mainScanLogger(getResources().getString(R.string.option_dialog_4), true);
                        pilferShushScanner.clearLogStorageFolder();
                        break;
                    case 2:
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
                        setFreqMinMax(1);
                        mainScanLogger(getResources().getString(R.string.option_dialog_10), false);
                        break;
                    case 1:
                        setFreqMinMax(2);
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
                pilferShushScanner.setMagnitude(AudioSettings.MAGNITUDES[which]);
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

    private void interruptRequestAudio(int focusChange) {
        // system app requests audio focus, respond here
        mainScanLogger(getResources().getString(R.string.audiofocus_check_5), true);
        if (SCANNING) {
            stopScanner();
            runScansButton.toggle();
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // total loss, focus abandoned, need to confirm this behaviour
            mainScanLogger("Audio Focus LOSS.", true);
        }
        else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // system forced loss, assuming telephony
            mainScanLogger("Audio Focus LOSS TRANSIENT.", true);
        }
    }

    private void setFreqMinMax(int pair) {
        // at the moment stick to ranges of 3kHz as DEFAULT or SECOND pair
        // use int as may get more ranges than the 2 presently used
        if (pair == 1) {
            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[14], AudioSettings.DEFAULT_FREQUENCY_MIN);
            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[15], AudioSettings.DEFAULT_FREQUENCY_MAX);
        }
        else if (pair == 2) {
            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[14], AudioSettings.SECOND_FREQUENCY_MIN);
            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[15], AudioSettings.SECOND_FREQUENCY_MAX);
        }
    }

    /********************************************************************/
    /*
     *      SCANNER
     */

    private void runScanner() {
        if (!pilferShushScanner.checkScanner()) {
            // no mic or audio record capabilities
            mainScanLogger(getResources().getString(R.string.init_state_17), true);
            return;
        }
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":wakelock");
        }

        // update scanner's audioBundle from UI changes if any
        pilferShushScanner.updateAudioBundle(audioBundle);

        SCANNING = true;
        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);
        // clear any caution lines from previous session
        visualiserView.clearFrequencyCaution();
        //TODO wakelock remove
        wakeLock.acquire(600000); // timeout in ms (10 mins)

        mainScanLogger(getResources().getString(R.string.main_scanner_10), false);
        pilferShushScanner.runAudioScanner();
    }

    private void stopScanner() {
        // do first to free up mic in case of passive jammer interrupt
        pilferShushScanner.stopAudioScanner();

        SCANNING = false;
        timerHandler.removeCallbacks(timerRunnable);
        timerText.setText(getResources().getString(R.string.timer_text));

        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release(); // this
                wakeLock = null;
            }
        }

        // FINISHED, determine type of signal
        mainScanLogger(getResources().getString(R.string.main_scanner_12), false);

        if (pilferShushScanner.hasAudioScanSequence()) {
            mainScanLogger("\n" + getResources().getString(R.string.main_scanner_13) + "\n", true);

            // to main debug view candidate numbers for logic 1,0
            mainScanLogger(pilferShushScanner.getModFrequencyLogic(), true);

            // all captures to detailed view:
            if (pilferShushScanner.getFreqSeqLogicEntries().isEmpty()) {
                mainScanLogger("FreqSequence Logic entries empty.", false);
            }

            // simple report to main logger
            mainScanLogger(getResources().getString(R.string.main_scanner_14) + pilferShushScanner.getFrequencySequenceSize(), true);
        }
        else {
            mainScanLogger(getResources().getString(R.string.main_scanner_16), false);
        }
        // allow freq list processing above first, then
        pilferShushScanner.resetAudioScanner();

        mainScanLogger("\n" + getResources().getString(R.string.main_scanner_17) + "\n\n", false);

    }

    /********************************************************************/
/*
 * 	AUDIO
 */
    private int audioFocusCheck() {
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
        return result;
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
                        interruptRequestAudio(focusChange);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // -2
                        // temporary loss ? API docs says a "transient loss"!
                        focusText.setText(getResources().getString(R.string.audiofocus_2));
                        interruptRequestAudio(focusChange);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // -3
                        // loss to other audio source, this can duck for the short duration if it wants
                        focusText.setText(getResources().getString(R.string.audiofocus_3));
                        interruptRequestAudio(focusChange);
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
            if (intent.getAction() == null) {
                return;
            }

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
 * 	LOGGERS
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

    public static void entryLogger(String entry, boolean caution) {
        // this prints ExpertView.log (detailed)
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
