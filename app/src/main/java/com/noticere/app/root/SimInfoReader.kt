package com.noticere.app.root

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * 读取系统 siminfo 表，建立「订阅ID(subId) → 手机号」的映射。
 *
 * 短信表的 sim_id、通话表的 simid 实际存的是订阅ID(subId)，用它在这里查到真实号码。
 */
class SimInfoReader(context: Context) {

    private val reader = RootDbReader(context)
    private var cache: Map<Int, String>? = null
    private var cacheTime = 0L

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private val DB_PATHS = listOf(
            "/data/user_de/0/com.android.providers.telephony/databases/telephony.db",
            "/data/data/com.android.providers.telephony/databases/telephony.db"
        )
    }

    fun phoneNumberFor(subId: Int): String? {
        if (subId <= 0) return null
        return getMap()[subId]
    }

    private fun getMap(): Map<Int, String> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheTime < CACHE_TTL_MS) return it }
        val m = readSimInfo()
        cache = m
        cacheTime = now
        return m
    }

    private fun readSimInfo(): Map<Int, String> {
        for (path in DB_PATHS) {
            val snap = reader.snapshot(path) ?: continue
            val db = reader.openReadOnly(snap) ?: continue
            val map = db.use { querySimInfo(it) }
            if (!map.isNullOrEmpty()) return map
        }
        return emptyMap()
    }

    private fun querySimInfo(db: SQLiteDatabase): Map<Int, String>? = runCatching {
        db.rawQuery(
            "SELECT _id, number, phone_number_source_ims, phone_number_source_carrier FROM siminfo",
            null
        ).use { c ->
            val iId = c.getColumnIndexOrThrow("_id")
            val iNum = c.getColumnIndex("number")
            val iIms = c.getColumnIndex("phone_number_source_ims")
            val iCar = c.getColumnIndex("phone_number_source_carrier")
            val map = HashMap<Int, String>()
            while (c.moveToNext()) {
                val id = c.getInt(iId)
                val num = (if (iNum >= 0) c.getString(iNum) else null)
                    ?: (if (iIms >= 0) c.getString(iIms) else null)
                    ?: (if (iCar >= 0) c.getString(iCar) else null)
                if (!num.isNullOrBlank()) map[id] = clean(num)
            }
            map
        }
    }.getOrNull()

    private fun clean(num: String): String =
        num.trim().removePrefix("+86").removePrefix("86").removePrefix("+")
}
