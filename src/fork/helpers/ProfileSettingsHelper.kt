package desu.inugram.helpers

import desu.inugram.InuConfig
import desu.inugram.helpers.menu.ProfileInfoMenuConfig
import desu.inugram.helpers.menu.ProfileMenuConfig
import desu.inugram.helpers.menu.ProfileRow
import desu.inugram.helpers.menu.ProfileRowRegistry
import desu.inugram.helpers.menu.reorderByMenu
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

object ProfileSettingsHelper {

    private const val MONEY_ROW_ID = 25

    // entiny: rows without a built-in entry are registered on the fly so new stock rows become orderable
    private fun classify(item: UItem, customized: Boolean): ProfileRow? {
        if (item.id == MONEY_ROW_ID && !customized) return null
        ProfileMenuConfig.Item.forSettingsId(item.id)?.let { return it }
        if (item.id == 0 || item.text.isNullOrEmpty()) return null
        val row = try {
            ProfileRowRegistry.discover(item.id, item.text, item.iconResId)
        } catch (e: Throwable) {
            android.util.Log.d("ProfileSettings", "discover failed", e)
            return null
        }
        return if (customized) row else null
    }

    // prism: sections: 10 account, 11 chats, 14 appearance, 12 notifications and data, 2 premium, 15 system, 16 about, 3 help; -1 unknown
    private fun stockGroupOf(item: UItem): Int = when (item.id) {
        1, 3 -> 10
        2, 7 -> 11
        5, 6, 8, 9 -> 12
        10 -> 15
        in 11..13, 15, 16, MONEY_ROW_ID -> 2
        17, 18, 19, 23, 24 -> 3
        else -> PrismSettingsRows.sectionOf(item.id)
    }

    private fun inheritedGroups(rows: List<UItem>): Map<Int, Int> {
        val groups = HashMap<Int, Int>()
        var last = -1
        for (row in rows) {
            val group = stockGroupOf(row)
            if (group >= 0) {
                last = group
            } else if (last >= 0) {
                groups[row.id] = last
            }
        }
        return groups
    }

    @JvmStatic
    fun reorder(items: MutableList<UItem>, from: Int) {
        if (from < 0 || from >= items.size) return
        val head = ArrayList(items.subList(0, from))
        val rows = ArrayList<UItem>(items.size - from)
        for (i in from until items.size) {
            val it = items[i]
            if (it.viewType != UniversalAdapter.VIEW_TYPE_SHADOW && it.viewType != UniversalAdapter.VIEW_TYPE_HEADER &&
                it.id != 18 && it.id != 23) // Prism: Telegram FAQ and Features rows removed
                rows.add(it)
        }
        val customized = InuConfig.PROFILE_SETTINGS_ROWS.isCustomized()
        val inherited = inheritedGroups(rows + PrismSettingsRows.items())
        fun groupOf(item: UItem): Int = stockGroupOf(item).takeIf { it >= 0 } ?: inherited[item.id] ?: -1
        val menuOrdered = reorderByMenu(rows, InuConfig.PROFILE_SETTINGS_ROWS.value) { classify(it, customized) } + PrismSettingsRows.items()
        // prism: gather rows by section so each heading appears once; order inside a section is kept
        val rank = mapOf(10 to 1, 11 to 2, 14 to 3, 12 to 4, 2 to 5, 15 to 6, 16 to 7, 3 to 8)
        val topMoney = menuOrdered.firstOrNull()?.takeIf { it.id == MONEY_ROW_ID }
        val rest = menuOrdered.filter { it !== topMoney }
        val ordered = (listOfNotNull(topMoney) + rest.sortedBy { rank[groupOf(it)] ?: 99 })
        val rebuilt = ArrayList<UItem>(ordered.size + 4)
        var lastGroup = -2
        for (row in ordered) {
            val group = groupOf(row)
            if (lastGroup != -1 && group != lastGroup) {
                if (rebuilt.isNotEmpty() && group != -1) rebuilt.add(UItem.asShadow(null))
                val label = when (group) {
                    3 -> R.string.SettingsHelp
                    10 -> R.string.PrismSecAccount
                    11 -> R.string.PrismSecChats
                    12 -> R.string.PrismSecData
                    14 -> R.string.PrismSecAppearance
                    15 -> R.string.PrismSecSystem
                    16 -> R.string.PrismSecAbout
                    2 -> if (row.id == MONEY_ROW_ID) 0 else R.string.PrismSecPremium
                    else -> 0
                }
                if (label != 0) rebuilt.add(UItem.asHeader(LocaleController.getString(label)))
            }
            rebuilt.add(row)
            lastGroup = group
        }
        if (rebuilt.isNotEmpty() && lastGroup != -1 &&
            rebuilt[rebuilt.size - 1].viewType != UniversalAdapter.VIEW_TYPE_SHADOW
        ) {
            rebuilt.add(UItem.asShadow(null))
        }
        items.clear()
        items.addAll(head)
        items.addAll(rebuilt)
    }

    @JvmStatic
    fun orderedEnabledInfoRows(unavailable: Set<ProfileInfoMenuConfig.Item>): List<ProfileInfoMenuConfig.Item> =
        InuConfig.PROFILE_INFO_ROWS.value
            .filter { it.enabled && it.item !in unavailable }
            .map { it.item }
}
