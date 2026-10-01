package com.restlight

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BreakService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var countdown: CountDownTimer? = null
    private val showBreak = Runnable { startBreak() }
    private var snoozeUsed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        scheduleNextBreak()
        return START_STICKY
    }

    private fun prefs() = getSharedPreferences("restlight", Context.MODE_PRIVATE)

    private fun scheduleNextBreak() {
        handler.removeCallbacks(showBreak)
        val workMs = prefs().getInt("workMin", 20) * 60_000L
        handler.postDelayed(showBreak, workMs)
    }

    private fun startBreak() {
        val breakSec = prefs().getInt("breakSec", 20)
        val view = buildOverlay()
        overlay = view

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
        vibrate()

        val timeText = view.findViewWithTag<TextView>("time")
        countdown = object : CountDownTimer(breakSec * 1000L, 1000L) {
            override fun onTick(ms: Long) { timeText.text = ((ms + 999) / 1000).toString() }
            override fun onFinish() { snoozeUsed = false; endBreak() }
        }.start()
    }

    private fun endBreak(reschedule: Boolean = true) {
        countdown?.cancel()
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
        if (reschedule) {
            vibrate()
            scheduleNextBreak()
        }
    }

    // Full-screen, dark and warm so it is gentle on tired eyes. No close button on purpose.
    private fun buildOverlay(): View {
        val root = object : LinearLayout(this) {
            // Swallow the Back key so the break can't be dismissed.
            override fun dispatchKeyEvent(e: KeyEvent): Boolean = true
        }
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#1B1926"))
        root.isClickable = true
        root.isFocusable = true

        fun text(s: String, size: Float, bold: Boolean = false, tag: String? = null) =
            TextView(this).apply {
                this.text = s
                textSize = size
                setTextColor(Color.parseColor("#F0DCC0"))
                gravity = Gravity.CENTER
                setPadding(48, 16, 48, 16)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                this.tag = tag
            }

        root.addView(text("Eye break", 34f, bold = true))
        root.addView(text("Close your eyes and rest them.", 20f))
        root.addView(text("", 96f, bold = true, tag = "time"))
        root.addView(text("Your screen comes back when the timer ends.", 16f))
        if (prefs().getBoolean("snooze", false) && !snoozeUsed) {
            root.addView(Button(this).apply {
                text = "Postpone 5 minutes (once)"
                setOnClickListener {
                    snoozeUsed = true
                    endBreak(reschedule = false)
                    handler.postDelayed(showBreak, 5 * 60_000L)
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
        nm.createNotificationChannel(
            NotificationChannel("restlight", "Eye breaks", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, "restlight")
            .setContentTitle("Restlight is running")
            .setContentText("Your next eye break is on its way.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(showBreak)
        endBreak(reschedule = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
