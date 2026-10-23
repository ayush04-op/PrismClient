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
}
