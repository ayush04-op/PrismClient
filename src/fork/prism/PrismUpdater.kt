package desu.inugram.prism

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Updates straight from Prism's own GitHub Releases. Downloads run in Android's DownloadManager, so they continue in the background and survive the app being closed. */
object PrismUpdater {
    private const val LATEST = "https://api.github.com/repos/ayush04-op/PrismClient/releases/latest"

    class Info(val build: Int, val tag: String, val url: String, val size: Long, val notes: String)

    @Volatile var latest: Info? = null
    @Volatile var status: String = ""
    @Volatile var lastChecked: Long = 0L
    @Volatile var downloaded: File? = null

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

    enum class Dl { NONE, RUNNING, DONE, FAILED }
    class DlStatus(val state: Dl, val percent: Int = 0, val file: File? = null)

    private const val PREFS = "prism_updater"
    private const val KEY_ID = "dl_id"
    private const val KEY_BUILD = "dl_build"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(ctx: Context) = ctx.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun apkDir(ctx: Context): File? = ctx.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    private fun apkName(build: Int) = "prism-$build.apk"
    private fun forget(ctx: Context) { prefs(ctx).edit().remove(KEY_ID).remove(KEY_BUILD).apply() }

    /** Where the system downloader is with the update for [build]. Safe to call any time, including after a restart. */
    fun downloadStatus(ctx: Context, build: Int, fallbackSize: Long = 0L): DlStatus {
        try {
            val sp = prefs(ctx)
            val id = sp.getLong(KEY_ID, -1L)
            if (id < 0 || sp.getInt(KEY_BUILD, -1) != build) return DlStatus(Dl.NONE)
            return manager(ctx).query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) {
                    forget(ctx)
                    DlStatus(Dl.NONE)
                } else {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    var total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total <= 0) total = fallbackSize
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val f = File(apkDir(ctx), apkName(build))
                            if (f.exists() && f.length() > 0) {
                                downloaded = f
                                DlStatus(Dl.DONE, 100, f)
                            } else {
                                forget(ctx)
                                DlStatus(Dl.NONE)
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            forget(ctx)
                            DlStatus(Dl.FAILED)
                        }
                        else -> DlStatus(Dl.RUNNING, if (total > 0) (soFar * 100 / total).toInt().coerceIn(0, 100) else 0)
                    }
                }
            }
        } catch (t: Throwable) {
            FileLog.e(t)
            return DlStatus(Dl.NONE)
        }
    }

    /** Starts (or reuses) the background download. Returns false if it could not be queued. */
    fun startDownload(ctx: Context, info: Info): Boolean {
        val app = ctx.applicationContext
        val current = downloadStatus(app, info.build, info.size).state
        if (current == Dl.RUNNING || current == Dl.DONE) return true
        cancelDownload(app)
        return try {
            val dir = apkDir(app) ?: return false
            dir.mkdirs()
            dir.listFiles()?.filter { it.name.startsWith("prism-") && it.name.endsWith(".apk") }?.forEach { it.delete() }
            val req = DownloadManager.Request(Uri.parse(info.url))
                .setTitle("Prism update")
                .setDescription("Build ${info.build}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", "PrismClient")
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, apkName(info.build))
            val id = manager(app).enqueue(req)
            prefs(app).edit().putLong(KEY_ID, id).putInt(KEY_BUILD, info.build).apply()
            downloaded = null
            true
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }

    /** Stops the download and removes whatever was fetched so far. */
    fun cancelDownload(ctx: Context) {
        try {
            val id = prefs(ctx).getLong(KEY_ID, -1L)
            if (id >= 0) manager(ctx).remove(id)
        } catch (t: Throwable) {
            FileLog.e(t)
        }
        forget(ctx)
        downloaded = null
    }
}
