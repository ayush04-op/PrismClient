package desu.inugram.prism

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.LoginActivity

/**
 * Prism's welcome: five short pages shown once to a new user, before login.
 * 1 welcome, 2 pick a look (palette + light/dark/auto, applied live), 3 what's inside, 4 privacy, 5 get started.
 * Everything is drawn from the active theme, so it re-colours the moment a palette is picked.
 * [replay] = opened again from Settings: the last button closes instead of going to login.
 */
class PrismIntroActivity @JvmOverloads constructor(private val replay: Boolean = false) :
    BaseFragment(), NotificationCenter.NotificationCenterDelegate {

    private var root: FrameLayout? = null
    private var pager: ViewPager? = null
    private var page = 0
    private var done = false

    /** kept so the logout path can use the same call shape as the stock intro */
    fun setOnLogout(): PrismIntroActivity = this

    // ── colours, always read fresh from the theme ───────────────────────────
    private fun bg() = Theme.getColor(Theme.key_windowBackgroundWhite)
    private fun ink() = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
    private fun muted() = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
    private fun accent() = Theme.getColor(Theme.key_featuredStickers_addButton)
    private fun onAccent() = Theme.getColor(Theme.key_featuredStickers_buttonText)
    private fun dark() = AndroidUtilities.computePerceivedBrightness(bg()) < 0.5f
    private fun card() = ColorUtils.blendARGB(bg(), 0xFFFFFFFF.toInt(), if (dark()) 0.06f else 0.55f)
    private fun line() = ColorUtils.blendARGB(bg(), ink(), if (dark()) 0.16f else 0.11f)

    override fun isLightStatusBar(): Boolean = !dark()

    override fun onFragmentCreate(): Boolean {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id != NotificationCenter.didSetNewTheme) return
        rebuild()
        // Telegram may still be blending colours for a moment; settle on the final ones
        AndroidUtilities.runOnUIThread({ rebuild() }, 420)
    }

    override fun createView(context: Context): View {
        actionBar.setAddToContainer(false)
        if (!replay) {
            try { PrismTheme.ensurePaper() } catch (t: Throwable) { FileLog.e(t) }
        }
        val r = FrameLayout(context)
        root = r
        fragmentView = r
        build(context, r)
        return r
    }

    private fun rebuild() {
        val r = root ?: return
        val ctx = r.context ?: return
        page = pager?.currentItem ?: page
        r.removeAllViews()
        build(ctx, r)
        parentActivity?.window?.let { AndroidUtilities.setLightStatusBar(it, isLightStatusBar()) }
    }

    private fun dp(v: Float) = AndroidUtilities.dp(v)

    private fun text(ctx: Context, s: CharSequence, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        if (bold) typeface = AndroidUtilities.bold()
        setLineSpacing(0f, 1.15f)
    }

    private fun round(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = AndroidUtilities.dpf2(radius)
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

    private fun ripple(base: GradientDrawable, radius: Float): RippleDrawable {
        val mask = GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = AndroidUtilities.dpf2(radius) }
        return RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink(), 30)), base, mask)
    }

    // ── layout ──────────────────────────────────────────────────────────────
    private fun build(ctx: Context, r: FrameLayout) {
        r.setBackgroundColor(bg())

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        r.addView(col, FrameLayout.LayoutParams(-1, -1))

        // top: Skip
        val top = FrameLayout(ctx)
        val skip = text(ctx, if (replay) "Close" else "Skip", 16f, muted()).apply {
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            background = ripple(round(0, 20f), 20f)
            setOnClickListener { finishWelcome() }
        }
        top.addView(skip, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(12f) })
        col.addView(top, LinearLayout.LayoutParams(-1, dp(52f)).apply { topMargin = AndroidUtilities.statusBarHeight })

        // middle: pages
        val pages = listOf(welcomePage(ctx), lookPage(ctx), featuresPage(ctx), privacyPage(ctx), startPage(ctx))
        val vp = ViewPager(ctx)
        vp.adapter = object : PagerAdapter() {
            override fun getCount() = pages.size
            override fun isViewFromObject(view: View, obj: Any) = view === obj
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val v = pages[position]
                (v.parent as? ViewGroup)?.removeView(v)
                container.addView(v, ViewGroup.LayoutParams(-1, -1))
                return v
            }
            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
        }
        vp.offscreenPageLimit = pages.size
        pager = vp
        col.addView(vp, LinearLayout.LayoutParams(-1, 0, 1f))

        // bottom: dots + main button
        val dots = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val dotViews = pages.indices.map { View(ctx).also { dots.addView(it) } }
        col.addView(dots, LinearLayout.LayoutParams(-1, dp(24f)).apply { topMargin = dp(8f) })

        val button = text(ctx, "", 17f, onAccent(), bold = true).apply {
            gravity = Gravity.CENTER
            background = ripple(round(accent(), 28f), 28f)
            elevation = AndroidUtilities.dpf2(2f)
        }
        col.addView(button, LinearLayout.LayoutParams(-1, dp(56f)).apply {
            leftMargin = dp(28f); rightMargin = dp(28f); topMargin = dp(14f); bottomMargin = dp(28f)
        })

        fun sync(p: Int) {
            page = p
            dotViews.forEachIndexed { i, d ->
                val on = i == p
                d.background = round(if (on) accent() else ColorUtils.setAlphaComponent(muted(), 90), 4f)
                d.layoutParams = LinearLayout.LayoutParams(dp(if (on) 22f else 7f), dp(7f)).apply {
                    leftMargin = dp(3.5f); rightMargin = dp(3.5f)
                }
            }
            val last = p == pages.lastIndex
            button.text = when {
                !last -> "Next"
                replay -> "Done"
                else -> LocaleController.getString(R.string.StartMessaging)
            }
            skip.visibility = if (last && !replay) View.INVISIBLE else View.VISIBLE
        }
        vp.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) = sync(position)
        })
        button.setOnClickListener {
            val cur = vp.currentItem
            if (cur < pages.lastIndex) vp.setCurrentItem(cur + 1, true) else finishWelcome()
        }
        vp.setCurrentItem(page.coerceIn(0, pages.lastIndex), false)
        sync(vp.currentItem)
    }

    /** a page body: centred column with even side margins; scrolls if the screen is short */
    private fun pageShell(ctx: Context, content: LinearLayout): View {
        val sv = ScrollView(ctx).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val holder = FrameLayout(ctx)
        holder.addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(28f); rightMargin = dp(28f); topMargin = dp(8f); bottomMargin = dp(8f)
        })
        sv.addView(holder, FrameLayout.LayoutParams(-1, -2))
        return sv
    }

    private fun column(ctx: Context, center: Boolean = false) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = if (center) Gravity.CENTER_HORIZONTAL else Gravity.START
    }

    private fun logoPlate(ctx: Context, size: Float): View {
        val plate = FrameLayout(ctx).apply { background = round(accent(), size * 0.27f) }
        val logo = ImageView(ctx).apply {
            setImageResource(R.drawable.prism_icon_fg)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        plate.addView(logo, FrameLayout.LayoutParams(-1, -1))
        plate.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        return plate
    }

    private fun heading(ctx: Context, title: String, sub: String?, col: LinearLayout, center: Boolean = false) {
        col.addView(text(ctx, title, 30f, ink(), bold = true).apply { if (center) gravity = Gravity.CENTER })
        if (sub != null) {
            col.addView(text(ctx, sub, 16f, muted()).apply { if (center) gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
        }
    }

    // ── 1. welcome ──────────────────────────────────────────────────────────
    private fun welcomePage(ctx: Context): View {
        val c = column(ctx, center = true)
        c.addView(logoPlate(ctx, 104f))
        c.addView(text(ctx, "Prism", 44f, ink(), bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24f) })
        c.addView(text(ctx, "A calmer way to use Telegram.", 18f, muted()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f) })
        c.addView(text(ctx, "Unofficial client. Uses the Telegram API.", 13f, muted()).apply {
            gravity = Gravity.CENTER; alpha = 0.85f
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(36f) })
        enter(c)
        return pageShell(ctx, c)
    }

    // ── 2. pick your look ───────────────────────────────────────────────────
    private fun currentMode(): Int = when {
        Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE -> MODE_AUTO
        Theme.isCurrentThemeDark() -> MODE_DARK
        else -> MODE_LIGHT
    }

    private fun lookPage(ctx: Context): View {
        val c = column(ctx)
        heading(ctx, "Pick your look", "Six paper palettes, each with a light and a dark side. You can change this later in Settings.", c)

        val current = PrismTheme.currentPalette()?.id ?: "paper"
        val isDark = dark()
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        PrismTheme.palettes.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, p ->
                val on = p.id == current
                val chip = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10f), 0, dp(14f), 0)
                    background = ripple(round(card(), 26f, if (on) dp(2f) else dp(1f), if (on) accent() else line()), 26f)
                    contentDescription = p.label
                    setOnClickListener { pickPalette(p) }
                }
                val dot = View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (isDark) p.accentDark else p.accentLight)
                    }
                }
                chip.addView(dot, LinearLayout.LayoutParams(dp(30f), dp(30f)))
                chip.addView(text(ctx, p.label, 16f, ink(), bold = on).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12f) })
                row.addView(chip, LinearLayout.LayoutParams(0, dp(52f), 1f).apply {
                    if (i == 0) rightMargin = dp(6f) else leftMargin = dp(6f)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(12f) })
        }
        c.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24f) })

        // light / dark / auto
        val mode = currentMode()
        val seg = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            background = round(card(), 26f, dp(1f), line())
        }
        listOf(MODE_LIGHT to "Light", MODE_DARK to "Dark", MODE_AUTO to "Auto").forEach { (m, label) ->
            val on = m == mode
            val b = text(ctx, label, 15f, if (on) onAccent() else muted(), bold = on).apply {
                gravity = Gravity.CENTER
                background = if (on) round(accent(), 22f) else ripple(round(0, 22f), 22f)
                setOnClickListener { setMode(m) }
            }
            seg.addView(b, LinearLayout.LayoutParams(0, dp(42f), 1f))
        }
        c.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20f) })
        c.addView(text(ctx, "Auto follows your phone's dark mode.", 13f, muted()),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f); leftMargin = dp(6f) })
        return pageShell(ctx, c)
    }

    private fun remember() {
        ApplicationLoader.applicationContext.getSharedPreferences("prism", Context.MODE_PRIVATE)
            .edit().putBoolean("paper_user_choice", true).apply()
    }

    private fun pickPalette(p: PrismTheme.Palette) {
        if (p.id == PrismTheme.currentPalette()?.id) return
        try {
            remember()
            PrismTheme.installPalette(p, Theme.isCurrentThemeDark())
            PrismTheme.pruneThemes()
        } catch (t: Throwable) { FileLog.e(t) }
    }

    private fun setMode(m: Int) {
        if (m == currentMode()) return
        val p = PrismTheme.currentPalette() ?: PrismTheme.palettes[0]
        try {
            remember()
            when (m) {
                MODE_AUTO -> PrismTheme.installPalette(p, PrismTheme.systemIsDark(), followSystem = true)
                else -> {
                    Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE
                    Theme.saveAutoNightThemeConfig()
                    Theme.cancelAutoNightThemeCallbacks()
                    PrismTheme.installPalette(p, m == MODE_DARK)
                }
            }
            PrismTheme.pruneThemes()
        } catch (t: Throwable) { FileLog.e(t) }
    }

    // ── 3. what's inside ────────────────────────────────────────────────────
    private fun featuresPage(ctx: Context): View {
        val c = column(ctx)
        heading(ctx, "Small tools, done well", null, c)
        val items = listOf(
            Triple(R.drawable.prism_welcome_colour, "Chat colours", "Give any chat its own accent."),
            Triple(R.drawable.prism_welcome_lock, "Locked chats", "Fingerprint lock that also hides previews."),
            Triple(R.drawable.prism_welcome_finder, "Link and file finder", "Search links, files and photos in every chat."),
            Triple(R.drawable.prism_welcome_widget, "Home-screen widget", "Your unread count at a glance."),
        )
        items.forEachIndexed { i, (img, title, sub) ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8f), dp(8f), dp(14f), dp(8f))
                background = round(card(), 22f, dp(1f), line())
            }
            row.addView(ImageView(ctx).apply {
                setImageResource(img)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }, LinearLayout.LayoutParams(dp(104f), dp(87f)))
            val t = column(ctx)
            t.addView(text(ctx, title, 17f, ink(), bold = true))
            t.addView(text(ctx, sub, 14f, muted()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
            row.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12f) })
            c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (i == 0) 20f else 10f) })
        }
        return pageShell(ctx, c)
    }

    // ── 4. privacy ──────────────────────────────────────────────────────────
    private fun privacyPage(ctx: Context): View {
        val c = column(ctx)
        heading(ctx, "Your privacy", "Prism has no servers of its own.", c)
        val points = listOf(
            "Your chats go directly between your phone and Telegram.",
            "Your settings stay on your phone unless you back them up.",
            "Prism contacts GitHub only to check for updates.",
            "The AI assistant stays off until you add your own key.",
        )
        val box = column(ctx).apply {
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            background = round(card(), 22f, dp(1f), line())
        }
        points.forEach { s ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent()) }
            }, LinearLayout.LayoutParams(dp(7f), dp(7f)).apply { topMargin = dp(9f) })
            row.addView(text(ctx, s, 15f, ink()), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12f) })
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); bottomMargin = dp(4f) })
        }
        c.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22f) })
        c.addView(text(ctx, "Read the code on GitHub", 15f, accent(), bold = true).apply {
            setPadding(dp(4f), dp(12f), dp(4f), dp(12f))
            setOnClickListener { parentActivity?.let { Browser.openUrl(it, "https://github.com/ayush04-op/PrismClient") } }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10f) })
        return pageShell(ctx, c)
    }

    // ── 5. get started ──────────────────────────────────────────────────────
    private fun startPage(ctx: Context): View {
        val c = column(ctx, center = true)
        c.addView(logoPlate(ctx, 84f))
        c.addView(text(ctx, if (replay) "That's Prism" else "You're all set", 30f, ink(), bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22f) })
        val sub = if (replay) "Everything here lives in Settings whenever you need it."
        else "Log in with your Telegram account to start. Updates arrive inside the app."
        c.addView(text(ctx, sub, 16f, muted()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8f) })
        return pageShell(ctx, c)
    }

    /** a single quick rise-and-fade on first show; nothing heavy */
    private fun enter(v: View) {
        if (page != 0 || done) return
        v.alpha = 0f
        v.translationY = AndroidUtilities.dpf2(14f)
        v.animate().alpha(1f).translationY(0f).setDuration(260).setStartDelay(60)
            .setInterpolator(DecelerateInterpolator(1.8f)).start()
        done = true
    }

    private fun finishWelcome() {
        markSeen()
        if (replay) finishFragment() else presentFragment(LoginActivity(), true)
    }

    companion object {
        private const val MODE_LIGHT = 0
        private const val MODE_DARK = 1
        private const val MODE_AUTO = 2

        private fun prefs() = ApplicationLoader.applicationContext.getSharedPreferences("prism", Context.MODE_PRIVATE)

        @JvmStatic
        fun markSeen() = prefs().edit().putBoolean("welcome_seen", true).apply()

        /** The screen a logged-out user starts on: the welcome once per install, then the login screen. */
        @JvmStatic
        fun firstScreen(): BaseFragment =
            if (prefs().getBoolean("welcome_seen", false)) LoginActivity() else PrismIntroActivity()
    }
}
