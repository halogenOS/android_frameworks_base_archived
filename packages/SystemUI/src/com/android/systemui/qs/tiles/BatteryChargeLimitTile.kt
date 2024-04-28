package com.android.systemui.qs.tiles

import android.content.Intent
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.ServiceManager
import android.provider.Settings
import android.service.quicksettings.Tile.*
import android.util.Log
import android.view.View
import com.android.internal.logging.MetricsLogger
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.SettingObserver
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.util.settings.SecureSettings
import vendor.lineage.health.ChargingControlSupportedMode
import vendor.lineage.health.IChargingControl
import java.io.PrintWriter
import javax.inject.Inject

class BatteryChargeLimitTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background private val backgroundLooper: Looper,
    @Main private val mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val batteryController: BatteryController,
    secureSettings: SecureSettings,
) : QSTileImpl<QSTile.State>(
    host,
    uiEventLogger,
    backgroundLooper,
    mainHandler,
    falsingManager,
    metricsLogger,
    statusBarStateController,
    activityStarter,
    qsLogger
), BatteryStateChangeCallback {

    private val limitSettingsKey = "BATTERY_CHARGE_LIMIT"
    private val icon = ResourceIcon.get(R.drawable.ic_battery_unknown)
    private val settingObserver: SettingObserver
    private var percentageLimit
        get() = Settings.Secure.getInt(mContext.contentResolver, limitSettingsKey, 100)
        set(value) { Settings.Secure.putInt(mContext.contentResolver, limitSettingsKey, value) }
    private val resumePercentage = percentageLimit - 2
    private val levels = listOf(90, 80, 75)
    private val chargingControl: IChargingControl? by lazy {
        IChargingControl.Stub.asInterface(
            ServiceManager.waitForDeclaredService(
                IChargingControl.DESCRIPTOR + "/default"))
    }
    private val backgroundHandler by lazy { Handler(backgroundLooper) }
    private var currentBatteryPercentage = 100
    private var isCharging = false
    private var isPluggedIn = false
    private val batteryManager by lazy { mContext.getSystemService(BatteryManager::class.java) }

    init {
        batteryController.observe(lifecycle, this)
        val currentUser = host.userContext.userId
        settingObserver = object : SettingObserver(
            secureSettings,
            mHandler,
            limitSettingsKey,
            currentUser
        ) {
            override fun handleValueChanged(value: Int, observedChange: Boolean) {
                handleBatteryLevelChange()
            }
        }
    }

    override fun newTileState(): QSTile.State {
        return BooleanState().apply {
            handlesLongClick = false
            label = tileLabel
        }
    }

    override fun handleClick(view: View?) { runCatching {
        percentageLimit = when (percentageLimit) {
            100 -> levels.first()
            in levels.dropLast(1) -> levels[levels.indexOf(percentageLimit) + 1]
            else -> 100
        }
        handleBatteryLevelChange()
    }.onFailure { it.printStackTrace() } }

    override fun handleUpdateState(state: QSTile.State, arg: Any?) { runCatching {
        state.icon = icon
        state.label = tileLabel
        state.secondaryLabel = "$percentageLimit% – ${
            if (chargingControl == null) mContext.getString(R.string.qsTileBatteryChargeLimitUnavailable)
            else if (chargingControl!!.chargingEnabled) mContext.getString(R.string.qsTileBatteryChargeLimitCharging)
            else mContext.getString(R.string.qsTileBatteryChargeLimitNotCharging)
        }"
        state.state =
            if (chargingControl == null) STATE_UNAVAILABLE
            else if (percentageLimit < 100) STATE_ACTIVE else STATE_INACTIVE
    }.onFailure {
        state.label = tileLabel
        state.secondaryLabel = "Error: ${it.message}"
        it.printStackTrace()
    } }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.qsTileBatteryChargeLimitLabel)
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    companion object {
        const val TILE_SPEC = "battery_charge_limit"
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        super<QSTileImpl>.dump(pw, args)
    }

    override fun onBatteryLevelChanged(batteryLevel: Int, pluggedIn: Boolean, charging: Boolean) {
        currentBatteryPercentage = batteryLevel
        isCharging = charging
        isPluggedIn = pluggedIn
        handleBatteryLevelChange()
    }

    private fun handleBatteryLevelChange() = chargingControl?.let { chargingControl ->
        Log.i(TAG, "Battery level: $currentBatteryPercentage, plugged in: $isPluggedIn, charging: $isCharging")
        backgroundHandler.post {
            if (currentBatteryPercentage >= percentageLimit) {
                Log.i(TAG, ">= $percentageLimit, disabling charge")
                if (chargingControl.chargingEnabled || isCharging || isPluggedIn) {
                    chargingControl.chargingEnabled = false
                }
            } else if (currentBatteryPercentage < resumePercentage) {
                Log.i(TAG, "< $resumePercentage, enabling charge")
                if (!chargingControl.chargingEnabled || !isCharging) {
                    chargingControl.chargingEnabled = true
                }
            } else if (isPluggedIn) {
                Log.i(TAG, "$resumePercentage < $currentBatteryPercentage < $percentageLimit, waiting to resume")
            }
            mainHandler.post {
                handleRefreshState(null)
            }
        }
    }

    override fun isAvailable(): Boolean {
        return batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_STATUS
        ) in arrayOf(
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_FULL
        ) && runCatching { chargingControl }.isSuccess &&
                chargingControl?.let { c ->
                    (c.supportedMode and ChargingControlSupportedMode.TOGGLE != 0).also { supported ->
                        if (!supported) {
                            Log.i(TAG, "Toggle charge control not supported, instead supports: ${c.supportedMode}")
                        }
                    }
                } ?: false
    }
}
