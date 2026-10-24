package desu.inugram.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import desu.inugram.prism.PrismUpdater
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

/** A "Software update" screen in the style of Android's own: one status card, one main button. */
class PrismUpdateActivity : SettingsPageActivity() {
    private enum class State { CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

    private var state = State.CHECKING
    private var progress = 0
    private var errorText = ""
    private var card: UpdateCard? = null

    override fun getTitle(): CharSequence = "Software update"

    override fun createView(context: Context): View {
        return super.createView(context).also {
            val pending = PrismUpdater.downloaded
            state = when {
                pending != null && pending.exists() && PrismUpdater.latest != null -> State.READY
                PrismUpdater.latest != null -> State.AVAILABLE
                else -> State.CHECKING
            }
            if (state == State.CHECKING) check()
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val ctx = context ?: return
        val c = card ?: UpdateCard(ctx) { onPrimary() }.also { it.onSecondary = { onSecondary() }; card = it }
        items.add(UItem.asCustom(c))
        items.add(UItem.asShadow(null))
        render()
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {}

    private fun check() {
        val ctx = context ?: return
        state = State.CHECKING; render()
        PrismUpdater.check(ctx) { info ->
            if (context == null) return@check
            PrismUpdater.downloaded = null
            state = when {
                PrismUpdater.status == "Could not check" -> { errorText = "Couldn't reach the update server. Check your connection and try again."; State.ERROR }
                info != null -> State.AVAILABLE
                else -> State.UP_TO_DATE
            }
            render()
        }
    }

    private fun onPrimary() {
        val ctx = context ?: return
        when (state) {
            State.UP_TO_DATE, State.ERROR -> check()
            State.AVAILABLE -> {
                val info = PrismUpdater.latest ?: return
                state = State.DOWNLOADING; progress = 0; render()
                PrismUpdater.download(ctx, info, { p ->
                    progress = p
                    card?.setProgress(p, animate = true)
                }) { file ->
                    if (context == null) return@download
                    if (file == null) {
                        errorText = "The download didn't finish. Try again."
                        state = State.ERROR
                    } else state = State.READY
                    render()
                }
            }
            State.READY -> {
                val file = PrismUpdater.downloaded ?: return
                val act = parentActivity ?: return
                desu.inugram.helpers.update.ApkInstaller.installFromFile(act, file)
            }
            else -> {}
        }
    }

    private fun onSecondary() {
        if (state == State.DOWNLOADING) PrismUpdater.cancelDownload()
    }

    private fun render() {
        val ctx = context ?: return
        val c = card ?: return
        val cur = PrismUpdater.currentBuild(ctx)
        val info = PrismUpdater.latest
        val size = info?.size?.takeIf { it > 0 }?.let { " · ${it / 1048576} MB" } ?: ""
        val checked = if (PrismUpdater.lastChecked > 0)
            "Last checked " + DateUtils.getRelativeTimeSpanString(PrismUpdater.lastChecked, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else ""
        when (state) {
            State.CHECKING -> c.show("Checking for updates", "Looking for the newest Prism build…", null, null, busy = true)
            State.UP_TO_DATE -> c.show("Prism is up to date", "Build $cur" + (if (checked.isNotEmpty()) "\n$checked" else ""), "Check for updates", null)
            State.AVAILABLE -> c.show("Build ${info?.build} is ready", "You have build $cur$size", "Download and install", null, notes = info?.notes)
            State.DOWNLOADING -> { c.show("Downloading update", "Keep this screen open", null, "Cancel", showBar = true); c.setProgress(progress, animate = false) }
            State.READY -> c.show("Ready to install", "Build ${info?.build} has been downloaded. Your chats and settings stay as they are.", "Install now", null, notes = info?.notes)
            State.ERROR -> c.show("Update problem", errorText, "Try again", null)
        }
    }

    private class UpdateCard(context: Context, val onPrimary: () -> Unit) : LinearLayout(context) {
        var onSecondary: () -> Unit = {}
        private fun dp(v: Float) = AndroidUtilities.dp(v)
        private fun c(key: Int) = Theme.getColor(key)

        private val logo = ImageView(context).apply {
            setBackgroundResource(R.drawable.prism_icon_bg)
            setImageResource(R.drawable.prism_icon_fg)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(22f).toFloat()) }
            }
        }
        private val title = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        private val sub = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f) }
        private val bar = ProgressBarView(context)
        private val primary = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener { onPrimary() }
        }
        private val secondary = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); gravity = Gravity.CENTER
            setOnClickListener { onSecondary() }
        }
        private val notes = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.2f) }
        private val spinner = org.telegram.ui.Components.RadialProgressView(context)

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(32f), dp(24f), dp(24f))
            addView(logo, LayoutParams(dp(84f), dp(84f)))
            addView(spinner, LayoutParams(dp(28f), dp(28f)).apply { topMargin = dp(18f) })
            addView(title, LayoutParams(-1, -2).apply { topMargin = dp(18f) })
            addView(sub, LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            addView(bar, LayoutParams(-1, dp(8f)).apply { topMargin = dp(22f); leftMargin = dp(12f); rightMargin = dp(12f) })
            addView(primary, LayoutParams(-1, dp(50f)).apply { topMargin = dp(24f) })
            addView(secondary, LayoutParams(-1, dp(44f)).apply { topMargin = dp(6f) })
            addView(notes, LayoutParams(-1, -2).apply { topMargin = dp(18f) })
            recolor()
        }

        fun recolor() {
            title.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText))
            sub.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText))
            notes.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText))
            secondary.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText))
            primary.background = GradientDrawable().apply { cornerRadius = dp(25f).toFloat(); setColor(c(Theme.key_featuredStickers_addButton)) }
            bar.recolor()
        }

        fun show(t: String, s: String, button: String?, cancel: String?, busy: Boolean = false, showBar: Boolean = false, notes: String? = null) {
            recolor()
            title.text = t; sub.text = s
            spinner.visibility = if (busy) VISIBLE else GONE
            logo.visibility = if (busy) GONE else VISIBLE
            bar.visibility = if (showBar) VISIBLE else GONE
            primary.visibility = if (button != null) VISIBLE else GONE
            primary.text = button ?: ""
            secondary.visibility = if (cancel != null) VISIBLE else GONE
            secondary.text = cancel ?: ""
            val n = notes?.trim().orEmpty()
            this.notes.visibility = if (n.isNotEmpty()) VISIBLE else GONE
            this.notes.text = if (n.isNotEmpty()) "What's new\n$n" else ""
        }

        fun setProgress(p: Int, animate: Boolean) { sub.text = "$p% downloaded"; bar.set(p / 100f, animate) }
    }

    private class ProgressBarView(context: Context) : View(context) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var value = 0f
        private var anim: ValueAnimator? = null
        fun recolor() {
            track.color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) and 0x00FFFFFF or 0x33000000
            fill.color = Theme.getColor(Theme.key_featuredStickers_addButton)
        }
        fun set(target: Float, animate: Boolean) {
            anim?.cancel()
            if (!animate) { value = target; invalidate(); return }
            anim = ValueAnimator.ofFloat(value, target).apply {
                duration = 250; interpolator = DecelerateInterpolator()
                addUpdateListener { value = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            rect.set(0f, 0f, width.toFloat(), height.toFloat()); canvas.drawRoundRect(rect, r, r, track)
            rect.set(0f, 0f, width * value.coerceIn(0f, 1f), height.toFloat()); canvas.drawRoundRect(rect, r, r, fill)
        }
    }
}
