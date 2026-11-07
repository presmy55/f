package com.relais.bt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class JoystickView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var onPositionChanged: ((Float, Float) -> Unit)? = null

    private var axisX = 0f
    private var axisY = 0f
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(221, 225, 232) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(23, 59, 53)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36, 107, 88) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(23, 59, 53)
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 12
    }

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.39f
        if (radius <= 0f) return

        canvas.drawCircle(centerX, centerY, radius, basePaint)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)
        canvas.drawCircle(
            centerX + axisX * radius * 0.55f,
            centerY + axisY * radius * 0.55f,
            radius * 0.38f,
            knobPaint
        )
        canvas.drawText(contentDescription?.toString() ?: "Joystick", centerX, height - 4f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> updatePosition(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                axisX = 0f
                axisY = 0f
                onPositionChanged?.invoke(0f, 0f)
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updatePosition(x: Float, y: Float) {
        val radius = min(width, height) * 0.39f
        if (radius <= 0f) return
        val centerX = width / 2f
        val centerY = height / 2f
        var nextX = ((x - centerX) / radius).coerceIn(-1f, 1f)
        var nextY = ((y - centerY) / radius).coerceIn(-1f, 1f)
        val magnitude = hypot(nextX, nextY)
        if (magnitude > 1f) {
            nextX /= magnitude
            nextY /= magnitude
        }
        axisX = nextX
        axisY = nextY
        onPositionChanged?.invoke(axisX, axisY)
        invalidate()
    }
}
