package com.nl.growwa.data.layout

import com.nl.growwa.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/** Tracks how often each app is launched, for Finder's default suggestions. */
class UsageRepository(private val dao: UsageDao) {

    fun observe(): Flow<List<UsageEntity>> = dao.observeAll()

    suspend fun recordLaunch(app: AppInfo) {
        dao.increment(
            app.key.packageName,
            app.key.className,
            app.key.userSerial,
            System.currentTimeMillis(),
        )
    }

    /** Most-used apps first, resolved against the current app list, limited to [limit]. */
    fun topApps(usage: List<UsageEntity>, apps: List<AppInfo>, limit: Int): List<AppInfo> {
        val byKey = apps.associateBy {
            Triple(it.key.packageName, it.key.className, it.key.userSerial)
        }
        return usage.asSequence()
            .mapNotNull { byKey[Triple(it.packageName, it.className, it.userSerial)] }
            .take(limit)
            .toList()
    }
}
