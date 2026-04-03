package com.onder1e.tbpandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class MainFragment : Fragment() {

    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var statusText: TextView
    private var refreshRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.tvShizukuStatus)
        logText = view.findViewById(R.id.tvLogs)
        logScroll = view.findViewById(R.id.scrollLogs)

        logText.setTextIsSelectable(true)

        view.findViewById<Button>(R.id.btnStartService).setOnClickListener {
            (activity as? MainActivity)?.requestShizukuIfNeeded()
            val intent = Intent(requireContext(), BypassMonitorService::class.java)
            requireContext().startForegroundService(intent)
        }

        view.findViewById<Button>(R.id.btnStopService).setOnClickListener {
            requireContext().stopService(Intent(requireContext(), BypassMonitorService::class.java))
        }

        view.findViewById<Button>(R.id.btnCopyLogs).setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("TBP Logs", logText.text))
            Toast.makeText(requireContext(), "Logs copied", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            LogBuffer.clear()
            logText.text = ""
        }

        startLogRefresh()
    }

    fun updateShizukuStatus(status: String) {
        if (::statusText.isInitialized) statusText.text = status
    }

    private fun startLogRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val logs = LogBuffer.getLogs()
                if (logText.text.toString() != logs) {
                    logText.text = logs
                    logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                logText.postDelayed(this, 1000)
            }
        }
        logText.post(refreshRunnable!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refreshRunnable?.let { logText.removeCallbacks(it) }
    }
}
