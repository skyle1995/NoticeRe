package com.noticere.app.sms

import android.content.Context
import android.database.ContentObserver
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.noticere.app.Config
import com.noticere.app.ForwardEngine
import com.noticere.app.RunLog
import com.noticere.app.filter.Filters
import com.noticere.app.forward.Forwarder
import com.noticere.app.model.ForwardMessage
import com.noticere.app.model.SmsRecord
import com.noticere.app.root.RootDbReader
import com.noticere.app.root.SimInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 短信转发器：监听 content://sms 变化，通过 root 读取 mmssms.db 中的新收件短信。
 */
class SmsForwarder(private val context: Context) {

    companion object {
        private const val TAG = "SmsForwarder"
        private const val SMS_TYPE_INBOX = 1

        // 不同 ROM / 多用户下的候选路径
        private val DB_PATHS = listOf(
            "/data/data/com.android.providers.telephony/databases/mmssms.db",
            "/data/user_de/0/com.android.providers.telephony/databases/mmssms.db"
        )
    }

    private val reader = RootDbReader(context)
    private val simInfo = SimInfoReader(context)
    private val mutex = Mutex()
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            ForwardEngine.onSmsChanged()
        }
    }

    fun register() {
        if (registered) return
        runCatching {
            context.contentResolver.registerContentObserver(Uri.parse("content://sms"), true, observer)
        }
        runCatching {
            context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms"), true, observer)
        }
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        registered = false
    }

    /** 读取并转发新增短信。内部用 Mutex 防止并发重复转发。 */
    suspend fun checkNow() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!Config.smsEnabled) return@withContext

            val records = readNew(Config.lastSmsId)

            if (!Config.smsBaselineDone) {
                // 首次运行只建立基线，避免把历史短信全部转发出去。
                // 仅当真的读到数据时才标记完成，避免「读取失败却误标记已基线」。
                val last = records.lastOrNull()
                if (last != null) {
                    Config.lastSmsId = last.id
                    Config.smsBaselineDone = true
                }
                return@withContext
            }

            if (records.isEmpty()) return@withContext
            for (r in records) {
                if (r.type == SMS_TYPE_INBOX
                    && Filters.numberAllowed(r.address, Config.smsNumberFilter)
                    && Filters.keywordAllowed(r.body, Config.smsKeywordFilter)
                ) {
                    Forwarder.send(ForwardMessage.fromSms(r, simInfo.phoneNumberFor(r.simId)))
                    RunLog.log("转发短信 ${r.address}")
                }
            }
            Config.lastSmsId = records.last().id
        }
    }

    private fun readNew(afterId: Long): List<SmsRecord> {
        for (path in DB_PATHS) {
            val snap = reader.snapshot(path) ?: continue
            val db = reader.openReadOnly(snap) ?: continue
            val list = db.use { querySms(it, afterId) }
            if (list != null) return list
        }
        Log.w(TAG, "无法读取短信数据库")
        RunLog.log("读取短信数据库失败")
        return emptyList()
    }

    private fun querySms(db: SQLiteDatabase, afterId: Long): List<SmsRecord>? = runCatching {
        db.rawQuery(
            "SELECT _id, address, body, date, type, sim_id FROM sms WHERE _id > ? ORDER BY _id ASC",
            arrayOf(afterId.toString())
        ).use { c ->
            val iId = c.getColumnIndexOrThrow("_id")
            val iAddr = c.getColumnIndexOrThrow("address")
            val iBody = c.getColumnIndexOrThrow("body")
            val iDate = c.getColumnIndexOrThrow("date")
            val iType = c.getColumnIndexOrThrow("type")
            val iSim = c.getColumnIndex("sim_id")
            val out = ArrayList<SmsRecord>()
            while (c.moveToNext()) {
                out.add(
                    SmsRecord(
                        id = c.getLong(iId),
                        address = c.getString(iAddr),
                        body = c.getString(iBody),
                        date = c.getLong(iDate),
                        type = c.getInt(iType),
                        simId = if (iSim >= 0) c.getInt(iSim) else -1
                    )
                )
            }
            out
        }
    }.getOrNull()
}
