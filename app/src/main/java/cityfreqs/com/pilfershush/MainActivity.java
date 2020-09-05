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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

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
import cityfreqs.com.pilfershush.assist.WriteProcessor;
import cityfreqs.com.pilfershush.scanners.AudioScanner;

import static cityfreqs.com.pilfershush.assist.AudioSettings.AUDIO_BUNDLE_KEYS;
import static cityfreqs.com.pilfershush.assist.AudioSettings.AUDIO_CHANNEL_IN;
import static cityfreqs.com.pilfershush.assist.AudioSettings.AUDIO_ENCODING;
import static cityfreqs.com.pilfershush.assist.AudioSettings.AUDIO_SOURCE;
import static cityfreqs.com.pilfershush.assist.WriteProcessor.MINIMUM_STORAGE_SIZE_BYTES;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "PilferShush";
    private static final boolean DEBUG = true;

    private static final int REQUEST_MULTIPLE_PERMISSIONS = 123;

    public static final String VERSION = "4.0.1";

    // TODO fix the the entryLogger
    private static TextView debugText;
    private TextView timerText;
    private long startTime;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private TextView focusText;

    private ToggleButton runScansButton;
    private String[] freqSteps;
    private String[] freqRanges;
    private String[] windowTypes;
    private String[] dbLevel;
    private String[] storageAdmins;

    private int scanBufferSize;
    private AudioChecker audioChecker;
    private AudioScanner audioScanner;
    private WriteProcessor writeProcessor;

    private boolean SCANNING;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private HeadsetIntentReceiver headsetReceiver;
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

        headsetReceiver = new HeadsetIntentReceiver();
        powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);

        SCANNING = false;
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

        visualiserView = findViewById(R.id.audio_visualiser_view);

        // permissions ask:
        // check API version, above 23 permissions are asked at runtime
        // if API version < 23 (6.x) fallback is manifest.xml file permission declares
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {

            List<String> permissionsNeeded = new ArrayList<>();
            final List<String> permissionsList = new ArrayList<>();

            if (addPermission(permissionsList, Manifest.permission.RECORD_AUDIO))
                permissionsNeeded.add(getResources().getString(R.string.perms_state_1));
            if (addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE))
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
        audioFocusCheck();
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
            case R.id.action_print_audio_bundle:
                printAudioSettings();
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

    protected boolean addPermission(List<String> permissionsList, String permission) {
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            return !(ActivityCompat.shouldShowRequestPermissionRationale(this, permission));
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_MULTIPLE_PERMISSIONS) {
            Map<String, Integer> perms = new HashMap<>();
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
            }
            else {
                // Permission Denied
                Toast.makeText(MainActivity.this, getResources().getString(R.string.perms_state_4), Toast.LENGTH_SHORT)
                        .show();
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void initPilferShush() {
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        // set defaults
        audioBundle = new Bundle();
        audioBundle.putInt(AUDIO_BUNDLE_KEYS[11], AudioSettings.DEFAULT_FREQ_STEP);
        audioBundle.putDouble(AUDIO_BUNDLE_KEYS[12], AudioSettings.DEFAULT_MAGNITUDE);
        audioBundle.putInt(AUDIO_BUNDLE_KEYS[13], AudioSettings.DEFAULT_WINDOW_TYPE);
        audioBundle.putBoolean(AUDIO_BUNDLE_KEYS[14], true); //write audio files for scanner
        audioBundle.putBoolean(AUDIO_BUNDLE_KEYS[16], DEBUG); //set DEBUG

        setFreqMinMax(1); // NUHF freq range

        AudioChecker audioChecker = new AudioChecker(this, audioBundle);

        if (initScanner()) {
            // get latest bundle updates bundle <- needed?
            audioBundle = audioChecker.getAudioBundle();

            if (audioChecker.checkAudioRecord()) {
                entryLogger("audioChecker AudioRecord passed.", false);
            }
            else {
                entryLogger("audioChecker AudioRecord failed.", true);
            }

            toggleHeadset(false); // default state at init
            audioFocusCheck();
            initAudioFocusListener();
            populateMenuItems();
            reportInitialState();
        }
        else {
            entryLogger(getResources().getString(R.string.init_state_12), true);
        }

        // delay this init in case
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
    }

    private void reportInitialState() {
        entryLogger("\n[:>----------------------------------<:]", true);
        entryLogger("\n" + getResources().getString(R.string.init_state_1) + VERSION, true);
        entryLogger("\n" + getResources().getString(R.string.init_state_2) + getAudioCheckerReport(), false);
        entryLogger("\n" + getResources().getString(R.string.init_state_3), true);
        entryLogger("\n" + getResources().getString(R.string.init_state_6) + audioBundle.getBoolean(AUDIO_BUNDLE_KEYS[14]), false);

        if (audioBundle.getBoolean(AUDIO_BUNDLE_KEYS[14])) {
            entryLogger(getResources().getString(R.string.init_state_7_1) +
                    audioChecker.saveFormatToString() +
                    getResources().getString(R.string.init_state_7_2), false);
        }
        else {
            // not using raw file saves
            entryLogger("\n" + getResources().getString(R.string.init_state_8_1) +
                    audioChecker.saveFormatToString() +
                    getResources().getString(R.string.init_state_8_2), false);
        }

        // run at init for awareness
        entryLogger("\n" + getResources().getString(R.string.init_state_9) + printFreeSize(), true);

        int storageSize = writeProcessor.cautionFreeSpace();
        if (storageSize <= MINIMUM_STORAGE_SIZE_BYTES) {
            // has under a minimum of 2048 bytes , pop a toast.
            if (storageSize == 0 ) {
                // have no ext storage or some error maybe
                entryLogger("Storage size reported as 0 bytes in size.", true);
            }
            else {
                cautionStorageSize();
            }
        }

        entryLogger("\n" + getResources().getString(R.string.init_state_10_1) +
                getResources().getString(R.string.main_scanner_11) +
                getResources().getString(R.string.init_state_10_2), false);

        entryLogger(getResources().getString(R.string.init_state_11) + "\n", true);

        if (DEBUG) Log.d(TAG, "audioBundle: " + bundlePrint(audioBundle));
    }

    private void printAudioSettings() {
        entryLogger("\nAudio" + bundlePrint(audioBundle) + "\n", false);
    }

    private String bundlePrint(Bundle b) {
        // debug printout to check vars in audioBundle
        if (b == null) {
            return "audioBundle not found.";
        }
        Bundle bundle = new Bundle();
        bundle.putAll(b);
        return bundle.toString();
    }


    private void populateMenuItems() {
        freqSteps = new String[6];
        freqSteps[0] = getResources().getString(R.string.freq_step_10_text);
        freqSteps[1] = getResources().getString(R.string.freq_step_25_text);
        freqSteps[2] = getResources().getString(R.string.freq_step_50_text);
        freqSteps[3] = getResources().getString(R.string.freq_step_75_text);
        freqSteps[4] = getResources().getString(R.string.freq_step_100_text);
        freqSteps[5] = getResources().getString(R.string.freq_step_250_text);

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
                entryLogger(getResources().getString(R.string.option_dialog_1), true);
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
                        entryLogger(getResources().getString(R.string.option_dialog_2) + printUsedSize(), false);
                        break;
                    case 1:
                        entryLogger(getResources().getString(R.string.option_dialog_4), true);
                        writeProcessor.deleteStorageFiles();
                        break;
                    case 2:
                        entryLogger(getResources().getString(R.string.option_dialog_5) + printFreeSize(), false);
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
                audioBundle.putBoolean(AUDIO_BUNDLE_KEYS[14], true);
                entryLogger(getResources().getString(R.string.option_dialog_6), false);
            }
        });
        dialogBuilder.setNegativeButton(R.string.dialog_write_file_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                audioBundle.putBoolean(AUDIO_BUNDLE_KEYS[14], false);
                entryLogger(getResources().getString(R.string.option_dialog_7), false);
            }
        });

        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void changeAudioScanSettings() {
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setItems(freqSteps, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                audioBundle.putInt(AUDIO_BUNDLE_KEYS[11], AudioSettings.FREQ_STEPS[which]);
                entryLogger(getResources().getString(R.string.option_dialog_9) + AudioSettings.FREQ_STEPS[which], false);
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
                        entryLogger(getResources().getString(R.string.option_dialog_10), false);
                        break;
                    case 1:
                        setFreqMinMax(2);
                        entryLogger(getResources().getString(R.string.option_dialog_11), false);
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
                audioScanner.setMagnitude(AudioSettings.MAGNITUDES[which]);
                entryLogger(getResources().getString(R.string.option_dialog_12) + AudioSettings.DECIBELS[which], false);
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
                audioBundle.putInt(AUDIO_BUNDLE_KEYS[13], which + 1);
                entryLogger(getResources().getString(R.string.option_dialog_13) + AudioSettings.FFT_WINDOWS[which], false);
            }
        });
        dialogBuilder.setTitle(R.string.dialog_window_text);
        alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private String printUsedSize() {
        return android.text.format.Formatter.formatShortFileSize(this, writeProcessor.getStorageSize());
    }

    private String printFreeSize() {
        return android.text.format.Formatter.formatShortFileSize(this, writeProcessor.getFreeStorageSpace());
    }

    private void interruptRequestAudio(int focusChange) {
        // system app requests audio focus, respond here
        entryLogger(getResources().getString(R.string.audiofocus_check_5), true);
        if (SCANNING) {
            stopScanner();
            runScansButton.toggle();
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // total loss, focus abandoned, need to confirm this behaviour
            entryLogger("Audio Focus LOSS.", true);
        }
        else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // system forced loss, assuming telephony
            entryLogger("Audio Focus LOSS TRANSIENT.", true);
        }
    }

    private void setFreqMinMax(int pair) {
        // at the moment stick to ranges of 3kHz as DEFAULT or SECOND pair
        // use int as may get more ranges than the 2 presently used
        if (pair == 1) {
            audioBundle.putInt(AUDIO_BUNDLE_KEYS[9], AudioSettings.DEFAULT_FREQUENCY_MIN);
            audioBundle.putInt(AUDIO_BUNDLE_KEYS[10], AudioSettings.DEFAULT_FREQUENCY_MAX);
        }
        else if (pair == 2) {
            audioBundle.putInt(AUDIO_BUNDLE_KEYS[9], AudioSettings.SECOND_FREQUENCY_MIN);
            audioBundle.putInt(AUDIO_BUNDLE_KEYS[10], AudioSettings.SECOND_FREQUENCY_MAX);
        }
    }

    /********************************************************************/
    /*
     *      SCANNER
     */

    private boolean initScanner() {
        scanBufferSize = 0;
        audioChecker = new AudioChecker(this, audioBundle);

        entryLogger(getString(R.string.audio_check_pre_1), false);
        if (audioChecker.determineRecordAudioType()) {
            entryLogger(getAudioCheckerReport(), false);
            // get output settings here.
            entryLogger(getString(R.string.audio_check_pre_2), false);
            if (!audioChecker.determineOutputAudioType()) {
                // have a setup error getting the audio for output
                entryLogger(getString(R.string.audio_check_pre_3), true);
            }
            writeProcessor = new WriteProcessor(this, audioBundle);
            audioScanner = new AudioScanner(this, audioBundle);
            return true;
        }
        return false;
    }

    private String getAudioCheckerReport() {
        return ("audio record format: "
                + audioBundle.getInt(AUDIO_BUNDLE_KEYS[1]) +
                " Hz, " + audioBundle.getInt(AUDIO_BUNDLE_KEYS[4]) +
                " ms, " + AUDIO_ENCODING[audioBundle.getInt(AUDIO_BUNDLE_KEYS[3])] +
                ", " + AUDIO_CHANNEL_IN[audioBundle.getInt(AUDIO_BUNDLE_KEYS[2])] +
                ", " + AUDIO_SOURCE[audioBundle.getInt(AUDIO_BUNDLE_KEYS[0])]);
    }

    private void runAudioScanner() {
        entryLogger(getString(R.string.main_scanner_18), false);
        scanBufferSize = 0;
        if (audioBundle.getBoolean(AUDIO_BUNDLE_KEYS[14])) {
            if (!writeProcessor.prepareWriteAudioFile()) {
                entryLogger(getString(R.string.init_state_15), true);
            }
        }
        audioScanner.runAudioScanner();
    }

    private void stopAudioScanner() {
        if (audioScanner != null) {
            entryLogger(getString(R.string.main_scanner_19), false);
            // below nulls the recordTask...
            audioScanner.stopAudioScanner();
            writeProcessor.audioFileConvert();

            if (audioScanner.canProcessBufferStorage()) {
                scanBufferSize = audioScanner.getSizeBufferStorage();
                entryLogger(getString(R.string.main_scanner_20) + scanBufferSize, false);
            }
            else {
                entryLogger(getString(R.string.main_scanner_21), false);
            }
        }
    }

    private void runScanner() {
        if (!audioChecker.checkAudioRecord()) {
            // no mic or audio record capabilities
            entryLogger(getResources().getString(R.string.init_state_17), true);
            return;
        }
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":wakelock");
        }

        SCANNING = true;
        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);
        // clear any caution lines from previous session
        visualiserView.clearFrequencyCaution();
        //TODO wakelock remove
        wakeLock.acquire(600000); // timeout in ms (10 mins)

        entryLogger(getResources().getString(R.string.main_scanner_10), false);
        runAudioScanner();
    }

    private void stopScanner() {
        // do first to free up mic in case of passive jammer interrupt
        stopAudioScanner();

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
        entryLogger(getResources().getString(R.string.main_scanner_12), false);

        if (audioScanner.hasFrequencySequence()) {
            entryLogger("\n" + getResources().getString(R.string.main_scanner_13) + "\n", true);

            // to main debug view candidate numbers for logic 1,0
            entryLogger(getString(R.string.audio_scan_1) + "\n" + audioScanner.getFrequencySequenceLogic(), true);

            // all captures to detailed view:
            if (audioScanner.getFreqSeqLogicEntries().isEmpty()) {
                entryLogger("FreqSequence Logic entries empty.", false);
            }

            // simple report to main logger
            entryLogger(getResources().getString(R.string.main_scanner_14) + audioScanner.getFrequencySequenceSize(), true);
        }
        else {
            entryLogger(getResources().getString(R.string.main_scanner_16), false);
        }
        // allow freq list processing above first, then
        audioScanner.resetAudioScanner();

        entryLogger("\n" + getResources().getString(R.string.main_scanner_17) + "\n\n", false);

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
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }

            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        entryLogger(getResources().getString(R.string.headset_state_1), false);
                        toggleHeadset(false);
                        break;
                    case 1:
                        entryLogger(getResources().getString(R.string.headset_state_2), false);
                        toggleHeadset(true);
                        break;
                    default:
                        entryLogger(getResources().getString(R.string.headset_state_3), false);
                }
            }
        }
    }

    /********************************************************************/
/*
 * 	LOGGER
 */
    public static void entryLogger(String entry, boolean caution) {
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
}
