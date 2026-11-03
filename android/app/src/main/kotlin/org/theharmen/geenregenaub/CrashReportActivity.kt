package org.theharmen.geenregenaub

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shows the last crash from [CrashLog] and lets you share it. Plain Android views on purpose:
 * this screen must open even when the rest of the app (Compose, the map) is what crashes.
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashLog.read(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val text = TextView(this).apply {
            setText(report ?: getString(R.string.crash_report_none))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
        }
        val share = Button(this).apply {
            setText(R.string.crash_report_share)
            isEnabled = report != null
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, getString(R.string.crash_report_share)))
            }
        }
        val clear = Button(this).apply {
            setText(R.string.crash_report_clear)
            isEnabled = report != null
            setOnClickListener {
                CrashLog.clear(this@CrashReportActivity)
                finish()
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, 0, pad, pad)
            addView(share)
            addView(clear)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(ScrollView(this@CrashReportActivity).apply { addView(text) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(content)
    }
}
