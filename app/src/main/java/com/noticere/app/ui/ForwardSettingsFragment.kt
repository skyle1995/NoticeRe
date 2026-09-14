package com.noticere.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import androidx.fragment.app.Fragment
import com.noticere.app.Config
import com.noticere.app.R

/** 转发设置：短信 / 未接来电 / 通知 的开关与过滤规则。 */
class ForwardSettingsFragment : Fragment(), SettingsFragment {

    private lateinit var switchSms: Switch
    private lateinit var etSmsNumberFilter: EditText
    private lateinit var etSmsKeywordFilter: EditText
    private lateinit var switchCall: Switch
    private lateinit var etCallNumberFilter: EditText
    private lateinit var switchNotif: Switch
    private lateinit var etNotifFilter: EditText
    private lateinit var etNotifKeywordFilter: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_forward_settings, container, false)
        switchSms = v.findViewById(R.id.switchSms)
        etSmsNumberFilter = v.findViewById(R.id.etSmsNumberFilter)
        etSmsKeywordFilter = v.findViewById(R.id.etSmsKeywordFilter)
        switchCall = v.findViewById(R.id.switchCall)
        etCallNumberFilter = v.findViewById(R.id.etCallNumberFilter)
        switchNotif = v.findViewById(R.id.switchNotif)
        etNotifFilter = v.findViewById(R.id.etNotifFilter)
        etNotifKeywordFilter = v.findViewById(R.id.etNotifKeywordFilter)
        loadConfig()
        return v
    }

    private fun loadConfig() {
        switchSms.isChecked = Config.smsEnabled
        etSmsNumberFilter.setText(Config.smsNumberFilter)
        etSmsKeywordFilter.setText(Config.smsKeywordFilter)

        switchCall.isChecked = Config.callEnabled
        etCallNumberFilter.setText(Config.callNumberFilter)

        switchNotif.isChecked = Config.notifEnabled
        etNotifFilter.setText(Config.notifPackageFilter)
        etNotifKeywordFilter.setText(Config.notifKeywordFilter)
    }

    override fun saveToConfig() {
        Config.smsEnabled = switchSms.isChecked
        Config.smsNumberFilter = etSmsNumberFilter.text.toString()
        Config.smsKeywordFilter = etSmsKeywordFilter.text.toString()

        Config.callEnabled = switchCall.isChecked
        Config.callNumberFilter = etCallNumberFilter.text.toString()

        Config.notifEnabled = switchNotif.isChecked
        Config.notifPackageFilter = etNotifFilter.text.toString()
        Config.notifKeywordFilter = etNotifKeywordFilter.text.toString()
    }
}
