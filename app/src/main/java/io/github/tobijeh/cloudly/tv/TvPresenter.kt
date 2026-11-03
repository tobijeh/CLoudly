package io.github.tobijeh.cloudly.tv

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shows the cloud on a wireless/wired presentation display (Android Presentation API) when one is connected.
 * The phone then shows only the controls. With no presentation display, the app offers full-screen mirror mode.
 */
class TvPresenter(
    private val activity: ComponentActivity,
    private val content: @Composable () -> Unit,
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private var presentation: CloudPresentation? = null
    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    fun start() {
        displayManager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(this)
        dismiss()
    }

    private fun refresh() {
        val target = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        if (presentation?.display?.displayId != target?.displayId || presentation == null) {
            dismiss()
            if (target != null) {
                val p = CloudPresentation(activity, target, content)
                p.setOnDismissListener { if (presentation === p) { presentation = null; _displayName.value = null } }
                try {
                    p.show()
                    presentation = p
                } catch (_: WindowManager.InvalidDisplayException) {
                    presentation = null
                }
            }
        }
        _displayName.value = presentation?.display?.name
    }

    private fun dismiss() {
        val p = presentation ?: return
        presentation = null
        p.dismiss()
        _displayName.value = null
    }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()
}

private class CloudPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val content: @Composable () -> Unit,
) : Presentation(activity, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setContent { content() }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
        }
        setContentView(view)
    }
}
