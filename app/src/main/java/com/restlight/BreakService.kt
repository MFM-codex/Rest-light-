package com.restlight

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.os.*
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class BreakService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var tintView: View? = null
    private var countdown: CountDownTimer? = null
    private var snoozeUsed = false
    private val showBreak = Runnable { startBreak() }
    private val tintTick = object : Runnable {
        override fun run() { applyTint(); handler.postDelayed(this, 60_000L) }
    }
    private val tips = listOf(
        "Look at something far away", "Blink slowly, ten times",
        "Roll your eyes gently", "Close your eyes and relax"
    )

    private fun prefs() = getSharedPreferences("restlight", Context.MODE_PRIVATE)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "toggle") prefs().edit().putBoolean("filterOn", !prefs().getBoolean("filterOn", true)).apply()
        startForeground(1, buildNotification())
        applyTint()
        handler.removeCallbacks(tintTick); handler.postDelayed(tintTick, 60_000L)
        if (action != "tint" && action != "toggle") scheduleNextBreak()
        return START_STICKY
    }

    private fun scheduleNextBreak() = postBreakIn(prefs().getInt("workMin", 20) * 60_000L)

    private fun postBreakIn(ms: Long) {
        handler.removeCallbacks(showBreak)
        prefs().edit().putLong("nextAt", System.currentTimeMillis() + ms).apply()
        handler.postDelayed(showBreak, ms)
    }

    // Filter is on unless toggled off, and (if a schedule is set) only inside its hours.
    private fun filterActive(): Boolean {
        val p = prefs()
        if (!p.getBoolean("filterOn", true)) return false
        if (!p.getBoolean("sched", false)) return true
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = p.getInt("schedStart", 19); val e = p.getInt("schedEnd", 6)
        return if (s <= e) h in s until e else (h >= s || h < e)
    }

    private fun applyTint() {
        val on = filterActive()
        val warm = if (on) prefs().getInt("warm", 0) else 0
        val dim = if (on) prefs().getInt("dim", 0) else 0
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (tintView == null) {
            val v = View(this)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(v, lp)
            tintView = v
        }
        tintView!!.background = LayerDrawable(arrayOf(
            ColorDrawable(Color.argb((warm * 1.15).toInt(), 255, 140, 30)),
            ColorDrawable(Color.argb((dim * 2.55).toInt(), 0, 0, 0))
        ))
    }

    private fun startBreak() {
        // Smart pausing: skip if the screen is off, wait a minute during calls.
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) { scheduleNextBreak(); return }
        val mode = (getSystemService(AUDIO_SERVICE) as AudioManager).mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            postBreakIn(60_000L); return
        }
        val breakSec = prefs().getInt("breakSec", 20)
        val view = buildOverlay()
        overlay = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
        vibrate()

        val timeText = view.findViewWithTag<TextView>("time")
        val tipText = view.findViewWithTag<TextView>("tip")
        val totalMs = breakSec * 1000L
        countdown = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(ms: Long) {
                timeText.text = ((ms + 999) / 1000).toString()
                val i = ((totalMs - ms) * tips.size / totalMs).toInt().coerceIn(0, tips.size - 1)
                tipText.text = tips[i]
            }
            override fun onFinish() { Stats.add(this@BreakService, 0); snoozeUsed = false; endBreak() }
        }.start()
    }

    private fun endBreak(reschedule: Boolean = true) {
        countdown?.cancel()
        overlay?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        overlay = null
        if (reschedule) { vibrate(); scheduleNextBreak() }
    }

    private fun buildOverlay(): View {
        val big = if (prefs().getBoolean("large", false)) 1.35f else 1f
        val root = object : LinearLayout(this) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean = true
        }
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#1B1926"))
        root.isClickable = true
        root.isFocusable = true

        fun text(s: String, size: Float, bold: Boolean = false, tag: String? = null) =
            TextView(this).apply {
                this.text = s; textSize = size * big
                setTextColor(Color.parseColor("#F0DCC0"))
                gravity = Gravity.CENTER
                setPadding(48, 16, 48, 16)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                this.tag = tag
            }

        root.addView(text("Eye break", 34f, bold = true))
        root.addView(text(tips[0], 20f, tag = "tip"))
        root.addView(text("", 96f, bold = true, tag = "time"))
        root.addView(text("Your screen comes back when the timer ends.", 16f))
        if (prefs().getBoolean("snooze", false) && !snoozeUsed) {
            root.addView(Button(this).apply {
                text = "Postpone 5 minutes (once)"
                setOnClickListener {
                    snoozeUsed = true
                    Stats.add(this@BreakService, 1)
                    endBreak(reschedule = false)
                    postBreakIn(5 * 60_000L)
                }
            })
        }
        return root
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("restlight", "Eye breaks", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getForegroundService(
            this, 0, Intent(this, BreakService::class.java).setAction("toggle"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_view), "Filter on/off", pi
        ).build()
        return Notification.Builder(this, "restlight")
            .setContentTitle("Restlight is running")
            .setContentText("Your next eye break is on its way.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(action)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(showBreak)
        handler.removeCallbacks(tintTick)
        endBreak(reschedule = false)
        tintView?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        tintView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
