package desu.inugram.ui.settings

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import desu.inugram.helpers.vibration.HapticFeedbackConstants
import org.telegram.messenger.AndroidUtilities
import desu.inugram.helpers.update.UpdateHelper
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.LayoutHelper

/** Prism's settings hero: a gradient card with the logo, name, tagline and a few status pills. */
class InuSettingsHeader(context: Context) : LinearLayout(context) {
    private fun dp(v: Float) = AndroidUtilities.dp(v)

    private val card = LinearLayout(context).apply {
        orientation = VERTICAL
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFF1E7D0.toInt(), 0xFFEADDBF.toInt()),
        ).apply { cornerRadius = dp(28f).toFloat(); setStroke(dp(1f), 0xFFD9CCB1.toInt()) }
        setPadding(dp(20f), dp(20f), dp(20f), dp(18f))
    }

    private val logo = ImageView(context).apply {
        setBackgroundResource(R.drawable.prism_icon_bg)
        setImageResource(R.drawable.prism_icon_fg)
        scaleType = ImageView.ScaleType.FIT_CENTER
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(20f).toFloat())
            }
        }
    }

    private fun pill(text: String) = TextView(context).apply {
        this.text = text
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f)
        setTextColor(0xFF2B2420.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(11f), dp(5f), dp(11f), dp(5f))
        background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(0x00000000)
            setStroke(dp(1f), 0x662B2420)
        }
    }

    var onHeaderClick: (() -> Unit)? = null
        set(value) {
            field = value
            card.setOnClickListener(
                if (value == null) null
                else View.OnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    ObjectAnimator.ofFloat(logo, View.ROTATION, 0f, 360f).setDuration(700).apply {
                        interpolator = DecelerateInterpolator()
                    }.start()
                }
            )
        }

    init {
        orientation = VERTICAL
        setPadding(dp(14f), dp(8f), dp(14f), dp(10f))

        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(logo, LayoutHelper.createLinear(68, 68))

        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(TextView(context).apply {
            text = "Prism"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 30f)
            setTextColor(0xFF2B2420.toInt())
            setTypeface(AndroidUtilities.bold())
        })
        texts.addView(TextView(context).apply {
            text = LocaleController.getString(R.string.PrismTagline)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(0xFF7A6E60.toInt())
        })
        top.addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16f) })
        card.addView(top)

        val pills = LinearLayout(context).apply { orientation = HORIZONTAL }
        pills.addView(pill("v${UpdateHelper.stockVersionName}"))
        pills.addView(
            pill(LocaleController.getString(R.string.PrismOpenSource)),
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) },
        )
        pills.addView(
            pill("by Ayush"),
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) },
        )
        card.addView(pills, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })

        addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
    }

    fun updateColors() {}
}
