package com.anhnn.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Activity nào có lúc KHÔNG được phép bị App Open "quay lại app" ([Ads.setupAppOpen]) che thì
 * implement interface này — [AppOpenManager] hỏi đúng lúc định hiện.
 *
 * Ví dụ bắt buộc: màn splash có App Open riêng ([Ads.loadAndShowAppOpen]). Mở lại app khi
 * process còn sống (sau `finish()`) thì bộ đếm cũng thấy "quay lại", không từ chối ở đây là hai
 * App Open liền nhau. Màn đang phát âm thanh cũng nên từ chối.
 */
interface SuppressesAppOpen {
    val suppressAppOpen: Boolean
}

/**
 * Tự hiện quảng cáo **App Open** khi người dùng quay lại app từ background (return-to-app).
 *
 * - Phát hiện foreground bằng cách đếm activity đang `started` (không cần lifecycle-process).
 * - **Bỏ qua lần mở app đầu tiên** (cold start) — lúc đó app thường đã có open/splash ad riêng.
 * - Phát hiện foreground ở `onActivityStarted` nhưng **hiện ad ở `onActivityResumed`** (activity
 *   đã resume) cho chắc — show ở onStart đôi khi bị SDK bỏ qua.
 * - Không đếm activity của SDK quảng cáo / UMP ([isAdSurface]); bỏ qua lần dựng lại vì đổi cấu
 *   hình và lần quay lại sau khi bấm quảng cáo; hỏi [SuppressesAppOpen] trước khi hiện.
 */
internal object AppOpenManager {

    private const val TAG = "AnhnnAds"

    @Volatile private var registered = false
    private var startedActivities = 0
    private var coldStart = true
    private var pendingShow = false

    /**
     * Activity vừa dừng chỉ để dựng lại (đổi cấu hình: dark mode, chia đôi màn hình, gập máy...).
     * Lúc đó instance cũ `onStop` TRƯỚC khi instance mới `onStart` nên bộ đếm về 0 — không lọc
     * thì người dùng KHÔNG rời app vẫn bị coi là "quay lại" và ăn một App Open.
     */
    private var relaunching = false

    /**
     * Màn của SDK quảng cáo / UMP. `AdActivity` chạy TRONG process của app: đếm cả nó thì lúc
     * interstitial hiện rồi đóng sẽ bị hiểu nhầm thành "ra nền rồi quay lại".
     */
    private fun Activity.isAdSurface(): Boolean {
        val name = javaClass.name
        return name.startsWith("com.google.android.gms.ads") ||
            name.startsWith("com.google.android.ump") ||
            name.startsWith("com.google.android.gms.internal.consent_sdk")
    }

    fun register(application: Application, adName: String) {
        if (registered) return
        registered = true

        // Không preload ở đây: lúc Application.onCreate SDK chưa init (init qua Ads.start ở Activity).
        // App nên preload [adName] cùng các placement khác sau khi start xong; showAppOpen cũng tự
        // nạp lại cho lượt sau khi miss/dismiss.
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity.isAdSurface()) return
                if (startedActivities == 0) {
                    if (coldStart) {
                        coldStart = false // lần mở app đầu tiên: không hiện App Open
                    } else if (!relaunching) {
                        // Quay lại sau khi bấm banner/native (sang trình duyệt, Play Store) thì
                        // KHÔNG hiện: quảng cáo chồng quảng cáo, chính sách AdMob cấm.
                        val fromAdClick = AdManager.consumeAdClick()
                        pendingShow = !fromAdClick // quay lại từ background -> hiện khi resume
                    }
                }
                relaunching = false
                startedActivities++
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity.isAdSurface() || !pendingShow) return
                pendingShow = false
                if ((activity as? SuppressesAppOpen)?.suppressAppOpen == true) {
                    Log.d(TAG, "return-to-app: ${activity.javaClass.simpleName} từ chối App Open")
                    return
                }
                Log.d(TAG, "return-to-app: show App Open '$adName'")
                AdManager.showAppOpen(activity, adName) {}
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity.isAdSurface()) return
                if (activity.isChangingConfigurations) relaunching = true
                if (startedActivities > 0) startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
