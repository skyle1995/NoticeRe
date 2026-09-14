package com.noticere.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noticere.app.Config

/**
 * 保活看门狗：由 AlarmManager 定时触发，若短信/未接来电转发开启则确保前台服务在运行。
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!Config.smsEnabled && !Config.callEnabled) return
        val i = Intent(context, ForwardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i)
        } else {
            context.startService(i)
        }
    }
}
