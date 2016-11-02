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
 * limitations under the License.
 */

package com.android.server;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Constructor;

/**
 * Hardware Control Service is supposed to be used to handle hardware-specific
 * settings and other important things related to hardware. Things like
 * button backlight belong here.
 **/
class HardwareControlService extends SystemService {
    
    private static final String TAG = HardwareControlService.class.getSimpleName();
    
    private SettingsObserver mSettingsObserver;
    private Context mContext;
    
    /* Hardware Control Components */
    
    
    /* --------------------------- */

    public HardwareControlService(Context context) {
        super(context);
        Log.d(TAG, "Hey, my name is " + TAG + "!");
        mContext = context;
        mSettingsObserver = new SettingsObserver(new Handler());
        
        /* Detect Hardware Control Components */
        
        loadHardwareComponents();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Getting ready...");
        mSettingsObserver.prepare();
        mSettingsObserver.observe();
        mSettingsObserver.onChange(true);
        Log.d(TAG, "Ready.");
    }

    @Override
    public void onBootPhase(int phase) {
        // No need
    }

    @Override
    public void onSwitchUser(int userHandle) {
        mSettingsObserver.prepare();
        mSettingsObserver.onChange(true);
    }

    @Override
    public void onUnlockUser(int userHandle) {
        // No need
    }
    
    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }
        
        /**
         * Prepare everything before user gets to touch anything
         **/
        public void prepare() {
            ContentResolver resolver = mContext.getContentResolver();
        }
        
        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();
        }
        
        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
        }
    }
    
    /**
     * Load a hardware component by class name
     * 
     * Can throw exceptions.
     * 
     * @param fullname Fully qualified class name
     **/
    private Object loadHardwareComponentCustom(String fullname) throws Exception {
        Class clazz = Class.forName(fullname);
        Constructor<?> ctor = clazz.getConstructor();
        return (Object) ctor.newInstance();
    }
    
    /**
     * Safe variant of loadHardwareComponentCustom.
     * Returns null if exception happens
     **/
    private Object loadHardwareComponentCustomSafe(String fullname) {
        try {
            return loadHardwareComponentCustom(fullname);
        } catch(Exception e) {
            return null;
        }
    }
    
    /**
     * Default loader for hardware components
     * 
     * @param name Component name
     **/
    private Object loadHardwareComponent(String name) {
        return loadHardwareComponentCustomSafe("org.halogenos.hardware." + name);
    }
    
    /**
     * Load all hardware components
     **/
    private boolean loadHardwareComponents() {
        try {
             
        } catch(Exception e) {
            return false;
        }
        return true;
    }

}
