package com.nl.growwa.core.model

/** An item occupying a slot on the home surface: a single app or a folder of apps. */
sealed interface HomeItem

data class AppItem(val app: AppInfo) : HomeItem

data class FolderItem(
    val id: Long,
    val name: String,
    val apps: List<AppInfo>,
) : HomeItem

/** A widget placed on the home grid; spans [spanX] x [spanY] cells from [position] (top-left). */
data class WidgetPlacement(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val page: Int,
    val position: Int,
    val spanX: Int,
    val spanY: Int,
)
