package pilfershush.cityfreqs.com.pilfershush.assist;

import android.media.AudioFormat;

import java.nio.ByteBuffer;

public class AudioSettings {
    // helper vars and defaults
    // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
    public static final int RATE_48 = 48000;
    public static final int RATE_44 = 44100;
    public static final int RATE_22 = 22050;
    public static final int RATE_16 = 16000;
    public static final int RATE_11 = 11025;
    public static final int RATE_8 = 8000;
    public static final int[] SAMPLE_RATES = new int[] {
            RATE_48, RATE_44, RATE_22, RATE_16, RATE_11, RATE_8 };

    public static final int[] POWERS_TWO_HIGH = new int[] {
            512, 1024, 2048, 4096, 8192, 16384 };

    public static final int[] POWERS_TWO_LOW = new int[] {
            2, 4, 8, 16, 32, 64, 128, 256 };

    public static final double PI2 = 6.283185307179586;
    public static final double PI4 = 12.566370614359172;
    public static final double PI6 = 18.84955592153876d;

    // max frequency range is 3000hz (between min and max)
    public static final int DEFAULT_FREQUENCY_MIN = 18000;
    public static final int DEFAULT_FREQUENCY_MAX = 21000;
    public static final int SECOND_FREQUENCY_MIN = 19000;
    public static final int SECOND_FREQUENCY_MAX = 22000;

    // using dB SPL (sound pressure level) without distance
    // db = 20 log10(goertzel_magnitude).
    // mag_50, mag_70 are way too sensitive = false positives
    //public static final double MAGNITUDE_50 = 500; // ~= 53.9794 dB
    //public static final double MAGNITUDE_70 = 3000; // ~= 69.5425 dB

    public static final double MAGNITUDE_80 = 10000; // ~= 80.0000 dB
    public static final double MAGNITUDE_90 = 30000; // ~= 89.5424 dB
    public static final double MAGNITUDE_100 = 80000; // ~= 98.0618 dB
    public static final int DEFAULT_MAGNITUDE = 80000; // was 50000
    // possible::
    // db = 20 log10(goertzel_magnitude).
    // 50000 ~= 93.9794
    // 80000 ~= 98.0618

    // steps in the frequencies to consider as a coded signal and not noise
    public static final int MIN_FREQ_STEP = 1;
    //public static final int FREQ_STEP_5 = 5;
    public static final int FREQ_STEP_10 = 10;
    public static final int FREQ_STEP_25 = 25;
    public static final int FREQ_STEP_50 = 50;
    public static final int FREQ_STEP_75 = 75;
    public static final int MAX_FREQ_STEP = 100;
    public static final int DEFAULT_FREQ_STEP = 25;
    public static final int FREQ_DIVISOR = 25;

    // possible number of audio signals to sequence (32 bits)
    //public static final int MAX_SEQUENCE_LENGTH = 32;

    // 1=Hann(ing), 2=Blackman, 3=Hamming, 4=Nuttall, 5=Blackman-Nuttall
    public static final int DEFAULT_WINDOW_TYPE = 2;

    // scanning delay for runner
    //public static final int MICRO_DELAY = 1; // for modulated code
    public static final int SHORT_DELAY = 1000;
    public static final int SEC_2_DELAY = 2000;
    public static final int SEC_3_DELAY = 3000;
    public static final int LONG_DELAY = 6000;

    // vars for AudioRecord creation and use
    private int sampleRate;
    private int bufferSize; // in bytes
    private int encoding;
    private int channelConfig;
    private int channelCount;
    private int audioSource;

    private int minFreq;
    private int maxFreq;

    private boolean writeFiles;

    public AudioSettings(boolean writeFiles) {
        // convenience class to hold values for audio recording
        // and useful vars for audio processing
        this.writeFiles = writeFiles;
        minFreq = DEFAULT_FREQUENCY_MIN;
        maxFreq = DEFAULT_FREQUENCY_MAX;
    }

    public void setBasicAudioSettings(int sampleRate, int bufferSize, int encoding, int channelConfig, int channelCount) {
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.encoding = encoding;
        this.channelConfig = channelConfig;
        this.channelCount = channelCount;
    }
    public void setEncoding(int encoding) {
        this.encoding = encoding;
    }
    public void setAudioSource(int audioSource) {
        this.audioSource = audioSource;
    }

    public int getSampleRate() {
        return sampleRate;
    }
    public int getBufferSize() {
        return bufferSize;
    }
    public int getEncoding() {
        return encoding;
    }

    public int getChannelConfig() {
        return channelConfig;
    }

    public int getChannelCount() {
        return channelCount;
    }
    public int getAudioSource() {
        return audioSource;
    }

    public void setMinFreq(int minFreq) {
        this.minFreq = minFreq;
    }
    public void setMaxFreq(int maxFreq) {
        this.maxFreq = maxFreq;
    }

    public int getMinFreq() {
        return minFreq;
    }
    public int getMaxFreq() {
        return maxFreq;
    }

    public void setWriteFiles(boolean writeFiles) {
        this.writeFiles = writeFiles;
    }
    public boolean getWriteFiles() {
        return writeFiles;
    }

    public String toString() {
        return new String("audio record format: "
                + sampleRate + ", " + bufferSize + ", "
                + encoding + ", " + channelConfig + ", " + audioSource);
    }

    public String saveFormatToString() {
        return new String(sampleRate + " Hz, "
                + getBitDepth() + " bits, "
                + channelCount + " channel");
    }

    public int getBitDepth() {
        // encoding == int value of bit depth
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) return 8;
        else if (encoding == AudioFormat.ENCODING_PCM_16BIT) return 16;
        else if (encoding == AudioFormat.ENCODING_PCM_FLOAT) return 32;
        else {
            // default or error, return "guaranteed" default
            return 16;
        }
    }

    /********************************************************************/
/*
 * Utilities, that may be useful...
 *
 */
    public static int getClosestPowersHigh(int reported) {
        // return the next highest power from the minimum reported
        // 512, 1024, 2048, 4096, 8192, 16384
        for (int power : POWERS_TWO_HIGH) {
            if (reported <= power) {
                return power;
            }
        }
        // didn't find power, return reported
        return reported;
    }

    public static int getClosestPowersLow(int reported) {
        // return the next highest power from the minimum reported
        // 2, 4, 8, 16, 32, 64, 128, 256
        for (int power : POWERS_TWO_LOW) {
            if (reported <= power) {
                return power;
            }
        }
        // didn't find power, return reported
        return reported;
    }

    public static byte[] toBytes(short s) {
        // convert shorts to bytes
        // Java short is a 16-bit type, and byte is an 8-bit type.
        return new byte[]{(byte)(s & 0x00FF),(byte)((s & 0xFF00)>>8)};
    }

    public static byte[] shortToByte(short[] arr) {
        ByteBuffer bb = ByteBuffer.allocate(arr.length * 2);
        bb.asShortBuffer().put(arr);
        return bb.array();
    }

	public double soundPressureLevel(final float[] buffer) {
		double power = 0.0D;
		for (float element : buffer) {
			power += element * element;
		}
		double value = Math.pow(power, 0.5) / buffer.length;
		return 20.0 * Math.log10(value);
	}

}

