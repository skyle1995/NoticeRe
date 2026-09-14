package com.noticere.app.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noticere.app.Config
import com.noticere.app.R
import com.noticere.app.RunLog
import com.noticere.app.notify.NotificationForwardService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusFragment : Fragment() {

    private lateinit var tvRootStatus: TextView
    private lateinit var tvNotifStatus: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_status, container, false)
        tvRootStatus = v.findViewById(R.id.tvRootStatus)
        tvNotifStatus = v.findViewById(R.id.tvNotifStatus)
        tvServiceStatus = v.findViewById(R.id.tvServiceStatus)
        tvLog = v.findViewById(R.id.tvLog)
        scrollLog = v.findViewById(R.id.scrollLog)

        v.findViewById<Button>(R.id.btnCheckRoot).setOnClickListener { refreshStatus() }
        v.findViewById<Button>(R.id.btnGrantNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        v.findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            RunLog.clear()
            refreshLog()
        }

        // 每 2 秒刷新一次日志
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                refreshLog()
                delay(2000)
            }
        }

        refreshLog()
        return v
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val shell = runCatching { Shell.getShell() }.getOrNull()
            val root = shell?.isRoot == true
            withContext(Dispatchers.Main) {
                tvRootStatus.text = getString(if (root) R.string.root_ok else R.string.root_no)
            }
        }

        val ctx = context ?: return
        val cn = ComponentName(ctx, NotificationForwardService::class.java)
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        val granted = !flat.isNullOrEmpty() && flat.split(':').any {
            ComponentName.unflattenFromString(it) == cn
        }
        tvNotifStatus.text = getString(if (granted) R.string.notif_ok else R.string.notif_no)
        tvServiceStatus.text = getString(
            if (Config.smsEnabled || Config.callEnabled) R.string.service_running else R.string.service_stopped
        )
    }

    private fun refreshLog() {
        val lines = RunLog.snapshot()
        tvLog.text = if (lines.isEmpty()) getString(R.string.log_empty) else lines.joinToString("\n")
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }
}
