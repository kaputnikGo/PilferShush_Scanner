package pilfershush.cityfreqs.com.pilfershush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;

import pilfershush.cityfreqs.com.pilfershush.assist.AudioSettings;

public class AudioVisualiserView extends View {
    private Rect mRect = new Rect();
    private int rectMidHeight;
    private Paint mForePaint = new Paint();
    private Paint mCautionPaint = new Paint();
    private float[] cautionLines;
    private int lineCounter;
    private int lineSpacer;
    private float freqValue;

    private static final int MULTIPLIER = 4;
    private static final int RANGE = 256;
    private static final int CAUTION_LINE_WIDTH = 6;
    private static final int CAUTION_LINE_MEDIAN = 90;
    private static final int CAUTION_MAX = 128;

    //
    private static final int HISTORY_SIZE = 6;
    private static final float MAX_AMPLITUDE_TO_DRAW = 8192.0f;
    private final LinkedList<short[]> mAudioData = new LinkedList<short[]>();
    private int colourDelta;
    private int brightness;

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
        colourDelta = 0;
        brightness = 0;
        mForePaint.setStyle(Paint.Style.STROKE);
        mForePaint.setStrokeWidth(6f);
        mForePaint.setAntiAlias(false);
        mForePaint.setColor(Color.argb(brightness,128, 166, 206));

        mCautionPaint.setColor(Color.argb(238, 255, 0, 0));
        mCautionPaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
        mCautionPaint.setStrokeWidth(CAUTION_LINE_WIDTH);

        rectMidHeight = 0;
        lineCounter = 0;
        lineSpacer = 0;
        freqValue = 0;
        clearFrequencyCaution();
    }

    public synchronized void updateVisualiser(short[] buffer) {
        short[] newBuffer;
        mRect.set(0, 0, getWidth(), getHeight());
        rectMidHeight = mRect.height() / 2;

        // keep a history for fading effect.
        if (mAudioData.size() == HISTORY_SIZE) {
            newBuffer = mAudioData.removeFirst();
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        } else {
            newBuffer = buffer.clone();
        }
        mAudioData.addLast(newBuffer);
        // Update the display.
        invalidate();
    }

    public void frequencyCaution(int frequency) {
        // canvas.drawLine(line.startX, line.startY, line.stopX, line.stopY, paint);
        //TODO make a peak ghost line that remains, fade out previous caution lines
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

    public void clearFrequencyCaution() {
        cautionLines = new float[CAUTION_MAX];
        cautionLines[0] = 0;
        cautionLines[1] = 0;
        cautionLines[2] = 0;
        cautionLines[3] = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        colourDelta = 255 / (HISTORY_SIZE + 1);
        brightness = colourDelta;

        for (short[] buffer : mAudioData) {
            mForePaint.setColor(Color.argb(brightness,128, 166, 206));
            float lastX = -1;
            float lastY = -1;
            float centerY = getHeight() / 2;

            // only draw lines that align with pixel boundaries.
            for (int x = 0; x < getWidth(); x++) {
                int index = (int) ((x / getWidth()) * buffer.length);
                short sample = buffer[index];
                float y = (sample / MAX_AMPLITUDE_TO_DRAW) * centerY + centerY;

                if (lastX != -1) {
                    canvas.drawLine(lastX, lastY, x, y, mForePaint);
                }
                lastX = x;
                lastY = y;
            }
            brightness += colourDelta;
        }
        // any caution lines draw here
        canvas.drawLines(cautionLines, mCautionPaint);
    }
}

