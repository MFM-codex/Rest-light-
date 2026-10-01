package com.restlight

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val bg = Color.parseColor("#1B1926")
    private val card = Color.parseColor("#262335")
    private val ink = Color.parseColor("#F0DCC0")
    private val mute = Color.parseColor("#A79CAE")
    private val accent = Color.parseColor("#7CC0B6")

    private lateinit var prefs: android.content.SharedPreferences
    private var scale = 1f
    private lateinit var workInput: EditText
    private lateinit var breakInput: EditText
    private lateinit var snoozeBox: CheckBox
    private lateinit var status: TextView
    private lateinit var statsText: TextView
    private lateinit var ring: RingView
    private val ticker = object : Runnable {
        override fun run() { refreshRing(); ring.postDelayed(this, 1000L) }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size * scale; setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    private fun cardBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(card, 18)
        setPadding(dp(18), dp(16), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
    }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 17f * scale; isAllCaps = false; stateListAnimator = null
        setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        setTextColor(if (filled) bg else ink)
        background = if (filled) shape(accent, 14) else shape(Color.TRANSPARENT, 14, mute)
        layoutParams = LinearLayout.LayoutParams(0, dp((52 * scale).toInt()), 1f).apply { marginEnd = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun field(value: Int) = EditText(this).apply {
        setText(value.toString()); textSize = 22f * scale; setTextColor(ink)
        inputType = InputType.TYPE_CLASS_NUMBER
        background = shape(bg, 12); setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(12) }
    }

    private fun slider(box: LinearLayout, title: String, key: String, max: Int) {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val value = text("${prefs.getInt(key, 0)}%", 17f, mute)
        head.addView(text(title, 17f).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        head.addView(value)
        box.addView(head)
        box.addView(SeekBar(this).apply {
            this.max = max; progress = prefs.getInt(key, 0)
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    prefs.edit().putInt(key, p).apply()
                    value.text = "$p%"
                    pushTint()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
    }

    private fun pushTint() {
        if (!Settings.canDrawOverlays(this)) { askOverlay(); return }
        startForegroundService(Intent(this, BreakService::class.java).setAction("tint"))
    }

    private fun askOverlay() {
        Toast.makeText(this, "Allow \"Display over other apps\" for Restlight, then try again.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("restlight", Context.MODE_PRIVATE)
        scale = if (prefs.getBoolean("large", false)) 1.35f else 1f

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        root.addView(text("Restlight", 34f, bold = true))
        root.addView(text("Gentle eye care for long study nights", 16f, mute))

        ring = RingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20)
            }
        }
        root.addView(ring)

        root.addView(CheckBox(this).apply {
            text = "Large text mode"
            setTextColor(ink); textSize = 17f * scale
            isChecked = prefs.getBoolean("large", false)
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("large", on).apply()
                recreate()
            }
        })

        val breaks = cardBox()
        breaks.addView(text("Eye breaks", 20f, bold = true))
        breaks.addView(text("Study time between breaks (minutes)", 15f, mute).apply { setPadding(0, dp(12), 0, 0) })
        workInput = field(prefs.getInt("workMin", 20)); breaks.addView(workInput)
        breaks.addView(text("Eye break length (seconds)", 15f, mute))
        breakInput = field(prefs.getInt("breakSec", 20)); breaks.addView(breakInput)
        snoozeBox = CheckBox(this).apply {
            text = "Allow one 5-minute postpone per break"
            setTextColor(ink); textSize = 16f * scale; isChecked = prefs.getBoolean("snooze", false)
        }
        breaks.addView(snoozeBox)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        row.addView(pill("Start", true) { start() })
        row.addView(pill("Stop", false) {
            prefs.edit().putBoolean("running", false).putLong("nextAt", 0L).apply()
            stopService(Intent(this, BreakService::class.java))
            status.text = "Stopped. Screen filter is off too."
        })
        breaks.addView(row)
        root.addView(breaks)

        val comfort = cardBox()
        comfort.addView(text("Screen comfort (whole phone)", 20f, bold = true))
        comfort.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(8)) })
        slider(comfort, "Warmth", "warm", 100)
        comfort.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(12)) })
        slider(comfort, "Dimming", "dim", 60)
        comfort.addView(text("Works over every app. Tap Stop to remove it.", 14f, mute).apply { setPadding(0, dp(10), 0, 0) })
        root.addView(comfort)

        val schedCard = cardBox()
        schedCard.addView(text("Night schedule", 20f, bold = true))
        val schedBox = CheckBox(this).apply {
            text = "Filter only during these hours"
            setTextColor(ink); textSize = 16f * scale; isChecked = prefs.getBoolean("sched", false)
        }
        schedCard.addView(schedBox)
        schedCard.addView(text("Starts at hour (0-23)", 15f, mute))
        val schedStart = field(prefs.getInt("schedStart", 19)); schedCard.addView(schedStart)
        schedCard.addView(text("Ends at hour (0-23)", 15f, mute))
        val schedEnd = field(prefs.getInt("schedEnd", 6)); schedCard.addView(schedEnd)
        val saveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        saveRow.addView(pill("Save schedule", true) {
            prefs.edit().putBoolean("sched", schedBox.isChecked)
                .putInt("schedStart", schedStart.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 19)
                .putInt("schedEnd", schedEnd.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 6).apply()
            pushTint()
            Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show()
        })
        schedCard.addView(saveRow)
        root.addView(schedCard)

        val statsCard = cardBox()
        statsCard.addView(text("Your eye breaks", 20f, bold = true))
        statsText = text(statsLine(), 16f, mute).apply { setPadding(0, dp(8), 0, 0) }
        statsCard.addView(statsText)
        root.addView(statsCard)

        val fontCard = cardBox()
        fontCard.addView(text("Phone text size (all apps)", 20f, bold = true))
        val current = try { Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE) } catch (e: Exception) { 1f }
        val fontLabel = text("${(current * 100).toInt()}%", 17f, mute).apply { setPadding(0, dp(10), 0, 0) }
        fontCard.addView(fontLabel)
        fontCard.addView(SeekBar(this).apply {
            max = 65
            progress = ((current - 0.85f) * 100).toInt().coerceIn(0, 65)
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) fontLabel.text = "${85 + p}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { setPhoneFont(0.85f + sb.progress / 100f) }
            })
        })
        fontCard.addView(text("Changes the text size in WhatsApp and other apps. Set 100% for normal.", 14f, mute).apply { setPadding(0, dp(10), 0, 0) })
        root.addView(fontCard)

        status = text("", 15f, mute).apply { setPadding(dp(4), dp(16), 0, 0) }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root); isFillViewport = true; setBackgroundColor(bg) })
    }

    private fun statsLine(): String {
        val (b, s) = Stats.day(this, 0)
        var week = 0
        for (i in 0..6) week += Stats.day(this, i).first
        return "Today: $b breaks, $s postponed\nLast 7 days: $week breaks"
    }

    override fun onResume() {
        super.onResume()
        if (::statsText.isInitialized) statsText.text = statsLine()
        ring.removeCallbacks(ticker); ring.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ring.removeCallbacks(ticker)
    }

    private fun refreshRing() {
        val next = prefs.getLong("nextAt", 0L)
        val total = prefs.getInt("workMin", 20) * 60_000L
        val left = next - System.currentTimeMillis()
        if (!prefs.getBoolean("running", false) || next == 0L) {
            ring.label = "--:--"; ring.sub = "Not running"; ring.fraction = 0f
        } else if (left <= 0) {
            ring.label = "Break"; ring.sub = "time to rest"; ring.fraction = 0f
        } else {
            val sec = (left + 999) / 1000
            ring.label = "%02d:%02d".format(sec / 60, sec % 60)
            ring.sub = "until eye break"
            ring.fraction = (left.toFloat() / total).coerceIn(0f, 1f)
        }
        ring.invalidate()
    }

    private fun setPhoneFont(scale: Float) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Allow \"Modify system settings\" for Restlight, then try again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        try {
            Settings.System.putFloat(contentResolver, Settings.System.FONT_SCALE, scale)
        } catch (e: Exception) {
            Toast.makeText(this, "Your phone blocked this. Use Settings > Display > Font size instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun start() {
        val work = workInput.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 20
        val brk = breakInput.text.toString().toIntOrNull()?.coerceIn(5, 600) ?: 20
        prefs.edit().putInt("workMin", work).putInt("breakSec", brk)
            .putBoolean("snooze", snoozeBox.isChecked).putBoolean("running", true).apply()
        if (!Settings.canDrawOverlays(this)) { askOverlay(); return }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, BreakService::class.java))
        status.text = "Running. First eye break in $work min, lasting $brk sec."
    }
}
