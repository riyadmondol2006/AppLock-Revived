package dev.riyadm.applock.services

import com.google.common.truth.Truth.assertThat
import dev.riyadm.applock.services.PollingController.Transition
import org.junit.Test

class PollingControllerTest {

    /** Records what the controller asked for; [pending] is the single tick that would run next. */
    private class FakeScheduler : PollingController.Scheduler {
        var pending: Long? = null
        var scheduleCalls = 0

        override fun schedule(delayMs: Long) {
            check(pending == null) { "a second tick was scheduled while one is pending" }
            pending = delayMs
            scheduleCalls++
        }

        override fun cancel() {
            pending = null
        }

        /** Runs the pending tick, the way the handler would. */
        fun runTick(controller: PollingController) {
            checkNotNull(pending) { "no tick pending" }
            pending = null
            controller.onTickFinished()
        }
    }

    private val scheduler = FakeScheduler()
    private val controller = PollingController(INTERVAL, scheduler)

    private fun update(interactive: Boolean = true, protect: Boolean = true, locked: Boolean = true) =
        controller.update(interactive, protect, locked)

    @Test
    fun startsWithAnImmediateTickAndThenPollsAtTheInterval() {
        assertThat(update()).isEqualTo(Transition.RESUMED)
        assertThat(scheduler.pending).isEqualTo(0L)

        scheduler.runTick(controller)
        assertThat(scheduler.pending).isEqualTo(INTERVAL)
        scheduler.runTick(controller)
        assertThat(scheduler.pending).isEqualTo(INTERVAL)
    }

    @Test
    fun screenOffStopsEveryTick() {
        update()
        scheduler.runTick(controller)

        assertThat(update(interactive = false)).isEqualTo(Transition.PAUSED)
        assertThat(scheduler.pending).isNull()
        assertThat(controller.isActive).isFalse()

        // A tick that was already running when the screen went off must not reschedule.
        controller.onTickFinished()
        assertThat(scheduler.pending).isNull()
    }

    @Test
    fun screenOnChecksAtOnce() {
        update(interactive = false)
        assertThat(scheduler.pending).isNull()

        assertThat(update(interactive = true)).isEqualTo(Transition.RESUMED)
        assertThat(scheduler.pending).isEqualTo(0L)
    }

    @Test
    fun protectionOffOrNothingLockedMeansNoPolling() {
        assertThat(update(protect = false)).isEqualTo(Transition.NONE)
        assertThat(scheduler.pending).isNull()
        assertThat(update(locked = false)).isEqualTo(Transition.NONE)
        assertThat(scheduler.pending).isNull()

        // Locking an app (or turning protection on) resumes right away.
        assertThat(update()).isEqualTo(Transition.RESUMED)
        assertThat(scheduler.pending).isEqualTo(0L)

        scheduler.runTick(controller)
        assertThat(update(protect = false)).isEqualTo(Transition.PAUSED)
        assertThat(scheduler.pending).isNull()
    }

    @Test
    fun repeatedUpdatesDoNotRestartOrDuplicateTicks() {
        update()
        scheduler.runTick(controller)
        val calls = scheduler.scheduleCalls

        repeat(5) { assertThat(update()).isEqualTo(Transition.NONE) }
        assertThat(scheduler.scheduleCalls).isEqualTo(calls)
        assertThat(scheduler.pending).isEqualTo(INTERVAL)
    }

    @Test
    fun tickNowReplacesThePendingTick() {
        update()
        scheduler.runTick(controller)
        assertThat(scheduler.pending).isEqualTo(INTERVAL)

        controller.tickNow()
        assertThat(scheduler.pending).isEqualTo(0L)
        controller.tickNow()
        assertThat(scheduler.pending).isEqualTo(0L)
    }

    @Test
    fun tickNowDoesNothingWhilePaused() {
        update(interactive = false)
        controller.tickNow()
        assertThat(scheduler.pending).isNull()
    }

    @Test
    fun shutdownIsFinal() {
        update()
        controller.shutdown()
        assertThat(scheduler.pending).isNull()
        assertThat(update()).isEqualTo(Transition.NONE)
        controller.tickNow()
        controller.onTickFinished()
        assertThat(scheduler.pending).isNull()
    }

    @Test
    fun pollsOnlyWhenEverythingAllowsIt() {
        for (interactive in listOf(true, false)) for (protect in listOf(true, false)) for (locked in listOf(true, false)) {
            assertThat(PollingController.shouldPoll(interactive, protect, locked))
                .isEqualTo(interactive && protect && locked)
        }
    }

    private companion object {
        const val INTERVAL = 250L
    }
}
