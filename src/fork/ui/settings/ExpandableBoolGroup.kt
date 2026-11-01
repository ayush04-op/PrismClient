package desu.inugram.ui.settings

import android.view.View
import desu.inugram.InuConfig
import desu.inugram.helpers.InuUtils
import org.telegram.messenger.LocaleController
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.TextCheckCell2
import org.telegram.ui.Components.UItem

class ExpandableBoolGroup(
    val title: CharSequence,
    val options: List<Option>,
    val sectionId: Int = InuUtils.generateId(),
) {
    class Option(
        val labelRes: Int,
        val config: InuConfig.BoolItem,
        val id: Int = InuUtils.generateId(),
        val locked: Boolean = false,
    )

    var expanded = false
    var enabled = true

    fun expandIfContains(id: Int) {
        if (options.any { it.id == id }) expanded = true
    }

    /** [info] is a row of its own shown first while the group is open, for a note that belongs to the group */
    fun addTo(items: ArrayList<UItem>, info: UItem? = null, onChange: (options: List<Option>) -> Unit) {
        for (o in options) if (o.locked && !o.config.value) o.config.value = true
        val shown = options.filter { !it.locked }
        val checkedCount = shown.count { it.config.value }
        val total = shown.size
        val isChecked = checkedCount > 0
        items.add(
            UItem.asExpandableSwitch(sectionId, title, "$checkedCount/$total")
                .setChecked(isChecked)
                .setCollapsed(!expanded)
                .setClickCallback {
                    if (!enabled) return@setClickCallback
                    val enable = !isChecked
                    val changed = arrayListOf<Option>()
                    for (opt in options) {
                        if (!opt.locked && opt.config.value != enable) {
                            changed.add(opt)
                            opt.config.value = enable
                        }
                    }
                    onChange(changed)
                }
                .onBind(Utilities.Callback { view ->
                    view.alpha = if (enabled) 1f else 0.5f
                    val cell = view as? TextCheckCell2 ?: return@Callback
                    cell.checkBox.also {
                        it.setDrawIconType(0)
                        it.setColors(
                            Theme.key_switchTrack,
                            Theme.key_switchTrackChecked,
                            Theme.key_windowBackgroundWhite,
                            Theme.key_windowBackgroundWhite,
                        )
                    }
                })
        )
        if (expanded) {
            info?.let { items.add(it) }
            for (opt in shown) {
                items.add(
                    UItem.asRoundCheckbox(opt.id, LocaleController.getString(opt.labelRes))
                        .setChecked(opt.config.value)
                        .setPad(1)
                        .setEnabled(enabled)
                        .onBind(Utilities.Callback { it.alpha = if (enabled) 1f else 0.5f })
                )
            }
        }
    }

    fun handleClick(item: UItem, view: View, onChange: (option: Option?) -> Unit): Boolean {
        if (item.id == sectionId) {
            expanded = !expanded
            onChange(null)
            return true
        }
        val opt = options.firstOrNull { it.id == item.id } ?: return false
        if (opt.locked) return true // always on
        if (!enabled) return true
        val new = opt.config.toggle()
        (view as? CheckBoxCell)?.setChecked(new, true)
        onChange(opt)
        return true
    }
}
