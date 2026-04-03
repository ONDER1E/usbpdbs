package com.onder1e.tbpandroid

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSlider(
            view,
            R.id.seekEnableThreshold,
            R.id.etEnableThreshold,
            R.id.tvEnableThresholdLabel,
            "Enable Threshold (%)",
            min = 1, max = 100,
            getValue = { SettingsManager.enableThreshold },
            setValue = { SettingsManager.enableThreshold = it }
        )

        setupSlider(
            view,
            R.id.seekDisableThreshold,
            R.id.etDisableThreshold,
            R.id.tvDisableThresholdLabel,
            "Disable Threshold (%)",
            min = 1, max = 100,
            getValue = { SettingsManager.disableThreshold },
            setValue = { SettingsManager.disableThreshold = it }
        )

        setupSlider(
            view,
            R.id.seekMinStateSeconds,
            R.id.etMinStateSeconds,
            R.id.tvMinStateSecondsLabel,
            "Min Time Between Toggles (seconds)",
            min = 10, max = 300,
            getValue = { SettingsManager.minStateSeconds },
            setValue = { SettingsManager.minStateSeconds = it }
        )

        view.findViewById<Button>(R.id.btnRestartService).setOnClickListener {
            val ctx = requireContext()
            ctx.stopService(Intent(ctx, BypassMonitorService::class.java))
            ctx.startForegroundService(Intent(ctx, BypassMonitorService::class.java))
            LogBuffer.log("Settings applied -- service restarted")
        }
    }

    private fun setupSlider(
        view: View,
        seekId: Int,
        editId: Int,
        labelId: Int,
        labelText: String,
        min: Int,
        max: Int,
        getValue: () -> Int,
        setValue: (Int) -> Unit
    ) {
        val seek = view.findViewById<SeekBar>(seekId)
        val edit = view.findViewById<EditText>(editId)
        val label = view.findViewById<TextView>(labelId)

        label.text = labelText
        seek.max = max - min
        val initial = getValue()
        seek.progress = initial - min
        edit.setText(initial.toString())

        var updatingFromSeek = false
        var updatingFromEdit = false

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && !updatingFromEdit) {
                    val value = progress + min
                    updatingFromSeek = true
                    edit.setText(value.toString())
                    edit.setSelection(edit.text.length)
                    updatingFromSeek = false
                    setValue(value)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (!updatingFromSeek) {
                    val value = s.toString().toIntOrNull() ?: return
                    if (value in min..max) {
                        updatingFromEdit = true
                        seek.progress = value - min
                        updatingFromEdit = false
                        setValue(value)
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {}
        })
    }
}
