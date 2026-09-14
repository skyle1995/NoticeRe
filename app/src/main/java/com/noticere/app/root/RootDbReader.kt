package com.noticere.app.root

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * 通过 root 把系统私有 SQLite 数据库快照到应用缓存目录，再用本进程只读打开。
 *
 * 短信 / 通话记录等系统数据库位于 /data/data/ 下，普通应用无法直接读取；
 * 利用 root 的 cp 把它们拷贝到应用可读的位置，同时拷贝 -wal / -shm 以兼容 WAL 模式。
 */
class RootDbReader(private val context: Context) {

    private val stageDir: String
        get() = context.cacheDir.absolutePath + "/rootdb"

    /**
     * 快照 [srcPath] 到应用缓存，返回本地可读路径；失败返回 null。
     *
     * 注意：-wal/-shm 不一定存在，因此逐条命令加 `|| true` 避免整体退出码受影响，
     * 最终以「目标 .db 是否真的落地」作为成功判据。
     */
    fun snapshot(srcPath: String): String? {
        val name = srcPath.substringAfterLast('/')
        val dst = "$stageDir/$name"
        val uid = context.applicationInfo.uid
        Shell.cmd(
            "mkdir -p $stageDir",
            "rm -f $dst $dst-wal $dst-shm",
            "cp -f $srcPath $dst 2>/dev/null || true",
            "cp -f $srcPath-wal $dst-wal 2>/dev/null || true",
            "cp -f $srcPath-shm $dst-shm 2>/dev/null || true",
            "chown $uid:$uid $stageDir $dst $dst-wal $dst-shm 2>/dev/null || true",
            "chmod 600 $dst $dst-wal $dst-shm 2>/dev/null || true"
        ).exec()
        return File(dst).takeIf { it.isFile && it.length() > 0 }?.absolutePath
    }

    /**
     * 以只读方式打开快照数据库。
     */
    fun openReadOnly(path: String): SQLiteDatabase? =
        runCatching {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull()
}
