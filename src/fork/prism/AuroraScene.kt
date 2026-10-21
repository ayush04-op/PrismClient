package desu.inugram.prism

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Soft drifting colour blobs: the "mesh" every glass surface refracts.
 * Gradients are built once per size; per frame it is only a handful of translated circles.
 */
class AuroraScene(val dark: Boolean) {
    private class Blob(
        val color: Int,
        val cx: Float, val cy: Float,
        val radius: Float,
        val ax: Float, val ay: Float,
        val speed: Float, val phase: Float,
    )

    private val base = if (dark) 0xFF06070F.toInt() else 0xFFF4F2FF.toInt()

    private val blobs: Array<Blob> = if (dark) arrayOf(
        Blob(0xFF6A3DFF.toInt(), 0.18f, 0.20f, 0.80f, 0.16f, 0.12f, 0.17f, 0.0f),
        Blob(0xFF1E46E0.toInt(), 0.88f, 0.34f, 0.78f, 0.14f, 0.16f, 0.13f, 2.1f),
        Blob(0xFF0FB5A6.toInt(), 0.22f, 0.88f, 0.70f, 0.18f, 0.10f, 0.15f, 4.2f),
        Blob(0xFFC13DE0.toInt(), 0.86f, 0.90f, 0.62f, 0.12f, 0.14f, 0.19f, 5.4f),
    ) else arrayOf(
        Blob(0xFFB9A6FF.toInt(), 0.18f, 0.20f, 0.80f, 0.16f, 0.12f, 0.17f, 0.0f),
        Blob(0xFF9CC9FF.toInt(), 0.88f, 0.34f, 0.78f, 0.14f, 0.16f, 0.13f, 2.1f),
        Blob(0xFF9BEBD2.toInt(), 0.22f, 0.88f, 0.70f, 0.18f, 0.10f, 0.15f, 4.2f),
        Blob(0xFFFFB6DC.toInt(), 0.86f, 0.90f, 0.62f, 0.12f, 0.14f, 0.19f, 5.4f),
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (dark) xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val shaders = arrayOfNulls<RadialGradient>(blobs.size)
    private var shaderSize = 0f

    private fun ensureShaders(size: Float) {
        if (size == shaderSize) return
        shaderSize = size
        for (i in blobs.indices) {
            val b = blobs[i]
            val r = b.radius * size
            val c = b.color
            val strong = Color.argb(if (dark) 235 else 210, Color.red(c), Color.green(c), Color.blue(c))
            val mid = Color.argb(if (dark) 90 else 95, Color.red(c), Color.green(c), Color.blue(c))
            val none = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))
            shaders[i] = RadialGradient(0f, 0f, r, intArrayOf(strong, mid, none), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }

    fun draw(canvas: Canvas, w: Float, h: Float, t: Float) {
        if (w <= 0f || h <= 0f) return
        val size = max(w, h) * 0.62f
        ensureShaders(size)
        canvas.drawColor(base)
        for (i in blobs.indices) {
            val b = blobs[i]
            val x = (b.cx + b.ax * sin(t * b.speed + b.phase)) * w
            val y = (b.cy + b.ay * cos(t * b.speed * 0.83f + b.phase * 1.3f)) * h
            paint.shader = shaders[i]
            canvas.save()
            canvas.translate(x, y)
            canvas.drawCircle(0f, 0f, b.radius * size, paint)
            canvas.restore()
        }
    }
}

/** [AuroraScene] as a view background. Animates only when [animated]; stops itself when hidden. */
class AuroraDrawable(
    val scene: AuroraScene,
    private val animated: Boolean,
    private val frameDelayMs: Long = PrismTier.frameDelayMs(PrismTier.tier()),
) : Drawable() {
    private val tick = Runnable { invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        scene.draw(canvas, b.width().toFloat(), b.height().toFloat(), if (animated) PrismTier.clock() else 0f)
        canvas.restore()
        if (animated && frameDelayMs > 0 && isVisible) {
            scheduleSelf(tick, android.os.SystemClock.uptimeMillis() + frameDelayMs)
        }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) unscheduleSelf(tick) else if (changed) invalidateSelf()
        return changed
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
