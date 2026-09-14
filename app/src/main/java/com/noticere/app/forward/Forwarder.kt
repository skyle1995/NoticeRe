package com.noticere.app.forward

import android.content.Context
import android.util.Base64
import android.util.Log
import com.noticere.app.Config
import com.noticere.app.RunLog
import com.noticere.app.model.ForwardMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 消息发送器：先把消息落盘（pending_messages.json），再立即触发发送；
 * 发送失败的消息保留在磁盘上，由启动时 + 定时循环持续重发（至少一次语义）。
 */
object Forwarder {

    private const val TAG = "Forwarder"
    private const val TARGET_BARK = "bark"
    private const val TARGET_WEBHOOK = "webhook"
    private const val FLUSH_INTERVAL_MS = 60_000L

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private lateinit var pendingFile: File
    private val queueMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val kick = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        pendingFile = File(context.applicationContext.filesDir, "pending_messages.json")
        scope.launch { immediateLoop() }
        scope.launch { periodicLoop() }
        scope.launch { flush() } // 启动即先清一次积压
    }

    /** 投递一条消息：先持久化再触发发送。 */
    fun send(msg: ForwardMessage) {
        val targets = mutableListOf<String>()
        if (Config.barkEnabled && Config.barkKey.isNotBlank()) targets += TARGET_BARK
        if (Config.webhookEnabled && Config.webhookUrl.isNotBlank()) targets += TARGET_WEBHOOK
        if (targets.isEmpty()) return

        scope.launch {
            queueMutex.withLock {
                val items = load()
                items.add(PendingItem(msg, targets))
                save(items)
            }
            kick.trySend(Unit)
        }
    }

    private suspend fun immediateLoop() {
        for (u in kick) {
            flush()
        }
    }

    private suspend fun periodicLoop() {
        while (true) {
            delay(FLUSH_INTERVAL_MS)
            flush()
        }
    }

    /** 把积压队列全部尝试发送一遍；成功的从队列移除。 */
    private suspend fun flush() = queueMutex.withLock {
        val items = load()
        if (items.isEmpty()) return
        var changed = false
        val it = items.iterator()
        while (it.hasNext()) {
            val item = it.next()
            val remain = item.targets.filter { t -> !tryTarget(item.msg, t) }.toMutableList()
            if (remain.isEmpty()) {
                it.remove()
                changed = true
            } else if (remain.size != item.targets.size) {
                item.targets = remain
                changed = true
            }
        }
        if (changed) save(items)
    }

    private fun tryTarget(msg: ForwardMessage, target: String): Boolean = when (target) {
        TARGET_BARK -> withRetry("bark") { sendBark(msg) }
        TARGET_WEBHOOK -> withRetry("webhook") { sendWebhook(msg) }
        else -> false
    }

    // ---- 发送实现 ----

    /** Bark：GET {server}/{key}/{title}/{body}?group=...&level=...&sound=... */
    private fun sendBark(msg: ForwardMessage) {
        val params = buildString {
            append("group=NoticeRe")
            if (Config.barkLevel.isNotBlank()) append("&level=").append(enc(Config.barkLevel))
            if (Config.barkSound.isNotBlank()) append("&sound=").append(enc(Config.barkSound))
        }
        val url = "${Config.barkServer.trimEnd('/')}/${Config.barkKey}/${enc(msg.title)}/${enc(msg.body)}?$params"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        }
    }

    private fun sendWebhook(msg: ForwardMessage) {
        when (WebhookPlatform.from(Config.webhookPlatform)) {
            WebhookPlatform.GENERIC -> sendGeneric(msg)
            WebhookPlatform.DINGTALK -> sendDingTalk(msg)
            WebhookPlatform.FEISHU -> sendFeishu(msg)
            WebhookPlatform.SERVERCHAN -> sendServerChan(msg)
        }
    }

    private fun sendGeneric(msg: ForwardMessage) {
        val json = buildGenericJson(msg).toString()
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder().url(Config.webhookUrl).post(body)
        applyCustomHeaders(builder)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        }
    }

    private fun sendDingTalk(msg: ForwardMessage) {
        val content = "${msg.title}\n${msg.body}".trim()
        val json = JSONObject()
            .put("msgtype", "text")
            .put("text", JSONObject().put("content", content))
            .toString()
        post(withDingTalkSign(Config.webhookUrl, Config.webhookSecret), json)
    }

    private fun sendFeishu(msg: ForwardMessage) {
        val content = "${msg.title}\n${msg.body}".trim()
        val obj = JSONObject()
            .put("msg_type", "text")
            .put("content", JSONObject().put("text", content))
        val secret = Config.webhookSecret
        if (secret.isNotBlank()) {
            val ts = (System.currentTimeMillis() / 1000).toString()
            obj.put("timestamp", ts)
            obj.put("sign", feishuSign(ts, secret))
        }
        post(Config.webhookUrl, obj.toString())
    }

    private fun sendServerChan(msg: ForwardMessage) {
        val json = JSONObject()
            .put("title", msg.title)
            .put("desp", msg.body)
            .toString()
        post(Config.webhookUrl, json)
    }

    private fun post(url: String, json: String) {
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        }
    }

    private fun buildGenericJson(msg: ForwardMessage): JSONObject = JSONObject().apply {
        put("type", msg.type)
        put("source", msg.source)
        put("title", msg.title)
        put("body", msg.body)
        put("package", msg.packageName ?: JSONObject.NULL)
        put("sim", msg.sim ?: JSONObject.NULL)
        put("timestamp", msg.timestamp)
    }

    private fun applyCustomHeaders(builder: Request.Builder) {
        Config.webhookHeaders.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val name = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (name.isNotEmpty()) builder.addHeader(name, value)
            }
        }
    }

    // ---- 加签 ----

    private fun withDingTalkSign(baseUrl: String, secret: String): String {
        if (secret.isBlank()) return baseUrl
        val ts = System.currentTimeMillis().toString()
        val sign = URLEncoder.encode(
            Base64.encodeToString(hmacSha256(secret, "$ts\n$secret"), Base64.NO_WRAP),
            "UTF-8"
        )
        return baseUrl + (if (baseUrl.contains('?')) "&" else "?") + "timestamp=$ts&sign=$sign"
    }

    private fun feishuSign(timestamp: String, secret: String): String =
        Base64.encodeToString(hmacSha256("$timestamp\n$secret", ""), Base64.NO_WRAP)

    private fun hmacSha256(key: String, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---- 重试 ----

    private fun withRetry(target: String, block: () -> Unit): Boolean {
        var last: Throwable? = null
        repeat(3) { i ->
            try {
                block()
                return true
            } catch (e: Exception) {
                last = e
                if (i < 2) {
                    try {
                        Thread.sleep((i + 1) * 2000L)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }
        Log.w(TAG, "$target 发送失败: ${last?.message}")
        RunLog.log("$target 发送失败：${last?.message}")
        return false
    }

    // ---- 持久化 ----

    private class PendingItem(var msg: ForwardMessage, var targets: MutableList<String>)

    private fun load(): MutableList<PendingItem> {
        if (!pendingFile.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(pendingFile.readText())
            val out = mutableListOf<PendingItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msg = msgFromJson(o.getJSONObject("msg"))
                val targets = o.getJSONArray("targets").let { a ->
                    (0 until a.length()).map { a.getString(it) }.toMutableList()
                }
                out.add(PendingItem(msg, targets))
            }
            out
        }.getOrElse {
            Log.w(TAG, "读取积压队列失败: ${it.message}")
            mutableListOf()
        }
    }

    private fun save(items: List<PendingItem>) {
        runCatching {
            val arr = JSONArray()
            items.forEach { item ->
                val o = JSONObject()
                o.put("msg", msgToJson(item.msg))
                o.put("targets", JSONArray(item.targets))
                arr.put(o)
            }
            pendingFile.writeText(arr.toString())
        }.onFailure { Log.w(TAG, "写入积压队列失败: ${it.message}") }
    }

    private fun msgToJson(m: ForwardMessage): JSONObject = JSONObject().apply {
        put("type", m.type)
        put("source", m.source)
        put("title", m.title)
        put("body", m.body)
        put("package", m.packageName ?: JSONObject.NULL)
        put("sim", m.sim ?: JSONObject.NULL)
        put("timestamp", m.timestamp)
    }

    private fun msgFromJson(o: JSONObject): ForwardMessage = ForwardMessage(
        type = o.optString("type"),
        source = o.optString("source"),
        title = o.optString("title"),
        body = o.optString("body"),
        packageName = o.optString("package").takeIf { it.isNotEmpty() },
        sim = o.optString("sim").takeIf { it.isNotEmpty() },
        timestamp = o.optLong("timestamp")
    )
}
