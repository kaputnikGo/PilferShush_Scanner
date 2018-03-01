package pilfershush.cityfreqs.com.pilfershush;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class AudioChecker {
    private int sampleRate;
    private int bufferSize;
    private int encoding;
    private int channelConfig;
    private int audioSource;

    private Context context;
    private AudioRecord audioRecord;
    private PollAudioChecker pollAudioChecker;
    private int userPollSpeed;

    private AudioSettings audioSettings;

    public AudioChecker(Context context, AudioSettings audioSettings) {
        //
        this.context = context;
        userPollSpeed = PollAudioChecker.LONG_DELAY;
        this.audioSettings = audioSettings;
        // still need to determine if this is useful if user switchable, ie USB.
        audioSource = AudioSource.DEFAULT; //DEFAULT = 0, MIC = 1, CAMCORDER = 5
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

    protected String getAudioSettingsReport() {
        return audioSettings.toString();
    }

    /********************************************************************/
/*
 *      Find audio record format for device.
 *
 *      NOTES
 *      channelConfig != number of channels of audio
 *      CHANNEL_IN_MONO (channel count = 1, mono ) = CHANNEL_IN_FRONT (channel count = 2, stereo)
 *
 *      other possible values to consider:
 *      AudioFormat.ENCODING_PCM_FLOAT = 4
 *      AudioFormat.ENCODING_AC3 = 5
 *      AudioFormat.ENCODING_E_AC3 = 6
 *
 *      below has channel count = 2 (stereo)
 *      AudioFormat.CHANNEL_IN_FRONT = 16 // n.b. CHANNEL_IN_MONO = CHANNEL_IN_FRONT
 *      AudioFormat.CHANNEL_IN_BACK = 32
 *
 *
 *
/system/media/audio/include/system/audio.h
/android/media/AudioFormat.java
/android/media/AudioRecord.java

 typedef enum {
    //input devices
    AUDIO_DEVICE_IN_COMMUNICATION         = 0x10000,
    AUDIO_DEVICE_IN_AMBIENT               = 0x20000,
    AUDIO_DEVICE_IN_BUILTIN_MIC           = 0x40000,
    AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET = 0x80000,
    AUDIO_DEVICE_IN_WIRED_HEADSET         = 0x100000,
    AUDIO_DEVICE_IN_AUX_DIGITAL           = 0x200000,
    AUDIO_DEVICE_IN_VOICE_CALL            = 0x400000,
    AUDIO_DEVICE_IN_BACK_MIC              = 0x800000,
    AUDIO_DEVICE_IN_DEFAULT               = 0x80000000,
}

typedef enum {
    AUDIO_SOURCE_DEFAULT             = 0,
    AUDIO_SOURCE_MIC                 = 1,
    AUDIO_SOURCE_VOICE_UPLINK        = 2,  // system only, requires Manifest.permission#CAPTURE_AUDIO_OUTPUT
    AUDIO_SOURCE_VOICE_DOWNLINK      = 3,  // system only, requires Manifest.permission#CAPTURE_AUDIO_OUTPUT
    AUDIO_SOURCE_VOICE_CALL          = 4,  // system only, requires Manifest.permission#CAPTURE_AUDIO_OUTPUT
    AUDIO_SOURCE_CAMCORDER           = 5,  // for video recording, same orientation as camera
    AUDIO_SOURCE_VOICE_RECOGNITION   = 6,  // tuned for voice recognition
    AUDIO_SOURCE_VOICE_COMMUNICATION = 7,  // VoIP with echo cancel, auto gain ctrl if available
    AUDIO_SOURCE_CNT,
    AUDIO_SOURCE_MAX                 = AUDIO_SOURCE_CNT - 1,
} audio_source_t;

also -

@SystemApi
public static final int HOTWORD = 1999; //  always-on software hotword detection,
         while gracefully giving in to any other application
         that might want to read from the microphone.
         This is a hidden audio source.

         same gain and tuning as VOICE_RECOGNITION
         Flat frequency response (+/- 3dB) from 100Hz to 4kHz
         Effects/pre-processing must be disabled by default
         Near-ultrasound requirements: no band-pass or anti-aliasing filters.

         android.Manifest.permission.HOTWORD_RECOGNITION

         ** the HOTWORD may not be detectable by technique of forcing errors when polling mic

 *
 */
    protected boolean determineInternalAudioType() {
        // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
        int buffSize = 0;
        for (int rate : AudioSettings.SAMPLE_RATES) {
            for (short audioFormat : new short[] {
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioFormat.ENCODING_PCM_8BIT}) {

                for (short channelConfig : new short[] {
                        AudioFormat.CHANNEL_IN_DEFAULT, // 1 - switched by OS, not native?
                        AudioFormat.CHANNEL_IN_MONO,    // 16, also CHANNEL_IN_FRONT == 16
                        AudioFormat.CHANNEL_IN_STEREO }) {  // 12
                    try {
                        MainActivity.logger("Try rate " + rate + "Hz, bits: " + audioFormat + ", channelConfig: "+ channelConfig);
                        buffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                        // force buffSize to powersOfTwo if it isnt (ie.S5)
                        buffSize = AudioSettings.getClosestPowersHigh(buffSize);

                        if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
                            AudioRecord recorder = new AudioRecord(
                                    audioSource,
                                    rate,
                                    channelConfig,
                                    audioFormat,
                                    buffSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                MainActivity.logger("found, rate: " + rate + ", buffer: " + buffSize + ", channel count: " + recorder.getChannelCount());
                                // set found values
                                // AudioRecord.getChannelCount() is number of input audio channels (1 is mono, 2 is stereo)
                                sampleRate = rate;
                                this.channelConfig = channelConfig;
                                encoding = audioFormat;
                                bufferSize = buffSize;
                                audioSettings.setBasicAudioSettings(sampleRate, bufferSize, encoding, this.channelConfig, recorder.getChannelCount());
                                audioSettings.setAudioSource(audioSource);
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
        MainActivity.logger(context.getString(R.string.audio_check_1));
        return false;
    }

    protected boolean determineUsbAudioType(boolean hasUSB_audio) {
        // android should auto switch to using USB audio device as default...
        int buffSize = 0;
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
                            MainActivity.logger("USB - try rate " + rate + "Hz, bits: " + audioFormat + ", channelConfig: "+ channelConfig);
                            buffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                            // force buffSize to powersOfTwo if it isnt (ie.S5)
                            buffSize = AudioSettings.getClosestPowersHigh(buffSize);

                            if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
                                AudioRecord recorder = new AudioRecord(
                                        audioSource,
                                        rate,
                                        channelConfig,
                                        audioFormat,
                                        buffSize);

                                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                    MainActivity.logger("USB - found:: rate: " + rate + ", buffer: " + buffSize + ", channel count: " + recorder.getChannelCount());
                                    MainActivity.logger("USB - Audio source: " + recorder.getAudioSource());
                                    // set found values
                                    sampleRate = rate;
                                    this.channelConfig = channelConfig;
                                    encoding = audioFormat;
                                    bufferSize = buffSize;
                                    audioSettings.setBasicAudioSettings(sampleRate, bufferSize, encoding, this.channelConfig, recorder.getChannelCount() );
                                    audioSettings.setAudioSource(audioSource);
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
            MainActivity.logger(context.getString(R.string.audio_check_2));
            return false;
        }
        MainActivity.logger(context.getString(R.string.audio_check_3));
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
                audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, encoding, bufferSize);
                //audioSessionId = audioRecord.getAudioSessionId();
                MainActivity.logger(context.getString(R.string.audio_check_4));
            }
            catch (Exception ex) {
                ex.printStackTrace();
                MainActivity.logger(context.getString(R.string.audio_check_5));
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
            audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, encoding, bufferSize );
            // need to start reading buffer to trigger an exception
            audioRecord.startRecording();
            short buffer[] = new short[bufferSize];
            int audioStatus = audioRecord.read(buffer, 0, bufferSize);

            // check for error on pre 6.x and 6.x API
            if(audioStatus == AudioRecord.ERROR_INVALID_OPERATION
                    || audioStatus == AudioRecord.STATE_UNINITIALIZED) {
                MainActivity.logger(context.getString(R.string.audio_check_6) + audioStatus);
            }
        }
        catch(Exception e) {
            MainActivity.logger(context.getString(R.string.audio_check_7));
        }
        finally {
            try {
                MainActivity.logger(context.getString(R.string.audio_check_8));
            }
            catch(Exception e){
                MainActivity.logger(context.getString(R.string.audio_check_9));
            }
        }
    }

    // currently this will start and then destroy after single use...
    protected boolean pollAudioCheckerInit() {
        //set for default
        pollAudioChecker = null;
        pollAudioChecker = new PollAudioChecker(context, audioSource, sampleRate, channelConfig, encoding, bufferSize);
        return pollAudioChecker.setupPollAudio();
    }

    protected boolean audioStateError() {
        return pollAudioChecker.audioError;
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
        MainActivity.logger(context.getString(R.string.audio_check_10));
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            MainActivity.logger(context.getString(R.string.audio_check_11));
        }
        else {
            MainActivity.logger(context.getString(R.string.audio_check_12));
        }
    }
}

