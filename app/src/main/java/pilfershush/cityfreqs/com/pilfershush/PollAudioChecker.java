package pilfershush.cityfreqs.com.pilfershush;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Handler;

public class PollAudioChecker {
    private Context context;
    private AudioRecord audioRecord;
    private int sampleRate;
    private int bufferSize;
    private int encoding;
    private int channel;
    private int audioSource;
    private int audioSessionId;

    protected static final int LONG_DELAY = 6000;
    protected static final int SHORT_DELAY = 1000;
    private int runningDelay = LONG_DELAY;
    private int userDelay;

    private Handler handlerU;
    private boolean polling;
    private boolean detected;
    protected boolean audioError;


    public PollAudioChecker(Context context, int audioSource, int sampleRate, int channel, int encoding, int bufferSize) {
        this.context = context;
        // use the AudioChecker's info
        this.audioSource = audioSource;
        this.sampleRate = sampleRate;
        this.channel = channel;
        this.encoding = encoding;
        this.bufferSize = bufferSize;
        audioSessionId = 0;
        userDelay = LONG_DELAY;

        detected = false;
        polling = false;
        audioError = false;
        handlerU = new Handler();
    }

    protected void destroy() {
        stopPolling();
    }

    /********************************************************************/
/*
 *
 */
    protected boolean setupPollAudio() {
        boolean setup = false;
        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSource, sampleRate, channel, encoding, bufferSize);
                audioSessionId = audioRecord.getAudioSessionId();
                // if returns a 0 then no new sessionId was generated
                if (audioSessionId == 0) {
                    MainActivity.entryLogger(context.getString(R.string.init_state_16), true);
                    audioError = true;
                }
                else {
                    MainActivity.logger(context.getString(R.string.polling_check_1) + audioSessionId);
                }

                setup = true;
            }
            catch (Exception ex) {
                ex.printStackTrace();
                MainActivity.logger(context.getString(R.string.polling_check_2));
                setup = false;
            }
        }
        else {
            return true;
        }
        return setup;
    }

    protected void togglePolling(int pollSpeed) {
        runningDelay = pollSpeed;
        userDelay = pollSpeed;
        if (polling) {
            stopPolling();
        }
        else {
            startPolling();
        }
    }

    protected boolean getDetected() {
        return detected;
    }

    /********************************************************************/
/*
 *
 */
    private void startPolling() {
        MainActivity.logger(context.getString(R.string.polling_check_3));
        pollingRunner.run();
        polling = true;
    }

    private void stopPolling() {
        if (polling) {
            handlerU.removeCallbacks(pollingRunner);
            polling = false;
            detected = false;
        }
        stopPollAudio();
    }

    private Runnable pollingRunner = new Runnable() {
        @Override
        public void run() {
            try {
                if (detected) {
                    // speed up polling
                    runningDelay = SHORT_DELAY;
                }
                else {
                    runningDelay = userDelay;
                }
                pollAudioForException();
            }
            finally {
                handlerU.postDelayed(pollingRunner, runningDelay);
            }
        }
    };

    private void pollAudioForException() {
        if (audioRecord == null) {
            // check
            setupPollAudio();
        }
        try {
            // need to start reading buffer to trigger an exception
            audioRecord.startRecording();
            short buffer[] = new short[bufferSize];
            int audioStatus = audioRecord.read(buffer, 0, bufferSize);

            // check for error on pre 6.x and 6.x API
            if(audioStatus == AudioRecord.ERROR_INVALID_OPERATION
                    || audioStatus == AudioRecord.STATE_UNINITIALIZED) {
                MainActivity.logger(context.getString(R.string.polling_check_4) + audioStatus);
                detected = true;
            }
        }
        catch(Exception e) {
            MainActivity.logger(context.getString(R.string.polling_check_5));
        }
        finally {
            try {
                audioRecord.stop();
                MainActivity.logger(context.getString(R.string.polling_check_6_1) + runningDelay + context.getString(R.string.polling_check_6_2));
                detected = false;
            }
            catch(Exception e){
                MainActivity.logger(context.getString(R.string.polling_check_7));
            }
        }
    }

    private void stopPollAudio() {
        // ensure we don't keep resources
        MainActivity.logger(context.getString(R.string.polling_check_8));
        if (audioRecord != null) {
            // check if recording first
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            MainActivity.logger(context.getString(R.string.polling_check_9));
        }
        else {
            MainActivity.logger(context.getString(R.string.polling_check_10));
        }
    }

	/*
	private void checkAudioTrackSessions() {
		// not this...
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
				channel, encoding, bufferSize, AudioTrack.MODE_STREAM);

		//audioSessionId = audioTrack.getAudioSessionId();
	}
	*/
}

