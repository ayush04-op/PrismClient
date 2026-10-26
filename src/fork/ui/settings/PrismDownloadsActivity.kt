package desu.inugram.ui.settings

import android.view.View
import desu.inugram.helpers.PrismDownloads
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

/** Downloads: everything Prism saved, grouped by category. Tap a category to see its files. */
class PrismDownloadsActivity(private val only: PrismDownloads.Category? = null) : SettingsPageActivity() {
    private var entries: List<PrismDownloads.Entry> = emptyList()

    override fun getTitle(): CharSequence = only?.label ?: "Downloads"

    override fun onFragmentCreate(): Boolean {
        entries = PrismDownloads.list(org.telegram.messenger.ApplicationLoader.applicationContext)
        return super.onFragmentCreate()
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        if (only == null) {
            if (entries.isEmpty()) {
                items.add(UItem.asShadow("Nothing saved yet. Files you save from chats appear here, sorted into folders."))
                return
            }
            PrismDownloads.Category.values().forEach { cat ->
                val n = entries.count { it.category == cat }
                if (n > 0) items.add(UItem.asButton(CAT_BASE + cat.ordinal, cat.label, "$n"))
            }
            items.add(UItem.asShadow("Documents are saved into folders such as APKs, PDFs and Archives inside your Prism download folder."))
        } else {
            val list = entries.filter { it.category == only }
            list.forEachIndexed { i, e ->
                items.add(UItem.asButton(FILE_BASE + i, e.name, PrismDownloads.readableSize(e.size)))
            }
            items.add(UItem.asShadow(null))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (only == null && item.id >= CAT_BASE && item.id < FILE_BASE) {
            presentFragment(PrismDownloadsActivity(PrismDownloads.Category.values()[item.id - CAT_BASE]))
        } else if (only != null && item.id >= FILE_BASE) {
            val list = entries.filter { it.category == only }
            list.getOrNull(item.id - FILE_BASE)?.let { PrismDownloads.open(context, it) }
        }
    }

    companion object {
        private const val CAT_BASE = 7000
        private const val FILE_BASE = 7100
    }
}
