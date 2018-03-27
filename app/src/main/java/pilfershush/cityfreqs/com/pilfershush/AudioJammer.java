package pilfershush.cityfreqs.com.pilfershush;

// test class for determining methods of jamming inbound nuhf
// concept: to run in background and play constant random noise at 18kHz and above
// links to checkout: https://github.com/m-abboud/android-tone-player

// passive jammer is for hold mic
// active jammer is for flood of n-uhf audio output

// need audioFocus listener halt resume, only telephony

// ACTIVE JAMMER
// user activated and runs for n-time, and/or till user stops it
// check for mic use first, then ensure PS not using it.

// PASSIVE JAMMER
// other technique, grab mic, zero the input values (cat/dev/null) and hold until telephony or user interrupt
// run on timer, ie set alarms hourly/half-hourly for continue?

// TODO all logtext to xml


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.widget.Toast;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class AudioJammer {
    Context context;
    AudioSettings audioSettings;
    AudioTrack audioTrack;
    Intent jammerIntent;
    AudioRecord audioRecord;

    private boolean RUN_PASSIVE_JAMMER;


    public AudioJammer(Context context, AudioSettings audioSettings) {
        this.context = context;
        this.audioSettings = audioSettings;
        RUN_PASSIVE_JAMMER = false;
    }

    protected void runActiveJammer() {
        jammerIntent = new Intent(context, PSActiveJammer.class);
        context.startService(jammerIntent);
    }

    protected void stopActiveJammer() {
        stopSound();
        context.stopService(jammerIntent);
    }

    protected void startPassiveJammer() {
        // grab mic via AudioRecord object,
        // zero the input
        // battery use check, CPU use check
        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSettings.getAudioSource(),
                        audioSettings.getSampleRate(),
                        audioSettings.getChannelConfig(),
                        audioSettings.getEncoding(),
                        audioSettings.getBufferSize());

                MainActivity.entryLogger("Passive Jammer init.", false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
                MainActivity.entryLogger("Passive Jammer failed to init.", true);
            }
        }
    }

    // TODO determine how little is needed to occupy/hold the microphone without actually recording and saving any audio
    // assuming that only one api call can be made to the mic at a time
    private void runPassiveJammer() {
        if ((audioRecord != null) || (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
            try {
                // android source says: Transport Control Method, Starts recording from the AudioRecord instance.
                audioRecord.startRecording();
                MainActivity.entryLogger("Passive Jammer start.", false);

                short buffer[] = new short[audioSettings.getBufferSize()];
                int audioStatus = audioRecord.read(buffer, 0, audioSettings.getBufferSize());
                // check for error on pre 6.x and 6.x API
                if (audioStatus == AudioRecord.ERROR_INVALID_OPERATION
                        || audioStatus == AudioRecord.STATE_UNINITIALIZED) {
                    MainActivity.entryLogger("Passive Jammer audio status: error.", true);
                }

                // TODO check is this state enough
                if (audioStatus == AudioRecord.RECORDSTATE_RECORDING) {
                    MainActivity.entryLogger("Passive Jammer audio status: running.", true);
                    RUN_PASSIVE_JAMMER = true;
                }

                // TODO prefer not to do this below
                // android source says: Audio data supply, Reads audio data from the audio hardware for recording into a buffer.
                short[] tempBuffer = new short[audioSettings.getBufferSize()];;
                do {
                    audioRecord.read(tempBuffer, 0, audioSettings.getBufferSize());
                } while (RUN_PASSIVE_JAMMER);

            }
            catch (IllegalStateException exState) {
                exState.printStackTrace();
                MainActivity.entryLogger("Passive Jammer failed to run.", true);
            }
        }
    }

    protected void stopPassiveJammer() {
        // get AudioRecord object, null it, clean up
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            RUN_PASSIVE_JAMMER = false;
            MainActivity.entryLogger("Passive Jammer stop and release.", false);
        }
        else {
            MainActivity.entryLogger("Passive Jammer not running.", false);
        }
    }


    // simple sine wave tone generator
    private void playSound(double frequency, int duration) {
        // AudioTrack definition - get settings from audioSettings.class
        int mBufferSize = AudioTrack.getMinBufferSize(44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize, AudioTrack.MODE_STREAM);

        // Sine wave
        double[] mSound = new double[4410];
        short[] mBuffer = new short[duration];
        for (int i = 0; i < mSound.length; i++) {
            mSound[i] = Math.sin((2.0 * Math.PI * i / (44100 / frequency)));
            mBuffer[i] = (short) (mSound[i] * Short.MAX_VALUE);
        }

        audioTrack.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume());
        audioTrack.play();

        audioTrack.write(mBuffer, 0, mSound.length);

        // need to loop this playback of duration/buffer

        audioTrack.stop();
        audioTrack.release();

    }

    private void stopSound() {
        if (audioTrack != null) {
            if (audioTrack.getPlayState() > 1) {
                //paused or playing
                audioTrack.stop();
            }
            audioTrack.release();
        }
    }


    private class PSActiveJammer extends Service {
        public static final String TAG = "PSJammer";
        private Looper serviceLooper;
        private ServiceHandler serviceHandler;

        // Handler that receives messages from the thread
        private final class ServiceHandler extends Handler {
            public ServiceHandler(Looper looper) {
                super(looper);
            }
            @Override
            public void handleMessage(Message msg) {
                // TODO run the jammer, ie:
                playSound(18000, 2048);
                // function to mod and cover all freqs of interest

                // then, if internal, else stop from outside

                // Stop the service using the startId, so that we don't stop
                // the service in the middle of handling another job
                stopSelf(msg.arg1);
            }
        }

        @Override
        public void onCreate() {
            // Start up the thread running the service.
            // background priority, do not disrupt UI.
            HandlerThread thread = new HandlerThread("ServiceStartArguments",
                    Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();

            serviceLooper = thread.getLooper();
            serviceHandler = new ServiceHandler(serviceLooper);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Toast.makeText(this, "jammer service starting", Toast.LENGTH_SHORT).show();

            // For each start request, send a message to start a job and deliver the
            // start ID so we know which request we're stopping when we finish the job
            Message msg = serviceHandler.obtainMessage();
            msg.arg1 = startId;
            serviceHandler.sendMessage(msg);
            return START_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            // no bindings as yet
            return null;
        }

        @Override
        public void onDestroy() {
            Toast.makeText(this, "PSJammer service done", Toast.LENGTH_SHORT).show();
        }
    }
}
