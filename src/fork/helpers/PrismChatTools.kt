package desu.inugram.helpers

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.EditText
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import desu.inugram.InuConfig
import org.json.JSONArray
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LaunchActivity
import java.io.File
import java.util.Calendar

/** Prism extras: quiet hours, saved replies, chat lock, PDF export of the loaded part of a chat. */
object PrismChatTools {
    const val MENU_LOCK = 9101
    const val MENU_REPLIES = 9102
    const val MENU_PDF = 9103

    private fun prefs() = ApplicationLoader.applicationContext.getSharedPreferences("prism_tools", Context.MODE_PRIVATE)

    // ── quiet hours ─────────────────────────────────────────────────────────
    @JvmStatic
    fun quietNow(): Boolean {
        if (!InuConfig.QUIET_ENABLED.value) return false
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val s = InuConfig.QUIET_START.value
        val e = InuConfig.QUIET_END.value
        return if (s == e) false else if (s < e) now in s until e else now >= s || now < e
    }

    fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    // ── saved replies ───────────────────────────────────────────────────────
    fun replies(): MutableList<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(prefs().getString("replies", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (_: Throwable) {
        }
        return out
    }

    fun saveReplies(list: List<String>) {
        prefs().edit().putString("replies", JSONArray(list).toString()).apply()
    }

    @JvmStatic
    fun showReplies(f: BaseFragment, field: EditText?) {
        val ctx = f.parentActivity ?: return
        val list = replies()
        if (list.isEmpty()) {
            AlertDialog.Builder(ctx).setTitle("Saved replies")
                .setMessage("No saved replies yet. Add some in Settings → Chats → Prism tools.")
                .setPositiveButton("OK", null).create().show()
            return
        }
        AlertDialog.Builder(ctx).setTitle("Saved replies")
            .setItems(list.map { if (it.length > 60) it.take(60) + "…" else it }.toTypedArray<CharSequence>()) { _, which ->
                field?.let { it.text.insert(it.selectionStart.coerceAtLeast(0), list[which]) }
            }
            .setNegativeButton("Cancel", null).create().show()
    }

    // ── chat lock ───────────────────────────────────────────────────────────
    private val unlocked = HashMap<Long, Long>()
    private const val UNLOCK_MS = 60_000L

    fun lockedIds(): MutableSet<String> = HashSet(prefs().getStringSet("locked", emptySet()) ?: emptySet())

    @JvmStatic
    fun isLocked(dialogId: Long) = lockedIds().contains(dialogId.toString())

    /** @return true when the chat is now locked */
    @JvmStatic
    fun toggleLock(dialogId: Long): Boolean {
        val s = lockedIds()
        val now = if (s.remove(dialogId.toString())) false else { s.add(dialogId.toString()); true }
        prefs().edit().putStringSet("locked", s).apply()
        unlocked.remove(dialogId)
        return now
    }

    @JvmStatic
    fun canAuthenticate(): Boolean = try {
        BiometricManager.from(ApplicationLoader.applicationContext)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Throwable) {
        false
    }

    /** Called from ChatActivity.onResume: hides the chat until the user unlocks it, or closes it. */
    @JvmStatic
    fun guard(f: BaseFragment, dialogId: Long) {
        if (!isLocked(dialogId)) return
        val ts = unlocked[dialogId]
        if (ts != null && System.currentTimeMillis() - ts < UNLOCK_MS) return
        val activity = LaunchActivity.instance
        val view = f.fragmentView
        if (activity == null || !canAuthenticate()) {
            // no secure lock available: fail closed
            f.finishFragment()
            return
        }
        view?.visibility = android.view.View.INVISIBLE
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked[dialogId] = System.currentTimeMillis()
                view?.visibility = android.view.View.VISIBLE
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                f.finishFragment()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Locked chat")
            .setSubtitle("Unlock to open this chat")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    // ── PDF export of the messages currently loaded in the chat ──────────────
    @JvmStatic
    fun exportPdf(f: BaseFragment, title: String, messages: List<MessageObject>): File? {
        val ctx = f.parentActivity ?: return null
        val lines = ArrayList<String>()
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
        for (m in messages.asReversed()) {
            val text = (m.messageText ?: m.caption)?.toString().orEmpty().ifBlank { "[media]" }
            lines.add("${fmt.format(java.util.Date(m.messageOwner.date * 1000L))}  ${text}")
        }
        if (lines.isEmpty()) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
        val w = 595; val h = 842; val margin = 40f; val lineH = 15f
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var canvas = page.canvas
        var y = margin + 16f
        canvas.drawText(title, margin, y, heading); y += 28f
        fun wrap(s: String): List<String> {
            val out = ArrayList<String>()
            var rest = s.replace("\n", " ")
            while (rest.isNotEmpty()) {
                val n = paint.breakText(rest, true, w - margin * 2, null).coerceAtLeast(1)
                out.add(rest.substring(0, n)); rest = rest.substring(n)
            }
            return out
        }
        for (l in lines) for (part in wrap(l)) {
            if (y > h - margin) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
                canvas = page.canvas; y = margin
            }
            canvas.drawText(part, margin, y, paint); y += lineH
        }
        doc.finishPage(page)
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Prism Exports").apply { mkdirs() }
        val file = File(dir, "chat-${System.currentTimeMillis()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
