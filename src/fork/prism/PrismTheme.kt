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

    class Palette(val id: String, val label: String, val accentLight: Int, val accentDark: Int, val bgLight: Int, val bgDark: Int) {
        val lightAsset get() = if (id == "paper") "paper.attheme" else "paper_$id.attheme"
        val darkAsset get() = if (id == "paper") "paper_dark.attheme" else "paper_${id}_dark.attheme"
        val lightName get() = if (id == "paper") "Prism Paper" else "Prism Paper $label"
        val darkName get() = "$lightName Dark"
    }

    @JvmField
    val palettes = listOf(
        Palette("paper", "Paper", 0xFFB5532F.toInt(), 0xFFD4693F.toInt(), 0xFFFBF6EA.toInt(), 0xFF1E1915.toInt()),
        Palette("sage", "Sage", 0xFF5C7A4B.toInt(), 0xFF8DB07A.toInt(), 0xFFF5FBEA.toInt(), 0xFF191C16.toInt()),
        Palette("ocean", "Ocean", 0xFF3F6E8C.toInt(), 0xFF6FA3C4.toInt(), 0xFFF1F6F8.toInt(), 0xFF1A1C1E.toInt()),
        Palette("plum", "Plum", 0xFF8E4F7A.toInt(), 0xFFC27BAE.toInt(), 0xFFF9F1F5.toInt(), 0xFF1D171B.toInt()),
        Palette("amber", "Amber", 0xFFB87A1E.toInt(), 0xFFE0A054.toInt(), 0xFFFBF4E4.toInt(), 0xFF1D1810.toInt()),
        Palette("slate", "Slate", 0xFF4A5568.toInt(), 0xFF9AA7BD.toInt(), 0xFFF3F4F6.toInt(), 0xFF15171B.toInt()),
    )

    /** The palette whose theme is active right now, if any. */
    @JvmStatic
    fun currentPalette(): Palette? {
        val n = Theme.getCurrentTheme()?.name?.removeSuffix(".attheme") ?: return null
        return palettes.firstOrNull { n == it.lightName || n == it.darkName }
    }

    /** Installs a palette's day + night themes, makes them the pair the day/night switch uses, and shows the right one. */
    @JvmStatic
    fun installPalette(p: Palette, wantDark: Boolean, followSystem: Boolean = false) {
        val ctx = ApplicationLoader.applicationContext
        val dark = Theme.applyThemeFile(asset("prism_theme/${p.darkAsset}", "${p.darkName}.attheme"), p.darkName, null, false)
        val light = Theme.applyThemeFile(asset("prism_theme/${p.lightAsset}", "${p.lightName}.attheme"), p.lightName, null, false)
        if (dark != null) Theme.setCurrentNightTheme(dark)
        // The day/night toggle reads these two keys; unset they point at stock Blue / Dark Blue.
        ctx.getSharedPreferences("themeconfig", android.content.Context.MODE_PRIVATE).edit().apply {
            if (light != null) putString("lastDayTheme", light.key)
            if (dark != null) putString("lastDarkTheme", dark.key)
        }.commit()
        if (followSystem) {
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM
            Theme.saveAutoNightThemeConfig()
            Theme.checkAutoNightThemeConditions(true)
        } else if (wantDark && dark != null) {
            // a manual pick: show the mode the user is in and leave the auto-night setting alone
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                .postNotificationName(org.telegram.messenger.NotificationCenter.needSetDayNightTheme, dark, false, null, -1)
        }
    }

    /** Installs Paper (day) + Paper Dark (night) and makes the system dark setting choose between them. */
    private fun installPair() {
        val ctx = ApplicationLoader.applicationContext
        val prefs = ctx.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("paper_v5", true).commit() // first: a failure must not loop
        installPalette(palettes[0], false, followSystem = true)
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
