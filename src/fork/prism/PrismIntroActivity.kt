package desu.inugram.prism

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LoginActivity

/**
 * Prism's welcome screen: a drifting aurora, a glass logo plate and a glass "Start" button.
 * Replaces Telegram's paper-plane intro. Costs nothing on low-end phones: they get a still frame.
 */
class PrismIntroActivity : BaseFragment() {
    private var startPressed = false
    private var floatAnimator: ObjectAnimator? = null

    /** kept so the logout path can use the same call shape as the stock intro */
    fun setOnLogout(): PrismIntroActivity = this

    override fun isLightStatusBar(): Boolean = false
    override fun hasForceLightStatusBar(): Boolean = false

    override fun createView(context: Context): View {
        actionBar.setAddToContainer(false)

        val tier = PrismTier.tier()
        val animated = tier >= PrismTier.MID
        val scene = AuroraScene(dark = true)

        val root = FrameLayout(context)
        val aurora = View(context)
        aurora.background = AuroraDrawable(scene, animated)
        root.addView(aurora, FrameLayout.LayoutParams(-1, -1))

        // ── centre block: logo plate, name, tagline ─────────────────────────
        val logoPlate = GlassPanelView(context, scene, aurora, 40f, animated)
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.prism_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = AndroidUtilities.dp(30f)
            setPadding(p, p, p, p)
        }
        logoPlate.addView(logo, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(context).apply {
            text = "Prism"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 46f)
            typeface = AndroidUtilities.bold()
            letterSpacing = -0.02f
            gravity = Gravity.CENTER
        }
        val tagline = TextView(context).apply {
            text = LocaleController.getString(R.string.PrismTagline)
            setTextColor(0xB8FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            gravity = Gravity.CENTER
        }

        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(logoPlate, LinearLayout.LayoutParams(AndroidUtilities.dp(132f), AndroidUtilities.dp(132f)))
            addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = AndroidUtilities.dp(26f) })
            addView(tagline, LinearLayout.LayoutParams(-2, -2).apply { topMargin = AndroidUtilities.dp(8f) })
        }
        root.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply {
            bottomMargin = AndroidUtilities.dp(96f)
        })

        // ── bottom block: glass button + credit line ────────────────────────
        val button = GlassPanelView(context, scene, aurora, 28f, animated)
        val buttonLabel = TextView(context).apply {
            text = LocaleController.getString(R.string.StartMessaging)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
        }
        button.addView(buttonLabel, FrameLayout.LayoutParams(-1, -1))
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = AndroidUtilities.dpf2(28f)
        }
        button.foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask)
        button.isClickable = true
        button.setOnClickListener { onStart() }

        val credit = TextView(context).apply {
            text = LocaleController.getString(R.string.PrismCreatedWith)
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            gravity = Gravity.CENTER
        }

        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(button, LinearLayout.LayoutParams(-1, AndroidUtilities.dp(56f)))
            addView(credit, LinearLayout.LayoutParams(-2, -2).apply { topMargin = AndroidUtilities.dp(18f) })
        }
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = AndroidUtilities.dp(28f)
            rightMargin = AndroidUtilities.dp(28f)
            bottomMargin = AndroidUtilities.dp(44f)
        })

        // ── entrance: everything starts hidden and settles in, staggered ────
        val rise = AndroidUtilities.dpf2(26f)
        val parts = listOf<View>(logoPlate, title, tagline, button, credit)
        parts.forEach { it.alpha = 0f; it.translationY = rise }
        logoPlate.scaleX = 0.7f
        logoPlate.scaleY = 0.7f
        root.post {
            parts.forEachIndexed { i, v ->
                val a = v.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(120L * i)
                    .setDuration(720)
                    .setInterpolator(DecelerateInterpolator(2.2f))
                if (v === logoPlate) a.scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(1.15f))
                a.start()
            }
            if (animated) startFloat(logoPlate)
        }

        fragmentView = root
        return root
    }

    /** a slow, endless bob of the logo plate: the "alive" cue */
    private fun startFloat(v: View) {
        floatAnimator?.cancel()
        floatAnimator = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -AndroidUtilities.dpf2(7f)).apply {
            duration = 3200
            startDelay = 1200
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun onStart() {
        if (startPressed) return
        startPressed = true
        presentFragment(LoginActivity(), true)
    }

    override fun onFragmentDestroy() {
        floatAnimator?.cancel()
        floatAnimator = null
        super.onFragmentDestroy()
    }
}
