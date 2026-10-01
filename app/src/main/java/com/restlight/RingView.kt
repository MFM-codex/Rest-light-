package com.restlight

import android.content.Context
import android.graphics.*
import android.view.View

class RingView(c: Context) : View(c) {
    var fraction = 0f
    var label = "--:--"
    var sub = "Not running"

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#3A3650") }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#7CC0B6"); strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0DCC0"); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A79CAE"); textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val s = minOf(w, height.toFloat())
        val sw = s * 0.06f
        track.strokeWidth = sw; arc.strokeWidth = sw
        val r = RectF(sw, sw, s - sw, s - sw)
        r.offset((w - s) / 2f, 0f)
        canvas.drawArc(r, 0f, 360f, false, track)
        canvas.drawArc(r, -90f, 360f * fraction, false, arc)
        big.textSize = s * 0.2f
        canvas.drawText(label, w / 2f, s / 2f + big.textSize * 0.3f, big)
        small.textSize = s * 0.07f
        canvas.drawText(sub, w / 2f, s / 2f + s * 0.2f, small)
    }
}
