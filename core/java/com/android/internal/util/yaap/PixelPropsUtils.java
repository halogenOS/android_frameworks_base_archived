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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    private static final Map<String, String> walleyeProps = Map.of(
        "DEVICE", "walleye",
        "PRODUCT", "walleye",
        "MODEL", "Pixel 2",
        "FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys"
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

    private static final Map<String, Set<String>> propsToKeep;

    static {
        // null means skip the package
        Map<String, Set<String>> tMap = new HashMap<>();
        tMap.put("com.google.android.settings.intelligence",
                Set.of("FINGERPRINT"));
        tMap.put("com.google.android.GoogleCamera", null);
        tMap.put("com.google.android.GoogleCameraGood", null);
        tMap.put("com.google.android.GoogleCamera.Cameight", null);
        tMap.put("com.google.android.GoogleCamera.Go", null);
        tMap.put("com.google.android.GoogleCamera.Urnyx", null);
        tMap.put("com.google.android.GoogleCameraAsp", null);
        tMap.put("com.google.android.GoogleCameraCVM", null);
        tMap.put("com.google.android.GoogleCameraEng", null);
        tMap.put("com.google.android.GoogleCameraEng2", null);
        tMap.put("com.google.android.MTCL83", null);
        tMap.put("com.google.android.UltraCVM", null);
        tMap.put("com.google.android.apps.cameralite", null);
        tMap.put("com.google.ar.core", null);
        propsToKeep = Collections.unmodifiableMap(tMap);
    }

    private static final Set<String> extraPackagesToChange = Set.of(
        "com.breel.wallpapers20",
        "com.google.android.gms.persistent",
        "com.google.android.as"
    );

    private static final Set<String> marlinPackagesToChange = Set.of(
        "com.google.android.apps.photos",
        "com.samsung.accessory.berrymgr",
        "com.samsung.accessory.fridaymgr",
        "com.samsung.accessory.neobeanmg",
        "com.samsung.android.app.watchma",
        "com.samsung.android.gearnplugin",
        "com.samsung.android.modenplugin",
        "com.samsung.android.neatplugin",
        "com.samsung.android.waterplugin"
    );

    private static final Set<String> walleyePackagesToChange = Set.of(
        "com.android.vending",
        "com.google.android.gms"
    );

    private static final Set<String> redfinPackagesToChange = Set.of(
        "com.google.android.tts",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.recorder"
    );

    public static void setProps(String packageName) {
        if (packageName == null) return;
        if (DEBUG) Log.d(TAG, "Package = " + packageName);
        if (marlinPackagesToChange.contains(packageName)) {
            commonProps.forEach(PixelPropsUtils::setPropValue);
            marlinProps.forEach(PixelPropsUtils::setPropValue);
        } else if (redfinPackagesToChange.contains(packageName)) {
            commonProps.forEach(PixelPropsUtils::setPropValue);
            redfinProps.forEach(PixelPropsUtils::setPropValue);
        } else if (walleyePackagesToChange.contains(packageName)) {
            commonProps.forEach(PixelPropsUtils::setPropValue);
            walleyeProps.forEach(PixelPropsUtils::setPropValue);
        } else if (packageName.startsWith("com.google.")
                || extraPackagesToChange.contains(packageName)) {
            if (propsToKeep.containsKey(packageName)
                    && propsToKeep.get(packageName) == null) {
                if (DEBUG) Log.d(TAG, "Skipping all props for: " + packageName);
                return;
            }
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
