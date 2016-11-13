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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

public class VisualizerViewWrapper {
    
    private static final boolean DEBUG = false;
    
    public VisualizerView visualizerView;
    private Context context;
    private ViewGroup parent;
    
    private boolean mScreenOn;
    private boolean mPlaying;
    
    public VisualizerViewWrapper(Context context, ViewGroup parent) {
        this.context = context;
        this.parent  = parent;
    }
    
    private static void log(String... msg) {
        if (DEBUG) for(String s : msg) Log.d("VisualizerView", s);
    }
    
    public synchronized void vanish() {
        log("[W] vanish");
        if(!isNull()) {
            if(isAttachedToWindow())
                ((ViewGroup)visualizerView.getParent()).removeView(visualizerView);
            visualizerView.setPlaying(false);
        }
    }
    
    private boolean isAttachedToWindow() {
        return visualizerView.getWindowToken() != null;
    }
    
    private boolean isNull() {
        log("[W] visualizerView is " + 
            (visualizerView != null ? "not " : "") + "null");
        return visualizerView == null;
    }
    
    public synchronized void setPlaying(boolean playing) {
        log("[W] setPlaying=" + playing);
        mPlaying = playing;
        setVisible(playing);
        if(!isNull()) visualizerView.setPlaying(playing);
    }
    
    public synchronized void setScreenOn(boolean screenOn) {
        log("[W] setScreenOn=" + screenOn);
        mScreenOn = screenOn;
        if(!isNull()) visualizerView.setScreenOn(screenOn);
    }
    
    public synchronized void setVisible(boolean visible) {
        log("[W] setVisible=" + visible);
        if(!isNull() && !isAttachedToWindow()) {
            parent.addView(visualizerView);
            for(int i = 0; i < parent.getChildCount(); i++)
                if(parent.getChildAt(i) != visualizerView)
                    parent.getChildAt(i).bringToFront();
            visualizerView.setTranslationZ(-2);
        }
        if(!isNull()) {
            if(mPlaying) visualizerView.setPlaying(true);
            visualizerView.setVisible(visible);
        }
    }
    
    public synchronized void setBitmap(Bitmap bitmap) {
        log("[W] setBitmap=[not null: " + (bitmap != null) + "]");
        if(!isNull()) {
            visualizerView.setBitmap(bitmap);
        }
    }
    
    public synchronized void ready() {
        if(!mScreenOn) return;
        log("[W] Getting ready...");
        if(isNull()) visualizerView = new VisualizerView(context);
        visualizerView.ready();
        log("[W] Ready.");
    }
    
    public synchronized void prepare() {
        if(isNull()) {
            mScreenOn = true;
            ready();
        }
    }
    
}