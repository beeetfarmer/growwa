package com.nl.growwa.feature.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Actions available depending on where the app currently lives. */
data class AppMenuActions(
    val onOpen: (() -> Unit)? = null,
    val onAddToDock: (() -> Unit)? = null,
    val onRemove: (() -> Unit)? = null,
    val onAppInfo: (() -> Unit)? = null,
    val onUninstall: (() -> Unit)? = null,
)

@Composable
fun AppContextMenu(
    expanded: Boolean,
    actions: AppMenuActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.onOpen?.let { act ->
            DropdownMenuItem(text = { Text("Open") }, onClick = { onDismiss(); act() })
        }
        actions.onAddToDock?.let { act ->
            DropdownMenuItem(text = { Text("Add to Dock") }, onClick = { onDismiss(); act() })
        }
        actions.onRemove?.let { act ->
            DropdownMenuItem(text = { Text("Remove") }, onClick = { onDismiss(); act() })
        }
        actions.onAppInfo?.let { act ->
            DropdownMenuItem(text = { Text("App info") }, onClick = { onDismiss(); act() })
        }
        actions.onUninstall?.let { act ->
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { onDismiss(); act() })
        }
    }
}
