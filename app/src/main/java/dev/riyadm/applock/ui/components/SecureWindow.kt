package dev.riyadm.applock.ui.components

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

private val secureRequests = WeakHashMap<Window, Int>()

/**
 * Keeps FLAG_SECURE on the current window while this composable is in the composition.
 *
 * Requests are reference-counted per window: during a navigation transition the outgoing
 * and incoming screens are composed together, and a plain add/clear pair would let the
 * outgoing screen clear the flag while the incoming lock screen is still visible.
 */
@Composable
fun SecureWindowEffect() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val count = secureRequests[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        secureRequests[window] = count + 1
        onDispose {
            val remaining = (secureRequests[window] ?: 1) - 1
            if (remaining <= 0) {
                secureRequests.remove(window)
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                secureRequests[window] = remaining
            }
        }
    }
}
