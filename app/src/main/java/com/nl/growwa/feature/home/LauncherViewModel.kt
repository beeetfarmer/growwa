package com.nl.growwa.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nl.growwa.core.di.AppContainer
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.AppItem
import com.nl.growwa.core.model.Container
import com.nl.growwa.core.model.ContactInfo
import com.nl.growwa.core.model.FolderItem
import com.nl.growwa.core.model.HomeItem
import com.nl.growwa.core.model.IconPackInfo
import com.nl.growwa.core.model.SettingAction
import com.nl.growwa.core.model.WidgetPlacement
import android.appwidget.AppWidgetProviderInfo
import com.nl.growwa.data.widgets.GrowwaWidgetHost
import com.nl.growwa.data.apps.AppRepository
import com.nl.growwa.data.contacts.ContactsRepository
import com.nl.growwa.data.iconpack.IconPackRepository
import com.nl.growwa.data.layout.FolderEntity
import com.nl.growwa.data.layout.LayoutRepository
import com.nl.growwa.data.layout.PlacementEntity
import com.nl.growwa.data.layout.UsageRepository
import com.nl.growwa.data.privatespace.PrivateSpaceManager
import com.nl.growwa.data.privatespace.PrivateSpaceState
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
    val home: Map<Int, Map<Int, HomeItem>> = emptyMap(), // page -> position -> item
    val dock: Map<Int, HomeItem> = emptyMap(),           // position -> item
    val widgets: Map<Int, List<WidgetPlacement>> = emptyMap(), // page -> widgets
)

/** Identifies a slot for drag & drop. container is "home" or "dock". */
data class DropSlot(val container: String, val page: Int, val position: Int)

data class LauncherUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val allApps: List<AppInfo> = emptyList(),
    val layout: ResolvedLayout = ResolvedLayout(),
    val topApps: List<AppInfo> = emptyList(),
    val privateSpace: PrivateSpaceState = PrivateSpaceState(),
) {
    /** Drawer's main grid: everything outside the private profile. */
    val mainApps: List<AppInfo> get() = allApps.filter { !it.isPrivate }

    /** Apps inside the private profile (empty while locked). */
    val privateApps: List<AppInfo> get() = allApps.filter { it.isPrivate }
}

class LauncherViewModel(
    private val appRepository: AppRepository,
    private val layoutRepository: LayoutRepository,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val contactsRepository: ContactsRepository,
    private val settingsActionRepository: SettingsActionRepository,
    private val iconPackRepository: IconPackRepository,
    private val privateSpaceManager: PrivateSpaceManager,
    val widgetHost: GrowwaWidgetHost,
) : ViewModel() {

    // Placements + folders combined first, so the main combine stays within 5 args.
    private val layoutInputs = combine(
        layoutRepository.observe(),
        layoutRepository.observeFolders(),
    ) { placements, folders -> placements to folders }

    val uiState: StateFlow<LauncherUiState> = combine(
        appRepository.apps,
        settingsRepository.settings,
        layoutInputs,
        usageRepository.observe(),
        privateSpaceManager.state,
    ) { apps, settings, layout, usage, privateSpace ->
        LauncherUiState(
            settings = settings,
            allApps = apps,
            layout = resolve(apps, layout.first, layout.second),
            topApps = usageRepository.topApps(usage, apps.filter { !it.isPrivate }, limit = FINDER_TOP_COUNT),
            privateSpace = privateSpace,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    private fun resolve(
        apps: List<AppInfo>,
        placements: List<PlacementEntity>,
        folders: List<FolderEntity>,
    ): ResolvedLayout {
        val byKey = apps.associateBy { Triple(it.key.packageName, it.key.className, it.key.userSerial) }
        val folderName = folders.associate { it.id to it.name }
        // Apps inside each folder, ordered by their slot position.
        val folderApps = HashMap<Long, MutableList<Pair<Int, AppInfo>>>()
        for (p in placements) {
            if (!p.container.startsWith("folder_")) continue
            val id = p.container.removePrefix("folder_").toLongOrNull() ?: continue
            val app = byKey[Triple(p.packageName, p.className, p.userSerial)] ?: continue
            folderApps.getOrPut(id) { mutableListOf() }.add(p.position to app)
        }

        fun itemFor(p: PlacementEntity): HomeItem? = when (p.kind) {
            "folder" -> {
                val list = folderApps[p.folderId].orEmpty().sortedBy { it.first }.map { it.second }
                FolderItem(p.folderId, folderName[p.folderId] ?: "Folder", list)
            }
            else -> byKey[Triple(p.packageName, p.className, p.userSerial)]?.let { AppItem(it) }
        }

        val home = HashMap<Int, HashMap<Int, HomeItem>>()
        val dock = HashMap<Int, HomeItem>()
        val widgets = HashMap<Int, MutableList<WidgetPlacement>>()
        var maxPage = 0
        for (p in placements) {
            if (p.container == "home" && p.kind == "widget") {
                widgets.getOrPut(p.page) { mutableListOf() }.add(
                    WidgetPlacement(
                        appWidgetId = p.appWidgetId,
                        providerPackage = p.packageName,
                        providerClass = p.className,
                        page = p.page,
                        position = p.position,
                        spanX = p.spanX,
                        spanY = p.spanY,
                    )
                )
                if (p.page > maxPage) maxPage = p.page
                continue
            }
            val item = when (p.container) {
                "home", "dock" -> itemFor(p) ?: continue
                else -> continue
            }
            when (p.container) {
                "home" -> {
                    home.getOrPut(p.page) { HashMap() }[p.position] = item
                    if (p.page > maxPage) maxPage = p.page
                }
                "dock" -> dock[p.position] = item
            }
        }
        return ResolvedLayout(pageCount = maxPage + 1, home = home, dock = dock, widgets = widgets)
    }

    private fun itemAt(layout: ResolvedLayout, slot: DropSlot): HomeItem? = when (slot.container) {
        "home" -> layout.home[slot.page]?.get(slot.position)
        "dock" -> layout.dock[slot.position]
        else -> null
    }

    /** True if [slot]'s home cell falls inside any widget's span on that page. */
    private fun coveredByWidget(layout: ResolvedLayout, slot: DropSlot): Boolean {
        val cols = uiState.value.settings.homeColumns
        val r = slot.position / cols
        val c = slot.position % cols
        return layout.widgets[slot.page].orEmpty().any { w ->
            val r0 = w.position / cols
            val c0 = w.position % cols
            r in r0 until r0 + w.spanY && c in c0 until c0 + w.spanX
        }
    }

    fun launch(app: AppInfo) {
        appRepository.launch(app)
        viewModelScope.launch { usageRepository.recordLaunch(app) }
    }

    fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app)

    // --- Private Space ---
    fun lockPrivateSpace() = privateSpaceManager.setLocked(true)
    fun unlockPrivateSpace() = privateSpaceManager.setLocked(false)

    // --- Finder search ---
    fun searchApps(query: String): List<AppInfo> {
        val q = query.trim()
        // Private-profile apps never appear in Finder (extra guard; they're absent while locked).
        return uiState.value.allApps.filter {
            !it.isPrivate && it.label.contains(q, ignoreCase = true)
        }
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
        val capacity = uiState.value.settings.dockCount.coerceAtMost(DOCK_MAX)
        val slot = layoutRepository.firstFreeSlot(Container.DOCK, 0, capacity) ?: return@launch
        layoutRepository.place(app, Container.DOCK, 0, slot)
    }

    /** Drop an app dragged out of the drawer onto a home cell or dock slot. */
    fun dropFromDrawer(app: AppInfo, to: DropSlot) = viewModelScope.launch {
        val s = uiState.value.settings
        when (to.container) {
            "dock" -> {
                val dockMax = s.dockCount.coerceAtMost(DOCK_MAX)
                layoutRepository.insertIntoDock(app, to.position, dockMax)
            }
            "home" -> {
                if (coveredByWidget(uiState.value.layout, to)) return@launch
                layoutRepository.placeOnHomeAt(app, to.page, to.position, s.homeColumns * s.homeRows)
            }
        }
    }

    // --- Drag & drop ---
    fun onDrop(from: DropSlot, to: DropSlot, makeFolder: Boolean) = viewModelScope.launch {
        if (from == to) return@launch
        val layout = uiState.value.layout
        val fromItem = itemAt(layout, from) ?: return@launch

        // Dock is a reorderable strip: drops into it shift items rather than swap/fold.
        if (to.container == "dock") {
            val dockMax = uiState.value.settings.dockCount.coerceAtMost(DOCK_MAX)
            if (from.container == "dock") {
                layoutRepository.reorderDock(from.position, to.position)
            } else {
                layoutRepository.moveIntoDock(from.container, from.page, from.position, to.position, dockMax)
            }
            return@launch
        }

        // Don't drop an item onto a cell already covered by a widget (would overlap it).
        if (to.container == "home" && coveredByWidget(layout, to)) return@launch

        val toItem = itemAt(layout, to)
        val fromTriple = Triple(from.container, from.page, from.position)
        when (fromItem) {
            is AppItem -> when (toItem) {
                null -> layoutRepository.moveOrSwap(
                    from.container, from.page, from.position, to.container, to.page, to.position,
                )
                is AppItem ->
                    // Quick drop swaps the two apps; a deliberate dwell makes a folder.
                    if (makeFolder) {
                        layoutRepository.createFolderWith(
                            first = toItem.app, second = fromItem.app,
                            container = to.container, page = to.page, position = to.position,
                            firstFrom = fromTriple,
                        )
                    } else {
                        layoutRepository.moveOrSwap(
                            from.container, from.page, from.position, to.container, to.page, to.position,
                        )
                    }
                is FolderItem -> layoutRepository.addToFolder(toItem.id, fromItem.app, fromTriple)
            }
            is FolderItem -> layoutRepository.moveOrSwap(
                from.container, from.page, from.position, to.container, to.page, to.position,
            )
        }
    }

    // --- Folders ---
    fun createFolder(page: Int) = viewModelScope.launch {
        val s = uiState.value.settings
        layoutRepository.createEmptyFolder("Folder", page, s.homeColumns * s.homeRows)
    }

    fun renameFolder(id: Long, name: String) = viewModelScope.launch {
        layoutRepository.renameFolder(id, name)
    }

    fun takeOutOfFolder(folderId: Long, app: AppInfo) = viewModelScope.launch {
        val s = uiState.value.settings
        layoutRepository.takeOutToHome(folderId, app, s.homeColumns * s.homeRows)
    }

    fun deleteFolder(id: Long) = viewModelScope.launch {
        layoutRepository.deleteFolder(id)
    }

    // --- Widgets ---
    fun addWidget(provider: AppWidgetProviderInfo, appWidgetId: Int, page: Int) = viewModelScope.launch {
        val s = uiState.value.settings
        fun cells(minDp: Int) = ((minDp + 30) / 70).coerceAtLeast(1)
        val spanX = cells(provider.minWidth).coerceIn(1, s.homeColumns)
        val spanY = cells(provider.minHeight).coerceIn(1, s.homeRows)
        val slot = layoutRepository.findWidgetSlot(page, s.homeColumns, s.homeRows, spanX, spanY)
        if (slot == null) {
            widgetHost.deleteId(appWidgetId) // no room; release the id
            return@launch
        }
        layoutRepository.placeWidget(provider.provider, appWidgetId, page, slot, spanX, spanY)
    }

    fun removeWidget(appWidgetId: Int) = viewModelScope.launch {
        if (layoutRepository.removeWidget(appWidgetId)) widgetHost.deleteId(appWidgetId)
    }

    fun resizeWidget(appWidgetId: Int, page: Int, position: Int, spanX: Int, spanY: Int) =
        viewModelScope.launch {
            layoutRepository.resizeWidget(appWidgetId, page, position, spanX, spanY)
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
                container.privateSpaceManager,
                container.widgetHost,
            ) as T
        }
    }

    companion object {
        const val FINDER_TOP_COUNT = 5
        const val DOCK_MAX = 6
    }
}
