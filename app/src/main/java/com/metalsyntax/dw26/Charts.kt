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
    var color = com.google.android.material.color.MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0)

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

/**
 * Simulación del DW26: cuerpo redondeado, corona lateral y pantalla 240×284 con la esfera dentro.
 * [inUse] dibuja el contorno azul de "en uso".
 */
class WatchMock @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var bitmap: android.graphics.Bitmap? = null
        set(v) { field = v; invalidate() }
    var inUse = false
        set(v) { field = v; invalidate() }
    var placeholder: android.graphics.drawable.Drawable? = null

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = com.google.android.material.color.MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0) }
    private val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val r = RectF(); private val sr = RectF(); private val clip = Path()

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        // cuerpo: pantalla 240×284 + bisel; la corona sobresale a la derecha
        setMeasuredDimension(width, (width * 0.94f / 240f * 284f * 1.04f).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val pad = width * 0.03f
        val bodyW = width * 0.94f - pad
        r.set(pad, pad, pad + bodyW, height - pad)
        val bodyR = bodyW * 0.24f
        body.shader = android.graphics.LinearGradient(0f, r.top, 0f, r.bottom, 0xFF2C3340.toInt(), 0xFF161A21.toInt(), android.graphics.Shader.TileMode.CLAMP)
        // corona
        cv.drawRoundRect(r.right - bodyW * 0.02f, r.top + r.height() * 0.28f, r.right + bodyW * 0.05f, r.top + r.height() * 0.42f, bodyW * 0.03f, bodyW * 0.03f, body)
        cv.drawRoundRect(r, bodyR, bodyR, body)
        if (inUse) { ring.strokeWidth = pad * 0.9f; cv.drawRoundRect(r, bodyR, bodyR, ring) }
        val bezel = bodyW * 0.07f
        sr.set(r.left + bezel, r.top + bezel, r.right - bezel, r.bottom - bezel)
        val sR = bodyR * 0.72f
        cv.drawRoundRect(sr, sR, sR, screen)
        clip.reset(); clip.addRoundRect(sr, sR, sR, Path.Direction.CW)
        cv.save(); cv.clipPath(clip)
        bitmap?.let { b ->
            // centerCrop
            val scale = maxOf(sr.width() / b.width, sr.height() / b.height)
            val dw = b.width * scale; val dh = b.height * scale
            cv.drawBitmap(b, null, RectF(sr.centerX() - dw / 2, sr.centerY() - dh / 2, sr.centerX() + dw / 2, sr.centerY() + dh / 2), img)
        } ?: placeholder?.let { d ->
            val s = (sr.width() * 0.35f).toInt()
            d.setBounds((sr.centerX() - s / 2).toInt(), (sr.centerY() - s / 2).toInt(), (sr.centerX() + s / 2).toInt(), (sr.centerY() + s / 2).toInt())
            d.draw(cv)
        }
        cv.restore()
    }
}
