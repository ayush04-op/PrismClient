package desu.inugram.prism

import android.content.Context
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Updates straight from Prism's own GitHub Releases. */
object PrismUpdater {
    private const val LATEST = "https://api.github.com/repos/ayush04-op/PrismClient/releases/latest"

    class Info(val build: Int, val tag: String, val url: String, val size: Long, val notes: String)

    @Volatile var latest: Info? = null
    @Volatile var status: String = ""
    @Volatile var lastChecked: Long = 0L
    @Volatile var downloaded: File? = null
    @Volatile private var cancelFlag = false

    fun cancelDownload() { cancelFlag = true }

    /** The CI run number stamped into the APK at build time (0 for local builds). */
    @JvmStatic
    fun currentBuild(ctx: Context): Int = try {
        ctx.assets.open("prism_build.txt").bufferedReader().use { it.readText().trim().toIntOrNull() } ?: 0
    } catch (_: Throwable) { 0 }

    /** Background check. [done] runs on the UI thread with the newer release, or null if up to date / failed. */
    fun check(ctx: Context, done: (Info?) -> Unit) {
        Thread {
            var result: Info? = null
            try {
                val c = URL(LATEST).openConnection() as HttpURLConnection
                c.connectTimeout = 12000; c.readTimeout = 12000
                c.setRequestProperty("User-Agent", "PrismClient")
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val text = c.inputStream.bufferedReader().use { it.readText() }
                val j = JSONObject(text)
                val tag = j.getString("tag_name")
                val n = tag.substringAfterLast('-').toIntOrNull() ?: 0
                val assets = j.getJSONArray("assets")
                var apk: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) { apk = a; break }
                }
                if (apk != null && n > currentBuild(ctx)) {
                    result = Info(n, tag, apk.getString("browser_download_url"), apk.optLong("size"), j.optString("body"))
                }
                status = if (result != null) "Build $n available" else "Up to date"
            } catch (t: Throwable) {
                FileLog.e(t)
                status = "Could not check"
            }
            latest = result
            lastChecked = System.currentTimeMillis()
            AndroidUtilities.runOnUIThread { done(result) }
        }.start()
    }

    /** Downloads the APK to the cache dir. [progress] 0..100 and [done] (null on failure) run on the UI thread. */
    fun download(ctx: Context, info: Info, progress: (Int) -> Unit, done: (File?) -> Unit) {
        cancelFlag = false
        Thread {
            var out: File? = null
            try {
                val dir = File(ctx.cacheDir, "prism_update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val f = File(dir, "prism-${info.build}.apk")
                val c = URL(info.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "PrismClient")
                c.instanceFollowRedirects = true
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
                var read = 0L; var last = -1
                c.inputStream.use { i -> f.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelFlag) throw java.io.IOException("cancelled")
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); read += n
                        if (total > 0) { val p = (read * 100 / total).toInt(); if (p != last) { last = p; AndroidUtilities.runOnUIThread { progress(p) } } }
                    }
                } }
                out = f
                downloaded = f
            } catch (t: Throwable) {
                FileLog.e(t)
            }
            AndroidUtilities.runOnUIThread { done(out) }
        }.start()
    }
}
