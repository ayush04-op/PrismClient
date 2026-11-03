package desu.inugram.helpers

import android.content.Context
import android.util.SparseIntArray
import desu.inugram.prism.PrismTheme
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeColors

/** Per-chat Paper accent: outgoing bubbles, reply lines and the send button take a palette's own colours. */
object PrismChatAccent {
    private fun prefs() = ApplicationLoader.applicationContext.getSharedPreferences("prism_tools", Context.MODE_PRIVATE)

    private fun load(): JSONObject = try {
        JSONObject(prefs().getString("accents", "{}") ?: "{}")
    } catch (_: Throwable) {
        JSONObject()
    }

    @JvmStatic
    fun get(dialogId: Long): String? = load().optString(dialogId.toString(), "").ifEmpty { null }

    fun set(dialogId: Long, paletteId: String?) {
        val o = load()
        if (paletteId == null) o.remove(dialogId.toString()) else o.put(dialogId.toString(), paletteId)
        prefs().edit().putString("accents", o.toString()).apply()
    }

    fun exportData(): JSONObject = load()

    fun importData(o: JSONObject) {
        prefs().edit().putString("accents", o.toString()).apply()
    }

    private val nameToKey: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        val sa = ThemeColors.createColorKeysMap()
        for (i in 0 until sa.size()) m[sa.valueAt(i)] = sa.keyAt(i)
        m
    }

    private val cache = HashMap<String, SparseIntArray>()

    private fun wanted(name: String) = name.startsWith("chat_out") || name == "chat_messagePanelSend"

    private fun parseColor(v: String): Int? = try {
        val t = v.trim()
        if (t.startsWith("#")) {
            val h = t.substring(1)
            val n = java.lang.Long.parseLong(h, 16).toInt()
            if (h.length <= 6) n or 0xFF000000.toInt() else n
        } else t.toLong().toInt()
    } catch (_: Throwable) {
        null
    }

    private fun overrides(paletteId: String, dark: Boolean): SparseIntArray {
        val ck = "$paletteId/$dark"
        cache[ck]?.let { return it }
        val out = SparseIntArray()
        val p = PrismTheme.palettes.firstOrNull { it.id == paletteId }
        if (p != null) {
            try {
                val asset = "prism_theme/" + if (dark) p.darkAsset else p.lightAsset
                ApplicationLoader.applicationContext.assets.open(asset).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val i = line.indexOf('=')
                        if (i <= 0) continue
                        val name = line.substring(0, i)
                        if (!wanted(name)) continue
                        val key = nameToKey[name] ?: continue
                        parseColor(line.substring(i + 1))?.let { out.put(key, it) }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        cache[ck] = out
        return out
    }

    /** Called from the chat's theme delegate for every colour it hands out. */
    @JvmStatic
    fun tint(dialogId: Long, key: Int, color: Int): Int {
        val id = get(dialogId) ?: return color
        val o = overrides(id, Theme.isCurrentThemeDark())
        val idx = o.indexOfKey(key)
        return if (idx >= 0) o.valueAt(idx) else color
    }
}
