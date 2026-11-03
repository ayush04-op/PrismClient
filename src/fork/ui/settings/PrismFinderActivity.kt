package desu.inugram.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.collection.LongSparseArray
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

/** Finds links, files and photos across every chat with Telegram's own global search. */
class PrismFinderActivity : SettingsPageActivity() {
    private enum class Kind(val label: String) { LINKS("Links"), FILES("Files"), PHOTOS("Photos") }

    private var kind = Kind.LINKS
    private var query = ""
    private var loading = false
    private var done = false
    private var nextRate = 0
    private var error: String? = null
    private val results = ArrayList<MessageObject>()
    private var reqId = 0

    override fun getTitle(): CharSequence = "Find links & files"

    override fun onFragmentCreate(): Boolean {
        search(true)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        if (reqId != 0) ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true)
        super.onFragmentDestroy()
    }

    private fun filter(): TLRPC.MessagesFilter = when (kind) {
        Kind.LINKS -> TLRPC.TL_inputMessagesFilterUrl()
        Kind.FILES -> TLRPC.TL_inputMessagesFilterDocument()
        Kind.PHOTOS -> TLRPC.TL_inputMessagesFilterPhotos()
    }

    private fun search(reset: Boolean) {
        if (loading) return
        if (reset) { results.clear(); nextRate = 0; done = false; error = null }
        if (done) return
        loading = true
        val req = TLRPC.TL_messages_searchGlobal()
        req.q = query
        req.filter = filter()
        req.limit = 30
        req.flags = req.flags or 1
        req.folder_id = 0
        if (results.isEmpty()) {
            req.offset_rate = 0; req.offset_id = 0; req.offset_peer = TLRPC.TL_inputPeerEmpty()
        } else {
            val last = results.last()
            req.offset_rate = nextRate
            req.offset_id = last.id
            req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(last.messageOwner.peer_id))
        }
        val account = currentAccount
        reqId = ConnectionsManager.getInstance(account).sendRequest(req) { response, err ->
            val list = ArrayList<MessageObject>()
            var rate = 0
            var more = false
            if (err == null && response is TLRPC.messages_Messages) {
                val users = LongSparseArray<TLRPC.User>()
                val chats = LongSparseArray<TLRPC.Chat>()
                for (u in response.users) users.put(u.id, u)
                for (c in response.chats) chats.put(c.id, c)
                for (m in response.messages) list.add(MessageObject(account, m, users, chats, false, true))
                MessagesController.getInstance(account).putUsers(response.users, false)
                MessagesController.getInstance(account).putChats(response.chats, false)
                rate = response.next_rate
                more = response.messages.size >= 30
            }
            AndroidUtilities.runOnUIThread {
                loading = false
                reqId = 0
                if (err != null) error = err.text ?: "Search failed"
                results.addAll(list)
                nextRate = rate
                done = !more
                softRebuild()
            }
        }
        softRebuild()
    }

    private fun chatName(m: MessageObject): String {
        val id = MessageObject.getDialogId(m.messageOwner)
        val mc = MessagesController.getInstance(currentAccount)
        return if (id > 0) mc.getUser(id)?.let { org.telegram.messenger.UserObject.getUserName(it) } ?: ""
        else mc.getChat(-id)?.title ?: ""
    }

    private fun rowTitle(m: MessageObject): String = when (kind) {
        Kind.FILES -> m.document?.let { FileLoader.getDocumentFileName(it) }?.takeIf { it.isNotBlank() } ?: "File"
        Kind.PHOTOS -> "Photo" + (m.caption?.let { " · $it" } ?: "")
        Kind.LINKS -> (m.messageText ?: m.caption)?.toString().orEmpty().replace('\n', ' ').take(90).ifBlank { "Link" }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader("Search"))
        items.add(UItem.asButton(BTN_KIND, "Look for", kind.label))
        items.add(UItem.asButton(BTN_QUERY, "Words", query.ifEmpty { "Any" }))
        items.add(UItem.asShadow(if (error != null) error else "Searches all your chats."))
        items.add(UItem.asHeader(if (loading && results.isEmpty()) "Searching…" else "Results"))
        results.forEachIndexed { i, m -> items.add(UItem.asButton(ROW_BASE + i, rowTitle(m), chatName(m))) }
        if (!done && !loading && results.isNotEmpty()) items.add(UItem.asButton(BTN_MORE, "Load more"))
        if (results.isEmpty() && !loading) items.add(UItem.asShadow("Nothing found."))
        else items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val ctx = parentActivity ?: return
        when {
            item.id == BTN_KIND -> RadioItemOptions.show(this, view, Kind.values().map { it.label }, kind.ordinal) { which ->
                kind = Kind.values()[which]; search(true)
            }
            item.id == BTN_QUERY -> {
                val input = EditText(ctx).apply { inputType = InputType.TYPE_CLASS_TEXT; setText(query); setSelection(query.length); hint = "Words to look for" }
                AlertDialog.Builder(ctx).setTitle("Words").setView(input)
                    .setPositiveButton("Search") { _, _ -> query = input.text.toString().trim(); search(true) }
                    .setNegativeButton("Cancel", null).create().show()
            }
            item.id == BTN_MORE -> search(false)
            item.id >= ROW_BASE -> results.getOrNull(item.id - ROW_BASE)?.let { m ->
                val id = MessageObject.getDialogId(m.messageOwner)
                val args = Bundle()
                if (id > 0) args.putLong("user_id", id) else args.putLong("chat_id", -id)
                args.putInt("message_id", m.id)
                presentFragment(ChatActivity(args))
            }
        }
    }

    companion object {
        private const val BTN_KIND = 7401
        private const val BTN_QUERY = 7402
        private const val BTN_MORE = 7403
        private const val ROW_BASE = 7500
    }
}
