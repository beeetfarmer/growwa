package com.nl.growwa.data.layout

import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.Container
import kotlinx.coroutines.flow.Flow

/** CRUD over home/dock placements, keyed by Container. */
class LayoutRepository(private val dao: PlacementDao) {

    fun observe(): Flow<List<PlacementEntity>> = dao.observeAll()

    private fun Container.key() = when (this) {
        Container.HOME -> "home"
        Container.DOCK -> "dock"
    }

    suspend fun place(app: AppInfo, container: Container, page: Int, position: Int) {
        dao.upsert(
            PlacementEntity(
                container = container.key(),
                page = page,
                position = position,
                packageName = app.key.packageName,
                className = app.key.className,
                userSerial = app.key.userSerial,
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
}
