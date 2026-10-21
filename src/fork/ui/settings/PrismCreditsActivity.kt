package desu.inugram.ui.settings

import android.view.View
import desu.inugram.helpers.InuUtils
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

/** Prism credits: who built it and whose work it stands on. */
class PrismCreditsActivity : SettingsPageActivity() {
    override fun getTitle(): CharSequence = LocaleController.getString(R.string.PrismCredits)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.PrismCreditsCreator)))
        items.add(UItem.asButton(ID_AYUSH, R.drawable.inu_tabler_brand_github, "Ayush", "github.com/ayush04-op"))
        items.add(UItem.asButton(ID_REPO, R.drawable.inu_tabler_brand_github, LocaleController.getString(R.string.PrismSourceCode), "ayush04-op/PrismClient"))
        items.add(UItem.asShadow(LocaleController.getString(R.string.PrismCreatedWith)))

        items.add(UItem.asHeader(LocaleController.getString(R.string.PrismCreditsBuiltOn)))
        items.add(UItem.asButton(ID_ENTINY, R.drawable.inu_tabler_brand_github, "entinyGram", LocaleController.getString(R.string.PrismCreditEntiny)))
        items.add(UItem.asButton(ID_INUGRAM, R.drawable.inu_tabler_brand_github, "Inugram", LocaleController.getString(R.string.PrismCreditInugram)))
        items.add(UItem.asButton(ID_TELEGRAM, R.drawable.inu_tabler_brand_telegram, "Telegram for Android", LocaleController.getString(R.string.PrismCreditTelegram)))
        items.add(UItem.asShadow(LocaleController.getString(R.string.PrismCreditsNotOfficial)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val ctx = context ?: return
        val url = when (item.id) {
            ID_AYUSH -> "https://github.com/ayush04-op"
            ID_REPO -> "https://github.com/ayush04-op/PrismClient"
            ID_ENTINY -> "https://github.com/entaytion/entinyGram"
            ID_INUGRAM -> "https://github.com/teidesu/inugram"
            ID_TELEGRAM -> "https://github.com/DrKLO/Telegram"
            else -> return
        }
        Browser.openUrl(ctx, url)
    }

    companion object {
        private val ID_AYUSH = InuUtils.generateId()
        private val ID_REPO = InuUtils.generateId()
        private val ID_ENTINY = InuUtils.generateId()
        private val ID_INUGRAM = InuUtils.generateId()
        private val ID_TELEGRAM = InuUtils.generateId()
    }
}
