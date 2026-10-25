package desu.inugram.helpers

import desu.inugram.prism.PrismSafe
import desu.inugram.prism.PrismUpdater
import desu.inugram.ui.settings.*
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.IconBackgroundColors
import org.telegram.ui.Components.UItem
import org.telegram.ui.SettingsActivity

/** Prism's own pages, shown as ordinary rows inside the main Settings page (one merged list). */
object PrismSettingsRows {
    const val PRIVACY = 201
    const val RESTRICTED = 202
    const val CHATS = 203
    const val CLUTTER = 204
    const val BEHAVIOR = 205
    const val TRANSLATOR = 206
    const val DESIGN = 207
    const val MAIN_SCREEN = 208
    const val PREMIUM = 209
    const val PLUGINS = 210
    const val BACKUP = 211
    const val UPDATE = 212
    const val INTERFACE = 213
    const val DEVELOPER = 214
    const val GITHUB = 215
    const val CREDITS = 216
    const val FONT_CHECK = 217
    const val SAFE_MODE = 218
    const val AI = 219

    /** settings section of every Prism row (see ProfileSettingsHelper) */
    @JvmStatic
    fun sectionOf(id: Int): Int = when (id) {
        PRIVACY, RESTRICTED -> 10
        CHATS, CLUTTER, BEHAVIOR, TRANSLATOR, AI -> 11
        DESIGN, MAIN_SCREEN, INTERFACE -> 14
        PREMIUM -> 2
        PLUGINS, BACKUP, UPDATE, SAFE_MODE -> 15
        DEVELOPER, GITHUB, CREDITS, FONT_CHECK -> 16
        else -> -1
    }

    private fun row(id: Int, icon: Int, title: String, sub: String?, value: String? = null): UItem =
        SettingsActivity.SettingCell.Factory.of(id, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, icon, title, sub, value)

    @JvmStatic
    fun items(): List<UItem> {
        val ctx = ApplicationLoader.applicationContext
        val build = PrismUpdater.currentBuild(ctx)
        val list = arrayListOf(
            row(PRIVACY, R.drawable.inu_tabler_shield_check, "Privacy tools", "Extra privacy options"),
            row(RESTRICTED, R.drawable.inu_tabler_lock_open, LocaleController.getString(R.string.InuTOS), "Restricted content options"),
            row(CHATS, R.drawable.msg_viewchats, LocaleController.getString(R.string.InuCategoryChats), "Chat list and chat behaviour"),
            row(CLUTTER, R.drawable.inu_tabler_shield_cancel, LocaleController.getString(R.string.InuAnnoyances), "Hide buttons, banners and badges"),
            row(BEHAVIOR, R.drawable.inu_tabler_adjustments_horizontal, LocaleController.getString(R.string.InuCategoryBehavior), "Downloads, input and small tools"),
            row(TRANSLATOR, R.drawable.msg_translate, LocaleController.getString(R.string.InuTranslator), "Translate messages in place"),
            row(AI, R.drawable.msg_translate, "AI assistant", "Add your OpenRouter or other API key"),
            row(DESIGN, R.drawable.msg_palette, LocaleController.getString(R.string.InuCategoryAppearance), "Fonts, icons, avatars and motion"),
            row(MAIN_SCREEN, R.drawable.msg_home_solar, LocaleController.getString(R.string.InuMainScreen), "Tabs, drawer and chat list layout"),
            row(INTERFACE, R.drawable.msg_newphone, LocaleController.getString(R.string.InuIosSettings), "Liquid glass and the input bar"),
            row(PREMIUM, R.drawable.inu_tabler_shield_cancel, LocaleController.getString(R.string.PrismPremium), "Hide gifts, Stars and upsells"),
            // #if PLUGINS
            row(PLUGINS, R.drawable.inu_tabler_code, LocaleController.getString(R.string.InuPlugins), "Extend Prism with plugins"),
            // #endif
            row(BACKUP, R.drawable.inu_tabler_device_floppy, "Backup & more", "Backup, restore and extras"),
            row(UPDATE, R.drawable.msg_download, "Software update", "Check for a newer Prism build", if (build > 0) "Build $build" else null),
            row(DEVELOPER, R.drawable.inu_tabler_brand_github, LocaleController.getString(R.string.PrismDeveloper), null, "Ayush"),
            row(GITHUB, R.drawable.inu_tabler_brand_github, "GitHub", null, "PrismClient"),
            row(CREDITS, R.drawable.inu_tabler_heart, LocaleController.getString(R.string.PrismCredits), "Open-source thanks"),
            row(FONT_CHECK, R.drawable.msg_info, LocaleController.getString(R.string.PrismFontCheck), "Check the app font"),
        )
        if (PrismSafe.isSafe()) list.add(row(SAFE_MODE, R.drawable.msg_info, LocaleController.getString(R.string.PrismSafeMode), LocaleController.getString(R.string.PrismSafeModeValue)))
        return list
    }

    @JvmStatic
    fun open(id: Int, f: BaseFragment) {
        val ctx = f.parentActivity ?: return
        when (id) {
            PRIVACY -> f.presentFragment(PrivacySecurityActivity())
            RESTRICTED -> f.presentFragment(TosSettingsActivity())
            CHATS -> f.presentFragment(CategoryChatsSettingsActivity())
            CLUTTER -> f.presentFragment(AnnoyancesSettingsActivity())
            BEHAVIOR -> f.presentFragment(BehaviorSettingsActivity())
            TRANSLATOR -> f.presentFragment(TranslatorSettingsActivity())
            AI -> f.presentFragment(AiSettingsActivity())
            DESIGN -> f.presentFragment(AppearanceSettingsActivity())
            MAIN_SCREEN -> f.presentFragment(MainScreenSettingsActivity())
            INTERFACE -> f.presentFragment(IosStyleSettingsActivity())
            PREMIUM -> f.presentFragment(AnnoyancesSettingsActivity(true))
            // #if PLUGINS
            PLUGINS -> f.presentFragment(PluginsActivity())
            // #endif
            BACKUP -> f.presentFragment(AdditionalSettingsActivity())
            UPDATE -> f.presentFragment(PrismUpdateActivity())
            DEVELOPER -> org.telegram.messenger.browser.Browser.openUrl(ctx, "https://github.com/ayush04-op")
            GITHUB -> org.telegram.messenger.browser.Browser.openUrl(ctx, "https://github.com/ayush04-op/PrismClient")
            CREDITS -> f.presentFragment(PrismCreditsActivity())
            FONT_CHECK -> org.telegram.ui.ActionBar.AlertDialog.Builder(ctx)
                .setTitle(LocaleController.getString(R.string.PrismFontCheck))
                .setMessage(desu.inugram.helpers.font.FontHelper.diagnose())
                .setPositiveButton("OK", null)
                .create().show()
            SAFE_MODE -> {
                PrismSafe.leaveSafeMode(ctx)
                org.telegram.ui.Components.BulletinFactory.of(f).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PrismSafeModeLeft)).show()
            }
        }
    }
}
