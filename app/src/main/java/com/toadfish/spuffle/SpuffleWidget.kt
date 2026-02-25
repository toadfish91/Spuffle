package com.toadfish.spuffle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SpuffleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_SPUFFLE = "com.toadfish.spuffle.ACTION_WIDGET_SPUFFLE"

        /**
         * Call this whenever the widget display should be refreshed —
         * e.g. after a successful Spuffle from the main app.
         */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, SpuffleWidget::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, SpuffleWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SPUFFLE) {
            // Launch WidgetSpuffleService to do the work in the background
            val serviceIntent = Intent(context, WidgetSpuffleService::class.java)
            context.startService(serviceIntent)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_spuffle)
        val lastPlaylist = PlaylistCache.getLastPlaylist(context)

        // Update text to show last used playlist
        if (lastPlaylist != null) {
            views.setTextViewText(R.id.widgetTitle, lastPlaylist.name)
            views.setTextViewText(R.id.widgetSubtitle, "${lastPlaylist.trackCount} songs")
        } else {
            views.setTextViewText(R.id.widgetTitle, "Spuffle")
            views.setTextViewText(R.id.widgetSubtitle, "Open app first")
        }

        // Set up the tap action
        val spuffleIntent = Intent(context, SpuffleWidget::class.java).apply {
            action = ACTION_SPUFFLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            spuffleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetButton, pendingIntent)
        views.setOnClickPendingIntent(R.id.widgetTextContainer, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}