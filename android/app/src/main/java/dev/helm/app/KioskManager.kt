package dev.helm.app

import android.app.Activity
import android.view.WindowInsetsController
import android.view.WindowManager

object KioskManager {
    fun enable(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.insetsController?.hide(
            android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
        )
        activity.window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun disable(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.insetsController?.show(
            android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
        )
    }
}
