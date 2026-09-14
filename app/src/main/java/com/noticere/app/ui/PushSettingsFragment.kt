package com.noticere.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import androidx.fragment.app.Fragment
import com.noticere.app.Config
import com.noticere.app.R

/** 推送设置：Bark 与 Webhook 的推送目标配置。 */
class PushSettingsFragment : Fragment(), SettingsFragment {

    private lateinit var switchBark: Switch
    private lateinit var etBarkServer: EditText
    private lateinit var etBarkKey: EditText
    private lateinit var spBarkLevel: Spinner
    private lateinit var etBarkSound: EditText
    private lateinit var switchWebhook: Switch
    private lateinit var spWebhookPlatform: Spinner
    private lateinit var etWebhookUrl: EditText
    private lateinit var etWebhookSecret: EditText
    private lateinit var etWebhookHeaders: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_push_settings, container, false)
        switchBark = v.findViewById(R.id.switchBark)
        etBarkServer = v.findViewById(R.id.etBarkServer)
        etBarkKey = v.findViewById(R.id.etBarkKey)
        spBarkLevel = v.findViewById(R.id.spBarkLevel)
        etBarkSound = v.findViewById(R.id.etBarkSound)
        switchWebhook = v.findViewById(R.id.switchWebhook)
        spWebhookPlatform = v.findViewById(R.id.spWebhookPlatform)
        etWebhookUrl = v.findViewById(R.id.etWebhookUrl)
        etWebhookSecret = v.findViewById(R.id.etWebhookSecret)
        etWebhookHeaders = v.findViewById(R.id.etWebhookHeaders)

        spBarkLevel.adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.bark_level_labels, android.R.layout.simple_spinner_dropdown_item
        )
        spWebhookPlatform.adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.webhook_platform_labels, android.R.layout.simple_spinner_dropdown_item
        )

        loadConfig()
        return v
    }

    private fun loadConfig() {
        switchBark.isChecked = Config.barkEnabled
        etBarkServer.setText(Config.barkServer)
        etBarkKey.setText(Config.barkKey)
        spBarkLevel.setSelection(indexIn(R.array.bark_level_values, Config.barkLevel))
        etBarkSound.setText(Config.barkSound)

        switchWebhook.isChecked = Config.webhookEnabled
        spWebhookPlatform.setSelection(indexIn(R.array.webhook_platform_values, Config.webhookPlatform))
        etWebhookUrl.setText(Config.webhookUrl)
        etWebhookSecret.setText(Config.webhookSecret)
        etWebhookHeaders.setText(Config.webhookHeaders)
    }

    override fun saveToConfig() {
        Config.barkEnabled = switchBark.isChecked
        Config.barkServer = etBarkServer.text.toString()
        Config.barkKey = etBarkKey.text.toString()
        Config.barkLevel = valueAt(R.array.bark_level_values, spBarkLevel.selectedItemPosition)
        Config.barkSound = etBarkSound.text.toString()

        Config.webhookEnabled = switchWebhook.isChecked
        Config.webhookPlatform = valueAt(R.array.webhook_platform_values, spWebhookPlatform.selectedItemPosition)
        Config.webhookUrl = etWebhookUrl.text.toString()
        Config.webhookSecret = etWebhookSecret.text.toString()
        Config.webhookHeaders = etWebhookHeaders.text.toString()
    }

    private fun indexIn(valuesResId: Int, value: String): Int {
        val values = resources.getStringArray(valuesResId)
        val i = values.indexOf(value)
        return if (i >= 0) i else 0
    }

    private fun valueAt(valuesResId: Int, position: Int): String {
        val values = resources.getStringArray(valuesResId)
        return values.getOrElse(position) { values.first() }
    }
}
