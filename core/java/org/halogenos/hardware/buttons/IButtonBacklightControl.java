/*
 * Copyright (C) 2016-2017 The halogenOS Project
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
package org.halogenos.hardware.buttons;

import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.io.File;

import org.halogenos.io.FileUtils;

/**
 * This class is supposed to control button backlight
 *
 * Consider this as a template.
 * Create your own class in the device tree.
 * Example: https://github.com/halogenOS/android_device_oneplus_oneplus2/blob/XOS-7.1/xoshw/ButtonBacklightControl.java
 *
 * @hide
 */
public class IButtonBacklightControl {

    public static final String COMPONENT_NAME = "buttons.ButtonBacklightControl";

    /**
     * Brightness not supported or no buttons
     */
    public static final int CONTROL_TYPE_NONE = -1;

    /**
     * Brightness can be fully adjusted
     */
    public static final int CONTROL_TYPE_FULL = -2;

    /**
     * Brightness can be partially adjusted
     * Off - Dim - On
     */
    public static final int CONTROL_TYPE_PARTIAL = -3;

    /**
     * Backlight can only be turned on and off
     */
    public static final int CONTROL_TYPE_SWITCH = -4;

    private static final boolean DEBUG = false;
    private static final String TAG = IButtonBacklightControl.class.getSimpleName();

    private static final String BRIGHTNESS_CONTROL_FILE = "brightness";
    private static final String MAX_BRIGHTNESS_FILE = "max_brightness";

    public int CONTROL_TYPE = CONTROL_TYPE_NONE;

    @Deprecated
    public boolean HAVE_TWO_BACKLIGHT_PATHS = false;

    @Deprecated
    public String BUTTON_BACKLIGHT_PATH = "/sys/class/leds/button-backlight/";
    @Deprecated
    public String BUTTON_BACKLIGHT_PATH2 = null;

    public String BUTTON_BACKLIGHT_PATHS[] = null;

    /// Device-specific maximum brightness value
    public int MAXIMUM_BRIGHTNESS = 100;

    public int mCurrentBrightnessSetting = 1000, mCurrentTimeout = 3,
               mCurrentBrightness = 0;

    /** 
     * @hide
     **/
    public IButtonBacklightControl() {

    }

    /**
     * Set the brightness directly without conversions
     *
     * @hide
     **/
    private void setBrightnessDirect(int brightness) {
        if(mCurrentBrightness == brightness) return;
        if(DEBUG) Log.d(TAG, "Setting brightness: " + brightness);
        for (int i = 0; i < BUTTON_BACKLIGHT_PATHS.length; i++) {
            if (BUTTON_BACKLIGHT_PATHS[i] == null) continue;
            File curFile = new File(
                    BUTTON_BACKLIGHT_PATHS[i], BRIGHTNESS_CONTROL_FILE);
            if (curFile.exists()) {
                FileUtils.writeString(String.valueOf(brightness), curFile);
            }
        }
        mCurrentBrightness = brightness;
    }

    /**
     * Set the brightness of the buttons.
     * Measured in a scale of 0-1000, while 0 means off, and 1000
     * means maximum brightness.
     *
     * @param brightness Brightness expressed in a
     *                   number between 0 and 1000, both inclusive
     *
     * @hide
     */
    public final void setBrightness(int brightness) {
        setBrightnessDirect((int) (brightness / 1000 * MAXIMUM_BRIGHTNESS));
    }

    /**
     * Get the current brightness of the buttons.
     *
     * See setBrightness(int brightness)
     *
     * @return Current brightness. Also returns 0 if not able to retrieve it.
     * @hide
     */
    public int getBrightness() {
        for (int i = 0; i < BUTTON_BACKLIGHT_PATHS.length; i++) {
            if (BUTTON_BACKLIGHT_PATHS[i] == null) continue;
            File curFile = new File(
                        BUTTON_BACKLIGHT_PATHS[i], BRIGHTNESS_CONTROL_FILE);
            if (curFile.exists()) {
                try {
                    String b = FileUtils.readString(curFile);
                    if (b == null || b.isEmpty()) continue;
                    return Integer.parseInt(b) / MAXIMUM_BRIGHTNESS * 1000;
                } catch(Exception e) {
                    // What is happening?!
                }
            }
        }
        return 0;
    }

    /**
     * Handle a brighthness change (this can also be used as a safer variant of setBrightness)
     *
     * Automatic conversion of brightness values, no unallowed values.
     *
     * @param newBrightness New brightness in a scale from 0-1000
     *
     * @hide
     **/
    public final void handleBrightnessChange(int newBrightness) {
        switch(CONTROL_TYPE) {
            case CONTROL_TYPE_SWITCH:
                setBrightness(newBrightness > 500 ? 1000 : 0);
                break;
            case CONTROL_TYPE_PARTIAL:
                setBrightnessDirect(newBrightness  > 1 ? MAXIMUM_BRIGHTNESS :
                                   (newBrightness == 1 ? 1 : 0));
                break;
            case CONTROL_TYPE_FULL:
                setBrightness(newBrightness > 1000 ? 1000 :
                                (newBrightness < 0 ? 0 : newBrightness));
                break;
            case CONTROL_TYPE_NONE:
            default:
                break;
        }
    }

    /**
     * Called when HardwareControlService wants this to be ready
     *
     * @hide
     **/
    public final void ready() {
        if (HAVE_TWO_BACKLIGHT_PATHS && BUTTON_BACKLIGHT_PATHS == null) {
            /* This is deprecated but still handled for compatibility.
             * Planning to remove support for old modules in next rebase */
            BUTTON_BACKLIGHT_PATHS = new String[] {
                BUTTON_BACKLIGHT_PATH, BUTTON_BACKLIGHT_PATH2
            };
        } else if (BUTTON_BACKLIGHT_PATHS == null &&
                    BUTTON_BACKLIGHT_PATH2 == null) {
            BUTTON_BACKLIGHT_PATHS = new String[] {
                BUTTON_BACKLIGHT_PATH
            };
        }
        for (int i = 0; i < BUTTON_BACKLIGHT_PATHS.length; i++) {
            if (new File(BUTTON_BACKLIGHT_PATHS[i], MAX_BRIGHTNESS_FILE).exists()) {
                try {
                    MAXIMUM_BRIGHTNESS = Integer.parseInt(
                        FileUtils.readString(new File(
                            BUTTON_BACKLIGHT_PATHS[i], MAX_BRIGHTNESS_FILE)));
                    break;
                } catch(NumberFormatException e) {
                    // Bad content
                }
            }
        }
        mCurrentBrightness = getBrightness();
    }

    /**
     * Retrieve the current control type from settings
     *
     * @param resolver Content resolver
     * @return Current control type
     * 
     * @hide
     **/
    public static int getCurrentControlType(ContentResolver resolver) {
        return Settings.System.getIntForUser(resolver,
                Settings.System.BUTTON_BACKLIGHT_CONTROL_TYPE, CONTROL_TYPE_NONE,
                UserHandle.USER_CURRENT);
    }

    /**
     * Get the current timeout
     *
     * @return Current timeout
     * @hide
     **/
    public final int getCurrentTimeout() {
        return mCurrentTimeout;
    }

    /**
     * Get the brightness chosen by the user.
     *
     * Using getBrightness() would return the real-time brightness,
     * not the brightness the user has chosen to be when the lights
     * are on.
     *
     * @return Current brightness setting
     * @hide
     **/
    public final int getCurrentBrightnessSetting() {
        return mCurrentBrightnessSetting;
    }

}