package desu.inugram.prism

import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ThemePreviewActivity
import java.io.File

/** Bundled colour themes. Colours only: the app font is never touched. */
object PrismTheme {
    /** Opens Telegram's theme preview for the warm cream "Paper" theme; the user taps Apply there. */
    @JvmStatic
    fun previewPaper(from: BaseFragment) {
        try {
            val ctx = ApplicationLoader.applicationContext
            val file = File(ctx.cacheDir, "Prism Paper.attheme")
            ctx.assets.open("prism_theme/paper.attheme").use { i -> file.outputStream().use { i.copyTo(it) } }
            val info = Theme.applyThemeFile(file, "Prism Paper", null, true) ?: return
            from.presentFragment(ThemePreviewActivity(info))
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }

    /** Called from the login screen: if Paper is not the active theme yet, apply it (max 3 tries). */
    @JvmStatic
    fun ensurePaper() {
        try {
            val ctx = ApplicationLoader.applicationContext
            val prefs = ctx.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
            val cur = Theme.getCurrentTheme()
            if (cur != null && cur.name != null && cur.name.startsWith("Prism Paper")) return
            val tries = prefs.getInt("paper_login_tries", 0)
            if (tries >= 3 || prefs.getBoolean("paper_user_choice", false)) return
            prefs.edit().putInt("paper_login_tries", tries + 1).commit()
            val file = File(ctx.cacheDir, "Prism Paper.attheme")
            ctx.assets.open("prism_theme/paper.attheme").use { i -> file.outputStream().use { i.copyTo(it) } }
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE
            Theme.saveAutoNightThemeConfig()
            Theme.applyThemeFile(file, "Prism Paper", null, false)
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }

    /** First launch: make Paper the app theme (day theme, no automatic night switching). */
    @JvmStatic
    fun applyDefaultOnce() {
        try {
            val ctx = ApplicationLoader.applicationContext
            val prefs = ctx.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("paper_v2", false)) return
            val file = File(ctx.cacheDir, "Prism Paper.attheme")
            ctx.assets.open("prism_theme/paper.attheme").use { i -> file.outputStream().use { i.copyTo(it) } }
            prefs.edit().putBoolean("paper_v2", true).commit() // set first: a failure must not retry in a loop
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE
            Theme.saveAutoNightThemeConfig()
            Theme.applyThemeFile(file, "Prism Paper", null, false)
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }
}
