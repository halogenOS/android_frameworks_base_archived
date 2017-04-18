/*
 * Copyright (C) 2016-2017 halogenOS
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

import org.cyanogenmod.internal.util.FileUtils;

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
    private static final boolean DEBUG = false;
    private static final String TAG = IButtonBacklightControl.class.getSimpleName();

    public static final int
            // Brightness not supported or no buttons
            CONTROL_TYPE_NONE = -1,
            // Brightness can be fully adjusted
            CONTROL_TYPE_FULL = -2,
            // Brightness can be partially adjusted
            // Off - Dim - On
            CONTROL_TYPE_PARTIAL = -3,
            // Backlight can only be turned on and off
            CONTROL_TYPE_SWITCH = -4
            ;

    public int CONTROL_TYPE = CONTROL_TYPE_NONE;

    public boolean HAVE_TWO_BACKLIGHT_PATHS = false;

    public String
        BUTTON_BACKLIGHT_PATH =
            "/sys/class/leds/button-backlight/",
        BUTTON_BACKLIGHT_PATH2 =
            "/sys/class/leds/button-backlight1/",
        BUTTON_BACKLIGHT_PATHS[] = null,
        BRIGHTNESS_CONTROL = "brightness",
        MAX_BRIGHTNESS_CONTROL = "max_brightness"
        ;

    /// Device-specific maximum brightness value
    public int MAXIMUM_BRIGHTNESS = -1;

    public int currentBrightnessSetting = 1000, currentTimeout = 3,
               currentBrightness = 0;

    /** @hide **/
    public IButtonBacklightControl() {

    }

    /**
     * Set the brightness directly without conversions
     *
     * @hide
     **/
    private void setBrightnessDirect(int brightness) {
        if(currentBrightness == brightness) return;
        if(DEBUG) Log.d(TAG, "Setting brightness: " + brightness);
        for (String path : BUTTON_BACKLIGHT_PATHS) {
            String finalPath = path + BRIGHTNESS_CONTROL;
            if (FileUtils.isFileWritable(finalPath)) {
                FileUtils.writeLine(finalPath, String.valueOf(brightness));
            }
        }
        currentBrightness = brightness;
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
     * @hide
     */
    public int getBrightness() {
        if (!FileUtils.isFileReadable(BRIGHTNESS_CONTROL) &&
                BUTTON_BACKLIGHT_PATHS.length > 1) {
            String altPath = BUTTON_BACKLIGHT_PATHS[1] + "/brightness";
            if (FileUtils.isFileReadable(altPath)) {
                BRIGHTNESS_CONTROL = altPath;
            } else return 0;
        }
        return Integer.parseInt(
            FileUtils.readOneLine(BRIGHTNESS_CONTROL)) / MAXIMUM_BRIGHTNESS * 1000;
    }

    /**
     * Handle a brighthness change (this can also be used as a safer variant of setBrightness)
     *
     * Auto-Conversion of brightness values, no unallowed values.
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
                setBrightness(
                    newBrightness > 1000 ? 1000 :
                    (newBrightness < 0 ? 0 : newBrightness)
                );
            case CONTROL_TYPE_NONE:
            default: break;
        }
    }

    /**
     * Called when HardwareControlService wants this to be ready
     *
     * @hide
     **/
    public final void ready() {
        if (HAVE_TWO_BACKLIGHT_PATHS && BUTTON_BACKLIGHT_PATHS == null)
            BUTTON_BACKLIGHT_PATHS = new String[]
                {BUTTON_BACKLIGHT_PATH,BUTTON_BACKLIGHT_PATH2};
        else if(BUTTON_BACKLIGHT_PATHS == null)
            BUTTON_BACKLIGHT_PATHS = new String[]
                {BUTTON_BACKLIGHT_PATH};
        MAX_BRIGHTNESS_CONTROL = BUTTON_BACKLIGHT_PATHS[0] + "max_brightness";
        try {
            if (!FileUtils.isFileReadable(MAX_BRIGHTNESS_CONTROL) &&
                    BUTTON_BACKLIGHT_PATHS.length > 1) {
                MAX_BRIGHTNESS_CONTROL = BUTTON_BACKLIGHT_PATHS[1] + "max_brightness";
            }
            if (!FileUtils.isFileReadable(MAX_BRIGHTNESS_CONTROL)) {
                MAXIMUM_BRIGHTNESS = 100;
            } else if (MAXIMUM_BRIGHTNESS == -1) {
                MAXIMUM_BRIGHTNESS =
                    Integer.parseInt(FileUtils.readOneLine(MAX_BRIGHTNESS_CONTROL));
            }
            currentBrightness = getBrightness();
        } catch(Exception e) {
            MAXIMUM_BRIGHTNESS = 100;
        }
    }

    /**
     * Retrieve the current control type from settings
     *
     * @param resolver Content resolver
     *
     * @hide
     **/
    public static int currentControlType(ContentResolver resolver) {
        return Settings.System.getIntForUser(resolver,
                Settings.System.BUTTON_BACKLIGHT_CONTROL_TYPE, CONTROL_TYPE_NONE,
                UserHandle.USER_CURRENT);
    }

    /**
     * Get the current timeout
     *
     * @hide
     **/
    public final int currentTimeout() {
        return currentTimeout;
    }

    /**
     * Get the brightness chosen by the user.
     *
     * Using getBrightness() would return the real-time brightness,
     * not the brightness the user has chosen to be when the lights
     * are on.
     *
     * @hide
     **/
    public final int currentBrightnessSetting() {
        return currentBrightnessSetting;
    }

}