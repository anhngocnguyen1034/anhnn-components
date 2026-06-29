package com.anhnn.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Tự hiện quảng cáo **App Open** khi người dùng quay lại app từ background (return-to-app).
 *
 * - Phát hiện foreground bằng cách đếm activity đang `started` (không cần lifecycle-process).
 * - **Bỏ qua lần mở app đầu tiên** (cold start) — lúc đó app thường đã có open/splash ad riêng.
 * - Phát hiện foreground ở `onActivityStarted` nhưng **hiện ad ở `onActivityResumed`** (activity
 *   đã resume) cho chắc — show ở onStart đôi khi bị SDK bỏ qua.
 */
internal object AppOpenManager {

    private const val TAG = "AnhnnAds"

    @Volatile private var registered = false
    private var startedActivities = 0
    private var coldStart = true
    private var pendingShow = false

    fun register(application: Application, adName: String) {
        if (registered) return
        registered = true

        // Không preload ở đây: lúc Application.onCreate SDK chưa init (init qua Ads.start ở Activity).
        // App nên preload [adName] cùng các placement khác sau khi start xong; showAppOpen cũng tự
        // nạp lại cho lượt sau khi miss/dismiss.
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    if (coldStart) {
                        coldStart = false // lần mở app đầu tiên: không hiện App Open
                    } else {
                        pendingShow = true // quay lại từ background -> hiện khi resume
                    }
                }
                startedActivities++
            }

            override fun onActivityResumed(activity: Activity) {
                if (!pendingShow) return
                pendingShow = false
                Log.d(TAG, "return-to-app: show App Open '$adName'")
                AdManager.showAppOpen(activity, adName) {}
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
