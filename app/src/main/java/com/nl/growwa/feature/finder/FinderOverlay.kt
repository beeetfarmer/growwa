package com.nl.growwa.feature.finder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.ContactInfo
import com.nl.growwa.core.model.FileInfo
import com.nl.growwa.core.model.SettingAction
import com.nl.growwa.feature.components.AppSuggestionsCard
import kotlinx.coroutines.delay
import java.io.File

/**
 * Finder (swipe down). A search pill, recent-query chips, and an app-suggestions card
 * float on the blurred wallpaper. Contacts, settings, and files still appear while searching.
 */
@Composable
fun FinderOverlay(
    topApps: List<AppInfo>,
    recentSearches: List<String>,
    searchApps: (String) -> List<AppInfo>,
    searchSettings: (String) -> List<SettingAction>,
    searchContacts: suspend (String) -> List<ContactInfo>,
    searchFiles: suspend (String) -> List<FileInfo>,
    fileAccessGranted: () -> Boolean,
    onLaunchApp: (AppInfo) -> Unit,
    onRememberSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    backgroundTransparency: Float = 0f,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var keyboardOpen by remember { mutableStateOf(true) }
    val searching = query.isNotBlank()
    val onSurface = MaterialTheme.colorScheme.onSurface

    var hasContacts by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasContacts = granted }

    var hasFileAccess by remember { mutableStateOf(fileAccessGranted()) }
    // Re-check after returning from the all-files-access system screen.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasFileAccess = fileAccessGranted() }
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasFileAccess = granted }

    val appResults = remember(query, topApps) { if (searching) searchApps(query) else topApps }
    val settingResults = remember(query) { if (searching) searchSettings(query) else emptyList() }
    val contactResults by produceState(emptyList<ContactInfo>(), query, hasContacts) {
        value = if (searching && hasContacts) searchContacts(query) else emptyList()
    }
    // Debounced so it only scans ~250ms after typing stops; each new key cancels the last scan.
    val fileResults by produceState(emptyList<FileInfo>(), query, hasFileAccess) {
        value = emptyList()
        if (searching && hasFileAccess) {
            delay(250)
            value = searchFiles(query)
        }
    }

    fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pkg = Uri.parse("package:${context.packageName}")
            runCatching {
                allFilesLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
                )
            }.onFailure {
                runCatching {
                    allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyStorageLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun rememberIfTyped() {
        if (query.isNotBlank()) onRememberSearch(query)
    }
    fun launchApp(app: AppInfo) {
        rememberIfTyped()
        keyboard?.hide()
        onLaunchApp(app)
        onDismiss()
    }
    fun openContact(c: ContactInfo) {
        rememberIfTyped()
        runCatching {
            val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismiss()
    }
    fun openSetting(s: SettingAction) {
        rememberIfTyped()
        runCatching {
            context.startActivity(Intent(s.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismiss()
    }
    fun openFile(f: FileInfo) {
        rememberIfTyped()
        runCatching {
            val file = File(f.path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = if (f.isDirectory) {
                "vnd.android.document/directory"
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            }
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(
                Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        onDismiss()
    }

    val scrim = ((1f - backgroundTransparency) * 0.2f).coerceIn(0f, 0.35f)
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        // Eat taps on the empty wallpaper so they don't launch icons underneath.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            FinderSearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = { rememberIfTyped(); focusRequester.requestFocus() },
                focusRequester = focusRequester,
            )
            RecentChips(
                queries = recentSearches,
                onPick = { query = it },
                onClear = onClearSearches,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
            ) {
                val suggested = if (searching) appResults else topApps
                if (suggested.isNotEmpty()) {
                    AppSuggestionsCard(suggested.take(MAX_APPS), ::launchApp)
                } else if (!searching) {
                    EmptyHint("Your most-used apps will appear here")
                }

                if (searching && (!hasContacts || contactResults.isNotEmpty())) {
                CategoryBox("Contacts") {
                    if (!hasContacts) {
                        TextButton(onClick = {
                            permLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                        }) { Text("Allow contacts access") }
                    } else {
                        contactResults.take(MAX_OTHER).forEach { c ->
                            ContactRow(c, onSurface) { openContact(c) }
                        }
                    }
                }
            }

            if (searching && settingResults.isNotEmpty()) {
                CategoryBox("Settings") {
                    settingResults.take(MAX_OTHER).forEach { s ->
                        SettingRow(s, onSurface) { openSetting(s) }
                    }
                }
            }

            if (searching && !hasFileAccess) {
                CategoryBox("Files") {
                    Text(
                        "Search files and folders on your device.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    TextButton(onClick = { requestFileAccess() }) { Text("Allow file access") }
                }
            } else if (searching && fileResults.isNotEmpty()) {
                CategoryBox("Files") {
                    fileResults.take(MAX_FILES).forEach { f ->
                        FileRow(f, onSurface) { openFile(f) }
                    }
                }
            }

            if (searching && appResults.isEmpty() && settingResults.isEmpty() &&
                hasContacts && contactResults.isEmpty() &&
                hasFileAccess && fileResults.isEmpty()
            ) {
                EmptyHint("No results")
            }
            }
            FinderBottomBar(
                onKeyboard = {
                    if (keyboardOpen) {
                        keyboard?.hide()
                        keyboardOpen = false
                    } else {
                        focusRequester.requestFocus()
                        keyboard?.show()
                        keyboardOpen = true
                    }
                },
                onSettings = onOpenSettings,
            )
        }
    }
}

private const val MAX_APPS = 10
private const val MAX_OTHER = 6
private const val MAX_FILES = 10

/** An outlined, titled container for one Finder result category. */
@Composable
private fun CategoryBox(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    )
}

private val Ink = Color(0xFF1C1C1E)

@Composable
private fun FinderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 17.sp),
            cursorBrush = SolidColor(Color(0xFF3478F6)),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search", color = Ink.copy(alpha = 0.4f), fontSize = 17.sp)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear",
                tint = Ink.copy(alpha = 0.55f),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(20.dp)
                    .clickable { onQueryChange("") },
            )
        }
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .width(1.dp)
                .height(18.dp)
                .background(Ink.copy(alpha = 0.18f)),
        )
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            tint = Ink.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp).clickable(onClick = onSearch),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentChips(
    queries: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (queries.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        queries.forEach { q ->
            Text(
                q,
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.55f))
                    .clickable { onPick(q) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Icon(
            Icons.Default.Delete,
            contentDescription = "Clear recent searches",
            tint = Ink.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.55f))
                .clickable(onClick = onClear)
                .padding(6.dp)
                .size(16.dp),
        )
    }
}

@Composable
private fun FinderBottomBar(onKeyboard: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinderBarButton(Icons.Default.Keyboard, "Keyboard", onKeyboard)
        FinderBarButton(Icons.Default.Settings, "Settings", onSettings)
    }
}

@Composable
private fun FinderBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        Text(label, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ContactRow(contact: ContactInfo, textColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (contact.photoUri != null) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = textColor.copy(alpha = 0.7f))
            }
        }
        Text(
            contact.name,
            color = textColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun FileRow(file: FileInfo, textColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
            )
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                file.name,
                color = textColor,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                prettyPath(file.path),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shows a file's parent folder relative to internal storage, e.g. "Internal storage/Download". */
private fun prettyPath(path: String): String {
    val root = Environment.getExternalStorageDirectory().absolutePath
    val parent = File(path).parent ?: path
    val rel = if (parent.startsWith(root)) parent.removePrefix(root) else parent
    return "Internal storage$rel"
}

@Composable
private fun SettingRow(setting: SettingAction, textColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = textColor.copy(alpha = 0.7f))
        }
        Text(
            setting.label,
            color = textColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
