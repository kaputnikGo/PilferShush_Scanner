package pilfershush.cityfreqs.com.pilfershush;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class AudioVisualiserView extends View {
    private byte[] mBytes;
    private float[] mPoints;
    private Bitmap mCanvasBitmap;
    private Canvas mCanvas;
    private Matrix mMatrix;
    private Rect mRect = new Rect();
    private int rectMidHeight;
    private Paint mForePaint = new Paint();
    private Paint mPeakPaint = new Paint();
    private Paint mCautionPaint = new Paint();
    private float amplitudeColour;
    private float accumulator;
    private float amp;
    private float[] cautionLines;
    private int lineCounter;
    private int lineSpacer;
    private float freqValue;

    private static final int MULTIPLIER = 4; // 4
    private static final int RANGE = 256; // 128, 256
    private static final int CAUTION_LINE_WIDTH = 6;
    private static final int CAUTION_LINE_MEDIAN = 90;
    private static final int CAUTION_MAX = 128;// needs to hold 32 bit binMod

    public AudioVisualiserView(Context context) {
        super(context);
        init();
    }
    public AudioVisualiserView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public AudioVisualiserView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBytes = null;
        mForePaint.setStrokeWidth(1f);
        mForePaint.setAntiAlias(true);
        mForePaint.setColor(Color.rgb(128, 166, 206));
        mPeakPaint.setColor(Color.rgb(0, 0, 255));
        amplitudeColour = 0;
        accumulator = 0;
        amp = 0;

        mCautionPaint.setColor(Color.argb(238, 255, 0, 0));
        mCautionPaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
        mCautionPaint.setStrokeWidth(CAUTION_LINE_WIDTH);
        mMatrix = new Matrix();
        rectMidHeight = 0;

        cautionLines = new float[CAUTION_MAX];
        cautionLines[0] = 0;
        cautionLines[1] = 0;
        cautionLines[2] = 0;
        cautionLines[3] = 0;

        lineCounter = 0;
        lineSpacer = 0;
        freqValue = 0;
    }

    public void updateVisualiser(short[] shortBuffer) {
        mBytes = new byte[shortBuffer.length];
        for (int i = 0; i < shortBuffer.length; i++) {
            mBytes[i] = (byte)shortBuffer[i];
        }
        invalidate();
    }

    public void frequencyCaution(int frequency) {
        // flash something or other
        // canvas.drawLine(line.startX, line.startY, line.stopX, line.stopY, paint);
        lineSpacer = RANGE - (lineCounter * (CAUTION_LINE_WIDTH + 2));
        if (lineSpacer <= 0) lineSpacer = 0;

        freqValue = frequency - AudioSettings.DEFAULT_FREQUENCY_MIN;
        freqValue *= 0.03f;
        if (freqValue > CAUTION_LINE_MEDIAN) freqValue = CAUTION_LINE_MEDIAN;

        if (mRect != null) {
            if (lineCounter * MULTIPLIER >= CAUTION_MAX) return;

            cautionLines[lineCounter * MULTIPLIER] = mRect.width() - lineSpacer;
            cautionLines[lineCounter * MULTIPLIER + 1] = rectMidHeight - CAUTION_LINE_MEDIAN - freqValue;
            cautionLines[lineCounter * MULTIPLIER + 2] = mRect.width() - lineSpacer;
            cautionLines[lineCounter * MULTIPLIER + 3] = rectMidHeight + CAUTION_LINE_MEDIAN + freqValue;
            lineCounter++;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBytes == null) {
            MainActivity.logger("no bytes to draw");
            return;
        }
        if (mPoints == null || mPoints.length < mBytes.length * MULTIPLIER) {
            mPoints = new float[mBytes.length * MULTIPLIER];
        }
        mRect.set(0, 0, getWidth(), getHeight());
        accumulator = 0;

        rectMidHeight = mRect.height() / 2;

        for (int i = 0; i < mBytes.length - 1; i++) {
            accumulator += Math.abs(mBytes[i]);
            mPoints[i * MULTIPLIER] = mRect.width() * i / (mBytes.length - 1);
            mPoints[i * MULTIPLIER + 1] = rectMidHeight + ((byte)(mBytes[i] + RANGE)) * (rectMidHeight) / RANGE;
            mPoints[i * MULTIPLIER + 2] = mRect.width() * (i + 1) / (mBytes.length - 1);
            mPoints[i * MULTIPLIER + 3] = rectMidHeight + ((byte)(mBytes[i + 1] + RANGE)) * (rectMidHeight) / RANGE;
        }
        if (mCanvasBitmap == null) {
            mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
        }
        if (mCanvas == null) {
            mCanvas = new Canvas(mCanvasBitmap);
        }
        //clear previous drawings
        mCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
        // get amp for this waveform
        amp = accumulator / (RANGE * mBytes.length);
        if (amp > amplitudeColour) {
            // increase amp colouring
            amplitudeColour = amp;
            mCanvas.drawLines(mPoints, mPeakPaint);
        }
        else {
            // reduce amp colouring
            amplitudeColour *= 0.99;
            mCanvas.drawLines(mPoints, mForePaint);
        }
        canvas.drawBitmap(mCanvasBitmap, mMatrix, null);
        canvas.drawLines(cautionLines, mCautionPaint);
    }
}

