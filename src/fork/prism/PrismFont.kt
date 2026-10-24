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
    private const val MONO_SEEDED = "mono_seeded_v2"
    private const val MONO_DIR = "prism_font_mono"
    const val MONO_FAMILY = "Prism Mono"
    private const val SEEDED = "font_seeded_v3"

    @JvmStatic
    fun seedDefault(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(SEEDED, false)) { ensureMono(context, prefs); return }
            val names = context.assets.list(DIR)
                ?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                .orEmpty()
            if (names.isEmpty()) return

            // reinstall cleanly: drop earlier Prism / split Anthropic families so no stale faces linger
            for (fid in FontLibrary.getCachedRoster().filterIsInstance<FontId.Family>()) {
                val n = FontLibrary.getFontFamily(fid)?.name ?: continue
                if (n == FAMILY || n.startsWith("Anthropic Serif", ignoreCase = true)) FontLibrary.removeFamily(fid.id)
            }
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
            ensureMono(context, prefs)
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }

    /** Imports the bundled Anthropic Mono once and uses it for monospace / code text. */
    private fun ensureMono(context: Context, prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(MONO_SEEDED, false)) return
        try {
            val names = context.assets.list(MONO_DIR)?.filter { it.endsWith(".otf", true) || it.endsWith(".ttf", true) }.orEmpty()
            if (names.isEmpty()) return
            for (fid in FontLibrary.getCachedRoster().filterIsInstance<FontId.Family>()) {
                if (FontLibrary.getFontFamily(fid)?.name == MONO_FAMILY) FontLibrary.removeFamily(fid.id)
            }
            val cache = File(context.cacheDir, MONO_DIR).apply { mkdirs() }
            for (name in names) {
                val out = File(cache, name)
                context.assets.open("$MONO_DIR/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
                FontLibrary.importFromFile(out, MONO_FAMILY)
                out.delete()
            }
            val id = FontLibrary.getCachedRoster().filterIsInstance<FontId.Family>()
                .lastOrNull { FontLibrary.getFontFamily(it)?.name == MONO_FAMILY } ?: return
            FontConfig.MONO_FONT.value = id.token()
            prefs.edit().putBoolean(MONO_SEEDED, true).apply()
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }
}
