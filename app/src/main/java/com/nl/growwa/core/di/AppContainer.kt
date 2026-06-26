package com.nl.growwa.core.di

import android.content.Context
import com.nl.growwa.data.apps.AppRepository
import com.nl.growwa.data.contacts.ContactsRepository
import com.nl.growwa.data.iconpack.IconPackRepository
import com.nl.growwa.data.layout.LayoutDatabase
import com.nl.growwa.data.layout.LayoutRepository
import com.nl.growwa.data.layout.UsageRepository
import com.nl.growwa.data.settings.SettingsRepository
import com.nl.growwa.data.settingsindex.SettingsActionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Manual DI container: holds app-scoped singletons. Created once in GrowwaApp. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())

    val settingsRepository = SettingsRepository(context)
    val iconPackRepository = IconPackRepository(context)
    val appRepository = AppRepository(context, appScope, iconPackRepository, settingsRepository)
    val contactsRepository = ContactsRepository(context)
    val settingsActionRepository = SettingsActionRepository()

    private val layoutDb = LayoutDatabase.build(context)
    val layoutRepository = LayoutRepository(layoutDb.placementDao())
    val usageRepository = UsageRepository(layoutDb.usageDao())
}
