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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.internal.telephony.IccCardConstants;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.SubsidyUtility.SubsidyLockState;
import com.android.settingslib.TetherUtil;
import org.codeaurora.internal.IExtTelephony;

public class SubsidyController {
    private static SubsidyController sSubsidyController;
    private static String TAG = "SubsidyController";
    private SubsidyState mCurrentSubsidyState;
    private SubsidyState mPreviousSubsidyState;
    private Context mContext;
    private boolean mStopStateTransitions = false;
    private TelephonyManager mTelephonyManager;
    private boolean mIsSIMWhiteListed;

    private SubsidyController(Context context) {
        mContext = context;

        mTelephonyManager = TelephonyManager.from(context);

        final IntentFilter subsidyLockFilter = new IntentFilter();
        subsidyLockFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        subsidyLockFilter.addAction(SubsidyUtility.ACTION_SUBSIDY_LOCK_CLIENT);
        subsidyLockFilter.addAction(
                SubsidyUtility.ACTION_SUBSIDY_LOCK_INTERNAL);

        mContext.registerReceiver(mSubsidyLockReceiver, subsidyLockFilter,
                    SubsidyUtility.BROADCAST_PERMISSION, null);
        setDefaultSubsidyState(context);
    }

    public static SubsidyController getInstance(Context context) {
        if (null == sSubsidyController) {
            sSubsidyController = new SubsidyController(context);
        }
        return sSubsidyController;
    }

    /*
     * Every reboot/factory reset, configuring screen is
     * shown until a valid intent received from the client.
     * If the device is Ap unlocked before reboot, subsidy
     * lock won't happen after boot completed and act according
     * to the intent received from the client.
     */
    private void setDefaultSubsidyState(Context context) {
        int state = SubsidyUtility.getSubsidyLockStatus(context);
        if (SubsidyUtility.shouldShowSubsidyLock(context)) {
            mCurrentSubsidyState = new ConfiguringScreenState();
        }
        if (state == SubsidyLockState.AP_UNLOCKED) {
            mCurrentSubsidyState = new ApUnlockedState();
        }
        if (state == SubsidyLockState.DEVICE_UNLOCKED) {
            mCurrentSubsidyState = new DeviceUnlockedState();
        }
        if (mCurrentSubsidyState != null) {
            mCurrentSubsidyState.init(mContext);
        }
    }

    private final BroadcastReceiver mSubsidyLockReceiver =
        new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {

                Log.d(TAG, "Received intent for SubsidyLock feature");

                Bundle bundle = intent.getExtras();
                if (null != bundle) {
                    for (String key : bundle.keySet()) {
                         Log.d(TAG, "Intent "+String.format("%s", key));
                    }
                } else {
                    Log.d(TAG, "Received intent bundle is null");
                }

                if (!mStopStateTransitions) {
                    boolean isValid = processIntent(intent);
                    Log.d(TAG, "Received different intent = "+ isValid);
                    Log.d(TAG, "Previous state progress is still on = "+ mPreviousSubsidyState
                            .getInProgressState());
                    if (isValid || mPreviousSubsidyState.getInProgressState()) {
                        KeyguardUpdateMonitor
                                .getInstance(mContext)
                                .dispatchSubsidyLockStateChanged(
                                        mCurrentSubsidyState.isLocked());
                    }
                }
            }
        };

    public SubsidyState getCurrentSubsidyState() {
        return mCurrentSubsidyState;
    }

    private boolean processIntent(Intent intent) {
        mPreviousSubsidyState = mCurrentSubsidyState;

        if (intent.getBooleanExtra(
                    SubsidyUtility.EXTRA_INTENT_KEY_LOCK_SCREEN, false)) {
            mCurrentSubsidyState = new UnlockScreenState();
        } else if (intent.getBooleanExtra(
                SubsidyUtility.EXTRA_INTENT_KEY_SWITCH_SIM_SCREEN, false)) {
            mCurrentSubsidyState = new SwitchSimScreenState();
        } else if (intent.getBooleanExtra(
                    SubsidyUtility.EXTRA_INTENT_KEY_UNLOCK_SCREEN, false)) {
            mCurrentSubsidyState = new ApUnlockedState();
        } else if (intent.getBooleanExtra(
                    SubsidyUtility.EXTRA_INTENT_KEY_ENTER_CODE_SCREEN, false)) {
            boolean isPinVisible = intent.getBooleanExtra(
                                   SubsidyUtility.EXTRA_INTENT_KEY_SHOW_PIN,
                                   false);
            Log.d(TAG, "Show keypad to enter pin = "+isPinVisible);
            if (!(mCurrentSubsidyState instanceof DeviceLockedState)) {
                mCurrentSubsidyState = new DeviceLockedState();
            }
            ((DeviceLockedState) mCurrentSubsidyState)
                    .setKeypadViewVisible(isPinVisible);
        } else if (intent.getBooleanExtra(
                    SubsidyUtility.EXTRA_INTENT_KEY_UNLOCK_PERMANENT, false)) {
            mCurrentSubsidyState = new DeviceUnlockedState();
        } else {
            return false;
        }

        if (mPreviousSubsidyState.getClass().equals(
                mCurrentSubsidyState.getClass())
                && !mCurrentSubsidyState.isAllowDuplicateIntents()) {
            Log.d(TAG, "Current state is same as previous so return");
            return false;
        }

        mCurrentSubsidyState.init(mContext);

        return true;
    }

    public void setDeviceUnlocked() {
        mCurrentSubsidyState = new DeviceUnlockedState();
        mCurrentSubsidyState.init(mContext);
        KeyguardUpdateMonitor.getInstance(mContext)
                   .dispatchSubsidyLockStateChanged(false);
    }

    public void stopStateTransitions(boolean enable) {
        mStopStateTransitions = enable;
    }

    public int getCurrentSubsidyViewId() {
        int viewId = 0;
        if (mCurrentSubsidyState != null) {
            viewId = mCurrentSubsidyState.getViewId();
        }
        if (mPreviousSubsidyState != null && viewId == 0) {
            viewId = mPreviousSubsidyState.getViewId();
        }
        if (viewId == 0) {
            mCurrentSubsidyState = new ConfiguringScreenState();
            viewId = mCurrentSubsidyState.getViewId();
        }
        return viewId;
    }

    public int getCurrentSubsidyLayoutId() {
        int layoutId = 0;
        if (mCurrentSubsidyState != null) {
            layoutId = mCurrentSubsidyState.getLayoutId();
        }
        if (mPreviousSubsidyState != null && layoutId == 0) {
            layoutId = mPreviousSubsidyState.getLayoutId();
        }
        if (layoutId == 0) {
            mCurrentSubsidyState = new ConfiguringScreenState();
            layoutId = mCurrentSubsidyState.getLayoutId();
        }
        return layoutId;
    }

    public abstract class SubsidyState {
        protected int mState;
        protected int mLayoutId;
        protected int mViewId;
        protected String mExtraLaunchIntent;
        private boolean mIsUserRequestInProgress;

        protected void init(final Context context) {
            final ConnectivityManager connectivityManager =
                    (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            new Thread(new Runnable() {
                public void run() {
                    disableWifiTethering(context);
                    disableUsbTethering(context, connectivityManager);
                    disableBluetooth();
                }
            }).start();
        }

        protected Intent getLaunchIntent() {
            return null;
        }

        protected String getLaunchIntent(int resId) {
            return null;
        }

        protected abstract int getLayoutId();

        protected abstract int getViewId();

        protected boolean isLocked() {
            return false;
        }

        protected void setInProgressState(boolean isProgress) {
            mIsUserRequestInProgress = isProgress;
        }

        protected boolean getInProgressState() {
            return mIsUserRequestInProgress;
        }

        protected boolean isAllowDuplicateIntents() {
            return false;
        }

        private void disableWifiTethering(Context context) {
            TetherUtil.setWifiTethering(false, context);
        }

        private void disableUsbTethering(Context context,
                                         ConnectivityManager cm) {
            cm.setUsbTethering(false);
        }

        private void disableBluetooth() {
            try {
                BluetoothAdapter mybluetooth =
                    BluetoothAdapter.getDefaultAdapter();
                mybluetooth.disable();
            } catch (Exception e) {
                Log.e(TAG, "Exception while disabling bluetooth " + e);
            }
        }
    }

    class ConfiguringScreenState extends SubsidyState {

        public ConfiguringScreenState() {
           Log.d(TAG, " In ConfiguringScreenState");

            mLayoutId = R.layout.keyguard_subsidy_configuring_view;
            mViewId = R.id.keyguard_subsidy_configuring_view;
        }

        @Override
        protected void init(Context context) {
            super.init(context);
            SubsidyUtility.writeSubsidyLockStatus(context,
                    SubsidyLockState.SUBSIDY_STATUS_UNKNOWN);
        }

        @Override
        protected int getLayoutId() {
            return mLayoutId;
        }

        @Override
        protected int getViewId() {
            return mViewId;
        }
    }

    public abstract class ApLockedState extends SubsidyState {
        @Override
        protected void init(Context context) {
            super.init(context);
            SubsidyUtility.writeSubsidyLockStatus(context,
                    SubsidyLockState.AP_LOCKED);
        }

        @Override
        protected boolean isLocked() {
            return true;
        }
    }

    class UnlockScreenState extends ApLockedState {
        public UnlockScreenState() {
           Log.d(TAG, " In UnlockScreenState");

            mLayoutId = R.layout.keyguard_subsidy_lock_view;
            mViewId = R.id.keyguard_subsidy_lock_view;
            mExtraLaunchIntent = SubsidyUtility.EXTRA_INTENT_KEY_UNLOCK;
        }

        @Override
        protected int getLayoutId() {
            return mLayoutId;
        }

        @Override
        protected int getViewId() {
            return mViewId;
        }

        @Override
        protected Intent getLaunchIntent() {
            Intent intent = null;
            if(SubsidyUtility.isDataConnectionActive(mContext)) {
                Log.w(TAG, "Data connection is now active!!!");
                intent = new Intent(
                        SubsidyUtility.ACTION_USER_REQUEST);
                intent.setPackage(mContext.getResources().getString(
                        R.string.config_slc_package_name));
                intent.putExtra(mExtraLaunchIntent, true);
            } else {
                Log.w(TAG, "No active data connection");
                intent = new Intent(
                        SubsidyUtility.ACTION_SUBSIDY_LOCK_INTERNAL);
                intent.putExtra(
                        SubsidyUtility.EXTRA_INTENT_KEY_ENTER_CODE_SCREEN,
                        true);
            }
            return intent;
        }
    }

    class SwitchSimScreenState extends ApLockedState {
        public SwitchSimScreenState() {
            Log.d(TAG, " In SwitchSimScreenState");

            mLayoutId = R.layout.keyguard_subsidy_switchsim_view;
            mViewId = R.id.keyguard_subsidy_switchsim_view;
        }

        @Override
        protected int getLayoutId() {
            return mLayoutId;
        }

        @Override
        protected int getViewId() {
            return mViewId;
        }

        public int getModifiedPrimarySimSlot(
                Context context, int currentSlotID) {
            int newSlodId =
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX;
            if (currentSlotID >= 0) {
                newSlodId = (currentSlotID == 0) ? 1 : 0;
            }
            return newSlodId;
        }
    }

    class ApUnlockedState extends SubsidyState {
        public ApUnlockedState() {
            Log.d(TAG, " In AppUnlockedState");
        }

        @Override
        protected void init(Context context) {
            SubsidyUtility.writeSubsidyLockStatus(context,
                    SubsidyLockState.AP_UNLOCKED);
        }

        @Override
        protected boolean isLocked() {
            return false;
        }

        @Override
        protected int getLayoutId() {
            return 0;
        }

        @Override
        protected int getViewId() {
            return 0;
        }
    }

    class DeviceLockedState extends SubsidyState {
        boolean mIsKeypadViewVisible;
        public DeviceLockedState() {
            Log.d(TAG, " In DeviceLockedState");

            mLayoutId = R.layout.keyguard_subsidy_pin_view;
            mViewId = R.id.keyguard_subsidy_pin_view;
            mExtraLaunchIntent = SubsidyUtility.EXTRA_INTENT_KEY_PIN_VERIFIED;
        }

        @Override
        protected void init(Context context) {
            super.init(context);
            SubsidyUtility.writeSubsidyLockStatus(context,
                    SubsidyLockState.DEVICE_LOCKED);
        }

        @Override
        protected Intent getLaunchIntent() {
            Intent intent = new Intent(SubsidyUtility.ACTION_USER_REQUEST);
            intent.setPackage(mContext.getResources().getString(
                    R.string.config_slc_package_name));
            intent.putExtra(mExtraLaunchIntent, true);
            return intent;
        }

        @Override
        protected boolean isLocked() {
            return true;
        }

        @Override
        protected int getLayoutId() {
            return mLayoutId;
        }

        @Override
        protected int getViewId() {
            return mViewId;
        }

        @Override
        protected boolean isAllowDuplicateIntents() {
            return true;
        }

        private void setKeypadViewVisible(boolean isVisible) {
            mIsKeypadViewVisible = isVisible;
        }

        public boolean getKeypadViewVisible() {
            return mIsKeypadViewVisible;
        }
    }

    class DeviceUnlockedState extends SubsidyState {

        public DeviceUnlockedState() {
            Log.d(TAG, " In DeviceUnlockedState");
        }

        @Override
        protected void init(Context context) {
            SubsidyUtility.writeSubsidyLockStatus(context,
                    SubsidyLockState.DEVICE_UNLOCKED);
            Toast toast =
                Toast.makeText(context, R.string.unlock_toast,
                        Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.getWindowParams().type =
                WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
            toast.show();
        }

        @Override
        protected boolean isLocked() {
            return false;
        }

        @Override
        protected int getLayoutId() {
            return 0;
        }

        @Override
        protected int getViewId() {
            return 0;
        }
    }

    public int getPrimaryCardSlotId() {
        try {
            IExtTelephony extTelephony =
                    IExtTelephony.Stub.asInterface(ServiceManager
                            .getService("extphone"));
            return extTelephony
                    .getPrimaryCarrierSlotId();
        } catch (RemoteException e) {
            Log.e(TAG,
                    "Exception for getPrimaryCarrierSlotId:", e);
        }
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    public int isEnableDataButtonVisible(Context context) {
        int primarySimSlot = getPrimaryCardSlotId();
        if (primarySimSlot != -1 /* White listed sim */) {
            Log.w(TAG, "Primary sim slot is white listed");
            return (isMobileDataEnabled(primarySimSlot) && !SubsidyUtility
                    .isAirplaneMode(context)) ? View.GONE : View.VISIBLE;
        }
        return View.GONE;
    }

    public boolean isMobileDataEnabled(int slotId) {
        if (mTelephonyManager != null) {
            Log.w(TAG, "Telephony Manager is not null");
            int[] subIds = SubscriptionManager.getSubId(slotId);
            if (subIds != null && subIds.length > 0) {
                return mTelephonyManager.getDataEnabled(subIds[0]);
            } else {
                Log.d(TAG, "isMobileDataEnabled: no valid subs");
            }
        }
        return false;
    }

    public void enableMobileData() {
        // Enable default data subscription
        if (mTelephonyManager != null) {
            int primarySimSlot = getPrimaryCardSlotId();
            Log.w(TAG, "Telephony Manager not null and primaryslot = "+primarySimSlot);
            if (primarySimSlot != -1 /* White listed sim */) {
                Log.w(TAG, "Enable data primary sim is white listed");
                int[] subIds = SubscriptionManager.getSubId(primarySimSlot);
                if (subIds != null && subIds.length > 0) {
                    mTelephonyManager.setDataEnabled(subIds[0], true);
                } else {
                    Log.d(TAG, "enableMobileData: no valid subs");
                }
            }
        }
    }
}
