package desu.inugram.prism

import android.os.SystemClock
import org.telegram.messenger.LiteMode
import org.telegram.messenger.SharedConfig

/**
 * Prism's quality tiers. The glass look is kept on every device; what changes is how much it costs:
 * HIGH animates at ~30fps, MID animates at ~20fps, LOW draws one still frame.
 */
object PrismTier {
    const val LOW = 0
    const val MID = 1
    const val HIGH = 2

    @JvmStatic
    fun tier(): Int {
        if (LiteMode.isPowerSaverApplied()) return LOW
        return when (SharedConfig.getDevicePerformanceClass()) {
            SharedConfig.PERFORMANCE_CLASS_LOW -> LOW
            SharedConfig.PERFORMANCE_CLASS_AVERAGE -> MID
            else -> HIGH
        }
    }

    @JvmStatic
    fun frameDelayMs(tier: Int): Long = when (tier) {
        HIGH -> 33L
        MID -> 50L
        else -> 0L
    }

    /** seconds since boot, shared by every glass surface so they stay in sync */
    @JvmStatic
    fun clock(): Float = (SystemClock.uptimeMillis() / 1000.0).toFloat()
}
