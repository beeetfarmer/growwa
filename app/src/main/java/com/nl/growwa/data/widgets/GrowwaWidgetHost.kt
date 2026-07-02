package com.nl.growwa.data.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context

/** Arbitrary, stable id identifying this launcher as a widget host. */
private const val WIDGET_HOST_ID = 0x47_57 // "GW"

/** Thin wrapper around AppWidgetHost/Manager for binding and rendering home-screen widgets. */
class GrowwaWidgetHost(context: Context) {
    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host: AppWidgetHost = AppWidgetHost(appContext, WIDGET_HOST_ID)

    /** Screen density, used to convert a provider's pixel min-sizes into grid cells. */
    val density: Float = appContext.resources.displayMetrics.density

    fun startListening() { runCatching { host.startListening() } }
    fun stopListening() { runCatching { host.stopListening() } }

    fun allocateId(): Int = host.allocateAppWidgetId()
    fun deleteId(id: Int) { runCatching { host.deleteAppWidgetId(id) } }

    fun providerInfo(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun installedProviders(): List<AppWidgetProviderInfo> =
        runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun bindAllowed(id: Int, provider: ComponentName): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, provider) }.getOrDefault(false)

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, id, info)
}
