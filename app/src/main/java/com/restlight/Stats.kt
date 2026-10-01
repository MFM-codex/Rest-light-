package com.restlight

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object Stats {
    private fun key(back: Int): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -back)
        return "stat_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }
    private fun p(ctx: Context) = ctx.getSharedPreferences("restlight", Context.MODE_PRIVATE)

    fun day(ctx: Context, back: Int): Pair<Int, Int> {
        val s = (p(ctx).getString(key(back), "0,0") ?: "0,0").split(",")
        return Pair(s[0].toIntOrNull() ?: 0, s.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    // idx 0 = break completed, 1 = break postponed
    fun add(ctx: Context, idx: Int) {
        val v = day(ctx, 0).toList().toMutableList()
        v[idx] = v[idx] + 1
        p(ctx).edit().putString(key(0), "${v[0]},${v[1]}").apply()
    }
}
