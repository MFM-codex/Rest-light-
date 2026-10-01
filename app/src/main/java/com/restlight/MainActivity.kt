package com.restlight

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var workInput: EditText
    private lateinit var breakInput: EditText
    private lateinit var status: TextView
    private lateinit var snoozeBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("restlight", Context.MODE_PRIVATE)

        val ink = Color.parseColor("#F0DCC0")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1926"))
            setPadding(56, 120, 56, 56)
        }

        fun label(s: String, size: Float = 18f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(ink); setPadding(0, 24, 0, 8)
        }
        fun numberField(value: Int) = EditText(this).apply {
            setText(value.toString()); textSize = 22f; setTextColor(ink)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply {
            text = s; textSize = 18f; setOnClickListener { onClick() }
        }

        workInput = numberField(prefs.getInt("workMin", 20))
        breakInput = numberField(prefs.getInt("breakSec", 20))
        status = label("", 16f)
        snoozeBox = CheckBox(this).apply {
            text = "Allow one 5-minute postpone per break"
            setTextColor(ink); isChecked = prefs.getBoolean("snooze", false)
        }

        root.addView(label("Restlight", 32f))
        root.addView(label("Study time between breaks (minutes)"))
        root.addView(workInput)
        root.addView(label("Eye break length (seconds)"))
        root.addView(breakInput)
        root.addView(snoozeBox)
        root.addView(button("Start") { start(prefs) })
        root.addView(button("Stop") {
            stopService(Intent(this, BreakService::class.java))
            status.text = "Stopped."
        })
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root); isFillViewport = true })
    }

    private fun start(prefs: android.content.SharedPreferences) {
        val work = workInput.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 20
        val brk = breakInput.text.toString().toIntOrNull()?.coerceIn(5, 600) ?: 20
        prefs.edit().putInt("workMin", work).putInt("breakSec", brk).putBoolean("snooze", snoozeBox.isChecked).apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" for Restlight, then tap Start again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, BreakService::class.java))
        status.text = "Running. First eye break in $work min, lasting $brk sec."
    }
}
