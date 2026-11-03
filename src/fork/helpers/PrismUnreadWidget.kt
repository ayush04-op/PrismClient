package desu.inugram.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig

/** Paper-styled home-screen widget that shows the unread chat count. */
class PrismUnreadWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        private fun unread(): Int = try {
            MessagesStorage.getInstance(UserConfig.selectedAccount).mainUnreadCount
        } catch (_: Throwable) {
            0
        }

        private fun build(context: Context): RemoteViews {
            val n = unread()
            val v = RemoteViews(context.packageName, R.layout.prism_widget)
            v.setTextViewText(R.id.prism_widget_count, if (n > 0) n.toString() else "0")
            v.setTextViewText(R.id.prism_widget_label, if (n == 1) "unread chat" else "unread chats")
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent()
            val pi = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.prism_widget_root, pi)
            return v
        }

        @JvmStatic
        fun refresh(context: Context) {
            try {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, PrismUnreadWidget::class.java))
                if (ids.isEmpty()) return
                for (id in ids) m.updateAppWidget(id, build(context))
            } catch (_: Throwable) {
            }
        }
    }
}
