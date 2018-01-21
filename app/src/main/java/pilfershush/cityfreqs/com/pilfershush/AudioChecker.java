package pilfershush.cityfreqs.com.pilfershush;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

//import android.media.AudioManager;
//import android.media.AudioTrack;

public class AudioChecker {
    private int sampleRate;
    private int bufferSize;
    private int encoding;
    private int channel;
    private int audioSource = AudioSource.DEFAULT;

    private AudioRecord audioRecord;
    private PollAudioChecker pollAudioChecker;
    private int userPollSpeed;

    private AudioSettings audioSettings;


    public AudioChecker() {
        //
        userPollSpeed = PollAudioChecker.LONG_DELAY;
        audioSettings = new AudioSettings();
    }

    protected void destroy() {
        stopAllAudio();
        if (audioRecord != null) {
            audioRecord = null;
        }
        if (pollAudioChecker != null) {
            pollAudioChecker.destroy();
        }
    }

    /********************************************************************/
/*
 *
 */
    protected boolean determineInternalAudioType() {
        // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
        for (int rate : AudioSettings.SAMPLE_RATES) {
            for (short audioFormat : new short[] {
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioFormat.ENCODING_PCM_8BIT}) {

                for (short channelConfig : new short[] {
                        AudioFormat.CHANNEL_IN_DEFAULT,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.CHANNEL_IN_STEREO }) {
                    try {
                        MainActivity.logger("Try rate " + rate + "Hz, bits: " + audioFormat + ", channel: "+ channelConfig);

                        int buffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                        if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
                            // check if we can instantiate and have a success

                            AudioRecord recorder = new AudioRecord(
                                    AudioSource.DEFAULT,
                                    rate,
                                    channelConfig,
                                    audioFormat,
                                    buffSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                MainActivity.logger("found, rate: " + rate + ", min-buff: " + buffSize);
                                // set our values
                                sampleRate = rate;
                                channel = channelConfig;
                                encoding = audioFormat;
                                bufferSize = buffSize;
                                audioSettings.setBasicAudioSettings(sampleRate, bufferSize, encoding, channel);
                                recorder.release();
                                return true;
                            }
                        }
                    }
                    catch (Exception e) {
                        MainActivity.logger("Rate: " + rate + "Exception, keep trying, e:" + e.toString());
                    }
                }
            }
        }
        MainActivity.logger("determine internal audio failure.");
        return false;
    }

    protected boolean determineUsbAudioType(boolean hasUSB_audio) {
        // android should auto switch to using USB audio device as default...
        if (hasUSB_audio) {
            for (int rate : AudioSettings.SAMPLE_RATES) {
                for (short audioFormat : new short[] {
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioFormat.ENCODING_PCM_8BIT }) {

                    for (short channelConfig : new short[] {
                            AudioFormat.CHANNEL_IN_DEFAULT,  //1
                            AudioFormat.CHANNEL_IN_MONO,  // 16
                            AudioFormat.CHANNEL_IN_STEREO }) { // 12
                        try {
                            MainActivity.logger("USB - try rate " + rate + "Hz, bits: " + audioFormat + ", channel: "+ channelConfig);

                            int buffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                            if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
                                // check if we can instantiate and have a success
                                // trying to get usb audio dongle with mic/line-in and headphone out...
                                AudioRecord recorder = new AudioRecord(
                                        AudioSource.DEFAULT,  //DEFAULT, MIC, CAMCORDER
                                        rate,
                                        channelConfig,
                                        audioFormat,
                                        buffSize);

                                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                    MainActivity.logger("USB - found:: rate: " + rate + ", min-buff: " + buffSize + ", channel: " + channelConfig);
                                    MainActivity.logger("USB - Audio source: " + recorder.getAudioSource());
                                    // set our values
                                    sampleRate = rate;
                                    channel = channelConfig;
                                    encoding = audioFormat;
                                    bufferSize = buffSize;
                                    audioSettings.setBasicAudioSettings(sampleRate, bufferSize, encoding, channel);
                                    // experimental: get usbaudio dongle mic to work
                                    audioSettings.setAudioSource(AudioSource.MIC);
                                    recorder.release();
                                    return true;
                                }
                            }
                        }
                        catch (Exception e) {
                            MainActivity.logger("Rate: " + rate + "Exception, keep trying, e:" + e.toString());
                        }
                    }
                }
            }
        }
        else {
            MainActivity.logger("Error: USB device container has no device.");
            return false;
        }
        MainActivity.logger("determine USB audio failure.");
        return false;
    }

    protected AudioSettings getAudioSettings() {
        return audioSettings;
    }

    protected boolean checkAudioRecord() {
        // return if can start new audioRecord object
        boolean recordable = false;
        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSource, sampleRate, channel, encoding, bufferSize);
                //audioSessionId = audioRecord.getAudioSessionId();
                MainActivity.logger("Can start Microphone Check.");
            }
            catch (Exception ex) {
                ex.printStackTrace();
                MainActivity.logger("Microphone in use...");
                recordable = false;
            }
            finally {
                try {
                    audioRecord.release();
                    recordable = true;
                }
                catch(Exception e){
                    recordable = false;
                }
            }
        }
        else {
            recordable = true;
        }
        return recordable;
    }

    // TODO
    // can check for AUDIOFOCUS ?

    /********************************************************************/
/*
 *
 */
    protected void checkAudioBufferState() {
        try {
            audioRecord = new AudioRecord(AudioSource.DEFAULT, sampleRate, channel, encoding, bufferSize );
            // need to start reading buffer to trigger an exception
            audioRecord.startRecording();
            short buffer[] = new short[bufferSize];
            int audioStatus = audioRecord.read(buffer, 0, bufferSize);

            // check for error on pre 6.x and 6.x API
            if(audioStatus == AudioRecord.ERROR_INVALID_OPERATION
                    || audioStatus == AudioRecord.STATE_UNINITIALIZED) {
                MainActivity.logger("checkAudioBufferState error status: " + audioStatus);
            }
        }
        catch(Exception e) {
            MainActivity.logger("checkAudioBufferState exception on start.");
        }
        finally {
            try {
                MainActivity.logger("checkAudioBufferState no error.");
            }
            catch(Exception e){
                MainActivity.logger("checkAudioBufferState exception on close.");
            }
        }
    }

    // currently this will start and then destroy after single use...
    protected boolean pollAudioCheckerInit() {
        //set for default
        pollAudioChecker = new PollAudioChecker(audioSource, sampleRate, channel, encoding, bufferSize);
        return pollAudioChecker.setupPollAudio();
    }

    protected void pollAudioCheckerStart() {
        if (pollAudioChecker != null) {
            pollAudioChecker.togglePolling(userPollSpeed);
        }
    }

    protected void finishPollChecker() {
        if (pollAudioChecker != null) {
            pollAudioChecker.togglePolling(userPollSpeed);
            pollAudioChecker = null;
        }
    }

    protected void setPollingSpeed(int userSpeed) {
        userPollSpeed = userSpeed;
    }

    protected boolean getDetected() {
        if (pollAudioChecker != null) {
            return pollAudioChecker.getDetected();
        }
        return false;
    }

    /********************************************************************/
/*
 *
 */
    protected void stopAllAudio() {
        // ensure we don't keep resources
        MainActivity.logger("stopAllAudio called.");
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            MainActivity.logger("audioRecord stop and release.");
        }
        else {
            MainActivity.logger("audioRecord is null.");
        }
    }
}

