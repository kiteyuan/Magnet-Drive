package {{package}}

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hosts Android WebView HTML5 fullscreen (video). Wry's default
 * [android.webkit.WebChromeClient.onShowCustomView] immediately hides the
 * custom view, so the site player cannot enter fullscreen.
 */
object WebViewFullscreen {
    @Volatile
    var isShowing: Boolean = false
        private set

    private var host: Activity? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @JvmStatic
    fun show(activity: Activity, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (isShowing) {
            hide()
        }

        host = activity
        customView = view
        customViewCallback = callback
        originalOrientation = activity.requestedOrientation
        isShowing = true

        view.setBackgroundColor(Color.BLACK)
        val decor = activity.window.decorView as FrameLayout
        decor.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        hideSystemBars(activity)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @JvmStatic
    fun hide() {
        val activity = host
        val view = customView
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
        }

        if (activity != null) {
            showSystemBars(activity)
            activity.requestedOrientation = originalOrientation
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        try {
            customViewCallback?.onCustomViewHidden()
        } catch (_: Exception) {
            // WebView may already have torn down the callback.
        }

        customView = null
        customViewCallback = null
        host = null
        isShowing = false
    }

    private fun hideSystemBars(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = activity.window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            activity.window.attributes = attrs
        }
    }

    private fun showSystemBars(activity: Activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        // Keep edge-to-edge; MainActivity calls enableEdgeToEdge() when available.
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }
}
