package com.nl.growwa.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal accessibility service that exists only so the launcher can perform global actions
 * (expand the notification shade, lock the screen) which are otherwise impossible without
 * system-level permissions. It listens to no events.
 */
class GrowwaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op */ }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: GrowwaAccessibilityService? = null

        /** True once the service is connected and ready to perform actions. */
        val isConnected: Boolean get() = instance != null

        /** Pull down the notification shade. Returns false if the service isn't running. */
        fun expandNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /** Lock the device (API 28+). Returns false if unsupported or service isn't running. */
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }

        /** Whether the user has enabled Growwa's accessibility service in system settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, GrowwaAccessibilityService::class.java)
                .flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            for (entry in splitter) {
                if (entry.equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
