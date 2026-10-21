package desu.inugram.prism

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import org.telegram.messenger.AndroidUtilities

/**
 * A frosted, refracting glass plate. It re-draws the aurora behind it, slightly magnified (the
 * "lens"), then lays a tint, a top highlight and a bright rim on top, so it reads as glass sitting
 * over the colour field without a second blur pass.
 *
 * [anchor] is the view the [scene] fills; the panel finds its own position inside it.
 */
class GlassPanelView(
    context: Context,
    private val scene: AuroraScene,
    private val anchor: View,
    private val radiusDp: Float,
    private val animated: Boolean = PrismTier.tier() >= PrismTier.MID,
    private val frameDelayMs: Long = PrismTier.frameDelayMs(PrismTier.tier()),
) : FrameLayout(context) {

    private val dark = scene.dark
    private val rect = RectF()
    private val path = Path()
    private val tmpSelf = IntArray(2)
    private val tmpAnchor = IntArray(2)

    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dpf2(1.2f)
    }
    private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dpf2(1f)
        color = if (dark) 0x14FFFFFF else 0x22000000
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = AndroidUtilities.dpf2(radiusDp)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)

        tintPaint.color = if (dark) 0x14FFFFFF else 0x60FFFFFF
        highlightPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.65f,
            if (dark) 0x38FFFFFF else 0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        rimPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                if (dark) 0xB3FFFFFF.toInt() else 0xFFFFFFFF.toInt(),
                if (dark) 0x1FFFFFFF else 0x33FFFFFF,
                if (dark) 0x66FFFFFF else 0xB3FFFFFF.toInt(),
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0f && h > 0f && anchor.width > 0) {
            getLocationInWindow(tmpSelf)
            anchor.getLocationInWindow(tmpAnchor)
            val ox = (tmpSelf[0] - tmpAnchor[0]).toFloat()
            val oy = (tmpSelf[1] - tmpAnchor[1]).toFloat()

            canvas.save()
            canvas.clipPath(path)
            // move into the scene's coordinates and magnify a touch around the plate's centre
            canvas.translate(-ox, -oy)
            canvas.scale(1.06f, 1.06f, ox + w / 2f, oy + h / 2f)
            scene.draw(canvas, anchor.width.toFloat(), anchor.height.toFloat(), if (animated) PrismTier.clock() else 0f)
            canvas.restore()

            canvas.drawPath(path, tintPaint)
            canvas.save()
            canvas.clipPath(path)
            canvas.drawRect(rect, highlightPaint)
            canvas.restore()
            canvas.drawPath(path, rimPaint)
            canvas.save()
            val inset = AndroidUtilities.dpf2(1.6f)
            canvas.translate(inset, inset)
            canvas.scale((w - 2 * inset) / w, (h - 2 * inset) / h)
            canvas.drawPath(path, innerRimPaint)
            canvas.restore()
        }
        super.dispatchDraw(canvas)
        if (animated && frameDelayMs > 0 && isShown) postInvalidateDelayed(frameDelayMs)
    }
}
