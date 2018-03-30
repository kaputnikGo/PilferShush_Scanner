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

import java.util.Random;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class AudioJammer {
    Context context;
    AudioSettings audioSettings;
    AudioTrack audioTrack;
    Intent jammerIntent;
    AudioRecord audioRecord;

    final static public int SAMPLE_SIZE = 2;
    final static public int PACKET_SIZE = 5000;

    protected static final int BYPASS = 0;
    protected static final int LOWPASS = 1;
    protected static final int HIGHPASS = 2;

    private boolean RUN_PASSIVE_JAMMER;
    private boolean RUN_ACTIVE_JAMMER;

    public AudioJammer(Context context, AudioSettings audioSettings) {
        this.context = context;
        this.audioSettings = audioSettings;
        RUN_PASSIVE_JAMMER = false;
        RUN_ACTIVE_JAMMER = true;
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
                /*
                After it's created the track is not active. Call start() to make it active. <--
                AudioRecord.java
                if (native_start(MediaSyncEvent.SYNC_EVENT_NONE, 0) == SUCCESS)
                status_t start(int [AudioSystem::sync_event_t] event, int triggerSession)
                */
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
                /*
                snip AudioRecord.cpp
                    ssize_t AudioRecord::read(void* buffer, size_t userSize)
                      memcpy(buffer, audioBuffer.i8, bytesRead);
                      read += bytesRead;
                      return read;
                */
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

    private void whiteNoiseGen() {
        short[] noiseBuffer = new short[PACKET_SIZE];
        Random random = new Random();

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                audioSettings.getBufferSize(), AudioTrack.MODE_STREAM);

        while (RUN_ACTIVE_JAMMER) {
            //noiseBuffer.clear();
            // nextGaussian:: random numbers that cluster around an average
            // TODO constrain to 18kHz - 22kHz otherwise it's audible
            Filter filter = new Filter(18000, 44100, HIGHPASS, 1);

            for (int i = 0; i < PACKET_SIZE /SAMPLE_SIZE; i++) {
                noiseBuffer[i] = (short) (random.nextGaussian() * Short.MAX_VALUE); // * Constant Value: 32767
                // test filtering method
                filter.Update(noiseBuffer[i]);
                //TODO this short convert...
                noiseBuffer[i] = (floatToShort(filter.getValue()));
            }

            audioTrack.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume());
            audioTrack.play();

            audioTrack.write(noiseBuffer, 0, PACKET_SIZE);
        }
        // need to loop this playback of duration/buffer
        audioTrack.stop();
        audioTrack.release();
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

    public class Filter {
        //resonance; // amount, from sqrt(2) to ~ 0.1

        private float c, a1, a2, a3, b1, b2;
        private float[] inputHistory = new float[2];
        private float[] outputHistory = new float[3];

        public Filter(float frequency, int sampleRate, int passType, float resonance) {
            switch (passType) {
                case LOWPASS:
                    c = 1.0f / (float) Math.tan(Math.PI * frequency / sampleRate);
                    a1 = 1.0f / (1.0f + resonance * c + c * c);
                    a2 = 2f * a1;
                    a3 = a1;
                    b1 = 2.0f * (1.0f - c * c) * a1;
                    b2 = (1.0f - resonance * c + c * c) * a1;
                    break;
                case HIGHPASS:
                    c = (float) Math.tan(Math.PI * frequency / sampleRate);
                    a1 = 1.0f / (1.0f + resonance * c + c * c);
                    a2 = -2f * a1;
                    a3 = a1;
                    b1 = 2.0f * (c * c - 1.0f) * a1;
                    b2 = (1.0f - resonance * c + c * c) * a1;
                    break;
                case BYPASS:
                    default:
                    break;
            }
        }

        public void Update(float newInput) {
            float newOutput = a1 * newInput + a2 * this.inputHistory[0] + a3
                    * this.inputHistory[1] - b1 * this.outputHistory[0] - b2
                    * this.outputHistory[1];

            this.inputHistory[1] = this.inputHistory[0];
            this.inputHistory[0] = newInput;

            this.outputHistory[2] = this.outputHistory[1];
            this.outputHistory[1] = this.outputHistory[0];
            this.outputHistory[0] = newOutput;
        }

        public float getValue() {
            return this.outputHistory[0];
        }
    }

    private static short floatToShort(float x) {
        // Constant Value: -32767
        if (x < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        // Constant Value: 32767
        if (x > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return (short) Math.round(x);
    }
}
