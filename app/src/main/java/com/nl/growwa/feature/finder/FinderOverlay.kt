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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.nl.growwa.feature.components.AppIconCell
import com.nl.growwa.feature.components.SearchField
import kotlinx.coroutines.delay
import java.io.File

/**
 * Finder (swipe down). Shows most-used apps by default; while searching it shows
 * results grouped into separate Apps / Contacts / Settings sections.
 */
@Composable
fun FinderOverlay(
    topApps: List<AppInfo>,
    searchApps: (String) -> List<AppInfo>,
    searchSettings: (String) -> List<SettingAction>,
    searchContacts: suspend (String) -> List<ContactInfo>,
    searchFiles: suspend (String) -> List<FileInfo>,
    fileAccessGranted: () -> Boolean,
    onLaunchApp: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    backgroundTransparency: Float = 0f,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
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

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun launchApp(app: AppInfo) { keyboard?.hide(); onLaunchApp(app); onDismiss() }
    fun openContact(c: ContactInfo) {
        runCatching {
            val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismiss()
    }
    fun openSetting(s: SettingAction) {
        runCatching {
            context.startActivity(Intent(s.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismiss()
    }
    fun openFile(f: FileInfo) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f - backgroundTransparency))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(16.dp),
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            hint = "Search apps, contacts, settings",
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            if (!searching) {
                if (topApps.isEmpty()) {
                    EmptyHint("Your most-used apps will appear here")
                } else {
                    CategoryBox("Suggestions") { AppsGrid(topApps, ::launchApp) }
                }
                return@Column
            }

            if (appResults.isNotEmpty()) {
                AppsCategory(appResults, ::launchApp)
            }

            if (!hasContacts || contactResults.isNotEmpty()) {
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

            if (settingResults.isNotEmpty()) {
                CategoryBox("Settings") {
                    settingResults.take(MAX_OTHER).forEach { s ->
                        SettingRow(s, onSurface) { openSetting(s) }
                    }
                }
            }

            if (!hasFileAccess) {
                CategoryBox("Files") {
                    Text(
                        "Search files and folders on your device.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    TextButton(onClick = { requestFileAccess() }) { Text("Allow file access") }
                }
            } else if (fileResults.isNotEmpty()) {
                CategoryBox("Files") {
                    fileResults.take(MAX_FILES).forEach { f ->
                        FileRow(f, onSurface) { openFile(f) }
                    }
                }
            }

            if (appResults.isEmpty() && settingResults.isEmpty() &&
                hasContacts && contactResults.isEmpty() &&
                hasFileAccess && fileResults.isEmpty()
            ) {
                EmptyHint("No results")
            }
        }
    }
}

private const val MAX_APPS = 8
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

/** Apps category: shows up to [MAX_APPS], expandable to reveal the rest. */
@Composable
private fun AppsCategory(apps: List<AppInfo>, onClick: (AppInfo) -> Unit) {
    var expanded by remember(apps) { mutableStateOf(false) }
    val shown = if (expanded || apps.size <= MAX_APPS) apps else apps.take(MAX_APPS)
    CategoryBox("Apps") {
        AppsGrid(shown, onClick)
        if (apps.size > MAX_APPS) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (expanded) "Show less" else "Show all ${apps.size}")
            }
        }
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

@Composable
private fun AppsGrid(apps: List<AppInfo>, onClick: (AppInfo) -> Unit) {
    val cols = 4
    apps.chunked(cols).forEach { rowApps ->
        Row(Modifier.fillMaxWidth()) {
            rowApps.forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AppIconCell(
                        icon = app.icon,
                        label = app.label,
                        iconSize = 52.dp,
                        labelSize = 12.sp,
                        showLabel = true,
                        onClick = { onClick(app) },
                        onLongClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            repeat(cols - rowApps.size) { Spacer(Modifier.weight(1f)) }
        }
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
