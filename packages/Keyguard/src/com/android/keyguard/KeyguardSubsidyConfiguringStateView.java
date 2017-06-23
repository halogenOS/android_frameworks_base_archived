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

 import android.content.BroadcastReceiver;
 import android.content.Context;
 import android.content.Intent;
 import android.content.IntentFilter;
 import android.util.AttributeSet;
 import android.util.Log;
 import android.view.View;
 import android.view.ViewParent;
 import android.widget.Button;
 import android.widget.LinearLayout;
 import android.widget.TextView;

 import com.android.internal.widget.LockPatternUtils;

/**
 * Displays screen to unlock
 */
public class KeyguardSubsidyConfiguringStateView  extends KeyguardSubsidyStateView {
    private static final String TAG = "KeyguardSubsidyLockView";
    private static final boolean DEBUG = SubsidyUtility.DEBUG;
    private TextView mProgressTitleView;
    private TextView mProgressContentView;
    private Context mContext;
    private LinearLayout mSubsidySetupContainer;

    public KeyguardSubsidyConfiguringStateView(Context context) {
        this(context, null);
    }

    public KeyguardSubsidyConfiguringStateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mProgressTitleView = (TextView) findViewById(R.id.kg_progress_title);
        mProgressTitleView.setText(R.string.kg_subsidy_title_progress_unknown);
        mProgressContentView =
            (TextView) findViewById(R.id.kg_progress_content);
        mProgressContentView.setText(R.string.kg_subsidy_content_progress_unknown);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(
                mInfoCallback);
        mSubsidySetupContainer =
                (LinearLayout) getRootView().findViewById(R.id.subsidy_setup_container);
        setSubsidySetupContainerVisibility(View.GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(
                mInfoCallback);
        mSubsidySetupContainer = null;
    }

    KeyguardUpdateMonitorCallback mInfoCallback =
        new KeyguardUpdateMonitorCallback() {
            @Override
            public void onSubsidyLockStateChanged(boolean isLocked) {
                Log.d(TAG, "SubsidyLock state changed isLocked ="+isLocked);
                if (!isLocked && null != mLockPatternUtils) {
                    Log.d(TAG, "Reset the lockout deadline");
                    mLockPatternUtils.setLockoutAttemptDeadline(
                            KeyguardUpdateMonitor.getCurrentUser(), 0);
                }
            }
        };

    public void setSubsidySetupContainerVisibility(int isVisible) {
        if (mSubsidySetupContainer != null) {
            mSubsidySetupContainer.setVisibility(isVisible);
        }
    }
}
