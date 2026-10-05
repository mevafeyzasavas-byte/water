package com.alikal.sutakibi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

class RingView(c: Context, a: AttributeSet?) : View(c, a) {
    private var total = 0
    private var goal = 2000
    private val d = resources.displayMetrics.density

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#D6E6F2")
        strokeWidth = 16 * d
    }
    private val prog = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#0A6EBD")
        strokeWidth = 16 * d; strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F2A3D"); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textSize = sp(52f)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B7083"); textAlign = Paint.Align.CENTER
        textSize = sp(15f)
    }

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    fun set(t: Int, g: Int) {
        total = t; goal = g.coerceAtLeast(1); invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val sw = 16 * d
        val s = min(width, height).toFloat()
        val r = RectF(sw / 2, sw / 2, s - sw / 2, s - sw / 2)
        cv.drawArc(r, 0f, 360f, false, track)
        val sweep = min(1f, total / goal.toFloat()) * 360f
        if (sweep > 0f) cv.drawArc(r, -90f, sweep, false, prog)
        cv.drawText(total.toString(), s / 2, s / 2 + sp(10f), big)
        cv.drawText("/ $goal ml", s / 2, s / 2 + sp(34f), small)
    }
}
