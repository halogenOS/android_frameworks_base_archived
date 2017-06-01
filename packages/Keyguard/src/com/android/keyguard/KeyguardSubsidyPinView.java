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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.CountDownTimer;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.telephony.IccCardConstants;
import org.codeaurora.internal.IDepersoResCallback;
import org.codeaurora.internal.IExtTelephony;
import com.android.keyguard.SubsidyController.*;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSubsidyPinView extends KeyguardPinBasedInputView {
    private static final String TAG = "KeyguardSubsidyPinView";
    private TextView mKeyguardMessageView;
    private TextView mNoDataText;
    private ImageButton mEnterKey;
    private Context mContext;
    private CheckUnlockPin mCheckUnlockPinThread;
    private ProgressDialog mUnlockProgressDialog = null;
    private int mRetryAttemptRemaining;
    private CountDownTimer mSubsidyPinCountDownTimer;
    private LinearLayout mSubsidySetupContainer;
    private KeyguardSubsidySetupButton mEnableDataButton;
    private ViewGroup mContainer;
    private SubsidyController mController;
    private boolean mIsCountDownTimerRunning = false;

    public KeyguardSubsidyPinView(Context context) {
        this(context, null);
    }

    public KeyguardSubsidyPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mRetryAttemptRemaining = getTotalRetryAttempts();
        mController = SubsidyController.getInstance(mContext);
    }

    public void resetState() {
        mPasswordEntry.setEnabled(true);
        showDefaultMessage();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected int getPromtReasonStringRes(int reason) {
        // No message on Device Pin
        return 0;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.subsidy_pinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mKeyguardMessageView =
            (TextView) findViewById(R.id.keyguard_message_area);
        mKeyguardMessageView.setSingleLine(false);
        mKeyguardMessageView.setEllipsize(null);

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging
        // status by default
        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(false);
        }

        mContainer = (ViewGroup) findViewById(R.id.container);
        mContainer.setVisibility(getkeypadViewVisibility());
    }

    private int getTotalRetryAttempts() {
        return mContext.getResources().getInteger(
                R.integer.config_max_enter_code_attempt);
    }

    @Override
    public void showUsabilityHint() {
    }

    private Dialog getUnlockProgressDialog() {
        if (mUnlockProgressDialog == null) {
            mUnlockProgressDialog = new ProgressDialog(mContext);
            mUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_pin_unlock_progress_dialog));
            mUnlockProgressDialog.setIndeterminate(true);
            mUnlockProgressDialog.setCancelable(false);
            mUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mUnlockProgressDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();
        if (entry.length() < 16) {
            // otherwise, display a message to the user, and don't
            // submit.
            Log.v(TAG, "Insufficient pin to process");
            handleErrorCase();
            return;
        }
        mController.stopStateTransitions(true);
        getUnlockProgressDialog().show();

        if (mCheckUnlockPinThread == null) {
            mCheckUnlockPinThread =
                new CheckUnlockPin(mPasswordEntry.getText()) {
                    void onUnlockResponse(final boolean isSuccess) {
                        post(new Runnable() {
                            public void run() {
                                mController.stopStateTransitions(false);
                                if (mUnlockProgressDialog != null) {
                                    mUnlockProgressDialog.hide();
                                }
                                if (isSuccess) {
                                    Log.d(TAG, "Local Unlock code is correct and verified");
                                    mContext.sendBroadcast(mController
                                            .getCurrentSubsidyState()
                                            .getLaunchIntent(),
                                        SubsidyUtility.BROADCAST_PERMISSION);

                                    mController.setDeviceUnlocked();
                                } else {
                                    handleErrorCase();
                                }
                                mCheckUnlockPinThread = null;
                            }
                        });
                    }
                };
            mCheckUnlockPinThread.start();
        }
    }

    @Override
    public void startAppearAnimation() {
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }

    private void showDefaultMessage() {
        mSecurityMessageDisplay.setMessage(
                R.string.kg_subsidy_content_pin_locked, true);
    }

    @Override
    public int getWrongPasswordStringId() {
        return R.plurals.kg_subsidy_wrong_pin;
    }

    @Override
    protected void resetPasswordText(boolean animate, boolean announce) {
        super.resetPasswordText(animate, announce);
        setOkButtonEnabled(false);
    }

    @Override
    protected void onUserInput() {
        super.onUserInput();
        setOkButtonEnabled(mPasswordEntry.getText().length() > 0);
    }

    public void handleErrorCase() {
        Log.d(TAG, "Handle error case when user attemp with wrong pin");
        mRetryAttemptRemaining--;
        Log.d(TAG, "remaining retry attempts = "+mRetryAttemptRemaining);
        if (mRetryAttemptRemaining > 0) {
            mSecurityMessageDisplay.setMessage(
                    mContext.getResources().getQuantityString(
                            R.plurals.kg_subsidy_wrong_pin,
                            mRetryAttemptRemaining, mRetryAttemptRemaining),
                    true);
        } else if (mRetryAttemptRemaining == 0) {
            Log.d(TAG, "Retry attempt count is over so start timer");
            int attemptTimeOut =
                    mContext.getResources().getInteger(
                            R.integer.config_timeout_after_max_attempt_milli);
            long deadline = mLockPatternUtils.setLockoutAttemptDeadline(
                    KeyguardUpdateMonitor.getCurrentUser(), attemptTimeOut);
            handleAttemptLockout(deadline);
        }
        resetPasswordText(true, true);
        mCallback.userActivity();
    }

    @Override
    protected void handleAttemptLockout(long elapsedRealtimeDeadline) {
        setPasswordEntryEnabled(false);

        final long elapsedRealtime = SystemClock.elapsedRealtime();
        Log.d(TAG, "Count down timer is still running =" +mIsCountDownTimerRunning);
        if (!mIsCountDownTimerRunning) {
            Log.d(TAG, "CountDownTimer instance is reset to null");
            mSubsidyPinCountDownTimer = null;
        }
        if (mSubsidyPinCountDownTimer == null) {
            Log.d(TAG, "Create new instance of CountDownTimer");
            mSubsidyPinCountDownTimer =
                new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime,
                        1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        // Condition to distinguish min/sec to display.
                        // If more than 1 minutes remaining it will be displayed
                        // in minutes, if less than 1 minutes is displayed in
                        // seconds.
                        if (getkeypadViewVisibility() != View.VISIBLE) {
                            showDefaultMessage();
                        } else if (millisUntilFinished > 60000) {
                            int minutesRemaining =
                                    (int) (millisUntilFinished / 60000);
                            minutesRemaining++;
                            mSecurityMessageDisplay.setMessage(
                                    R.string.kg_subsidy_too_many_failed_attempts_countdown,
                                    true, minutesRemaining);
                        } else {
                            int secondsRemaining =
                                    (int) (millisUntilFinished / 1000);
                            secondsRemaining++;
                            mSecurityMessageDisplay.setMessage(
                                    R.string.kg_subsidy_too_many_failed_attempts_countdown_sec,
                                    true, secondsRemaining);
                        }
                        mIsCountDownTimerRunning = true;
                    }

                    @Override
                    public void onFinish() {
                        Log.d(TAG, "CountDownTimer finished");
                        mRetryAttemptRemaining = getTotalRetryAttempts();
                        resetState();
                        mSubsidyPinCountDownTimer = null;
                        mIsCountDownTimerRunning = false;
                    }

                }.start();
        }
    }

    private abstract class CheckUnlockPin extends Thread {
        private final String mPin;
        private final String PERSOSUBSTATE_SIM_NETWORK = "3";

        protected CheckUnlockPin(String pin) {
            mPin = pin;
        }

        abstract void onUnlockResponse(final boolean result);

        @Override
        public void run() {
            try {
                IExtTelephony extTelephony =
                    IExtTelephony.Stub.asInterface(ServiceManager
                            .getService("extphone"));
                IDepersoResCallback depersoResCallback = new IDepersoResCallback.Stub() {
                    @Override
                    public void onDepersoResult(int result, int phoneId) {
                        Log.v(TAG, "onDepersoResult called: "
                            +result);
                        post(new Runnable() {
                             public void run() {
                                 onUnlockResponse(result == 0 /* 0 means SUCCESS */);
                             }
                        });
                    }
                };

                int slotId = extTelephony.getCurrentPrimaryCardSlotId();

                Log.v(TAG, "call supplyIccDepersonalization SlotId ="
                        + slotId);
                 extTelephony
                    .supplyIccDepersonalization(mPin, PERSOSUBSTATE_SIM_NETWORK, depersoResCallback, slotId);

            } catch (RemoteException e) {
                Log.e(TAG,
                        "Exception for supplyNetworkDepersonalization:", e);
                post(new Runnable() {
                    public void run() {
                        onUnlockResponse(false);
                    }
                });
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(
                mInfoCallback);
        mContext.registerReceiver(connectivityReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
        mSubsidySetupContainer = (LinearLayout) getRootView()
                .findViewById(R.id.subsidy_setup_container);
        mEnableDataButton = (KeyguardSubsidySetupButton) getRootView()
                .findViewById(R.id.enable_data);
        mNoDataText = (TextView) getRootView()
                .findViewById(R.id.no_data_connection);
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
                    if (!isLocked) {
                        Log.d(TAG, "Reset the lockout deadline");
                        mLockPatternUtils.setLockoutAttemptDeadline(
                                KeyguardUpdateMonitor.getCurrentUser(), 0);
                        if (mSubsidyPinCountDownTimer != null) {
                            mSubsidyPinCountDownTimer.cancel();
                            mSubsidyPinCountDownTimer = null;
                        }
                        mRetryAttemptRemaining = getTotalRetryAttempts();
                        showDefaultMessage();
                    }
                    mContainer.setVisibility(getkeypadViewVisibility());
                    setEnableDataButtonVisibility();
                    setNoDataTextVisibility();
                    setSubsidySetupContainerVisibility(View.VISIBLE);
                    // dismiss the dialog.

                    if (mUnlockProgressDialog != null) {
                        mUnlockProgressDialog.dismiss();
                        mUnlockProgressDialog = null;
                    }
                }
                public void onSimStateChanged(int subId, int slotId,
                        IccCardConstants.State simState) {
                    Log.d(TAG, "onSimStateChanged event occured");
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

    public int getkeypadViewVisibility() {
        SubsidyState currentState = mController.getCurrentSubsidyState();
        if (currentState instanceof DeviceLockedState) {
            return ((DeviceLockedState) currentState).getKeypadViewVisible()
                    ? View.VISIBLE
                    : View.INVISIBLE;
        }
        return View.INVISIBLE;
    }
}
