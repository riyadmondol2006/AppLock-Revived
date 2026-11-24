package dev.riyadm.applock.features.notifications

import android.app.Notification

/**
 * Pure decision logic for hiding the notifications of locked apps, kept free of Android services
 * so it can be unit tested.
 */
internal object HideNotificationsPolicy {

    /** Notification categories that must keep working (calls, navigation, media, alarms). */
    private val EXEMPT_CATEGORIES = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_ALARM,
    )

    private const val EXEMPT_FLAGS =
        Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_ONGOING_EVENT

    /**
     * Foreground-service, ongoing, call, media, navigation and alarm notifications are left alone:
     * the system refuses to dismiss some of them, and replacing the rest would break their controls.
     */
    fun isExempt(flags: Int, category: String?): Boolean =
        flags and EXEMPT_FLAGS != 0 || category in EXEMPT_CATEGORIES

    /**
     * @param withinUnlockWindow the app was unlocked and its unlock duration is still running.
     */
    fun shouldHide(
        enabled: Boolean,
        ownPackage: String,
        postedBy: String,
        lockedApps: Set<String>,
        withinUnlockWindow: Boolean,
        exempt: Boolean,
    ): Boolean = enabled &&
        postedBy != ownPackage &&
        postedBy in lockedApps &&
        !withinUnlockWindow &&
        !exempt

    const val PLACEHOLDER_TAG_PREFIX = "hidden:"

    /** One placeholder per locked app: later notifications update it instead of stacking up. */
    fun placeholderTag(packageName: String): String = PLACEHOLDER_TAG_PREFIX + packageName

    fun packageOfPlaceholderTag(tag: String?): String? =
        tag?.takeIf { it.startsWith(PLACEHOLDER_TAG_PREFIX) }?.removePrefix(PLACEHOLDER_TAG_PREFIX)

    /** Placeholders whose app is no longer locked (or all of them when the feature is off). */
    fun stalePlaceholderPackages(
        placeholderPackages: Collection<String>,
        lockedApps: Set<String>,
        enabled: Boolean,
    ): List<String> =
        if (!enabled) placeholderPackages.toList() else placeholderPackages.filter { it !in lockedApps }
}

/** Remembers the notifications handled recently so a repeated callback is processed only once. */
internal class RecentNotificationKeys(private val capacity: Int = 256) {
    private val seen = object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > capacity
    }

    /** True the first time a key is seen with this post time; an update has a newer post time. */
    @Synchronized
    fun markIfNew(key: String, postTime: Long): Boolean {
        if (seen[key] == postTime) return false
        seen[key] = postTime
        return true
    }

    @Synchronized
    fun clear() = seen.clear()
}
