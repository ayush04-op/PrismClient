package desu.inugram.ui.settings

import android.app.TimePickerDialog
import android.text.InputType
import android.view.View
import android.widget.EditText
import desu.inugram.InuConfig
import desu.inugram.helpers.PrismChatTools
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

/** Prism tools: downloads, caption position, quiet hours, saved replies, chat lock. */
class PrismToolsActivity : SettingsPageActivity() {
    override fun getTitle(): CharSequence = "Prism tools"

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader("Downloads"))
        items.add(UItem.asButton(BTN_DOWNLOADS, "Downloads", "Browse by category"))
        items.add(UItem.asCheck(TOG_SORT, "Sort saved files into folders").setChecked(InuConfig.SORT_DOWNLOADS.value))
        items.add(UItem.asShadow("APKs, PDFs, documents and archives are saved into their own folders."))

        items.add(UItem.asHeader("Posting"))
        items.add(UItem.asCheck(TOG_CAPTION, "Caption above photo by default").setChecked(InuConfig.CAPTION_ABOVE_DEFAULT.value))
        items.add(UItem.asShadow("You can still switch it for each post."))

        items.add(UItem.asHeader("Quiet hours"))
        items.add(UItem.asCheck(TOG_QUIET, "Silence notifications on a schedule").setChecked(InuConfig.QUIET_ENABLED.value))
        items.add(UItem.asButton(BTN_QUIET_START, "From", PrismChatTools.fmt(InuConfig.QUIET_START.value)))
        items.add(UItem.asButton(BTN_QUIET_END, "Until", PrismChatTools.fmt(InuConfig.QUIET_END.value)))
        items.add(UItem.asShadow("Notifications are not shown during these hours. Messages still arrive."))

        items.add(UItem.asHeader("Saved replies"))
        PrismChatTools.replies().forEachIndexed { i, r ->
            items.add(UItem.asButton(REPLY_BASE + i, if (r.length > 50) r.take(50) + "…" else r, "Tap to delete"))
        }
        items.add(UItem.asButton(BTN_ADD_REPLY, "Add a reply"))
        items.add(UItem.asShadow("Use them from the ⋮ menu inside any chat."))

        items.add(UItem.asHeader("Chat lock"))
        val n = PrismChatTools.lockedIds().size
        items.add(UItem.asButton(BTN_LOCKS, "Locked chats", "$n"))
        items.add(UItem.asShadow("Open a chat, tap ⋮ and choose Lock chat. It then asks for your fingerprint or screen lock."))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val ctx = parentActivity ?: return
        when (item.id) {
            TOG_SORT -> (view as? TextCheckCell)?.isChecked = InuConfig.SORT_DOWNLOADS.toggle()
            TOG_CAPTION -> (view as? TextCheckCell)?.isChecked = InuConfig.CAPTION_ABOVE_DEFAULT.toggle()
            TOG_QUIET -> (view as? TextCheckCell)?.isChecked = InuConfig.QUIET_ENABLED.toggle()
            BTN_DOWNLOADS -> presentFragment(PrismDownloadsActivity())
            BTN_QUIET_START -> pickTime(InuConfig.QUIET_START.value) { InuConfig.QUIET_START.value = it; softRebuild() }
            BTN_QUIET_END -> pickTime(InuConfig.QUIET_END.value) { InuConfig.QUIET_END.value = it; softRebuild() }
            BTN_ADD_REPLY -> {
                val input = EditText(ctx).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; hint = "Reply text" }
                AlertDialog.Builder(ctx).setTitle("Add a reply").setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val t = input.text.toString().trim()
                        if (t.isNotEmpty()) { PrismChatTools.saveReplies(PrismChatTools.replies() + t); softRebuild() }
                    }.setNegativeButton("Cancel", null).create().show()
            }
            BTN_LOCKS -> {
                val n = PrismChatTools.lockedIds().size
                AlertDialog.Builder(ctx).setTitle("Locked chats")
                    .setMessage(if (n == 0) "No chats are locked." else "$n chat(s) locked. Open a locked chat and choose Unlock chat from ⋮ to remove the lock.")
                    .setPositiveButton("OK", null).create().show()
            }
            else -> if (item.id >= REPLY_BASE) {
                val list = PrismChatTools.replies()
                val idx = item.id - REPLY_BASE
                if (idx in list.indices) {
                    AlertDialog.Builder(ctx).setTitle("Delete this reply?").setMessage(list[idx])
                        .setPositiveButton("Delete") { _, _ -> list.removeAt(idx); PrismChatTools.saveReplies(list); softRebuild() }
                        .setNegativeButton("Cancel", null).create().show()
                }
            }
        }
    }

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        val ctx = parentActivity ?: return
        TimePickerDialog(ctx, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    companion object {
        private const val TOG_SORT = 7201
        private const val TOG_CAPTION = 7202
        private const val TOG_QUIET = 7203
        private const val BTN_DOWNLOADS = 7204
        private const val BTN_QUIET_START = 7205
        private const val BTN_QUIET_END = 7206
        private const val BTN_ADD_REPLY = 7207
        private const val BTN_LOCKS = 7208
        private const val REPLY_BASE = 7300
    }
}
