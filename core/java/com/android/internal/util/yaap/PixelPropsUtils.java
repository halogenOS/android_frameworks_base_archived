/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2021 AOSP-Krypton Project
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
package com.android.internal.util.yaap;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PixelPropsUtils {

    private static final String TAG = "PixelPropsUtils";
    private static final boolean DEBUG = false;

    private static final String build_device =
            Resources.getSystem().getString(com.android.internal.R.string.build_device);
    private static final String build_fp =
            Resources.getSystem().getString(com.android.internal.R.string.build_fp);
    private static final String build_model =
            Resources.getSystem().getString(com.android.internal.R.string.build_model);

    private static final String redfin_device =
            Resources.getSystem().getString(com.android.internal.R.string.redfin_device);
    private static final String redfin_fp =
            Resources.getSystem().getString(com.android.internal.R.string.redfin_fp);
    private static final String redfin_model =
            Resources.getSystem().getString(com.android.internal.R.string.redfin_model);

    private static final Map<String, String> marlinProps = Map.of(
        "DEVICE", "marlin",
        "PRODUCT", "marlin",
        "MODEL", "Pixel XL",
        "FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Map<String, String> redfinProps = Map.of(
        "DEVICE", redfin_device,
        "PRODUCT", redfin_device,
        "MODEL", redfin_model,
        "FINGERPRINT", redfin_fp
    );

    private static final Map<String, String> buildProps = Map.of(
        "DEVICE", build_device,
        "PRODUCT", build_device,
        "MODEL", build_model,
        "FINGERPRINT", build_fp
    );

    private static final Map<String, Object> commonProps = Map.of(
        "BRAND", "google",
        "MANUFACTURER", "Google",
        "IS_DEBUGGABLE", false,
        "IS_ENG", false,
        "IS_USERDEBUG", false,
        "IS_USER", true,
        "TYPE", "user"
    );

    private static final Map<String, ArrayList<String>> propsToKeep = Map.of(
        "com.google.android.settings.intelligence", new ArrayList<String>(Arrays.asList("FINGERPRINT"))
    );

    private static final String[] extraPackagesToChange = {
        "com.breel.wallpapers20"
    };

    private static final String[] marlinPackagesToChange = {
        "com.google.android.apps.photos",
        "com.samsung.accessory.berrymgr",
        "com.samsung.accessory.fridaymgr",
        "com.samsung.accessory.neobeanmg",
        "com.samsung.android.app.watchma",
        "com.samsung.android.gearnplugin",
        "com.samsung.android.modenplugin",
        "com.samsung.android.neatplugin",
        "com.samsung.android.waterplugin"
    };

    private static final String[] redfinPackagesToChange = {
            "com.google.android.tts",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.recorder"
    };

    public static void setProps(String packageName) {
        if (packageName == null) return;
        if (DEBUG) Log.d(TAG, "Package = " + packageName);
        if (Arrays.asList(marlinPackagesToChange).contains(packageName)) {
            commonProps.forEach(PixelPropsUtils::setPropValue);
            marlinProps.forEach(PixelPropsUtils::setPropValue);
        } else if (Arrays.asList(redfinPackagesToChange).contains(packageName)) {
            commonProps.forEach(PixelPropsUtils::setPropValue);
            redfinProps.forEach(PixelPropsUtils::setPropValue);
        } else if (packageName.startsWith("com.google.")
                || Arrays.asList(extraPackagesToChange).contains(packageName)) {
            commonProps.forEach(PixelPropsUtils::setPropValue);
            buildProps.forEach((key, value) -> {
                if (propsToKeep.containsKey(packageName)
                        && propsToKeep.get(packageName).contains(key)) {
                    if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + packageName);
                    return;
                }
                if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
                setPropValue(key, value);
            });
        }
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Setting prop " + key + " to " + value);
            final Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }
}
