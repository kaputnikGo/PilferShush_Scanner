package pilfershush.cityfreqs.com.pilfershush;

// test class for determining methods of jamming inbound nuhf
// concept: to run in background and play constant random noise at 18kHz and above
// links to checkout: https://github.com/m-abboud/android-tone-player

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
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


    public AudioJammer(Context context, AudioSettings audioSettings) {
        this.context = context;
        this.audioSettings = audioSettings;

    }

    protected void runJammer() {
        jammerIntent = new Intent(context, PSJammer.class);
        context.startService(jammerIntent);
    }

    protected void stopJammer() {
        stopSound();
        context.stopService(jammerIntent);
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


    private class PSJammer extends Service {
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
                // run the jammer, ie:
                playSound(18000, 2048);

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
