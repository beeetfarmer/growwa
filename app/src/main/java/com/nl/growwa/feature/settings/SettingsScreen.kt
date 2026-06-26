package com.nl.growwa.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.core.model.IconPackInfo
import com.nl.growwa.data.settings.AccentColor
import com.nl.growwa.data.settings.DrawerSearch
import com.nl.growwa.data.settings.GestureAction
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.data.settings.ThemeMode
import com.nl.growwa.feature.gestures.GrowwaAccessibilityService
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    loadIconPacks: suspend () -> List<IconPackInfo>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Home settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            SectionHeader("Grid")
            StepperRow(
                "Home columns", settings.homeColumns,
                LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS,
            ) { v -> onUpdate { it.copy(homeColumns = v) } }
            StepperRow(
                "Home rows", settings.homeRows,
                LauncherSettings.MIN_ROWS, LauncherSettings.MAX_ROWS,
            ) { v -> onUpdate { it.copy(homeRows = v) } }
            StepperRow(
                "Dock apps", settings.dockCount, 1, LauncherSettings.MAX_DOCK,
            ) { v -> onUpdate { it.copy(dockCount = v) } }
            StepperRow(
                "Drawer columns", settings.drawerColumns,
                LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS,
            ) { v -> onUpdate { it.copy(drawerColumns = v) } }

            SectionHeader("Icons & labels")
            SliderRow(
                "Icon size", "${settings.iconSizeDp.roundToInt()} dp",
                settings.iconSizeDp, LauncherSettings.MIN_ICON_DP..LauncherSettings.MAX_ICON_DP,
            ) { v -> onUpdate { it.copy(iconSizeDp = v) } }
            SliderRow(
                "Label size", "${settings.labelSizeSp.roundToInt()} sp",
                settings.labelSizeSp, LauncherSettings.MIN_LABEL_SP..LauncherSettings.MAX_LABEL_SP,
            ) { v -> onUpdate { it.copy(labelSizeSp = v) } }
            SwitchRow("Show labels on home", settings.showHomeLabels) { v ->
                onUpdate { it.copy(showHomeLabels = v) }
            }
            SwitchRow("Show labels in drawer", settings.showDrawerLabels) { v ->
                onUpdate { it.copy(showDrawerLabels = v) }
            }
            IconPackRow(settings.iconPack, loadIconPacks) { pkg ->
                onUpdate { it.copy(iconPack = pkg) }
            }

            SectionHeader("App drawer")
            ChipRow(
                title = "Search bar",
                options = DrawerSearch.entries,
                selected = settings.drawerSearch,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            ) { mode -> onUpdate { it.copy(drawerSearch = mode) } }
            SliderRow(
                "Background transparency",
                "${(settings.drawerTransparency * 100).roundToInt()}%",
                settings.drawerTransparency, 0f..1f,
            ) { v -> onUpdate { it.copy(drawerTransparency = v) } }

            SectionHeader("Appearance")
            ChipRow(
                title = "Theme",
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            ) { mode -> onUpdate { it.copy(themeMode = mode) } }
            AccentRow(settings.accent) { accent -> onUpdate { it.copy(accent = accent) } }

            SectionHeader("Gestures")
            GesturePickerRow("Swipe up", settings.gestureSwipeUp) { a ->
                onUpdate { it.copy(gestureSwipeUp = a) }
            }
            GesturePickerRow("Swipe down", settings.gestureSwipeDown) { a ->
                onUpdate { it.copy(gestureSwipeDown = a) }
            }
            GesturePickerRow("Double tap", settings.gestureDoubleTap) { a ->
                onUpdate { it.copy(gestureDoubleTap = a) }
            }
            AccessibilityRow(settings)

            SectionHeader("System")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_HOME_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Set Growwa as default home") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        FilledTonalIconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
        ) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
        Text(
            "$value",
            modifier = Modifier.width(40.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
        ) { Icon(Icons.Default.Add, contentDescription = "Increase") }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IconPackRow(
    current: String,
    loadIconPacks: suspend () -> List<IconPackInfo>,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var dialogOpen by remember { mutableStateOf(false) }
    val currentLabel = remember(current) {
        if (current.isBlank()) "System default"
        else runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(current, 0)).toString()
        }.getOrDefault(current)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Icon pack", modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text(currentLabel, color = MaterialTheme.colorScheme.primary)
    }

    if (dialogOpen) {
        IconPackDialog(
            current = current,
            loadIconPacks = loadIconPacks,
            onSelect = { onSelect(it); dialogOpen = false },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun IconPackDialog(
    current: String,
    loadIconPacks: suspend () -> List<IconPackInfo>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val packs by produceState<List<IconPackInfo>?>(initialValue = null) {
        value = loadIconPacks()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Icon pack") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                IconPackOption("System default", null, current.isBlank()) { onSelect("") }
                when (val list = packs) {
                    null -> Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    else -> if (list.isEmpty()) {
                        Text(
                            "No icon packs installed",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        list.forEach { pack ->
                            IconPackOption(pack.label, pack.icon, current == pack.packageName) {
                                onSelect(pack.packageName)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun IconPackOption(
    label: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Box(Modifier.size(32.dp))
        }
        Text(label, modifier = Modifier.weight(1f).padding(start = 14.dp), fontSize = 16.sp)
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AccentRow(current: AccentColor, onSelect: (AccentColor) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Accent color", modifier = Modifier.weight(1f), fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentColor.entries.forEach { accent ->
                val selected = accent == current
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(accent.argb))
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { onSelect(accent) },
                )
            }
        }
    }
}

@Composable
private fun GesturePickerRow(
    title: String,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text(current.label, color = MaterialTheme.colorScheme.primary)
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            title = { Text(title) },
            text = {
                Column {
                    GestureAction.entries.forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(action); open = false }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(action.label, modifier = Modifier.weight(1f), fontSize = 16.sp)
                            if (action == current) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun AccessibilityRow(settings: LauncherSettings) {
    val needsService = listOf(
        settings.gestureSwipeUp, settings.gestureSwipeDown, settings.gestureDoubleTap,
    ).any { it == GestureAction.NOTIFICATIONS || it == GestureAction.LOCK_SCREEN }
    if (!needsService) return

    val context = LocalContext.current
    val enabled = GrowwaAccessibilityService.isEnabled(context)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Accessibility service", fontSize = 16.sp)
            Text(
                if (enabled) "Enabled" else "Required for shade / lock — tap to enable",
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun <T> ChipRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
