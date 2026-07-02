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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.core.model.IconPackInfo
import com.nl.growwa.data.settings.AccentColor
import com.nl.growwa.data.settings.DrawerSearch
import com.nl.growwa.data.settings.GestureAction
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.data.settings.PrivateSpacePosition
import com.nl.growwa.data.settings.ThemeMode
import com.nl.growwa.feature.gestures.GrowwaAccessibilityService
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    loadIconPacks: suspend () -> List<IconPackInfo>,
    privateSpaceAvailable: Boolean,
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
            // Large title (One UI style). Closed via the system back gesture.
            Text(
                "Settings",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp),
            )

            SettingsGroup("Grid") {
                StepperRow(
                    Icons.Default.ViewColumn, "Home columns", settings.homeColumns,
                    LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS,
                ) { v -> onUpdate { it.copy(homeColumns = v) } }
                Divider()
                StepperRow(
                    Icons.Default.TableRows, "Home rows", settings.homeRows,
                    LauncherSettings.MIN_ROWS, LauncherSettings.MAX_ROWS,
                ) { v -> onUpdate { it.copy(homeRows = v) } }
                Divider()
                StepperRow(
                    Icons.Default.Apps, "Dock apps", settings.dockCount, 1, LauncherSettings.MAX_DOCK,
                ) { v -> onUpdate { it.copy(dockCount = v) } }
                Divider()
                StepperRow(
                    Icons.Default.GridView, "Drawer columns", settings.drawerColumns,
                    LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS,
                ) { v -> onUpdate { it.copy(drawerColumns = v) } }
            }

            SettingsGroup("Icons & labels") {
                SliderRow(
                    Icons.Default.Image, "Icon size", "${settings.iconSizeDp.roundToInt()} dp",
                    settings.iconSizeDp, LauncherSettings.MIN_ICON_DP..LauncherSettings.MAX_ICON_DP,
                ) { v -> onUpdate { it.copy(iconSizeDp = v) } }
                Divider()
                SliderRow(
                    Icons.Default.FormatSize, "Label size", "${settings.labelSizeSp.roundToInt()} sp",
                    settings.labelSizeSp, LauncherSettings.MIN_LABEL_SP..LauncherSettings.MAX_LABEL_SP,
                ) { v -> onUpdate { it.copy(labelSizeSp = v) } }
                Divider()
                SwitchRow(Icons.AutoMirrored.Filled.Label, "Show labels on home", settings.showHomeLabels) { v ->
                    onUpdate { it.copy(showHomeLabels = v) }
                }
                Divider()
                SwitchRow(Icons.AutoMirrored.Filled.Label, "Show labels in drawer", settings.showDrawerLabels) { v ->
                    onUpdate { it.copy(showDrawerLabels = v) }
                }
                Divider()
                IconPackRow(settings.iconPack, loadIconPacks) { pkg ->
                    onUpdate { it.copy(iconPack = pkg) }
                }
            }

            SettingsGroup("App drawer") {
                ChipRow(
                    icon = Icons.Default.Search,
                    title = "Search bar",
                    options = DrawerSearch.entries,
                    selected = settings.drawerSearch,
                    label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                ) { mode -> onUpdate { it.copy(drawerSearch = mode) } }
                Divider()
                SliderRow(
                    Icons.Default.Opacity,
                    "Background transparency",
                    "${(settings.drawerTransparency * 100).roundToInt()}%",
                    settings.drawerTransparency, 0f..1f,
                ) { v -> onUpdate { it.copy(drawerTransparency = v) } }
            }

            SettingsGroup("Finder") {
                SliderRow(
                    Icons.Default.Opacity,
                    "Background transparency",
                    "${(settings.finderTransparency * 100).roundToInt()}%",
                    settings.finderTransparency, 0f..1f,
                ) { v -> onUpdate { it.copy(finderTransparency = v) } }
            }

            SettingsGroup("Appearance") {
                ChipRow(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                ) { mode -> onUpdate { it.copy(themeMode = mode) } }
                Divider()
                AccentRow(settings.accent) { accent -> onUpdate { it.copy(accent = accent) } }
            }

            SettingsGroup("Gestures") {
                GesturePickerRow(Icons.Default.KeyboardArrowUp, "Swipe up", settings.gestureSwipeUp) { a ->
                    onUpdate { it.copy(gestureSwipeUp = a) }
                }
                Divider()
                GesturePickerRow(Icons.Default.KeyboardArrowDown, "Swipe down", settings.gestureSwipeDown) { a ->
                    onUpdate { it.copy(gestureSwipeDown = a) }
                }
                Divider()
                GesturePickerRow(Icons.Default.TouchApp, "Double tap", settings.gestureDoubleTap) { a ->
                    onUpdate { it.copy(gestureDoubleTap = a) }
                }
                AccessibilityRow(settings)
            }

            if (privateSpaceAvailable) {
                SettingsGroup("Private space") {
                    SwitchRow(Icons.Default.VisibilityOff, "Hide from app drawer", settings.hidePrivateSpace) { v ->
                        onUpdate { it.copy(hidePrivateSpace = v) }
                    }
                    if (!settings.hidePrivateSpace) {
                        Divider()
                        ChipRow(
                            icon = Icons.Default.Tab,
                            title = "Tab bar position",
                            options = PrivateSpacePosition.entries,
                            selected = settings.privateSpacePosition,
                            label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        ) { pos -> onUpdate { it.copy(privateSpacePosition = pos) } }
                    }
                }
            }

            SettingsGroup("System") {
                NavRow(
                    icon = Icons.Default.Home,
                    title = "Default home app",
                    value = "Choose",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_HOME_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------- Layout primitives ----------

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 8.dp),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** Leading accent-tinted icon shared by every row. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
    )
    Spacer(Modifier.width(18.dp))
}

// ---------- Rows ----------

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
    }
}

@Composable
private fun StepperRow(
    icon: ImageVector,
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        FilledTonalIconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
        ) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
        Text(
            "$value",
            modifier = Modifier.width(36.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
        ) { Icon(Icons.Default.Add, contentDescription = "Increase") }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.padding(start = 42.dp),
        )
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, fontSize = 16.sp)
        }
        Row(
            modifier = Modifier.padding(start = 42.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

@Composable
private fun AccentRow(current: AccentColor, onSelect: (AccentColor) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Default.ColorLens)
            Text("Accent color", fontSize = 16.sp)
        }
        Row(
            modifier = Modifier.padding(start = 42.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                val selected = accent == current
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(accent.argb))
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { onSelect(accent) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GesturePickerRow(
    icon: ImageVector,
    title: String,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    NavRow(icon = icon, title = title, value = current.label, onClick = { open = true })

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
    Divider()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(Icons.Default.Accessibility)
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

    NavRow(
        icon = Icons.Default.Palette,
        title = "Icon pack",
        value = currentLabel,
        onClick = { dialogOpen = true },
    )

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
