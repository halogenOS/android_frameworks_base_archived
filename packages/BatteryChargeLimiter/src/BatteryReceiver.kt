package org.halogenos.extra.batterychargelimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

class BatteryReceiver : BroadcastReceiver() {
    private val tag get() = this::class.simpleName

    private fun getBatteryLevel(batteryIntent: Intent): Int {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        Log.d(tag, "Level: $level, scale $scale")

        return if (level == -1 || scale == -1) {
            -1
        } else {
            level * 100 / scale
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            Log.i(tag, "Received battery intent")
            context.startService(
                Intent(context.applicationContext, BatteryChargeLimiterService::class.java).apply {
                    putExtra("level", getBatteryLevel(intent))
                    putExtra("status", intent.getIntExtra(BatteryManager.EXTRA_CHARGING_STATUS, -1))
                }
            )
        }.onFailure { it.printStackTrace() }
    }
}
