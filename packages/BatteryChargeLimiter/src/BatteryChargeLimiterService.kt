package org.halogenos.extra.batterychargelimit

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ServiceManager
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import vendor.lineage.health.ChargingControlSupportedMode
import vendor.lineage.health.IChargingControl


class BatteryChargeLimiterService : Service() {
    private val limitSettingsKey = "BATTERY_CHARGE_LIMIT"
    private val tag get() = this::class.simpleName
    private val batteryManager by lazy { getSystemService(BatteryManager::class.java) }
    private var percentageLimit
        get() = runCatching {
            contentResolver?.let { cr -> Settings.Secure.getInt(cr, limitSettingsKey, 100) } ?: 100
        }.onFailure { it.printStackTrace() }.getOrElse { 100 }
        set(value) {
            runCatching {
                Settings.Secure.putInt(contentResolver, limitSettingsKey, value)
            }.onFailure { it.printStackTrace() }
        }
    private val resumePercentage = percentageLimit - 2
    private val chargingControl: IChargingControl? by lazy {
        IChargingControl.Stub.asInterface(
            ServiceManager.waitForDeclaredService(
                IChargingControl.DESCRIPTOR + "/default"))
    }
    private var registeredObserver = false
    private val IChargingControl.isToggleSupported get() = supportedMode and ChargingControlSupportedMode.TOGGLE != 0
    private val IChargingControl.isBypassSupported get() = supportedMode and ChargingControlSupportedMode.BYPASS != 0

    private inner class BackgroundLooperThread : Thread() {
        var handler: Handler? = null

        override fun run() {
            Looper.prepare()
            handler = Handler(Looper.myLooper())
            Looper.loop()
        }
    }

    private val backgroundHandler by lazy {
        BackgroundLooperThread().apply {
            start()
            handler
        }
    }

    private var currentBatteryPercentage = 100
        set(value) {
            field = value
            runCatching {
                onBatteryStateChanged()
            }.onFailure { it.printStackTrace() }
        }
    private var isCharging = false
        set(value) {
            field = value
            runCatching {
                onBatteryStateChanged()
            }.onFailure { it.printStackTrace() }
        }

    private fun registerObserverIfNecessary() = runCatching {
        if (registeredObserver) {
            return@runCatching
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(limitSettingsKey),
            false, object : ContentObserver(mainThreadHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d(tag, "Received setting change")
                    onBatteryStateChanged()
                }
            }
        )
        registeredObserver = true
    }.onFailure { it.printStackTrace() }

    override fun onCreate() {
        super.onCreate()
        registerObserverIfNecessary()
        //val batteryLevelFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        //registerReceiver(BatteryReceiver(), batteryLevelFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "Received start command")
        registerObserverIfNecessary()
        intent?.also { i ->
            val level = i.getIntExtra("level", -1)
            if (level == -1) return@also
            val status = i.getIntExtra("status", -1)
            if (status == -1) return@also

            currentBatteryPercentage = level
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun handleBypassMode() = chargingControl?.also { chargingControl ->
        when {
            percentageLimit == 100 -> {
                chargingControl.setChargingDeadline(-1)
            }
            currentBatteryPercentage >= percentageLimit -> {
                if (isCharging) {
                    Log.i(tag, ">= $percentageLimit, disabling charge (bypass)")
                    chargingControl.setChargingDeadline(DateUtils.HOUR_IN_MILLIS)
                }
            }
            currentBatteryPercentage < resumePercentage -> {
                Log.i(tag, "< $resumePercentage, enabling charge (bypass)")
                chargingControl.setChargingDeadline(-1)
            }
        }
    }

    private fun handleToggleMode() = chargingControl?.also { chargingControl ->
        when {
            percentageLimit == 100 -> {
                if (!chargingControl.chargingEnabled) {
                    chargingControl.chargingEnabled = true
                }
            }
            currentBatteryPercentage >= percentageLimit -> {
                Log.i(tag, ">= $percentageLimit, disabling charge (toggle)")
                if (chargingControl.chargingEnabled || isCharging) {
                    chargingControl.chargingEnabled = false
                }
            }
            currentBatteryPercentage < resumePercentage -> {
                Log.i(tag, "< $resumePercentage, enabling charge (toggle)")
                if (!chargingControl.chargingEnabled || !isCharging) {
                    chargingControl.chargingEnabled = true
                }
            }
        }
    }

    private fun onBatteryStateChanged() = chargingControl?.let { chargingControl ->
        Log.i(tag, "Battery level: $currentBatteryPercentage, charging: $isCharging")
        if (!isAvailable()) {
            return@let
        }
        backgroundHandler.handler?.post {
            when {
                chargingControl.isBypassSupported -> handleBypassMode()
                chargingControl.isToggleSupported -> handleToggleMode()
            }

        } ?: Log.i(tag, "Waiting for handler")
    }

    private fun isAvailable(): Boolean = runCatching {
        return batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_STATUS
        ) in arrayOf(
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_FULL
        ) && runCatching { chargingControl }.isSuccess &&
                chargingControl?.let { c ->
                    (c.isToggleSupported || c.isBypassSupported).also { supported ->
                        if (supported) {
                            Log.i(tag, "Charge control supported: ${c.supportedMode}")
                        } else {
                            Log.i(tag, "Charge control not supported, instead supports: ${c.supportedMode}")
                        }
                    }
                } ?: false
    }.getOrElse { false }
}