package {{package}}

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Video-player fullscreen only: overlay the WebView's custom video view in
 * landscape. This is not app/page fullscreen (the website UI is hidden).
 */
object WebViewFullscreen {
    @Volatile
    var isShowing: Boolean = false
        private set

    private var host: Activity? = null
    private var webView: WebView? = null
    private var overlay: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @JvmStatic
    fun attach(view: WebView) {
        webView = view
    }

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

        // Hide the site UI so only the player surface is visible.
        webView?.visibility = View.GONE

        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            isClickable = true
            isFocusable = true
            addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        overlay = container
        (activity.window.decorView as FrameLayout).addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars(activity)
    }

    @JvmStatic
    fun hide() {
        val activity = host
        overlay?.let { frame ->
            (frame.parent as? ViewGroup)?.removeView(frame)
        }
        overlay = null
        customView = null

        webView?.visibility = View.VISIBLE

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
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }
}
