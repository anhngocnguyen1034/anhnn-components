package com.anhnn.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd as GmsNativeAd

/**
 * Bộ máy nạp/hiện quảng cáo: preload theo định dạng → cache → hiện ngay → tự nạp lại.
 * Tất cả lệnh load chạy trên main thread (đúng yêu cầu của Mobile Ads SDK) nhưng bất đồng bộ
 * nên không chặn UI.
 */
internal object AdManager {

    private const val TAG = "AnhnnAds"

    /** Cấu hình do app bơm vào qua [Ads.init]; null = chưa init → mọi thao tác ad bỏ qua. */
    @Volatile var config: AdsConfig? = null

    // Cooldown dùng chung toàn app: 2 quảng cáo full-screen bất kỳ không hiện quá sát nhau.
    @Volatile private var lastInterShownAt: Long = 0L

    // Đang có 1 quảng cáo full-screen hiển thị (chặn App Open chồng lên interstitial / chính nó).
    @Volatile private var showingFullScreen: Boolean = false

    // App Open ad hết hạn sau ~4 giờ kể từ lúc load (theo khuyến nghị của Google).
    private const val APP_OPEN_EXPIRY_MS = 4L * 60L * 60L * 1000L

    private fun enabled(): Boolean = config?.adsEnabled() == true

    private fun unitId(adName: String): String = config!!.adUnitId(adName)

    // ---------------------------------------------------------------- preload dispatch

    fun preload(context: Context, adNames: Array<out String>) {
        if (!enabled()) return
        val cfg = config ?: return
        adNames.forEach { name ->
            when (cfg.adFormat(name)) {
                AdFormat.INTERSTITIAL -> preloadInterstitial(context, name)
                AdFormat.NATIVE -> preloadNative(context, name)
                AdFormat.APP_OPEN -> preloadAppOpen(context, name)
                AdFormat.BANNER -> Unit // banner load inline trong BannerAd composable
                null -> Log.w(TAG, "preload: unknown adName '$name'")
            }
        }
    }

    // ---------------------------------------------------------------- interstitial

    fun isInterstitialReady(adName: String): Boolean = AdCache.inter(adName).ad != null

    private fun preloadInterstitial(context: Context, adName: String) {
        if (!enabled()) return
        val slot = AdCache.inter(adName)
        if (slot.ad != null || slot.loading) return
        slot.loading = true
        InterstitialAd.load(
            context.applicationContext,
            unitId(adName),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    slot.ad = loaded
                    slot.loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] interstitial load failed: ${error.message}")
                    slot.ad = null
                    slot.loading = false
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, adName: String, onClosed: () -> Unit) {
        if (!enabled()) {
            onClosed()
            return
        }
        val slot = AdCache.inter(adName)
        val now = System.currentTimeMillis()
        val inCooldown = now - lastInterShownAt < (config?.interCooldownMs() ?: 30_000L)
        val ad = slot.ad
        if (ad == null || inCooldown) {
            if (ad == null) preloadInterstitial(activity, adName)
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
            }

            override fun onAdDismissedFullScreenContent() {
                slot.ad = null
                showingFullScreen = false
                lastInterShownAt = System.currentTimeMillis()
                preloadInterstitial(activity, adName)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "[$adName] interstitial show failed: ${error.message}")
                slot.ad = null
                showingFullScreen = false
                preloadInterstitial(activity, adName)
                onClosed()
            }
        }
        ad.show(activity)
    }

    // ---------------------------------------------------------------- app open

    fun isAppOpenReady(adName: String): Boolean {
        val slot = AdCache.appOpen(adName)
        return slot.ad != null &&
            System.currentTimeMillis() - slot.loadedAt < APP_OPEN_EXPIRY_MS
    }

    private fun preloadAppOpen(context: Context, adName: String) {
        if (!enabled()) return
        val slot = AdCache.appOpen(adName)
        val fresh = slot.ad != null &&
            System.currentTimeMillis() - slot.loadedAt < APP_OPEN_EXPIRY_MS
        if (fresh || slot.loading) return
        slot.loading = true
        AppOpenAd.load(
            context.applicationContext,
            unitId(adName),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    slot.ad = loaded
                    slot.loadedAt = System.currentTimeMillis()
                    slot.loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] app open load failed: ${error.message}")
                    slot.ad = null
                    slot.loading = false
                }
            }
        )
    }

    /**
     * Hiện App Open [adName] nếu đã sẵn, chưa hết hạn, không đang có full-screen khác và qua
     * cooldown; sau đó gọi [onClosed]. Nếu không hiện được thì gọi [onClosed] ngay (không chặn
     * user) và preload cho lượt sau.
     */
    fun showAppOpen(activity: Activity, adName: String, onClosed: () -> Unit) {
        if (!enabled() || showingFullScreen) {
            onClosed()
            return
        }
        val slot = AdCache.appOpen(adName)
        val now = System.currentTimeMillis()
        val ad = slot.ad
        val expired = now - slot.loadedAt >= APP_OPEN_EXPIRY_MS
        val inCooldown = now - lastInterShownAt < (config?.interCooldownMs() ?: 30_000L)
        if (ad == null || expired || inCooldown) {
            if (ad == null || expired) {
                slot.ad = null
                preloadAppOpen(activity, adName)
            }
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
            }

            override fun onAdDismissedFullScreenContent() {
                slot.ad = null
                showingFullScreen = false
                lastInterShownAt = System.currentTimeMillis()
                preloadAppOpen(activity, adName)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "[$adName] app open show failed: ${error.message}")
                slot.ad = null
                showingFullScreen = false
                preloadAppOpen(activity, adName)
                onClosed()
            }
        }
        ad.show(activity)
    }

    // ---------------------------------------------------------------- native

    fun isNativeReady(adName: String): Boolean = AdCache.nat(adName).ad != null

    fun preloadNative(context: Context, adName: String) {
        if (!enabled()) return
        val slot = AdCache.nat(adName)
        if (slot.ad != null || slot.loading) return
        slot.loading = true
        val loader = AdLoader.Builder(context.applicationContext, unitId(adName))
            .forNativeAd { loaded ->
                slot.ad?.destroy() // phòng trường hợp còn ad cũ chưa tiêu thụ
                slot.ad = loaded
                slot.loading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] native load failed: ${error.message}")
                    slot.loading = false
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Lấy native đã preload ra dùng — **chuyển quyền sở hữu** cho caller (caller phải `destroy()`
     * khi xong) rồi tự nạp lượt kế vào cache. Trả null nếu chưa có sẵn (caller tự load inline).
     */
    fun acquireNative(context: Context, adName: String): GmsNativeAd? {
        if (!enabled()) return null
        val slot = AdCache.nat(adName)
        val ad = slot.ad
        slot.ad = null
        preloadNative(context, adName) // nạp sẵn cho lần sau (auto-reload)
        return ad
    }

    /** Load 1 native ngay (fallback khi cache trống). Caller sở hữu & phải destroy. */
    fun loadNativeNow(
        context: Context,
        adName: String,
        onLoaded: (GmsNativeAd) -> Unit,
        onFailed: () -> Unit,
    ) {
        if (!enabled()) {
            onFailed()
            return
        }
        val loader = AdLoader.Builder(context.applicationContext, unitId(adName))
            .forNativeAd { onLoaded(it) }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] native load failed: ${error.message}")
                    onFailed()
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    // ---------------------------------------------------------------- banner (inline)

    fun bannerUnitId(adName: String): String? = if (enabled()) unitId(adName) else null
}
