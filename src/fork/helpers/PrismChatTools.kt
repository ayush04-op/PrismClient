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
import org.json.JSONObject
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
    const val MENU_COLOR = 9104

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

    // ── backup of Prism's own data ───────────────────────────────────────────
    fun exportData(): JSONObject {
        val o = JSONObject()
        o.put("replies", JSONArray(replies()))
        o.put("locked", JSONArray(lockedIds().toList()))
        o.put("accents", PrismChatAccent.exportData())
        desu.inugram.prism.PrismTheme.currentPalette()?.let { o.put("palette", it.id) }
        return o
    }

    fun importData(o: JSONObject) {
        o.optJSONArray("replies")?.let { a -> saveReplies((0 until a.length()).map { a.getString(it) }) }
        o.optJSONArray("locked")?.let { a ->
            prefs().edit().putStringSet("locked", (0 until a.length()).map { a.getString(it) }.toSet()).apply()
        }
        o.optJSONObject("accents")?.let { PrismChatAccent.importData(it) }
        val pid = o.optString("palette", "")
        desu.inugram.prism.PrismTheme.palettes.firstOrNull { it.id == pid }?.let {
            desu.inugram.prism.PrismTheme.installPalette(it, org.telegram.ui.ActionBar.Theme.isCurrentThemeDark(), false)
        }
    }

    // ── per-chat accent picker ───────────────────────────────────────────────
    @JvmStatic
    fun showColorPicker(f: BaseFragment, dialogId: Long) {
        val ctx = f.parentActivity ?: return
        val dark = org.telegram.ui.ActionBar.Theme.isCurrentThemeDark()
        val row = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(org.telegram.messenger.AndroidUtilities.dp(12f), org.telegram.messenger.AndroidUtilities.dp(16f), org.telegram.messenger.AndroidUtilities.dp(12f), org.telegram.messenger.AndroidUtilities.dp(8f))
        }
        var dialog: AlertDialog? = null
        val current = PrismChatAccent.get(dialogId)
        fun swatch(color: Int, selected: Boolean, label: String, pick: String?) {
            val size = org.telegram.messenger.AndroidUtilities.dp(40f)
            val v = android.widget.TextView(ctx).apply {
                text = if (selected) "✓" else ""
                setTextColor(0xFFFFFFFF.toInt()); textSize = 18f; gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color)
                }
                contentDescription = label
                setOnClickListener {
                    PrismChatAccent.set(dialogId, pick)
                    dialog?.dismiss()
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.didSetNewTheme, false, true, true)
                }
            }
            row.addView(v, android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins(org.telegram.messenger.AndroidUtilities.dp(5f), 0, org.telegram.messenger.AndroidUtilities.dp(5f), 0) })
        }
        swatch(0xFF9A9A9A.toInt(), current == null, "Default", null)
        for (p in desu.inugram.prism.PrismTheme.palettes) swatch(if (dark) p.accentDark else p.accentLight, current == p.id, p.label, p.id)
        dialog = AlertDialog.Builder(ctx).setTitle("Chat colour").setView(row).setNegativeButton("Cancel", null).create()
        dialog.show()
    }

    // ── PDF of the whole chat history ────────────────────────────────────────
    private const val FULL_PDF_LIMIT = 5000

    @JvmStatic
    fun exportFullPdf(f: BaseFragment, title: String, dialogId: Long) {
        val ctx = f.parentActivity ?: return
        val account = f.currentAccount
        val progress = AlertDialog(ctx, AlertDialog.ALERT_TYPE_SPINNER).apply { setCanCancel(true) }
        val collected = ArrayList<MessageObject>()
        val users = androidx.collection.LongSparseArray<org.telegram.tgnet.TLRPC.User>()
        val chats = androidx.collection.LongSparseArray<org.telegram.tgnet.TLRPC.Chat>()
        var cancelled = false
        progress.setOnCancelListener { cancelled = true }
        progress.show()
        fun finish() {
            progress.dismiss()
            if (cancelled) return
            val file = exportPdf(f, title, collected.asReversed().let { ArrayList(it) })
            if (file == null) {
                org.telegram.ui.Components.BulletinFactory.of(f).createSimpleBulletin(org.telegram.messenger.R.raw.chats_infotip, "Nothing to export").show()
            } else {
                org.telegram.messenger.AndroidUtilities.openForView(file, file.name, "application/pdf", ctx, null, false)
            }
        }
        fun page(offsetId: Int) {
            val req = org.telegram.tgnet.TLRPC.TL_messages_getHistory()
            req.peer = org.telegram.messenger.MessagesController.getInstance(account).getInputPeer(dialogId)
            req.offset_id = offsetId
            req.limit = 100
            org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
                org.telegram.messenger.AndroidUtilities.runOnUIThread {
                    if (cancelled) return@runOnUIThread
                    if (error != null || response !is org.telegram.tgnet.TLRPC.messages_Messages) { finish(); return@runOnUIThread }
                    for (u in response.users) users.put(u.id, u)
                    for (c in response.chats) chats.put(c.id, c)
                    for (m in response.messages) collected.add(MessageObject(account, m, users, chats, false, true))
                    val last = response.messages.lastOrNull()
                    if (response.messages.size < 100 || last == null || collected.size >= FULL_PDF_LIMIT) finish() else page(last.id)
                }
            }
        }
        page(0)
    }
}
