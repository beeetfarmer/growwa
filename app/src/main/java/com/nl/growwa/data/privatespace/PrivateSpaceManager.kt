package com.nl.growwa.data.privatespace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Observable Private Space status. */
data class PrivateSpaceState(
    val available: Boolean = false,
    val locked: Boolean = false,
)

/**
 * Detects and controls the Android 15+ Private Space profile.
 * Feature-gated: on anything below API 35 this is inert and reports unavailable.
 */
class PrivateSpaceManager(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager

    /** Private Space only exists on Android 15 (API 35)+. */
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    private val _state = MutableStateFlow(PrivateSpaceState())
    val state: StateFlow<PrivateSpaceState> = _state

    // Fired when the profile is locked/unlocked/added/removed so the app list can refresh.
    private val listeners = mutableSetOf<() -> Unit>()
    fun addChangeListener(listener: () -> Unit) { listeners += listener }

    init {
        if (supported) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    refresh()
                    listeners.forEach { it() }
                }
            }
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
        refresh()
    }

    /** The private profile's [UserHandle], or null when none exists / unsupported. */
    fun privateUser(): UserHandle? {
        if (!supported) return null
        return runCatching { launcherApps.profiles }.getOrDefault(emptyList())
            .firstOrNull { isPrivate(it) }
    }

    private fun isPrivate(user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val info = runCatching { launcherApps.getLauncherUserInfo(user) }.getOrNull() ?: return false
        return info.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
    }

    /** Recompute availability + lock state from the system. */
    fun refresh() {
        val user = privateUser()
        _state.value = if (user == null) {
            PrivateSpaceState(available = false)
        } else {
            PrivateSpaceState(available = true, locked = userManager.isQuietModeEnabled(user))
        }
    }

    /**
     * Lock (true) or request unlock (false) of the private profile.
     * Unlock surfaces the system authentication challenge.
     */
    fun setLocked(locked: Boolean) {
        val user = privateUser() ?: return
        runCatching { userManager.requestQuietModeEnabled(locked, user) }
        refresh()
    }
}
