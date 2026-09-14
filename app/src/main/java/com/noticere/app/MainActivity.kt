package com.noticere.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.noticere.app.forward.Forwarder
import com.noticere.app.model.ForwardMessage
import com.noticere.app.service.ForwardService
import com.noticere.app.ui.ForwardSettingsFragment
import com.noticere.app.ui.PushSettingsFragment
import com.noticere.app.ui.StatusFragment
import com.noticere.app.ui.TemplateSettingsFragment

class MainActivity : AppCompatActivity() {

    private val tabTitles = intArrayOf(
        R.string.tab_status, R.string.tab_forward, R.string.tab_push, R.string.tab_template
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pager = findViewById<ViewPager2>(R.id.viewPager)
        pager.offscreenPageLimit = 3
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 4
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> StatusFragment()
                1 -> ForwardSettingsFragment()
                2 -> PushSettingsFragment()
                else -> TemplateSettingsFragment()
            }
        }
        TabLayoutMediator(findViewById<TabLayout>(R.id.tabLayout), pager) { tab, pos ->
            tab.setText(tabTitles[pos])
        }.attach()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { test() }

        requestNotifPermissionIfNeeded()
    }

    private fun save() {
        (supportFragmentManager.findFragmentByTag("f1") as? ForwardSettingsFragment)?.saveToConfig()
        (supportFragmentManager.findFragmentByTag("f2") as? PushSettingsFragment)?.saveToConfig()
        (supportFragmentManager.findFragmentByTag("f3") as? TemplateSettingsFragment)?.saveToConfig()

        applyService()
        ForwardEngine.refresh()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun applyService() {
        if (Config.smsEnabled || Config.callEnabled) {
            val i = Intent(this, ForwardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        } else {
            stopService(Intent(this, ForwardService::class.java))
        }
    }

    private fun test() {
        val msg = ForwardMessage(
            type = "test",
            source = "NoticeRe",
            title = "测试消息",
            body = "如果你能收到这条消息，说明转发配置正确。",
            packageName = packageName,
            sim = null,
            timestamp = System.currentTimeMillis()
        )
        Forwarder.send(msg)
        Toast.makeText(this, R.string.test_sent, Toast.LENGTH_SHORT).show()
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}
