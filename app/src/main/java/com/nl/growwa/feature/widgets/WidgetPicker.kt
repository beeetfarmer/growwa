package com.nl.growwa.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.nl.growwa.data.widgets.GrowwaWidgetHost

/**
 * Full-screen picker listing every installed app-widget provider, grouped by app.
 * Handles the allocate → bind → configure handshake itself, then reports the bound
 * id back via [onPlace] for the layout to position.
 */
@Composable
fun WidgetPicker(
    widgetHost: GrowwaWidgetHost,
    onPlace: (AppWidgetProviderInfo, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val groups = remember {
        widgetHost.installedProviders()
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                appLabel to list.sortedBy { it.loadLabel(pm).lowercase() }
            }
            .sortedBy { it.first.lowercase() }
    }

    var pendingId by remember { mutableIntStateOf(-1) }
    var pendingProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingId
        val provider = pendingProvider
        pendingId = -1; pendingProvider = null
        if (result.resultCode == Activity.RESULT_OK && provider != null && id != -1) {
            onPlace(provider, id)
        } else if (id != -1) {
            widgetHost.deleteId(id)
        }
        onDismiss()
    }

    // Bound (either already-allowed or just granted): configure if the provider needs it.
    fun placeOrConfigure(provider: AppWidgetProviderInfo, id: Int) {
        val configure = provider.configure
        if (configure != null) {
            pendingId = id; pendingProvider = provider
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            runCatching { configureLauncher.launch(intent) }.onFailure {
                widgetHost.deleteId(id); onDismiss()
            }
        } else {
            onPlace(provider, id)
            onDismiss()
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingId
        val provider = pendingProvider
        if (result.resultCode == Activity.RESULT_OK && provider != null && id != -1) {
            placeOrConfigure(provider, id)
        } else {
            if (id != -1) widgetHost.deleteId(id)
            pendingId = -1; pendingProvider = null
            onDismiss()
        }
    }

    fun choose(provider: AppWidgetProviderInfo) {
        val id = widgetHost.allocateId()
        if (widgetHost.bindAllowed(id, provider.provider)) {
            placeOrConfigure(provider, id)
        } else {
            pendingId = id; pendingProvider = provider
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            runCatching { bindLauncher.launch(intent) }.onFailure {
                widgetHost.deleteId(id)
                pendingId = -1; pendingProvider = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Widgets", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    groups.forEach { (appLabel, providers) ->
                        item(key = "h_$appLabel") {
                            Text(
                                appLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(providers, key = { it.provider.flattenToString() }) { provider ->
                            WidgetRow(provider = provider, onClick = { choose(provider) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(provider: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val preview = remember(provider.provider.flattenToString()) { provider.previewBitmap(context) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(provider.loadLabel(pm), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(2.dp))
            Text(
                "${cells(provider.minWidth)} × ${cells(provider.minHeight)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun cells(minDp: Int) = ((minDp + 30) / 70).coerceAtLeast(1)

/** Provider preview image (falls back to the provider icon), rasterized for Compose. */
private fun AppWidgetProviderInfo.previewBitmap(context: Context): ImageBitmap? {
    val drawable = runCatching { loadPreviewImage(context, 0) }.getOrNull()
        ?: runCatching { loadIcon(context, 0) }.getOrNull()
        ?: return null
    val w = drawable.intrinsicWidth.coerceIn(1, 512)
    val h = drawable.intrinsicHeight.coerceIn(1, 512)
    return runCatching { drawable.toBitmap(w, h) }
        .getOrElse { createBitmap(1, 1) }
        .asImageBitmap()
}
