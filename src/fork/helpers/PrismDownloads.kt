package desu.inugram.helpers

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import desu.inugram.InuConfig

/** Sorts saved files into category folders and lists them for the Downloads page. */
object PrismDownloads {
    enum class Category(val label: String, val folder: String) {
        APK("Apps (APK)", "APKs"),
        PDF("PDFs", "PDFs"),
        DOC("Documents", "Documents"),
        ARCHIVE("Archives", "Archives"),
        AUDIO("Audio", "Audio"),
        VIDEO("Videos", "Videos"),
        IMAGE("Images", "Images"),
        OTHER("Other", "Other"),
    }

    class Entry(val uri: Uri, val name: String, val mime: String, val size: Long, val date: Long, val category: Category)

    fun categoryOf(name: String?, mime: String?): Category {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val m = mime.orEmpty().lowercase()
        return when {
            ext == "apk" || ext == "apks" || ext == "xapk" || m == "application/vnd.android.package-archive" -> Category.APK
            ext == "pdf" || m == "application/pdf" -> Category.PDF
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") || m.contains("zip") || m.contains("compressed") -> Category.ARCHIVE
            m.startsWith("audio") || ext in setOf("mp3", "m4a", "ogg", "opus", "flac", "wav", "aac") -> Category.AUDIO
            m.startsWith("video") || ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp") -> Category.VIDEO
            m.startsWith("image") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") -> Category.IMAGE
            ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "epub", "md") ||
                m.startsWith("text") || m.contains("officedocument") || m.contains("msword") -> Category.DOC
            else -> Category.OTHER
        }
    }

    /** sub folder used by MediaController when saving a document to Downloads */
    @JvmStatic
    fun categoryFolder(name: String?, mime: String?): String = categoryOf(name, mime).folder

    /** Every file Prism saved (own files are readable without extra permission on Android 10+). */
    fun list(ctx: Context): List<Entry> {
        if (Build.VERSION.SDK_INT < 29) return emptyList()
        val root = InuConfig.DOWNLOAD_DIRECTORY.value
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val out = ArrayList<Entry>()
        try {
            ctx.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("%/$root/%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    out.add(Entry(ContentUris.withAppendedId(uri, c.getLong(0)), name, mime, c.getLong(3), c.getLong(4), categoryOf(name, mime)))
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun open(ctx: Context, e: Entry) {
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(e.uri, e.mime.ifBlank { "*/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Throwable) {
        }
    }

    fun readableSize(b: Long): String = when {
        b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }
}
