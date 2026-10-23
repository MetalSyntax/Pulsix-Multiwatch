package com.metalsyntax.dw26

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

private fun Context.dp(v: Float) = v * resources.displayMetrics.density

/** Anillo de progreso (pasos vs. meta). */
class Ring @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val stroke = c.dp(14f)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = 0x33FFFFFF }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }
    private val box = RectF()

    override fun onDraw(cv: Canvas) {
        val r = minOf(width, height) / 2f - stroke
        box.set(width / 2f - r, height / 2f - r, width / 2f + r, height / 2f + r)
        cv.drawArc(box, 0f, 360f, false, track)
        if (progress > 0f) cv.drawArc(box, -90f, 360f * progress, false, bar)
    }
}

/** Barras (pasos/sueño por día) o línea (ritmo cardíaco del día). */
class Chart @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private var values: List<Float> = emptyList()
    private var xs: List<Float>? = null
    private var labels: List<String> = emptyList()
    private var line = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = c.dp(11f); color = c.getColor(R.color.dw_muted); textAlign = Paint.Align.CENTER }
    private val grid = Paint().apply { color = c.getColor(R.color.dw_line); strokeWidth = c.dp(1f) }
    var color = c.getColor(R.color.dw_blue)

    fun bars(v: List<Float>, l: List<String>) { values = v; labels = l; xs = null; line = false; invalidate() }

    /** x en 0..1 (fracción del día), etiquetas repartidas a lo ancho. */
    fun line(x: List<Float>, v: List<Float>, l: List<String>) { xs = x; values = v; labels = l; line = true; invalidate() }

    override fun onDraw(cv: Canvas) {
        val bottom = height - context.dp(20f)
        cv.drawLine(0f, bottom, width.toFloat(), bottom, grid)
        text.textAlign = if (line) Paint.Align.LEFT else Paint.Align.CENTER
        labels.forEachIndexed { i, s ->
            // línea: la etiqueta i marca la fracción i/n del día; barras: centrada bajo cada barra
            val x = if (line) width * i / labels.size.toFloat() else width * (i + 0.5f) / values.size.coerceAtLeast(1)
            cv.drawText(s, x, height - context.dp(4f), text)
        }
        if (values.isEmpty()) return
        val top = context.dp(8f)
        paint.color = color
        if (line) {
            val lo = values.min() - 5; val hi = values.max() + 5
            val p = Path()
            values.forEachIndexed { i, v ->
                val x = xs!![i] * width; val y = bottom - (v - lo) / (hi - lo) * (bottom - top)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dp(2f); paint.strokeJoin = Paint.Join.ROUND
            cv.drawPath(p, paint)
            paint.style = Paint.Style.FILL
            values.forEachIndexed { i, v -> cv.drawCircle(xs!![i] * width, bottom - (v - lo) / (hi - lo) * (bottom - top), context.dp(2.5f), paint) }
        } else {
            val max = values.max().coerceAtLeast(1f)
            val slot = width / values.size.toFloat(); val w = slot * 0.5f
            paint.style = Paint.Style.FILL
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - w) / 2; val h = v / max * (bottom - top)
                cv.drawRoundRect(x, bottom - h, x + w, bottom, w / 3, w / 3, paint)
            }
        }
    }
}
