package com.anhnn.ads

import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import java.util.concurrent.ConcurrentHashMap

/**
 * Kho ad đã preload, dùng chung toàn app — mỗi tên vị trí có 1 [Slot] giữ ad đã load sẵn + cờ
 * `loading` chống nạp trùng. Banner KHÔNG cache ở đây: [BannerAd] load inline để tránh giữ tham
 * chiếu Activity gây leak (banner vốn load nhanh).
 */
internal object AdCache {

    class Slot<T> {
        @Volatile var ad: T? = null
        @Volatile var loading: Boolean = false
    }

    private val interstitial = ConcurrentHashMap<String, Slot<InterstitialAd>>()
    private val native = ConcurrentHashMap<String, Slot<NativeAd>>()

    fun inter(adName: String): Slot<InterstitialAd> = interstitial.getOrPut(adName) { Slot() }
    fun nat(adName: String): Slot<NativeAd> = native.getOrPut(adName) { Slot() }

    /** Hủy & xóa toàn bộ ad đang giữ. */
    fun clear() {
        interstitial.values.forEach { it.ad = null; it.loading = false }
        native.values.forEach { it.ad?.destroy(); it.ad = null; it.loading = false }
    }
}
