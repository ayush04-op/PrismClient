package desu.inugram.prism

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.ViewGroup
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

/** Gives the stock login screen Prism's glass look. Called from a one-line hook in LoginActivity. */
object PrismLogin {
    /**
     * @param page the login screen's root view: gets the aurora as its background
     * @param card the container the code/phone forms live in: gets a glass plate behind it
     */
    @JvmStatic
    fun decorate(context: Context, page: View, card: ViewGroup) {
        val dark = Theme.isCurrentThemeDark()
        val tier = PrismTier.tier()
        val scene = AuroraScene(dark)
        page.background = AuroraDrawable(scene, animated = tier >= PrismTier.MID)

        val plate = GlassPlateDrawable(dark, AndroidUtilities.dpf2(30f))
        val m = AndroidUtilities.dp(12f)
        card.background = InsetDrawable(plate, m, AndroidUtilities.dp(6f), m, AndroidUtilities.dp(6f))
    }
}
