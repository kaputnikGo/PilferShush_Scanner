package pilfershush.cityfreqs.com.pilfershush.scanners;
/*

test for quicker/efficient AudioRecord methods,
    form of hpf,
    and magnitude scanner

taken from:
https://github.com/billthefarmer/scope/blob/master/src/main/java/org/billthefarmer/scope/SpectrumActivity.java
Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.

 */

import java.util.ArrayList;
import java.util.HashMap;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;
import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class SpectrumAudio {
    private static final String TAG = "SpecAudio";
    private static final int OVERSAMPLE = 4;
    private static final double MIN = 0.5;
    private static final int FIXED_BUFFER_SIZE = 4096;
    // Data
    protected double frequency;
    protected double fps;

    private int samples;// = 4096;
    private int range;// = SAMPLES / 2;
    private int step;// = SAMPLES / OVERSAMPLE;
    private double expect;// = 2.0 * Math.PI * step / SAMPLES;

    private int counter;
    private ArrayList<Integer> freqList;
    private HashMap<Integer, Integer> freqMap;

    private double buffer[];
    private double xr[];
    private double xi[];
    protected double xa[];
    private double xp[];
    private double xf[];

    protected SpectrumAudio(int minBuffer) {
        if (minBuffer != FIXED_BUFFER_SIZE) {
            // sam5 minbuffer is 3840, not a powers of two...
            // not all phones, roms etc will have 4096 as minBufferSize, or powers of two,
            // but 'should' have divisible by 8
            MainActivity.logger(TAG + " minBuffer error, found: " + minBuffer);
            samples = AudioSettings.getClosestPowersHigh(minBuffer);
            MainActivity.logger(TAG + "try closest PowersOfTwo: " + samples);
        }
        else {
            samples = minBuffer;
        }
        range = samples / 2;
        step = samples / OVERSAMPLE;
        expect = 2.0 * Math.PI * step / samples;

        buffer = new double[samples];

        xr = new double[samples];
        xi = new double[samples];

        xa = new double[range];
        xp = new double[range];
        xf = new double[range];
    }

    protected void initSpectrumAudio(int sampleRate) {
        freqList = new ArrayList<Integer>();
        freqMap = new HashMap<Integer, Integer>();
        // Calculate fps
        fps = (double) sampleRate / samples;
        counter = 0;
    }

    protected void finishSpectrumAudio() {
        MainActivity.logger(TAG + " freq count: " + counter);
        //MainActivity.logger(String.format("spectrumAudio freq: %1.1f Hz", frequency));
        if (counter > 0) {
           //assume
            sortFreqMap();
        }
    }

    protected void processSpectrumAudio(short[] data, int bufferRead) {
        // pass the data short buffer in to this, check for freqs above
        // AudioSettings.DEFAULT_FREQUENCY_MIN;
        double dmax = 0.0;
        double norm;
        double window;
        double real;
        double imag;
        double p;
        double dp;
        double df;
        double max;
        double level;
        double dB; // using dBFS(full scale) where 0dB is peak amplitude

        if (data != null) {
            System.arraycopy(buffer, step, buffer, 0, bufferRead - step);

            for (int i = 0; i < step; i++) {
                buffer[(bufferRead - step) + i] = data[i];
            }

            //if (dmax < bufferRead) //4096.0
            //    dmax = bufferRead; //4096.0

            norm = dmax;
            dmax = 0.0;

            for (int i = 0; i < bufferRead; i++) {
                // Find the magnitude
                if (dmax < Math.abs(buffer[i])) {
                    dmax = Math.abs(buffer[i]);
                }

                // uses a Hann(ing) window
                window = 0.5 - 0.5 * Math.cos(AudioSettings.PI2 * i / bufferRead);

                // Normalise and window the input data
                xr[i] = buffer[i] / norm * window;
            }

            fftr(xr, xi);
            // Process FFT output
            for (int i = 1; i < range; i++) {
                real = xr[i];
                imag = xi[i];

                xa[i] = Math.hypot(real, imag);

                // Do frequency calculation
                p = Math.atan2(imag, real);
                dp = xp[i] - p;

                xp[i] = p;

                // Calculate phase difference
                dp -= i * expect;

                int qpd = (int)(dp / Math.PI);

                if (qpd >= 0) {
                    qpd += qpd & 1;
                }
                else {
                    qpd -= qpd & 1;
                }
                dp -=  Math.PI * qpd;

                // Calculate frequency difference
                df = OVERSAMPLE * dp / AudioSettings.PI2;

                // Calculate actual frequency from slot frequency plus
                // frequency difference and correction value
                xf[i] = i * fps + df * fps;
            }

            // Maximum FFT output
            max = 0.0;
            // Find maximum value
            for (int i = 1; i < range; i++) {
                if (xa[i] > max) {
                    max = xa[i];
                    frequency = xf[i];
                }
            }

            level = 0.0;

            for (int i = 0; i < step; i++) {
                // 0x7FFF == 32767.0 or 32768.0
                level += ((double) data[i] / 32768.0) * ((double) data[i] / 32768.0);
            }

            // add a magnitude check here before proceeding
            // level is a negative number rising to zero
            level = Math.sqrt(level / step) * 2.0;

            dB = Math.log10(level) * 20.0;

            if (dB < -80.0) {
                dB = -80.0;
            }

            // check frequency and dB
            // need a dB sensitivity switch here
            // tests get ~ -40dB to -30dB (internal) as a range

            if (max > MIN) {
                // check frequency (%1.1fHz)
                // check its level over threshold
                // produces variations, so may need to group within range then count
                if (frequency >= AudioSettings.DEFAULT_FREQUENCY_MIN) {
                    MainActivity.logger(String.format(TAG, " freq: %1.1f Hz", frequency));
                    //MainActivity.logger("dB: " + dB + ", level: " + level);
                    freqList.add(Integer.valueOf((int) Math.round(frequency)));
                    counter++;
                }
            }
            else {
                frequency = 0.0;
            }
        }
        // end of process
    }

    // Real to complex FFT, ignores imaginary values in input array
    private void fftr(double ar[], double ai[]) {
        final int n = ar.length;
        final double norm = Math.sqrt(1.0 / n);

        for (int i = 0, j = 0; i < n; i++) {

            if (j >= i) {
                double tr = ar[j] * norm;
                ar[j] = ar[i] * norm;
                ai[j] = 0.0;
                ar[i] = tr;
                ai[i] = 0.0;
            }

            int m = n / 2;
            while (m >= 1 && j >= m) {
                j -= m;
                m /= 2;
            }
            j += m;
        }

        for (int mmax = 1, istep = 2 * mmax; mmax < n; mmax = istep, istep = 2 * mmax) {
            double delta = Math.PI / mmax;

            for (int m = 0; m < mmax; m++) {
                double w = m * delta;
                double wr = Math.cos(w);
                double wi = Math.sin(w);

                for (int i = m; i < n; i += istep) {
                    int j = i + mmax;
                    double tr = wr * ar[j] - wi * ai[j];
                    double ti = wr * ai[j] + wi * ar[j];
                    ar[j] = ar[i] - tr;
                    ai[j] = ai[i] - ti;
                    ar[i] += tr;
                    ai[i] += ti;
                }
            }
        }
    }

    // this is duping AudioScanner functions from first scanner type.
    private void sortFreqMap() {
        // SparseIntArray is suggested...
        // this only counts, order of occurrence is not preserved.

        for (int freq : freqList) {
            if (freqMap.containsKey(freq)) {
                freqMap.put(freq, freqMap.get(freq) + 1);
            }
            else {
                freqMap.put(freq, 1);
            }
        }
    }
}
