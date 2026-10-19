package dev.riyadm.applock.services

import android.os.Handler

/**
 * Decides when a polling lock backend (Usage Stats, Shizuku) checks the foreground app.
 *
 * Ticks only run while they can matter: the screen is interactive, protection is on and at
 * least one app is locked. While any of these is false nothing is scheduled at all, so the
 * backend costs no CPU time. Every event that could put a locked app on screen while polling
 * was paused (screen on, keyguard gone, protection turned on, an app locked) starts a tick at
 * once, so pausing never delays a lock. While active the interval stays at [intervalMs]: a
 * locked app can be opened at any moment and the interval is the longest it could stay visible.
 *
 * Not thread-safe: call it only from the thread that runs the ticks.
 */
internal class PollingController(
    private val intervalMs: Long,
    private val scheduler: Scheduler,
) {
    interface Scheduler {
        /** Runs the tick after [delayMs]. Only one tick is ever pending. */
        fun schedule(delayMs: Long)
        fun cancel()
    }

    enum class Transition { NONE, RESUMED, PAUSED }

    var isActive = false
        private set

    private var shutDown = false

    /** Re-evaluates whether ticks should run; returns what changed. */
    fun update(interactive: Boolean, protectEnabled: Boolean, hasLockedApps: Boolean): Transition {
        if (shutDown) return Transition.NONE
        val shouldRun = shouldPoll(interactive, protectEnabled, hasLockedApps)
        return when {
            shouldRun && !isActive -> {
                isActive = true
                scheduler.cancel()
                scheduler.schedule(0L)
                Transition.RESUMED
            }

            !shouldRun && isActive -> {
                isActive = false
                scheduler.cancel()
                Transition.PAUSED
            }

            else -> Transition.NONE
        }
    }

    /** Checks right away (e.g. the keyguard just went away) instead of waiting for the next tick. */
    fun tickNow() {
        if (!isActive) return
        scheduler.cancel()
        scheduler.schedule(0L)
    }

    /** Call at the end of every tick. */
    fun onTickFinished() {
        if (!isActive) return
        scheduler.cancel()
        scheduler.schedule(intervalMs)
    }

    fun shutdown() {
        shutDown = true
        isActive = false
        scheduler.cancel()
    }

    companion object {
        fun shouldPoll(interactive: Boolean, protectEnabled: Boolean, hasLockedApps: Boolean): Boolean =
            interactive && protectEnabled && hasLockedApps
    }
}

/** Runs [tick] on [handler]'s thread. Delays use uptime, so a pending tick never wakes the device. */
internal class HandlerPollScheduler(
    private val handler: Handler,
    private val tick: Runnable,
) : PollingController.Scheduler {
    override fun schedule(delayMs: Long) {
        if (delayMs <= 0L) handler.post(tick) else handler.postDelayed(tick, delayMs)
    }

    override fun cancel() = handler.removeCallbacks(tick)
}
