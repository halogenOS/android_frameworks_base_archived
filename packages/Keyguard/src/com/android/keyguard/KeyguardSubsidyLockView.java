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
import android.net.ConnectivityManager;
import android.telephony.SubscriptionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.widget.LockPatternUtils;

/**
 * Displays screen to unlock
 */
public class KeyguardSubsidyLockView extends KeyguardSubsidyStateView {
    KeyguardSecurityCallback mCallBack;
    private static final String TAG = "KeyguardSubsidyLockView";
    private static final boolean DEBUG = SubsidyUtility.DEBUG;
    private View mContentView;
    private View mProgressView;
    private View mEmergencyView;
    private TextView mProgressTitleView;
    private TextView mProgressContentView;
    private TextView mNoDataText;
    private Button mUnlockBtn;
    private Context mContext;
    private LinearLayout mSubsidySetupContainer;
    private KeyguardSubsidySetupButton mEnableDataButton;
    private SubsidyController mController;

    public KeyguardSubsidyLockView(Context context) {
        this(context, null);
    }

    public KeyguardSubsidyLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mController = SubsidyController.getInstance(mContext);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContentView = findViewById(R.id.content_view);
        mProgressView = findViewById(R.id.keyguard_subsidy_progress);

        mEmergencyView = findViewById(R.id.emergency_view);
        mProgressTitleView = (TextView) findViewById(R.id.kg_progress_title);
        mProgressTitleView.setText(R.string.kg_subsidy_title_unlock_progress_dialog);
        mProgressContentView =
            (TextView) findViewById(R.id.kg_progress_content);
        mProgressContentView.setText(R.string.kg_subsidy_content_progress_server);
        mUnlockBtn = (Button) findViewById(R.id.unlock);

        mUnlockBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) {
                    Log.d(TAG, " Unlock Button Pressed ");
                }
                mContentView.setVisibility(View.GONE);
                mEmergencyView.setVisibility(View.GONE);
                mProgressView.setVisibility(View.VISIBLE);
                setSubsidySetupContainerVisibility(View.GONE);
                mController.getCurrentSubsidyState().setInProgressState(
                        true);
                mContext.sendBroadcast(mController
                        .getCurrentSubsidyState().getLaunchIntent(),
                        SubsidyUtility.BROADCAST_PERMISSION);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(
                mInfoCallback);
        mSubsidySetupContainer = (LinearLayout) getRootView()
                .findViewById(R.id.subsidy_setup_container);
        mEnableDataButton = (KeyguardSubsidySetupButton) getRootView()
                .findViewById(R.id.enable_data);
        mNoDataText = (TextView) getRootView()
                .findViewById(R.id.no_data_connection);
        mContext.registerReceiver(connectivityReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
        IntentFilter primaryCardIntentFilter = new IntentFilter();
        primaryCardIntentFilter
                .addAction(SubsidyUtility.ACTION_SET_PRIMARY_CARD_DONE);
        mContext.registerReceiver(primaryCardChangeReceiver,
                primaryCardIntentFilter);
        setNoDataTextVisibility();
        setEnableDataButtonVisibility();
        setSubsidySetupContainerVisibility(View.VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(
                mInfoCallback);
        mContext.unregisterReceiver(connectivityReceiver);
        mContext.unregisterReceiver(primaryCardChangeReceiver);
        mNoDataText = null;
        mSubsidySetupContainer = null;
        mEnableDataButton = null;
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
                if (mProgressView.getVisibility() == View.VISIBLE) {
                    mContentView.setVisibility(View.VISIBLE);
                    mEmergencyView.setVisibility(View.VISIBLE);
                    mProgressView.setVisibility(View.GONE);
                    mController.getCurrentSubsidyState()
                        .setInProgressState(false);
                }
                setEnableDataButtonVisibility();
                setNoDataTextVisibility();
                setSubsidySetupContainerVisibility(View.VISIBLE);
            }
                public void onSimStateChanged(int subId, int slotId,
                        IccCardConstants.State simState) {
                    Log.d(TAG, "onSimStateChanged event occured ="+slotId);
                    setEnableDataButtonVisibility();
                }
        };

    private final BroadcastReceiver connectivityReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    setNoDataTextVisibility();
                }
            };

    private final BroadcastReceiver primaryCardChangeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "PrimaryConifg receiver Intent = " + intent);
                    if (intent.getAction().equals(SubsidyUtility
                            .ACTION_SET_PRIMARY_CARD_DONE)) {
                        setEnableDataButtonVisibility();
                    }
                }
            };


    public void setNoDataTextVisibility() {
        if (mNoDataText != null) {
            mNoDataText.setVisibility(SubsidyUtility
                    .isDataConnectionActive(mContext) ? View.GONE
                    : View.VISIBLE);
        }
    }

    public void setSubsidySetupContainerVisibility(int isVisible) {
        if (mSubsidySetupContainer != null) {
            mSubsidySetupContainer.setVisibility(isVisible);
        }
    }

    public void setEnableDataButtonVisibility() {
        if (mEnableDataButton != null) {
            int visibility = mController.isEnableDataButtonVisible(mContext);
            mEnableDataButton.setVisibility(visibility);
        }
    }
}
