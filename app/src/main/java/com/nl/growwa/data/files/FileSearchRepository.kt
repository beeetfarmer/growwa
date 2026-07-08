package com.nl.growwa.data.files

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.nl.growwa.core.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

/**
 * Searches shared storage for files and folders whose name matches a query.
 *
 * The scan is on-demand only (runs while the user is typing in Finder, never in the
 * background) and breadth-first, so shallow — more relevant — matches surface first. It is
 * bounded by [MAX_VISITS] and [limit] and honours coroutine cancellation, so a new keystroke
 * cancels the previous scan. Requires all-files access (MANAGE_EXTERNAL_STORAGE on R+).
 */
class FileSearchRepository(context: Context) {
    private val appContext = context.applicationContext

    /** Whether we currently have permission to walk shared storage. */
    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    suspend fun search(
        query: String,
        excluded: Set<String>,
        limit: Int = 30,
    ): List<FileInfo> {
        val q = query.trim()
        if (q.length < MIN_QUERY || !hasAccess()) return emptyList()
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val excludedNorm = excluded.map { it.trimEnd('/') }.filter { it.isNotBlank() }.toSet()

        return withContext(Dispatchers.IO) {
            val results = ArrayList<FileInfo>(limit)
            val queue = ArrayDeque<File>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && results.size < limit && visited < MAX_VISITS) {
                coroutineContext.ensureActive() // bail out when the query changes / Finder closes
                val children = queue.poll()?.listFiles() ?: continue
                for (child in children) {
                    visited++
                    val name = child.name
                    if (name.startsWith(".")) continue // hidden files / caches
                    if (isSkipped(child.absolutePath, excludedNorm)) continue
                    if (name.contains(q, ignoreCase = true)) {
                        results += FileInfo(
                            name = name,
                            path = child.absolutePath,
                            isDirectory = child.isDirectory,
                            size = if (child.isDirectory) 0L else child.length(),
                            lastModified = child.lastModified(),
                        )
                        if (results.size >= limit) break
                    }
                    if (child.isDirectory) queue.add(child)
                }
            }
            // Folders first, then alphabetical.
            results.sortedWith(
                compareByDescending<FileInfo> { it.isDirectory }.thenBy { it.name.lowercase() },
            )
        }
    }

    private fun isSkipped(path: String, excluded: Set<String>): Boolean {
        // Android/data & Android/obb are inaccessible even with all-files access.
        if (path.contains("/Android/data") || path.contains("/Android/obb")) return true
        val p = path.trimEnd('/')
        return excluded.any { p == it || p.startsWith("$it/") }
    }

    companion object {
        private const val MIN_QUERY = 2
        private const val MAX_VISITS = 60_000
    }
}
