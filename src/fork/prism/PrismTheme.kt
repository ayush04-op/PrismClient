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

    /** True when the phone itself is in dark mode. */
    @JvmStatic
    fun systemIsDark(): Boolean {
        val ui = ApplicationLoader.applicationContext.resources.configuration.uiMode
        return (ui and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Copies a palette theme into the app once per build. Telegram's applyThemeFile also applies the theme
     * as a side effect, so re-installing on every tap made the screen flash dark, light, dark and lag.
     */
    private fun ensureInstalled(name: String, assetName: String): Theme.ThemeInfo? {
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
        val stamp = PrismUpdater.currentBuild(ApplicationLoader.applicationContext).toString() + "-" + org.telegram.messenger.BuildVars.BUILD_VERSION_STRING
        val existing = Theme.getTheme("$name.attheme")
        if (existing != null && existing.pathToFile != null && File(existing.pathToFile).exists() &&
            prefs.getString("installed_$name", null) == stamp
        ) return existing
        val t = Theme.applyThemeFile(asset("prism_theme/$assetName", "$name.attheme"), name, null, false)
        if (t != null) prefs.edit().putString("installed_$name", stamp).apply()
        return t
    }

    /** Installs a palette's day + night themes, makes them the pair the day/night switch uses, and shows the right one. */
    @JvmStatic
    fun installPalette(p: Palette, wantDark: Boolean, followSystem: Boolean = false) {
        val ctx = ApplicationLoader.applicationContext
        if (followSystem) {
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM
            Theme.saveAutoNightThemeConfig()
        }
        // With automatic switching on, dark is the night slot and light stays the day theme.
        // With it off, the theme the user picked (even a dark one) becomes the day theme, so it survives a restart.
        val autoNight = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE
        val dayIsDark = wantDark && !autoNight
        // install the other one first: the last install is what Telegram saves as the day theme
        val light: Theme.ThemeInfo?
        val dark: Theme.ThemeInfo?
        if (dayIsDark) {
            light = ensureInstalled(p.lightName, p.lightAsset)
            dark = ensureInstalled(p.darkName, p.darkAsset)
        } else {
            dark = ensureInstalled(p.darkName, p.darkAsset)
            light = ensureInstalled(p.lightName, p.lightAsset)
        }
        if (dark != null) Theme.setCurrentNightTheme(dark)
        // The day/night toggle reads these two keys; unset they point at stock Blue / Dark Blue.
        ctx.getSharedPreferences("themeconfig", android.content.Context.MODE_PRIVATE).edit().apply {
            val day = if (dayIsDark) dark else light
            if (day != null) putString("lastDayTheme", day.key)
            if (dark != null) putString("lastDarkTheme", dark.key)
        }.commit()
        val target = if (wantDark) dark else light
        if (target != null) {
            // same path as Telegram's own switcher: applies the theme and re-colours every open screen
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                org.telegram.messenger.NotificationCenter.needSetDayNightTheme, target, wantDark && autoNight, null, -1
            )
        }
        if (followSystem) Theme.checkAutoNightThemeConditions(true)
    }

    /** Installs Paper (day) + Paper Dark (night) and makes the system dark setting choose between them. */
    private fun installPair() {
        val ctx = ApplicationLoader.applicationContext
        val prefs = ctx.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("paper_v6", true).commit() // first: a failure must not loop
        installPalette(palettes[0], systemIsDark(), followSystem = true)
    }

    private fun needsInstall(): Boolean {
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences("prism", android.content.Context.MODE_PRIVATE)
        return !prefs.getBoolean("paper_v6", false) && !prefs.getBoolean("paper_user_choice", false)
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
