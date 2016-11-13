/*
* Copyright (C) 2015 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.systemui.statusbar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class VisualizerView extends View implements Palette.PaletteAsyncListener {

    private static final String TAG = VisualizerView.class.getSimpleName();
    private static final boolean DEBUG = false;

    public boolean isVisualizerEnabled = true;

    private Paint mPaint;
    private Visualizer mVisualizer;
    private ObjectAnimator mVisualizerColorAnimator;

    private ValueAnimator[] mValueAnimators;
    private float[] mFFTPoints;

    private int mStatusBarState;
    protected boolean mVisualizerEnabled = true;
    protected boolean mVisible = false;
    protected boolean mPlaying = false;
    protected boolean mPowerSaveMode = false;
    protected boolean mDisplaying = false; // the state we're animating to
    protected boolean mDozing = false;
    protected boolean mOccluded = false;
    protected boolean mScreenOn = false;
    protected boolean mAlive = true;

    private int mColor;
    private int[] colorToFill;
    private Bitmap mCurrentBitmap;
    
    private Boolean calculatorLock = false;

    private Visualizer.OnDataCaptureListener mVisualizerListener =
            new Visualizer.OnDataCaptureListener() {
        byte rfk, ifk;
        int dbValue;
        float magnitude;

        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
            
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            if(!mAlive || mFFTPoints == null) return;
            if(calculatorLock) return;
            synchronized(calculatorLock) {
                calculatorLock = true;
                for (int i = 0; i < 32; i++) {
                    mValueAnimators[i].cancel();
                    rfk = fft[i * 2 + 2];
                    ifk = fft[i * 2 + 3];
                    magnitude = rfk * rfk + ifk * ifk;
                    dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;
    
                    mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                            mFFTPoints[3] - (dbValue * 16f));
                    mValueAnimators[i].setDuration(92);
                    mValueAnimators[i].start();
                }
                calculatorLock = false;
            }
        }
    };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "screenOn: " + mScreenOn + 
                    " displaying: " + mDisplaying + " visible: " + mVisible);
                Log.d(TAG, "+++ mLinkVisualizer run()");
            }

            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                Log.e(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(68);
            mVisualizer.setDataCaptureListener(mVisualizerListener, Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);

            if (DEBUG) {
                Log.d(TAG, "--- mLinkVisualizer run()");
            }
        }
    };

    private final Runnable mAsyncUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            AsyncTask.execute(mUnlinkVisualizer);
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.w(TAG, "+++ mUnlinkVisualizer run(), mVisualizer: " + mVisualizer);
            }
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }
            if (DEBUG) {
                Log.w(TAG, "--- mUninkVisualizer run()");
            }
        }
    };

    public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public void ready() {
        if(!mAlive) return;
        mColor = Color.WHITE;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);

        mFFTPoints = new float[128];
        mValueAnimators = new ValueAnimator[32];
        for (int i = 0; i < 32; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(92);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if(mFFTPoints == null) return;
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                    postInvalidate();
                }
            });
        }
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mCurrentBitmap = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        if(!mAlive) return;
        if(mPaint == null) return;
        
        float barUnit = w / 32f;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * 32f / 31f;
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = h;
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!mAlive) return;
        
        if (mVisualizer != null) {
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    public void setVisible(boolean visible) {
        if(!mAlive) return;
        if (mVisible != visible) {
            if (DEBUG) {
                Log.i(TAG, "setVisible() called with visible = [" + visible + "]");
            }
            mVisible = visible && mScreenOn && mPlaying;
            if(mScreenOn) checkStateChanged();
        }
    }

    public void setDozing(boolean dozing) {
        if(!mAlive) return;
        if (mDozing != dozing) {
            if (DEBUG) {
                Log.i(TAG, "setDozing() called with dozing = [" + dozing + "]");
            }
            mDozing = dozing;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if(!mAlive) return;
        if (mPlaying != playing) {
            if (DEBUG) {
                Log.i(TAG, "setPlaying() called with playing = [" + playing + "]");
            }
            mPlaying = playing && mScreenOn;
            checkStateChanged();
        }
    }

    public void setPowerSaveMode(boolean powerSaveMode) {
        if(!mAlive) return;
        if (mPowerSaveMode != powerSaveMode) {
            if (DEBUG) {
                Log.i(TAG, "setPowerSaveMode() called with powerSaveMode = [" + powerSaveMode + "]");
            }
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    public void setOccluded(boolean occluded) {
        if(!mAlive) return;
        if (mOccluded != occluded) {
            if (DEBUG) {
                Log.i(TAG, "setOccluded() called with occluded = [" + occluded + "]");
            }
            mOccluded = occluded;
            checkStateChanged();
        }
    }
    
    public void setScreenOn(boolean screenOn) {
        if(screenOn) mDisplaying = false;
        mScreenOn = screenOn;
        checkStateChanged();
    }
    
    public void refreshColor() {
        if(mCurrentBitmap != null)
            Palette.generateAsync(mCurrentBitmap, this);
    }

    public void setBitmap(Bitmap bitmap) {
        if(!mAlive) return;
        if (mCurrentBitmap == bitmap) {
            return;
        }
        mCurrentBitmap = bitmap;
        if (bitmap != null) {
            Palette.generateAsync(bitmap, this);
        } else {
            setColor(Color.WHITE);
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        if(!mAlive) return;
        int color = Color.TRANSPARENT;

        color = palette.getVibrantColor(color);
        if (color == Color.TRANSPARENT) {
            color = palette.getLightVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
            }
        }

        setColor(color);
    }

    protected void setColor(int color) {
        if(!mAlive) return;
        if (color == Color.TRANSPARENT) {
            color = Color.WHITE;
        }

        color = Color.argb(138, Color.red(color), Color.green(color), Color.blue(color));

        if (mColor != color) {
            mColor = color;

            if (mVisualizer != null) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(420);
                mVisualizerColorAnimator.setDuration(1080);
                mVisualizerColorAnimator.start();
            } else {
                mPaint.setColor(mColor);
            }
        }
    }

    protected void checkStateChanged() {
        if(!mAlive) return;
        if(!isVisualizerEnabled) {
            if(DEBUG) Log.d(TAG, "Visualizer not enabled!");
            mVisualizerEnabled = false;
            mVisible = false;
            mPlaying = false;
            mPowerSaveMode = false;
            mDisplaying = false;
            mDozing = false;
            mOccluded = false;
            return;
        }
        if(DEBUG)
            Log.d(TAG,
                "mVisible: " + mVisible + " mPlaying: " + mPlaying + " " +
                "mDozing:  " + mDozing  + " mPowerSaveMode: " + mPowerSaveMode + " " +
                "mVisualizerEnabled: "  + mVisualizerEnabled + " " +
                "mOccluded: " + mOccluded + " mScreenOn: " + mScreenOn + " mDisplaying: " + mDisplaying + " " +
                "visible:  " + (getVisibility() == View.VISIBLE)
            );
        if (getVisibility() == View.VISIBLE && mScreenOn && mVisible && mPlaying && !mDozing && !mPowerSaveMode
                && mVisualizerEnabled && !mOccluded) {
            if(DEBUG) Log.d(TAG, "We are good!");
            if (!mDisplaying) {
                if(DEBUG) Log.d(TAG, "Setting visualizer on fire!");
                mDisplaying = true;
                AsyncTask.execute(mLinkVisualizer);
                animate()
                        .alpha(1f)
                        .withEndAction(null)
                        .setDuration(720);
            }
        } else {
            if (mDisplaying) {
                if(DEBUG) Log.d(TAG, "Getting rid of visualizer");
                mDisplaying = false;
                if (mVisible) {
                    animate()
                            .alpha(0f)
                            .withEndAction(mAsyncUnlinkVisualizer)
                            .setDuration(540);
                } else {
                    animate().
                            alpha(0f)
                            .withEndAction(mAsyncUnlinkVisualizer)
                            .setDuration(0);
                }
            }
        }
    }
    
    public void destroy() {
        if(DEBUG) Log.d(TAG, "DESTROY");
        mAlive = false;
        mVisualizerEnabled = false;
        mVisible = false;
        mPlaying = false;
        mPowerSaveMode = false;
        mDisplaying = false;
        mDozing = false;
        mOccluded = false;
        mPaint = null;
        if(mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }
        mVisualizerColorAnimator = null;
        mValueAnimators = null;
        mFFTPoints = null;
        mStatusBarState = 0;
        mColor = 0;
        mCurrentBitmap = null;
        mVisualizerListener = null;
    }

}
