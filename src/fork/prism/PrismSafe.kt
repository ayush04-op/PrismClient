package desu.inugram.prism

import android.content.Context
import android.os.SystemClock

/**
 * Crash guard for Prism's extras (default font, auto liquid glass). If the app dies with an
 * uncaught exception within [EARLY_MS] of starting, Prism switches to safe mode: the extras are
 * switched off. The user can turn them back on from Prism Settings.
 */
object PrismSafe {
    private const val PREFS = "prism"
    private const val EARLY_MS = 25_000L
    @Volatile private var safe = false
    private var appCtx: Context? = null
    private var startedAt = 0L

    @JvmStatic
    fun begin(context: Context) {
        try {
            appCtx = context.applicationContext ?: context
            startedAt = SystemClock.elapsedRealtime()
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            safe = p.getBoolean("safe", false)
            if (safe) {
                val g = context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                g.edit().putInt("lite_mode6", g.getInt("lite_mode6", 0) and (1 shl 18).inv())
                    .putBoolean("prism_glass_v2", true).apply()
            }
        } catch (_: Throwable) {}
    }

    /** Called from the uncaught-exception handler. */
    @JvmStatic
    fun onCrash() {
        try {
            if (SystemClock.elapsedRealtime() - startedAt < EARLY_MS) {
                appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("safe", true)?.commit()
            }
        } catch (_: Throwable) {}
    }

    @JvmStatic fun isSafe(): Boolean = safe

    /** Leave safe mode: font is re-applied and glass re-enabled on the next launch. */
    @JvmStatic
    fun leaveSafeMode(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("safe", false).putBoolean("font_seeded_v2", false).commit()
        context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit()
            .putBoolean("prism_glass_v2", false).commit()
        safe = false
    }
}
