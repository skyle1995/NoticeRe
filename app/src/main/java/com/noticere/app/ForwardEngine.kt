package com.noticere.app

import android.content.Context
import com.noticere.app.call.CallForwarder
import com.noticere.app.sms.SmsForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 转发引擎：负责注册短信/通话的 ContentObserver，并用定时轮询兜底。
 * 进程存活期间一直运行（由前台服务保持存活）。
 */
object ForwardEngine {

    private const val POLL_INTERVAL_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var smsForwarder: SmsForwarder? = null
    private var callForwarder: CallForwarder? = null
    private var pollJob: Job? = null

    fun init(context: Context) {
        if (smsForwarder != null) return
        val app = context.applicationContext
        smsForwarder = SmsForwarder(app)
        callForwarder = CallForwarder(app)
        smsForwarder?.register()
        callForwarder?.register()
        startPolling()
        RunLog.log("转发引擎已启动")
    }

    /** 短信数据库发生变化（ContentObserver 触发）。 */
    fun onSmsChanged() {
        scope.launch { runCatching { smsForwarder?.checkNow() } }
    }

    /** 通话记录发生变化（ContentObserver 触发）。 */
    fun onCallChanged() {
        scope.launch { runCatching { callForwarder?.checkNow() } }
    }

    /** 配置变更后调用：立即按新配置检查一次并重启轮询。 */
    fun refresh() {
        startPolling()
        scope.launch {
            runCatching { if (Config.smsEnabled) smsForwarder?.checkNow() }
            runCatching { if (Config.callEnabled) callForwarder?.checkNow() }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                runCatching {
                    if (Config.smsEnabled) smsForwarder?.checkNow()
                    if (Config.callEnabled) callForwarder?.checkNow()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
