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

    private fun asset(name: String, out: String): File {
        val ctx = ApplicationLoader.applicationContext
        val file = File(ctx.cacheDir, out)
        ctx.assets.open(name).use { i -> file.outputStream().use { i.copyTo(it) } }
        return file
    }

    /** Installs Paper (day) + Paper Dark (night) and makes the system dark setting choose between them. */
    private fun installPair() {
        val ctx = ApplicationLoader.applicationContext
        val prefs = ctx.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("paper_v5", true).commit() // first: a failure must not loop
        val dark = Theme.applyThemeFile(asset("prism_theme/paper_dark.attheme", "Prism Paper Dark.attheme"), "Prism Paper Dark", null, false)
        val light = Theme.applyThemeFile(asset("prism_theme/paper.attheme", "Prism Paper.attheme"), "Prism Paper", null, false)
        if (dark != null) Theme.setCurrentNightTheme(dark)
        // The day/night toggle reads these two keys; unset they point at stock Blue / Dark Blue.
        ctx.getSharedPreferences("themeconfig", android.content.Context.MODE_PRIVATE).edit().apply {
            if (light != null) putString("lastDayTheme", light.key)
            if (dark != null) putString("lastDarkTheme", dark.key)
        }.commit()
        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM
        Theme.saveAutoNightThemeConfig()
        Theme.checkAutoNightThemeConditions(true)
    }

    private fun needsInstall(): Boolean {
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
        return !prefs.getBoolean("paper_v5", false) && !prefs.getBoolean("paper_user_choice", false)
    }

    /** Called from the login screen: make sure Paper (and its dark twin) is installed. */
    @JvmStatic
    fun ensurePaper() {
        try { if (needsInstall()) installPair() } catch (t: Throwable) { FileLog.e(t) }
        pruneThemes()
    }

    /** Hides the built-in themes; Paper, Paper Dark and anything the user imports or creates stay. */
    @JvmStatic
    fun pruneThemes() {
        try {
            val l = Theme.themes ?: return
            if (l.none { it.name != null && it.name.startsWith("Prism Paper") }) return
            l.removeAll { it.pathToFile == null && (it.name == null || !it.name.startsWith("Prism Paper")) } // stock themes only; imported/created ones stay
        } catch (t: Throwable) { FileLog.e(t) }
    }

    /** Startup: install Paper + Paper Dark once, then hide every other theme. */
    @JvmStatic
    fun applyDefaultOnce() {
        ensurePaper()
        pruneThemes()
    }
}
