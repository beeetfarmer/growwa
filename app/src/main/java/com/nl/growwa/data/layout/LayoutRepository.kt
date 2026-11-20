package com.nl.growwa.data.layout

import android.content.ComponentName
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.Container
import kotlinx.coroutines.flow.Flow

/** CRUD over home/dock placements and folders. */
class LayoutRepository(
    private val dao: PlacementDao,
    private val folderDao: FolderDao,
) {

    fun observe(): Flow<List<PlacementEntity>> = dao.observeAll()
    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    private fun Container.key() = when (this) {
        Container.HOME -> "home"
        Container.DOCK -> "dock"
    }

    fun folderContainer(folderId: Long) = "folder_$folderId"

    suspend fun place(app: AppInfo, container: Container, page: Int, position: Int) =
        placeApp(app, container.key(), page, position)

    private suspend fun placeApp(app: AppInfo, container: String, page: Int, position: Int) {
        dao.upsert(
            PlacementEntity(
                container = container,
                page = page,
                position = position,
                packageName = app.key.packageName,
                className = app.key.className,
                userSerial = app.key.userSerial,
                kind = "app",
            )
        )
    }

    suspend fun removeSlot(container: Container, page: Int, position: Int) =
        dao.removeSlot(container.key(), page, position)

    suspend fun removeApp(app: AppInfo) =
        dao.removeApp(app.key.packageName, app.key.className, app.key.userSerial)

    /** First free slot in a container/page within [capacity], or null if full. */
    suspend fun firstFreeSlot(container: Container, page: Int, capacity: Int): Int? {
        val taken = dao.getAll()
            .filter { it.container == container.key() && it.page == page }
            .map { it.position }
            .toSet()
        return (0 until capacity).firstOrNull { it !in taken }
    }

    /**
     * Place [app] on home at [position]; if that cell is taken, fall back to the first free slot.
     * Returns false if the page is full.
     */
    suspend fun placeOnHomeAt(app: AppInfo, page: Int, position: Int, capacity: Int): Boolean {
        val taken = dao.getAll()
            .filter { it.container == "home" && it.page == page }
            .map { it.position }.toSet()
        val slot = if (position in 0 until capacity && position !in taken) {
            position
        } else {
            (0 until capacity).firstOrNull { it !in taken } ?: return false
        }
        placeApp(app, "home", page, slot)
        return true
    }

    /** Insert [app] into the dock at [index], shifting the rest; false if the dock is full. */
    suspend fun insertIntoDock(app: AppInfo, index: Int, max: Int): Boolean {
        val items = dao.itemsIn("dock").sortedBy { it.position }.toMutableList()
        if (items.size >= max) return false
        val entity = PlacementEntity(
            container = "dock", page = 0, position = 0,
            packageName = app.key.packageName,
            className = app.key.className,
            userSerial = app.key.userSerial,
            kind = "app",
        )
        items.add(index.coerceIn(0, items.size), entity)
        dao.replaceContainer(
            "dock",
            items.mapIndexed { j, p -> p.copy(container = "dock", page = 0, position = j) },
        )
        return true
    }

    // ---------- Move / swap (drag & drop) ----------

    /** Move the item at [from] to [to]; if [to] is occupied, the two swap places. */
    suspend fun moveOrSwap(
        fromContainer: String, fromPage: Int, fromPos: Int,
        toContainer: String, toPage: Int, toPos: Int,
    ) {
        if (fromContainer == toContainer && fromPage == toPage && fromPos == toPos) return
        val a = dao.getSlot(fromContainer, fromPage, fromPos) ?: return
        val b = dao.getSlot(toContainer, toPage, toPos)
        dao.removeSlot(fromContainer, fromPage, fromPos)
        if (b != null) dao.removeSlot(toContainer, toPage, toPos)
        dao.upsert(a.copy(container = toContainer, page = toPage, position = toPos))
        if (b != null) {
            dao.upsert(b.copy(container = fromContainer, page = fromPage, position = fromPos))
        }
    }

    // ---------- Dock reordering ----------

    /** Move the dock item at [fromPos] to insertion index [toIndex], shifting the rest. */
    suspend fun reorderDock(fromPos: Int, toIndex: Int) {
        val items = dao.itemsIn("dock").sortedBy { it.position }.toMutableList()
        val i = items.indexOfFirst { it.position == fromPos }
        if (i < 0) return
        val moving = items.removeAt(i)
        // toIndex is an insertion point in the original list; account for the removed item.
        val idx = (if (i < toIndex) toIndex - 1 else toIndex).coerceIn(0, items.size)
        items.add(idx, moving)
        dao.replaceContainer("dock", items.mapIndexed { j, p -> p.copy(page = 0, position = j) })
    }

    /** Move an item from another container into the dock at [toIndex]; false if the dock is full. */
    suspend fun moveIntoDock(
        fromContainer: String, fromPage: Int, fromPos: Int, toIndex: Int, max: Int,
    ): Boolean {
        val src = dao.getSlot(fromContainer, fromPage, fromPos) ?: return false
        val items = dao.itemsIn("dock").sortedBy { it.position }.toMutableList()
        if (items.size >= max) return false
        val idx = toIndex.coerceIn(0, items.size)
        items.add(idx, src)
        dao.moveSlotIntoContainer(
            fromContainer, fromPage, fromPos,
            "dock",
            items.mapIndexed { j, p -> p.copy(container = "dock", page = 0, position = j) },
        )
        return true
    }

    // ---------- Folders ----------

    /** Create a folder containing [first] then [second], placed where [second] was. */
    suspend fun createFolderWith(
        first: AppInfo, second: AppInfo,
        container: String, page: Int, position: Int,
        firstFrom: Triple<String, Int, Int>,
        defaultName: String = "Folder",
    ): Long {
        val id = folderDao.insert(FolderEntity(name = defaultName))
        val fc = folderContainer(id)
        placeApp(first, fc, 0, 0)
        placeApp(second, fc, 0, 1)
        // Remove both source app placements, then drop the folder at the target slot.
        dao.removeSlot(firstFrom.first, firstFrom.second, firstFrom.third)
        dao.removeSlot(container, page, position)
        dao.upsert(
            PlacementEntity(
                container = container, page = page, position = position,
                kind = "folder", folderId = id,
            )
        )
        return id
    }

    /** Add [app] (currently at [from]) into folder [folderId]. */
    suspend fun addToFolder(folderId: Long, app: AppInfo, from: Triple<String, Int, Int>) {
        val existing = dao.itemsIn(folderContainer(folderId)).map { it.position }.toSet()
        val slot = generateSequence(0) { it + 1 }.first { it !in existing }
        placeApp(app, folderContainer(folderId), 0, slot)
        dao.removeSlot(from.first, from.second, from.third)
    }

    /** Create an empty folder at the first free home slot on [page]. */
    suspend fun createEmptyFolder(name: String, page: Int, capacity: Int): Long? {
        val taken = dao.getAll()
            .filter { it.container == "home" && it.page == page }
            .map { it.position }.toSet()
        val slot = (0 until capacity).firstOrNull { it !in taken } ?: return null
        val id = folderDao.insert(FolderEntity(name = name))
        dao.upsert(
            PlacementEntity(container = "home", page = page, position = slot, kind = "folder", folderId = id)
        )
        return id
    }

    /** Move [app] from folder [folderId] onto a free home slot (dissolving the folder if needed). */
    suspend fun takeOutToHome(folderId: Long, app: AppInfo, capacity: Int): Boolean {
        val fp = dao.folderPlacement(folderId)
        val page = if (fp?.container == "home") fp.page else 0
        val taken = dao.getAll()
            .filter { it.container == "home" && it.page == page }
            .map { it.position }.toSet()
        val slot = (0 until capacity).firstOrNull { it !in taken } ?: return false
        placeApp(app, "home", page, slot)
        removeFromFolder(folderId, app)
        return true
    }

    suspend fun renameFolder(folderId: Long, name: String) =
        folderDao.rename(folderId, name.ifBlank { "Folder" })

    /**
     * Remove [app] from folder [folderId]. If only one app remains, the folder dissolves
     * back into that app at the folder's slot; if none remain, the folder is removed.
     */
    suspend fun removeFromFolder(folderId: Long, app: AppInfo) {
        val fc = folderContainer(folderId)
        val items = dao.itemsIn(fc)
        val target = items.firstOrNull {
            it.packageName == app.key.packageName &&
                it.className == app.key.className &&
                it.userSerial == app.key.userSerial
        } ?: return
        dao.removeSlot(fc, target.page, target.position)
        val remaining = dao.itemsIn(fc)
        if (remaining.size <= 1) dissolveFolder(folderId, remaining.firstOrNull())
    }

    /** Delete a folder entirely (and its contents). */
    suspend fun deleteFolder(folderId: Long) {
        dao.folderPlacement(folderId)?.let { dao.removeSlot(it.container, it.page, it.position) }
        dao.clearContainer(folderContainer(folderId))
        folderDao.delete(folderId)
    }

    // ---------- Widgets ----------

    suspend fun placeWidget(
        provider: ComponentName, appWidgetId: Int, page: Int, position: Int, spanX: Int, spanY: Int,
    ) {
        dao.upsert(
            PlacementEntity(
                container = "home", page = page, position = position,
                packageName = provider.packageName, className = provider.className,
                kind = "widget", appWidgetId = appWidgetId, spanX = spanX, spanY = spanY,
            )
        )
    }

    /** First top-left slot where a [spanX] x [spanY] block fits without overlapping anything. */
    suspend fun findWidgetSlot(page: Int, cols: Int, rows: Int, spanX: Int, spanY: Int): Int? {
        val occ = HashSet<Int>()
        dao.getAll().filter { it.container == "home" && it.page == page }.forEach { p ->
            if (p.kind == "widget") {
                val r0 = p.position / cols
                val c0 = p.position % cols
                for (rr in r0 until r0 + p.spanY) for (cc in c0 until c0 + p.spanX) occ.add(rr * cols + cc)
            } else {
                occ.add(p.position)
            }
        }
        for (r in 0..rows - spanY) for (c in 0..cols - spanX) {
            var ok = true
            blockScan@ for (rr in r until r + spanY) for (cc in c until c + spanX) {
                if ((rr * cols + cc) in occ) { ok = false; break@blockScan }
            }
            if (ok) return r * cols + c
        }
        return null
    }

    suspend fun reorderHomePage(from: Int, to: Int) = dao.reorderHomePages(from, to)

    suspend fun removeWidget(appWidgetId: Int): Boolean {
        val p = dao.widgetPlacement(appWidgetId) ?: return false
        dao.removeSlot(p.container, p.page, p.position)
        return true
    }

    /** Re-position and/or re-span an existing widget (position is part of the key, so re-insert). */
    suspend fun resizeWidget(appWidgetId: Int, page: Int, position: Int, spanX: Int, spanY: Int) {
        val p = dao.widgetPlacement(appWidgetId) ?: return
        if (p.page == page && p.position == position && p.spanX == spanX && p.spanY == spanY) return
        dao.removeSlot(p.container, p.page, p.position)
        dao.upsert(p.copy(page = page, position = position, spanX = spanX, spanY = spanY))
    }

    private suspend fun dissolveFolder(folderId: Long, lone: PlacementEntity?) {
        val placement = dao.folderPlacement(folderId)
        dao.clearContainer(folderContainer(folderId))
        folderDao.delete(folderId)
        if (placement != null && lone != null) {
            dao.upsert(
                lone.copy(
                    container = placement.container,
                    page = placement.page,
                    position = placement.position,
                    kind = "app",
                    folderId = 0,
                )
            )
        } else if (placement != null) {
            dao.removeSlot(placement.container, placement.page, placement.position)
        }
    }
}
