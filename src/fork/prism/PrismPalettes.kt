package desu.inugram.prism

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.ui.ActionBar.Theme

/** The row of Paper colour palettes shown under "Color theme" in Chat Settings. */
object PrismPalettes {
    @JvmStatic
    fun createRow(context: Context): View {
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(10f), AndroidUtilities.dp(12f), AndroidUtilities.dp(6f))
        }
        val swatches = PrismTheme.palettes.map { Swatch(context, it) }
        fun refresh() { swatches.forEach { it.invalidate() } }
        for (s in swatches) {
            s.setOnClickListener {
                try {
                    val dark = Theme.isCurrentThemeDark()
                    context.getSharedPreferences("prism", Context.MODE_PRIVATE).edit().putBoolean("paper_user_choice", true).apply()
                    PrismTheme.installPalette(s.palette, dark)
                    PrismTheme.pruneThemes()
                } catch (t: Throwable) { FileLog.e(t) }
                AndroidUtilities.runOnUIThread({ refresh() }, 250)
            }
            row.addView(s, LinearLayout.LayoutParams(AndroidUtilities.dp(84f), AndroidUtilities.dp(112f)).apply { rightMargin = AndroidUtilities.dp(8f) })
        }
        scroll.addView(row)
        return scroll
    }

    private class Swatch(context: Context, val palette: PrismTheme.Palette) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val clip = Path()
        private fun dp(v: Float) = AndroidUtilities.dp(v).toFloat()

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat() - dp(22f)
            r.set(dp(2f), dp(2f), w - dp(2f), h)
            clip.reset(); clip.addRoundRect(r, dp(16f), dp(16f), Path.Direction.CW)
            c.save(); c.clipPath(clip)
            p.style = Paint.Style.FILL
            p.color = palette.bgLight; c.drawRect(r.left, r.top, w / 2f, r.bottom, p)
            p.color = palette.bgDark; c.drawRect(w / 2f, r.top, r.right, r.bottom, p)
            // a mock bubble in the accent colour on each half
            p.color = palette.accentLight; c.drawRoundRect(r.left + dp(8f), r.top + dp(14f), w / 2f - dp(4f), r.top + dp(30f), dp(8f), dp(8f), p)
            p.color = palette.accentDark; c.drawRoundRect(w / 2f + dp(4f), r.top + dp(34f), r.right - dp(8f), r.top + dp(50f), dp(8f), dp(8f), p)
            c.restore()
            val selected = PrismTheme.currentPalette() == palette
            p.style = Paint.Style.STROKE
            p.strokeWidth = if (selected) dp(3f) else dp(1f)
            p.color = if (selected) palette.accentLight else (Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) and 0x00FFFFFF or 0x55000000)
            c.drawRoundRect(r, dp(16f), dp(16f), p)
            p.style = Paint.Style.FILL
            p.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
            p.textSize = dp(13f); p.textAlign = Paint.Align.CENTER
            c.drawText(palette.label, w / 2f, height - dp(4f), p)
        }
    }
}
