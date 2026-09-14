package com.noticere.app.call

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
import com.noticere.app.model.CallRecord
import com.noticere.app.model.ForwardMessage
import com.noticere.app.root.RootDbReader
import com.noticere.app.root.SimInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 未接来电转发器：监听 content://call_log/calls 变化，通过 root 读取 calls 表。
 */
class CallForwarder(private val context: Context) {

    companion object {
        private const val TAG = "CallForwarder"
        // 未接(3)、拒接(5)、拦截(6) 都作为「未接来电」转发
        private val FORWARD_TYPES = setOf(3, 5, 6)

        // 不同 ROM / 多用户 / 不同版本下的候选路径
        private val DB_PATHS = listOf(
            "/data/data/com.android.providers.contacts/databases/calllog.db",
            "/data/user_de/0/com.android.providers.contacts/databases/calllog.db",
            "/data/data/com.android.providers.contacts/databases/contacts2.db",
            "/data/user_de/0/com.android.providers.contacts/databases/contacts2.db"
        )
    }

    private val reader = RootDbReader(context)
    private val simInfo = SimInfoReader(context)
    private val mutex = Mutex()
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            ForwardEngine.onCallChanged()
        }
    }

    fun register() {
        if (registered) return
        runCatching {
            context.contentResolver.registerContentObserver(
                Uri.parse("content://call_log/calls"), true, observer
            )
        }
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        registered = false
    }

    /** 读取并转发新增未接来电。内部用 Mutex 防止并发重复转发。 */
    suspend fun checkNow() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!Config.callEnabled) return@withContext

            val records = readNew(Config.lastCallId)

            if (!Config.callBaselineDone) {
                // 首次运行只建立基线，避免把历史未接来电全部转发出去。
                // 仅当真的读到数据时才标记完成，避免「读取失败却误标记已基线」。
                val last = records.lastOrNull()
                if (last != null) {
                    Config.lastCallId = last.id
                    Config.callBaselineDone = true
                }
                return@withContext
            }

            if (records.isEmpty()) return@withContext
            for (r in records) {
                if (r.type in FORWARD_TYPES
                    && Filters.numberAllowed(r.number, Config.callNumberFilter)
                ) {
                    Forwarder.send(ForwardMessage.fromCall(r, simInfo.phoneNumberFor(r.simId)))
                    RunLog.log("转发来电 ${r.number}")
                }
            }
            Config.lastCallId = records.last().id
        }
    }

    private fun readNew(afterId: Long): List<CallRecord> {
        for (path in DB_PATHS) {
            val snap = reader.snapshot(path) ?: continue
            val db = reader.openReadOnly(snap) ?: continue
            val list = db.use { queryCalls(it, afterId) }
            if (list != null) return list
        }
        Log.w(TAG, "无法读取通话记录数据库")
        RunLog.log("读取通话记录数据库失败")
        return emptyList()
    }

    private fun queryCalls(db: SQLiteDatabase, afterId: Long): List<CallRecord>? = runCatching {
        db.rawQuery(
            "SELECT _id, number, date, duration, type, simid FROM calls WHERE _id > ? ORDER BY _id ASC",
            arrayOf(afterId.toString())
        ).use { c ->
            val iId = c.getColumnIndexOrThrow("_id")
            val iNum = c.getColumnIndexOrThrow("number")
            val iDate = c.getColumnIndexOrThrow("date")
            val iDur = c.getColumnIndexOrThrow("duration")
            val iType = c.getColumnIndexOrThrow("type")
            val iSim = c.getColumnIndex("simid")
            val out = ArrayList<CallRecord>()
            while (c.moveToNext()) {
                out.add(
                    CallRecord(
                        id = c.getLong(iId),
                        number = c.getString(iNum),
                        date = c.getLong(iDate),
                        duration = c.getLong(iDur),
                        type = c.getInt(iType),
                        simId = if (iSim >= 0) c.getInt(iSim) else -1
                    )
                )
            }
            out
        }
    }.getOrNull()
}
