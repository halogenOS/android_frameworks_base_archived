/*
 * Copyright (C) 2016 halogenOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.systemui.statusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

public class VisualizerViewWrapper {
    
    private static final boolean DEBUG = false;
    
    static final class StateHolder {
        public boolean
                mVisible            = false,
                mPlayingIndependent = false,
                mPlaying            = false,
                mPowerSaveMode      = false,
                mDisplaying         = false,
                mOccluded           = false,
                mScreenOn           = false,
                mScreenOnInternal   = false,
                mKeyguardVisible    = false,
                mHaveInstance       = false;
        
        public int mColor = Color.TRANSPARENT;
        
        public Bitmap mCurrentBitmap;
        
        public StateHolder() {
            
        }
    }
    
    public VisualizerView visualizerView;
    private Context context;
    private ViewGroup parent;
    private StateHolder state = new StateHolder();
    
    public VisualizerViewWrapper(Context context, ViewGroup parent) {
        this.context = context;
        this.parent  = parent;
    }
    
    private static void log(String... msg) {
        if (DEBUG) for(String s : msg) Log.d("VisualizerView", "[W] " + s);
    }
    
    public synchronized void vanish() {
        log("vanish");
        if(!isNull()) {
            visualizerView.setVisible(false);
            if(isAttachedToWindow())
                ((ViewGroup)visualizerView.getParent()).removeView(visualizerView);
            visualizerView.destroy();
            visualizerView = null;
            state.mDisplaying = false;
            state.mHaveInstance = false;
        }
    }
    
    private boolean isAttachedToWindow() {
        return visualizerView.getWindowToken() != null;
    }
    
    private boolean isNull() {
        if(DEBUG) log("visualizerView is " + 
            (visualizerView != null ? "not " : "") + "null");
        return visualizerView == null;
    }
    
    public synchronized void setPlaying(boolean playing) {
        log("setPlaying=" + playing);
        state.mPlayingIndependent = playing;
        state.mPlaying = playing;
        checkState();
    }
    
    private synchronized void setScreenOn(boolean screenOn) {
        log("setScreenOn=" + screenOn);
        state.mScreenOn = screenOn;
        state.mScreenOnInternal = screenOn;
    }
    
    public final boolean isScreenOn() {
        return state.mScreenOnInternal;
    }
    
    public synchronized void setKeyguardShowing(boolean showing) {
        log("setKeyguardShowing=" + showing);
        state.mKeyguardVisible = showing;
        checkState();
    }
    
    private synchronized void setVisible(boolean visible) {
        log("setVisible=" + visible);
        state.mVisible = visible;
    }
    
    public synchronized void setBitmap(Bitmap bitmap) {
        log("setBitmap=[not null: " + (bitmap != null) + "]");
        if(!isNull()) visualizerView.setBitmap(bitmap);
        else state.mCurrentBitmap = bitmap;
    }
    
    private synchronized void ready() {
        log("Getting ready...");
        if(isNull() && !state.mHaveInstance) {
            visualizerView = new VisualizerView(context);
            visualizerView.ready(state);
            state.mHaveInstance = true;
        }
        log("Ready.");
    }
    
    private synchronized void prepare() {
        log("prepare");
        state.mScreenOn = state.mScreenOnInternal;
        state.mPlaying = state.mPlayingIndependent;
        ready();
    }
    
    public synchronized void checkState() {
        if(!state.mHaveInstance && state.mScreenOnInternal
            && state.mKeyguardVisible && state.mPlaying) {
            if(DEBUG) {
                log("Current color: " + state.mColor);
                log("Is bitmap null: " + (state.mCurrentBitmap == null));
            }
            prepare();
            if(!isNull() && !isAttachedToWindow()) {
                log("Adding to lockscreen");
                parent.addView(visualizerView);
                for(int i = 0; i < parent.getChildCount(); i++)
                    if(parent.getChildAt(i) != visualizerView)
                        parent.getChildAt(i).bringToFront();
                visualizerView.setTranslationZ(-2);
            }
            state.mVisible = true;
            visualizerView.refreshColor();
            visualizerView.checkStateChanged();
        } else if(state.mHaveInstance && (
            !state.mScreenOnInternal ||
            !state.mKeyguardVisible)) {
            vanish();
        } else if(state.mHaveInstance && state.mScreenOnInternal &&
                    state.mKeyguardVisible) {
            visualizerView.checkStateChanged();
        }
    }
    
    public synchronized void onScreenOn() {
        setScreenOn(true);
        checkState();
    }
    
    public synchronized void onScreenOff() {
        setScreenOn(false);
        checkState();
    }
    
}