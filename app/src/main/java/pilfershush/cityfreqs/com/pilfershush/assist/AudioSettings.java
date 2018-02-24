package pilfershush.cityfreqs.com.pilfershush.assist;

import android.media.AudioFormat;

import java.nio.ByteBuffer;

public class AudioSettings {
    // helper vars and defaults
    // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
    public static final int[] SAMPLE_RATES = new int[] {
            48000, 44100, 22050, 16000, 11025, 8000 };

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

    // below, rem'd, are way too sensitive = false positives
    // public static final double MAGNITUDE_50 = 500; // ~= 53.9794 dB
    // public static final double MAGNITUDE_70 = 3000; // ~= 69.5425 dB

    public static final double[] MAGNITUDES = new double[] {
            10000, 30000, 50000, 80000
    };
    public static final int DEFAULT_MAGNITUDE = 80000; // was 50000
    // using dB SPL (sound pressure level) without distance
    // db = 20 log10(goertzel_magnitude).
    // 50000 ~= 93.9794
    // 80000 ~= 98.0618
    public static final int[] DECIBELS = new int[] {
            80, 89, 93, 98
    };

    // steps in the frequencies to consider as a coded signal and not noise
    // public static final int FREQ_STEP_5 = 5;
    public static final int[] FREQ_STEPS = new int[] {
            10, 25, 50, 75, 100
    };
    public static final int DEFAULT_FREQ_STEP = 25;

    // possible number of audio signals to sequence (32 bits)
    //public static final int MAX_SEQUENCE_LENGTH = 32;

    // 1=Hann(ing), 2=Blackman, 3=Hamming, 4=Nuttall, 5=Blackman-Nuttall
    public static final String[] FFT_WINDOWS = new String[] {
            "Hann", "Blackman", "Hamming", "Nuttall", "Blackman-Nuttall"
    };

    public static final int DEFAULT_WINDOW_TYPE = 2;

    //public static final int MICRO_DELAY = 1; // for modulated code
    public static final int[] POLLING_DELAY = new int[] {
            1000, 2000, 3000, 4000, 5000, 6000
    };

    // vars for AudioRecord creation and use
    private int sampleRate;
    private int bufferSize; // in bytes
    private int encoding;
    private int channelConfig;
    private int channelCount;
    private int audioSource;

    private int minFreq;
    private int maxFreq;
    private int freqStep;

    private boolean writeFiles;
    private int USER_WINDOW_TYPE = DEFAULT_WINDOW_TYPE;

    public AudioSettings(boolean writeFiles) {
        // convenience class to hold values for audio recording
        // and useful vars for audio processing
        this.writeFiles = writeFiles;
        minFreq = DEFAULT_FREQUENCY_MIN;
        maxFreq = DEFAULT_FREQUENCY_MAX;
        freqStep = DEFAULT_FREQ_STEP;
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

    public void setFreqStep(int freqStep) {
        this.freqStep = freqStep;
    }
    public int getFreqStep() {
        return freqStep;
    }

    public void setWriteFiles(boolean writeFiles) {
        this.writeFiles = writeFiles;
    }
    public boolean getWriteFiles() {
        return writeFiles;
    }

    public void setUserWindowType(int userWindow) {
        USER_WINDOW_TYPE = userWindow;
    }
    public int getUserWindowType() {
        return USER_WINDOW_TYPE;
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

