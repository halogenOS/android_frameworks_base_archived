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
package org.halogenos.hardware.buttons;

import android.content.Context;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * @hide
 */
public class KeyDisablerUtils {
    
    private static final String TAG = KeyDisablerUtils.class.getSimpleName();
    
    /** @hide **/
    public static void setHwKeysEnabled(boolean disabled) {
        try {
            Class keyDisabler = Class.forName("org.cyanogenmod.hardware.KeyDisabler");
    	    Method setActiveMethod = 
    	        keyDisabler.getDeclaredMethod("setActive", boolean.class);
            setActiveMethod.invoke(null, disabled);
        } catch(Exception ex) {
            Log.e(TAG, "Could not " + (disabled ? "disable" : "enable") +
                        "HW keys!");
            ex.printStackTrace();
        }
    }
    
    /** @hide **/
    public static boolean areHwKeysSupported() {
        try {
            Class keyDisabler = Class.forName("org.cyanogenmod.hardware.KeyDisabler");
            Method isSupportedMethod =
                keyDisabler.getDeclaredMethod("isSupported");
            Object result = isSupportedMethod.invoke(null);
            return ((boolean)result);
        } catch(Exception ex) {
            Log.d(TAG, "KeyDisabler doesn't seem to be there");
            return false;
        }
    }
    
    /** @hide **/
    public static boolean areHwKeysSupportedInSettings(Context ctx) {
        return Settings.System.getIntForUser(ctx.getContentResolver(),
                Settings.System.HARDWARE_BUTTONS_ENABLED, 0,
                UserHandle.USER_CURRENT) != -1;
    }
    
}