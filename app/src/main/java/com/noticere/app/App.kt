package com.noticere.app

import android.app.Application
import com.noticere.app.forward.Forwarder
import com.topjohnwu.superuser.Shell

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Config.init(this)

        // 初始化 libsu：全局主 Shell 配置。
        // FLAG_MOUNT_MASTER 使用全局挂载命名空间（su --mount-master），
        // 否则应用进程的隔离命名空间里看不到 /data/data 下其它应用（短信/通话）的数据库。
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )

        // 发送器（先落盘再发送，失败自动重发）
        Forwarder.init(this)

        // 启动转发引擎（注册监听 + 轮询兜底）
        ForwardEngine.init(this)
    }
}
