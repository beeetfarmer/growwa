package com.nl.growwa.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nl.growwa.core.di.AppContainer
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.Container
import com.nl.growwa.core.model.ContactInfo
import com.nl.growwa.core.model.IconPackInfo
import com.nl.growwa.core.model.SettingAction
import com.nl.growwa.data.apps.AppRepository
import com.nl.growwa.data.contacts.ContactsRepository
import com.nl.growwa.data.iconpack.IconPackRepository
import com.nl.growwa.data.layout.LayoutRepository
import com.nl.growwa.data.layout.PlacementEntity
import com.nl.growwa.data.layout.UsageRepository
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.data.settings.SettingsRepository
import com.nl.growwa.data.settingsindex.SettingsActionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resolved home/dock layout ready for rendering. */
data class ResolvedLayout(
    val pageCount: Int = 1,
    val home: Map<Int, Map<Int, AppInfo>> = emptyMap(), // page -> position -> app
    val dock: Map<Int, AppInfo> = emptyMap(),           // position -> app
)

data class LauncherUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val allApps: List<AppInfo> = emptyList(),
    val layout: ResolvedLayout = ResolvedLayout(),
    val topApps: List<AppInfo> = emptyList(),
)

class LauncherViewModel(
    private val appRepository: AppRepository,
    private val layoutRepository: LayoutRepository,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val contactsRepository: ContactsRepository,
    private val settingsActionRepository: SettingsActionRepository,
    private val iconPackRepository: IconPackRepository,
) : ViewModel() {

    val uiState: StateFlow<LauncherUiState> = combine(
        appRepository.apps,
        settingsRepository.settings,
        layoutRepository.observe(),
        usageRepository.observe(),
    ) { apps, settings, placements, usage ->
        LauncherUiState(
            settings = settings,
            allApps = apps,
            layout = resolve(apps, placements),
            topApps = usageRepository.topApps(usage, apps, limit = FINDER_TOP_COUNT),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    private fun resolve(apps: List<AppInfo>, placements: List<PlacementEntity>): ResolvedLayout {
        val byKey = apps.associateBy { Triple(it.key.packageName, it.key.className, it.key.userSerial) }
        val home = HashMap<Int, HashMap<Int, AppInfo>>()
        val dock = HashMap<Int, AppInfo>()
        var maxPage = 0
        for (p in placements) {
            val app = byKey[Triple(p.packageName, p.className, p.userSerial)] ?: continue
            when (p.container) {
                "home" -> {
                    home.getOrPut(p.page) { HashMap() }[p.position] = app
                    if (p.page > maxPage) maxPage = p.page
                }
                "dock" -> dock[p.position] = app
            }
        }
        return ResolvedLayout(pageCount = maxPage + 1, home = home, dock = dock)
    }

    fun launch(app: AppInfo) {
        appRepository.launch(app)
        viewModelScope.launch { usageRepository.recordLaunch(app) }
    }

    fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app)

    // --- Finder search ---
    fun searchApps(query: String): List<AppInfo> {
        val q = query.trim()
        return uiState.value.allApps.filter { it.label.contains(q, ignoreCase = true) }
    }

    suspend fun searchContacts(query: String): List<ContactInfo> =
        contactsRepository.search(query)

    fun searchSettings(query: String): List<SettingAction> =
        settingsActionRepository.search(query)

    suspend fun loadIconPacks(): List<IconPackInfo> = iconPackRepository.availablePacks()

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    fun addToHome(app: AppInfo, page: Int) = viewModelScope.launch {
        val s = uiState.value.settings
        val capacity = s.homeColumns * s.homeRows
        val slot = layoutRepository.firstFreeSlot(Container.HOME, page, capacity) ?: return@launch
        layoutRepository.place(app, Container.HOME, page, slot)
    }

    fun addToDock(app: AppInfo) = viewModelScope.launch {
        val capacity = uiState.value.settings.dockCount
        val slot = layoutRepository.firstFreeSlot(Container.DOCK, 0, capacity) ?: return@launch
        layoutRepository.place(app, Container.DOCK, 0, slot)
    }

    fun removeFromHome(page: Int, position: Int) = viewModelScope.launch {
        layoutRepository.removeSlot(Container.HOME, page, position)
    }

    fun removeFromDock(position: Int) = viewModelScope.launch {
        layoutRepository.removeSlot(Container.DOCK, 0, position)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(
                container.appRepository,
                container.layoutRepository,
                container.settingsRepository,
                container.usageRepository,
                container.contactsRepository,
                container.settingsActionRepository,
                container.iconPackRepository,
            ) as T
        }
    }

    companion object {
        const val FINDER_TOP_COUNT = 5
    }
}
