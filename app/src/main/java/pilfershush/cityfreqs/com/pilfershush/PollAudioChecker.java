package pilfershush.cityfreqs.com.pilfershush;

import android.media.AudioRecord;
import android.os.Handler;

public class PollAudioChecker {
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


    public PollAudioChecker(int audioSource, int sampleRate, int channel, int encoding, int bufferSize) {
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
                MainActivity.logger("Setup for polling mic ready, id: " + audioSessionId);
                setup = true;
            }
            catch (Exception ex) {
                ex.printStackTrace();
                MainActivity.logger("Polling mic in use...");
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
        MainActivity.logger("Polling Runner call.");
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
                MainActivity.logger("pollAudio error status: " + audioStatus);
                detected = true;
            }
        }
        catch(Exception e) {
            MainActivity.logger("pollAudio exception on start.");
        }
        finally {
            try {
                audioRecord.stop();
                MainActivity.logger("polling Audio at " + runningDelay + "(ms), no error.");
                detected = false;
            }
            catch(Exception e){
                MainActivity.logger("pollAudio exception on stop.");
            }
        }
    }

    private void stopPollAudio() {
        // ensure we don't keep resources
        MainActivity.logger("stop Poll Audio called.");
        if (audioRecord != null) {
            // check if recording first
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            MainActivity.logger("Poll Audio stop and release.");
        }
        else {
            MainActivity.logger("audioRecord is null.");
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

