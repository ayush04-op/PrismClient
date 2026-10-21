package desu.inugram.prism

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Crash guard for Prism's extras (default font, auto liquid glass). A "boot pending" flag is set at
 * launch and cleared once the app has stayed alive for a while. If it is still set on the next
 * launch, the previous run died early: Prism switches to safe mode for good and turns the extras off.
 */
object PrismSafe {
    private const val PREFS = "prism"
    @Volatile private var safe = false

    @JvmStatic
    fun begin(context: Context) {
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getBoolean("boot_pending", false)) p.edit().putBoolean("safe", true).apply()
            safe = p.getBoolean("safe", false)
            if (safe) {
                val g = context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                g.edit().putInt("lite_mode6", g.getInt("lite_mode6", 0) and (1 shl 18).inv())
                    .putBoolean("prism_glass_v1", true).apply()
            }
            p.edit().putBoolean("boot_pending", true).commit()
            Handler(Looper.getMainLooper()).postDelayed({
                try { p.edit().putBoolean("boot_pending", false).apply() } catch (_: Throwable) {}
            }, 12000)
        } catch (_: Throwable) {}
    }

    @JvmStatic fun isSafe(): Boolean = safe
}
