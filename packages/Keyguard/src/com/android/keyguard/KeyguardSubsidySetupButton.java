/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above
 *        copyright notice, this list of conditions and the following
 *        disclaimer in the documentation and/or other materials provided
 *        with the distribution.
 *      * Neither the name of The Linux Foundation nor the names of its
 *        contributors may be used to endorse or promote products derived
 *        from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.keyguard;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Method;

public class KeyguardSubsidySetupButton extends Button {
    private final String TAG = "WifiSetupAndDataEnableButton";
    private final TelephonyManager mTelephonyManager;

    public KeyguardSubsidySetupButton(Context context) {
        this(context, null);
    }

    public KeyguardSubsidySetupButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTelephonyManager = TelephonyManager.from(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if(v.getId() == R.id.setup_wifi) {
                Intent wifiIntent = new Intent(
                        SubsidyUtility.WIFI_SETUP_SCREEN_INTENT);
                wifiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                getContext().startActivity(wifiIntent);
                } else if (v.getId() == R.id.enable_data) {
                    boolean isAirplaneModeOn =
                            SubsidyUtility.isAirplaneMode(getContext());
                    if(isAirplaneModeOn) {
                        // Disable Airplane mode first if enabled
                        final ConnectivityManager connectivityManager =
                                (ConnectivityManager) getContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
                        connectivityManager.setAirplaneMode(false);
                    }
                    // Enable default data subscription
                    SubsidyController.getInstance(getContext()).enableMobileData();
                    // Enable Data view is set to GONE
                    v.setVisibility(View.GONE);
                }
            }
        });
    }
}
