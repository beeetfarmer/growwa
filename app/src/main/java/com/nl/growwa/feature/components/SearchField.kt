package com.nl.growwa.feature.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One UI-style rounded search pill. Used by the app drawer (inline filter) and Finder.
 * Pass a [focusRequester] to auto-focus (Finder); omit it to stay unfocused (drawer).
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Always have a requester so tapping anywhere on the pill focuses the field and shows the keyboard.
    val requester = remember { FocusRequester() }
    val focus = focusRequester ?: requester
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.clickable {
            focus.requestFocus()
            keyboard?.show()
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Box(Modifier.weight(1f).padding(start = 12.dp)) {
                if (query.isEmpty()) {
                    Text(hint, color = onSurface.copy(alpha = 0.6f), fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(onSurface),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
    }
}
