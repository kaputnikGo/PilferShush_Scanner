package cityfreqs.com.pilfershush.assist;

public class AudioSettings {
    // helper vars and defaults
    // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
    public static final int[] SAMPLE_RATES = new int[] {
            48000, 44100, 22050, 16000, 11025, 8000 };

    public static final int[] POWERS_TWO_HIGH = new int[] {
            512, 1024, 2048, 4096, 8192, 16384 };

    /*
    public static final int[] POWERS_TWO_LOW = new int[] {
            2, 4, 8, 16, 32, 64, 128, 256 };
    */

    public static final double PI2 = 6.283185307179586;
    public static final double PI4 = 12.566370614359172;
    public static final double PI6 = 18.84955592153876d;

    // max frequency range is 3000hz (between min and max)
    public static final int DEFAULT_FREQUENCY_MIN = 18000;
    public static final int DEFAULT_FREQUENCY_MAX = 21000;
    public static final int SECOND_FREQUENCY_MIN = 19000;
    public static final int SECOND_FREQUENCY_MAX = 22000;

    // below are added but are very sensitive
    // public static final double MAGNITUDE_50 = 500; // ~= 53.9794 dB
    // public static final double MAGNITUDE_60 = 1200; // ~= 61.5836 dB
    // public static final double MAGNITUDE_70 = 3000; // ~= 69.5425 dB

    public static final double[] MAGNITUDES = new double[] {
            500, 1200, 3000, 10000, 30000, 50000, 80000
    };
    public static final int DEFAULT_MAGNITUDE = 50000; // was 80000
    // using dB SPL (sound pressure level) without distance
    // db = 20 log10(goertzel_magnitude).
    // 50000 ~= 93.9794
    // 80000 ~= 98.0618
    public static final int[] DECIBELS = new int[] {
            50, 60, 70, 80, 89, 93, 98
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
    /*
    public static final int[] POLLING_DELAY = new int[] {
            1000, 2000, 3000, 4000, 5000, 6000
    };
    */

    //public static final int CARRIER_NUHF_FREQUENCY = 21000;
    //public static final int MAXIMUM_NUHF_FREQUENCY = 24000;
    public static final int MINIMUM_NUHF_FREQUENCY = 18000;
    public static final int DEFAULT_NUHF_FREQUENCY = 19000;

    public static final int CARRIER_TEST_FREQUENCY = 440;
    public static final int MAXIMUM_TEST_FREQUENCY = CARRIER_TEST_FREQUENCY + (int)(CARRIER_TEST_FREQUENCY * 0.5);
    public static final int MINIMUM_TEST_FREQUENCY = CARRIER_TEST_FREQUENCY - (int)(CARRIER_TEST_FREQUENCY * 0.5);

    public static final int DEFAULT_RANGE_DRIFT_LIMIT = 1000;
    //public static final int DEFAULT_DRIFT_SPEED = 1000;
    public static final int MINIMUM_DRIFT_LIMIT = 10;
    public static final int DRIFT_SPEED_MULTIPLIER = 1000;

    public static final int JAMMER_TONE = 0;
    public static final int JAMMER_WHITE = 1;

    public static final int JAMMER_TYPE_TEST = 0; // only test tone drift
    public static final int JAMMER_TYPE_NUHF = 1;
    public static final int JAMMER_TYPE_DEFAULT_RANGED = 2;
    public static final int JAMMER_TYPE_USER_RANGED = 3;

    // Bundle keys string names
    public static final String[] AUDIO_BUNDLE_KEYS = new String[] {
            "audioSource", "sampleRate", "channelInConfig", "encoding", //3
            "bufferInSize", "channelOutConfig", "bufferOutSize", "activeType", //7
            "jammerType", "userCarrier", "userLimit", "userSpeed", "hasEQ", //12
            "maxFreq", "scanMinFreq", "scanMaxFreq", "scanFreqStep",  //16
            "ScanMagnitude", "scanWindow", "writeFiles", "bitDepth" //20
    };

/*
 * Utilities, that may be useful...
 *
 */

    /*
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
	*/

}

