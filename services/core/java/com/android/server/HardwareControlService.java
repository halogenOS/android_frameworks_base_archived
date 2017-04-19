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

import org.halogenos.hardware.buttons.IButtonBacklightControl;
import org.halogenos.hardware.buttons.KeyDisablerUtils;

/**
 * Hardware Control Service is supposed to be used to handle hardware-specific
 * settings and other important things related to hardware. Things like
 * button backlight belong here.
 **/
class HardwareControlService extends SystemService {
    
    private static final String TAG = HardwareControlService.class.getSimpleName();
    private static final boolean DEBUG = false;
    
    private SettingsObserver mSettingsObserver;
    private Context mContext;
    
    /* Hardware Control Components */
    
    private IButtonBacklightControl mButtonBacklightControl;
    
    /* --------------------------- */

    public HardwareControlService(Context context) {
        super(context);
        if(DEBUG) Log.d(TAG, "Hey, my name is " + TAG + "!");
        mContext = context;
        mSettingsObserver = new SettingsObserver(new Handler());
        
        /* Detect Hardware Control Components */
        
        loadHardwareComponents();
    }

    @Override
    public void onStart() {
        if(DEBUG) Log.d(TAG, "Getting ready...");
        mSettingsObserver.prepare();
        mSettingsObserver.observe();
        mSettingsObserver.onChange(true);
        if(DEBUG) Log.d(TAG, "Ready.");
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
        private boolean isKeyDisablerSupported;
        
        public SettingsObserver(Handler handler) {
            super(handler);
        }
        
        /**
         * Prepare everything before user gets to touch anything
         **/
        public void prepare() {
            ContentResolver resolver = mContext.getContentResolver();
            // KeyDisabler
            isKeyDisablerSupported = KeyDisablerUtils.areHwKeysSupported();
            int currentKdSetting = Settings.System.getIntForUser(resolver,
                Settings.System.HARDWARE_BUTTONS_ENABLED, -2, UserHandle.USER_CURRENT);
            if(currentKdSetting == -2 && !isKeyDisablerSupported)
                Settings.System.putIntForUser(resolver,
                    Settings.System.HARDWARE_BUTTONS_ENABLED,
                    -1, UserHandle.USER_CURRENT);
            else if(currentKdSetting == -2 && isKeyDisablerSupported)
                Settings.System.putIntForUser(resolver,
                    Settings.System.HARDWARE_BUTTONS_ENABLED,
                    1, UserHandle.USER_CURRENT);
            // Button backlight
            mButtonBacklightControl.ready();
            int currentBlSetting = Settings.System.getIntForUser(resolver,
                Settings.System.BUTTON_BACKLIGHT_BRIGHTNESS, -10,
                    UserHandle.USER_CURRENT);
            if(currentBlSetting == -10) {
                if(mButtonBacklightControl.CONTROL_TYPE !=
                    mButtonBacklightControl.CONTROL_TYPE_NONE) {
                    mButtonBacklightControl.setBrightness(1000); // 1000 = MAX
                    Settings.System.putIntForUser(resolver,
                        Settings.System.BUTTON_BACKLIGHT_BRIGHTNESS, 1000,
                        UserHandle.USER_CURRENT);
                    Settings.System.putIntForUser(resolver,
                        Settings.System.BUTTON_BACKLIGHT_TIMEOUT,
                        3, UserHandle.USER_CURRENT);
                }
            }

            if (IButtonBacklightControl.getCurrentControlType(resolver) !=
                mButtonBacklightControl.CONTROL_TYPE) {
                Settings.System.putIntForUser(resolver,
                    Settings.System.BUTTON_BACKLIGHT_CONTROL_TYPE,
                    mButtonBacklightControl.CONTROL_TYPE, UserHandle.USER_CURRENT);
            }
        }
        
        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BUTTON_BACKLIGHT_BRIGHTNESS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BUTTON_BACKLIGHT_TIMEOUT), false, this,
                    UserHandle.USER_ALL);
            if(isKeyDisablerSupported)
                resolver.registerContentObserver(Settings.System.getUriFor(
                        Settings.System.HARDWARE_BUTTONS_ENABLED), false, this,
                        UserHandle.USER_ALL);
        }
        
        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
            int br = 
                Settings.System.getIntForUser(
                    resolver,
                    Settings.System.BUTTON_BACKLIGHT_BRIGHTNESS,
                    0, UserHandle.USER_CURRENT
                );
            int tm =
                Settings.System.getIntForUser(
                    resolver,
                    Settings.System.BUTTON_BACKLIGHT_TIMEOUT,
                    0, UserHandle.USER_CURRENT
                );
            mButtonBacklightControl.handleBrightnessChange(br);
            mButtonBacklightControl.mCurrentBrightnessSetting = br;
            mButtonBacklightControl.mCurrentTimeout = tm;
            if(isKeyDisablerSupported)
                KeyDisablerUtils.setHwKeysEnabled(
                    Settings.System.getIntForUser(resolver,
                        Settings.System.HARDWARE_BUTTONS_ENABLED, 0, UserHandle.USER_CURRENT) == 1);
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
            mButtonBacklightControl = (IButtonBacklightControl)
                loadHardwareComponent(IButtonBacklightControl.COMPONENT_NAME);
            if(mButtonBacklightControl == null)
                mButtonBacklightControl = new IButtonBacklightControl();
        } catch(Exception e) {
            return false;
        }
        return true;
    }
    
    /**
     * Get the button backlight control object
     * 
     * This is used by the System Server to assign the reference to the
     * current instance to a variable in PowerManagerService
     **/
    IButtonBacklightControl getButtonBacklightControl() {
        return mButtonBacklightControl;
    }

}
