package com.noticere.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.noticere.app.Config
import com.noticere.app.R

/** 模板设置：自定义短信 / 未接来电 / 通知 的消息模板。 */
class TemplateSettingsFragment : Fragment(), SettingsFragment {

    private lateinit var etSmsTitle: EditText
    private lateinit var etSmsBody: EditText
    private lateinit var etCallTitle: EditText
    private lateinit var etCallBody: EditText
    private lateinit var etNotifTitle: EditText
    private lateinit var etNotifBody: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_template_settings, container, false)
        etSmsTitle = v.findViewById(R.id.etSmsTitle)
        etSmsBody = v.findViewById(R.id.etSmsBody)
        etCallTitle = v.findViewById(R.id.etCallTitle)
        etCallBody = v.findViewById(R.id.etCallBody)
        etNotifTitle = v.findViewById(R.id.etNotifTitle)
        etNotifBody = v.findViewById(R.id.etNotifBody)

        v.findViewById<Button>(R.id.btnResetTemplate).setOnClickListener {
            Config.resetTemplates()
            loadConfig()
            Toast.makeText(requireContext(), R.string.template_reset_done, Toast.LENGTH_SHORT).show()
        }

        loadConfig()
        return v
    }

    private fun loadConfig() {
        etSmsTitle.setText(Config.smsTitleTemplate)
        etSmsBody.setText(Config.smsBodyTemplate)
        etCallTitle.setText(Config.callTitleTemplate)
        etCallBody.setText(Config.callBodyTemplate)
        etNotifTitle.setText(Config.notifTitleTemplate)
        etNotifBody.setText(Config.notifBodyTemplate)
    }

    override fun saveToConfig() {
        Config.smsTitleTemplate = etSmsTitle.text.toString()
        Config.smsBodyTemplate = etSmsBody.text.toString()
        Config.callTitleTemplate = etCallTitle.text.toString()
        Config.callBodyTemplate = etCallBody.text.toString()
        Config.notifTitleTemplate = etNotifTitle.text.toString()
        Config.notifBodyTemplate = etNotifBody.text.toString()
    }
}
