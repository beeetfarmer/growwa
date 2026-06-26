package com.nl.growwa.core.model

import androidx.compose.ui.graphics.ImageBitmap

/** An installed icon pack the user can choose from. */
data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)
