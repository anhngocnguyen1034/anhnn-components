package com.anhnn.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds

/**
 * Cổng vào duy nhất của hệ quảng cáo: **preload trước vào cache → lúc cần lấy ra hiện ngay →
 * tự nạp lại** cho lượt sau, nên cảm giác nhanh & mượt.
 *
 * Luồng dùng tối thiểu:
 * ```
 * // 1. Khai báo cấu hình 1 lần (vd trong Application/Activity):
 * Ads.init(AdsConfig(
 *     adsEnabled = { remote.adsEnabled() },
 *     adUnitId   = { name -> remote.adUnitId(name) },
 *     adFormat   = { name -> myFormats[name] },
 * ))
 *
 * // 2. Thu thập consent + init SDK, xong thì preload:
 * Ads.start(activity) {
 *     Ads.preload(activity, "splash_open", "exit_native")
 * }
 *
 * // 3. Interstitial: hiện nếu sẵn, không thì chạy tiếp ngay (không chặn user):
 * Ads.showInterstitial(activity, "home_tuvi") { navigate() }
 *
 * // 4. Native/Banner: đặt composable, tự lấy ad đã preload:
 * NativeAd(adName = "exit_native")
 * BannerAd(adName = "exit_banner")
 * ```
 */
object Ads {

    /** Khai báo cấu hình. Gọi 1 lần trước mọi thao tác ad khác. */
    fun init(config: AdsConfig) {
        AdManager.config = config
    }

    /**
     * Thu thập consent (UMP) rồi khởi tạo Mobile Ads SDK; xong (dù consent hay lỗi) chạy [onReady].
     * Gọi ở `Activity.onCreate`. Không gọi [onReady] thì ad vẫn chưa init nên đừng preload sớm.
     */
    fun start(activity: Activity, onReady: () -> Unit = {}) {
        AdConsent.gather(activity) {
            MobileAds.initialize(activity) { onReady() }
        }
    }

    /** Nạp trước vào cache các vị trí [adNames] theo đúng định dạng của từng tên. */
    fun preload(context: Context, vararg adNames: String) {
        AdManager.preload(context, adNames)
    }

    /** true nếu interstitial [adName] đã load sẵn (gọi [showInterstitial] sẽ hiện ngay). */
    fun isInterstitialReady(adName: String): Boolean = AdManager.isInterstitialReady(adName)

    /**
     * Hiện interstitial [adName] nếu đã sẵn & qua cooldown, sau đó gọi [onClosed]. Nếu chưa sẵn
     * / đang cooldown thì gọi [onClosed] ngay (không chặn user) và preload cho lượt sau.
     */
    fun showInterstitial(activity: Activity, adName: String, onClosed: () -> Unit) {
        AdManager.showInterstitial(activity, adName, onClosed)
    }

    /** true nếu đã đủ điều kiện request ad (consent obtained / không bắt buộc). */
    fun canRequestAds(activity: Activity): Boolean = AdConsent.canRequestAds(activity)

    /** Giải phóng toàn bộ ad đang cache (vd khi tắt ads / đổi user). */
    fun clear() = AdCache.clear()
}
