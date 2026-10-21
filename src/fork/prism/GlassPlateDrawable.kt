package desu.inugram.prism

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import org.telegram.messenger.AndroidUtilities

/**
 * A static glass plate (tint + top highlight + bright rim) for places that need a cheap glass
 * background and cannot host a [GlassPanelView], such as the stock login forms. It sits over the
 * aurora, which is already soft, so the tint alone sells the frosted look.
 */
class GlassPlateDrawable(private val dark: Boolean, private val radius: Float) : Drawable() {
    private val rect = RectF()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dpf2(1.2f)
    }

    override fun onBoundsChange(b: android.graphics.Rect) {
        rect.set(b)
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        tint.color = if (dark) 0x1AFFFFFF else 0x70FFFFFF
        highlight.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.top + h * 0.5f,
            if (dark) 0x2EFFFFFF else 0x88FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        rim.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.left + w, b.top + h,
            intArrayOf(
                if (dark) 0xAAFFFFFF.toInt() else 0xFFFFFFFF.toInt(),
                if (dark) 0x1AFFFFFF else 0x44FFFFFF,
                if (dark) 0x55FFFFFF else 0xAAFFFFFF.toInt(),
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, tint)
        canvas.drawRoundRect(rect, radius, radius, highlight)
        val half = rim.strokeWidth / 2f
        rect.inset(half, half)
        canvas.drawRoundRect(rect, radius - half, radius - half, rim)
        rect.inset(-half, -half)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
