package desu.inugram.prism

import android.content.Context
import desu.inugram.helpers.font.FontConfig
import desu.inugram.helpers.font.FontId
import desu.inugram.helpers.font.FontLibrary
import org.telegram.messenger.FileLog
import java.io.File

/**
 * Prism's default font. Any .ttf/.otf placed in `assets/prism_font/` is imported once, on first
 * launch, and becomes the app font. The user can still change it in the font settings afterwards.
 */
object PrismFont {
    private const val DIR = "prism_font"
    private const val PREFS = "prism"
    private const val FAMILY = "Prism Serif"
    private const val SEEDED = "font_seeded_v2"

    @JvmStatic
    fun seedDefault(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(SEEDED, false)) return
            val names = context.assets.list(DIR)
                ?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                .orEmpty()
            if (names.isEmpty()) return

            val cache = File(context.cacheDir, DIR).apply { mkdirs() }
            for (name in names) {
                val out = File(cache, name)
                context.assets.open("$DIR/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
                FontLibrary.importFromFile(out, FAMILY)
                out.delete()
            }
            val id = FontLibrary.getCachedRoster().filterIsInstance<FontId.Family>()
                .lastOrNull { FontLibrary.getFontFamily(it)?.name == FAMILY }
                ?: FontLibrary.getCachedRoster().filterIsInstance<FontId.Family>().lastOrNull()
                ?: FontLibrary.firstFamilyId()?.let { FontId.Family(it) }
                ?: return
            FontConfig.FONT.value = FontConfig.FontMode.Custom(id, emptyList())
            prefs.edit().putBoolean(SEEDED, true).apply()
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }
}
