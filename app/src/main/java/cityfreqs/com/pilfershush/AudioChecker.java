package cityfreqs.com.pilfershush;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.media.audiofx.Equalizer;

import cityfreqs.com.pilfershush.assist.AudioSettings;

public class AudioChecker {
    private int sampleRate;
    private int bufferSize;
    private int encoding;
    private int channelConfig;
    private int audioSource;

    private Context context;
    private AudioRecord audioRecord;

    private AudioSettings audioSettings;

    public AudioChecker(Context context, AudioSettings audioSettings) {
        //
        this.context = context;
        this.audioSettings = audioSettings;
        // still need to determine if this is useful if user switchable, ie USB.
        audioSource = AudioSource.DEFAULT; //DEFAULT = 0, MIC = 1, CAMCORDER = 5
    }

    protected void destroy() {
        stopAllAudio();
        if (audioRecord != null) {
            audioRecord = null;
        }
    }

    protected String getAudioSettingsReport() {
        return audioSettings.toString();
    }

    /********************************************************************/
    /*
    AudioRecord.cpp ::

    if (inputSource == AUDIO_SOURCE_DEFAULT) {
        inputSource = AUDIO_SOURCE_MIC;
    }

    */

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
    protected boolean determineRecordAudioType() {
        // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
        int buffSize;
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

    protected boolean determineUsbRecordAudioType() {
        // android should auto switch to using USB audio device as default...
        int buffSize;
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
        MainActivity.logger(context.getString(R.string.audio_check_3));
        return false;
    }

    protected boolean determineOutputAudioType() {
        // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
        int buffSize;
        for (int rate : AudioSettings.SAMPLE_RATES) {
            for (short audioFormat : new short[] {
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioFormat.ENCODING_PCM_8BIT}) {

                for (short channelOutConfig : new short[] {
                        AudioFormat.CHANNEL_OUT_DEFAULT, // 1 - switched by OS, not native?
                        AudioFormat.CHANNEL_OUT_MONO,    // 4
                        AudioFormat.CHANNEL_OUT_STEREO }) {  // 12
                    try {
                        MainActivity.entryLogger("Try rate " + rate + "Hz, bits: " + audioFormat + ", channelOutConfig: "+ channelOutConfig, false);

                        buffSize = AudioTrack.getMinBufferSize(rate, channelOutConfig, audioFormat);
                        // dont need to force buffSize to powersOfTwo if it isnt (ie.S5) as no FFT

                        AudioTrack audioTrack = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                rate,
                                channelOutConfig,
                                audioFormat,
                                buffSize,
                                AudioTrack.MODE_STREAM);

                        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                            MainActivity.entryLogger("found: " + rate + ", buffer: " + buffSize + ", channelOutConfig: " + channelOutConfig, true);
                            // buffOutSize may not be same as buffInSize conformed to powersOfTwo
                            audioSettings.setChannelOutConfig(channelOutConfig);
                            audioSettings.setBufferOutSize(buffSize);

                            MainActivity.entryLogger("\nTesting for device audiofx equalizer.", false);
                            if (testOnboardEQ(audioTrack.getAudioSessionId())) {
                                MainActivity.entryLogger("Device audiofx equalizer test passed.\n", false);
                                audioSettings.setHasEQ(true);
                            }
                            else {
                                MainActivity.entryLogger("Device audiofx equalizer test failed.\n", true);
                                audioSettings.setHasEQ(false);
                            }

                            audioTrack.pause();
                            audioTrack.flush();
                            audioTrack.release();
                            return true;
                        }
                    }
                    catch (Exception e) {
                        MainActivity.entryLogger("Error, keep trying.", false);
                    }
                }
            }
        }
        MainActivity.entryLogger(context.getString(R.string.audio_check_2), true);
        return false;
    }

    // testing android/media/audiofx/Equalizer
    // idea is to make the whitenoise less annoying
    private boolean testOnboardEQ(int audioSessionId) {
        try {
            Equalizer equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            // get some info
            short bands = equalizer.getNumberOfBands();
            final short minEQ = equalizer.getBandLevelRange()[0]; // returns milliBel
            final short maxEQ = equalizer.getBandLevelRange()[1];

            MainActivity.entryLogger("Number EQ bands: " + bands, false);
            MainActivity.entryLogger("EQ min mB: " + minEQ, false);
            MainActivity.entryLogger("EQ max mB: " + maxEQ, false);

            for (short band = 0; band < bands; band++) {
                // divide by 1000 to get numbers into recognisable ranges
                MainActivity.entryLogger("\nband freq range min: " + (equalizer.getBandFreqRange(band)[0] / 1000), false);
                MainActivity.entryLogger("Band " + band + " center freq Hz: " + (equalizer.getCenterFreq(band) / 1000), true);
                MainActivity.entryLogger("band freq range max: " + (equalizer.getBandFreqRange(band)[1] / 1000), false);
                // band 5 reports center freq: 14kHz, minrange: 7000 and maxrange: 0  <- is this infinity? uppermost limit?
                // could be 21kHz if report standard of same min to max applies.
            }


            // only active test is to squash all freqs in bands 0-3, leaving last band (4) free...
            MainActivity.entryLogger("\nHPF test reduce EQ bands 2x loop by minEQ value: " + minEQ, false);

            for (int i = 0; i < 2; i++) {
                for (short j = 0; j < bands; j++) {
                    equalizer.setBandLevel(j, minEQ);
                }
            }
            // not a filter... reduced amplitude seems the best description when using eq.
            // repeat calls to -15 dB improves sound reduction
            // band4 to maxEQ will prob not do anything useful?

            return true;
        }
        catch (Exception ex) {
            MainActivity.entryLogger("testEQ Exception.", true);
            ex.printStackTrace();
            return false;
        }
    }

    protected AudioSettings getAudioSettings() {
        return audioSettings;
    }

    protected boolean checkAudioRecord() {
        // return if can start new audioRecord object
        try {
            audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, encoding, bufferSize );
            MainActivity.logger(context.getString(R.string.audio_check_4));
            // need to start reading buffer to trigger an exception
            audioRecord.startRecording();
            short buffer[] = new short[bufferSize];
            int audioStatus = audioRecord.read(buffer, 0, bufferSize);

            // check for error on pre 6.x and 6.x API
            if(audioStatus == AudioRecord.ERROR_INVALID_OPERATION
                    || audioStatus == AudioRecord.STATE_UNINITIALIZED) {
                MainActivity.logger(context.getString(R.string.audio_check_6) + audioStatus);
                // audioStatus == 0(uninitialized) is an error, does not throw exception
                MainActivity.logger(context.getString(R.string.audio_check_5));
                return false;
            }
        }
        catch(Exception e) {
            MainActivity.logger(context.getString(R.string.audio_check_7));
            MainActivity.logger(context.getString(R.string.audio_check_9));
            return false;
        }
        // no errors
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        MainActivity.logger(context.getString(R.string.audio_check_8));
        return true;
    }

    private void stopAllAudio() {
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

