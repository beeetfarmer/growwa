package com.nl.growwa.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Growwa's built-in world-clock widget: a capsule split into up to three city segments, each
 * tinted by that city's local time of day. Time and date update live via TextClock; the tint is
 * refreshed on every [onUpdate] (periodic + on resize).
 */
class WorldClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWorldClockWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Re-render so the capsule bitmap matches the new widget size.
        updateWorldClockWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WorldClockStore.clear(context, it) }
    }
}
