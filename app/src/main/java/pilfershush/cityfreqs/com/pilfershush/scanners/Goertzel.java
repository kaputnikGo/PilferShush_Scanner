package pilfershush.cityfreqs.com.pilfershush.scanners;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class Goertzel {
    private float sampleRate;
    private float targetFreq;
    private int length;
    private double[] data;
    private double coeff;
    private double Q1;
    private double Q2;
    private double sine;
    private double cosine;

    public Goertzel(float sampleRate, float targetFreq, double[] data) {
        this.sampleRate = sampleRate;
        this.targetFreq = targetFreq;
        this.data = data;
        length = data.length;
        sine = 0.14904226617617444d;
        cosine = -0.9888308262251285d;
        coeff = 2.0d * cosine;
    }

    private void resetGoertzel() {
        Q2 = 0.0d;
        Q1 = 0.0d;
    }

    public void initGoertzel() {
        float f = (float)length;
        double omega = (AudioSettings.PI2 * ((double) ((int) (0.5d + ((double) ((targetFreq * f) / sampleRate)))))) / ((double) f);
        sine = Math.sin(omega);
        cosine = Math.cos(omega);
        coeff = 2.0d * cosine;
        resetGoertzel();
    }

    private void processSample(double sample) {
        double Q0 = ((coeff * Q1)- Q2) + sample;
        Q2 = Q1;
        Q1 = Q0;
    }

    private double[] getRealImag(double[] dArr) {
        dArr[0] = Q1 - (Q2 * cosine);
        dArr[1] = Q2 * sine;
        return dArr;
    }

    private double getMagnitudeSquared() {
        return ((Q1 * Q1) + (Q2 * Q2) - (Q1 * Q2) * coeff);
    }

    /********************************************************************/

    // not used
    public double getMagnitude() {
        double[] dArr = new double[2];
        double magnitudeSquared;

        for (int i = 0; i < length; i++) {
            processSample(data[i]);
        }

        double[] parts = getRealImag(dArr);
        double real = parts[0];
        double imag = parts[1];

        magnitudeSquared = Math.sqrt((imag * imag) + (real * real));
        resetGoertzel();
        return magnitudeSquared;
    }

    // used
    public double getOptimisedMagnitude() {
        for (int i = 0; i < length; i++) {
            processSample(data[i]);
        }
        double sqrt = Math.sqrt(getMagnitudeSquared());
        resetGoertzel();
        return sqrt;
    }
}



