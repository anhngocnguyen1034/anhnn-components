package com.anhnn.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
     *
     * Sau lượt consent mà UMP trả `canRequestAds() == false` (form lỗi / mất mạng ở vùng bắt buộc
     * consent) thì KHÔNG init SDK, mọi request bị chặn ([isConsentBlocked] = true) — đúng hướng dẫn tích
     * hợp UMP của Google. [onReady] vẫn chạy để app không bị kẹt chờ.
     */
    fun start(activity: Activity, onReady: () -> Unit = {}) {
        AdConsent.gather(activity) {
            val allowed = runCatching { AdConsent.canRequestAds(activity) }.getOrDefault(false)
            AdManager.consentBlocked = !allowed
            if (!allowed) {
                android.util.Log.w("AnhnnAds", "UMP: canRequestAds() = false -> không init quảng cáo")
                onReady()
                return@gather
            }
            initSdk(activity, onReady)
        }
    }

    /**
     * Tính lại consent sau khi người dùng đổi lựa chọn (vd đóng form "Privacy options" của UMP).
     *
     * Lượt [start] bị chặn (`canRequestAds() == false`) mà nay người dùng đã đồng ý thì gỡ chặn và
     * init SDK, rồi [onReady] nhận `true` — app bật lại quảng cáo cho phiên này mà không phải đợi
     * mở lại app. Ngược lại, người dùng rút consent thì chặn lại mọi request ngay. Trạng thái
     * không đổi thì [onReady] nhận `false` và không làm gì thêm.
     */
    fun refreshConsent(activity: Activity, onReady: (unblocked: Boolean) -> Unit = {}) {
        val allowed = runCatching { AdConsent.canRequestAds(activity) }.getOrDefault(false)
        val wasBlocked = AdManager.consentBlocked
        AdManager.consentBlocked = !allowed
        if (wasBlocked && allowed) {
            initSdk(activity) { onReady(true) }
        } else {
            onReady(false)
        }
    }

    private fun initSdk(activity: Activity, onReady: () -> Unit) {
        // MobileAds.initialize PHẢI chạy ở background thread.
        //
        // Nó nạp module Dynamite của Play Services (DynamiteModule.getRemoteVersion) và đọc
        // đĩa; gọi thẳng trên main thread làm app đứng hình vài trăm ms — đo trên máy thật:
        // "Choreographer: Skipped 37 frames!" ngay lúc mở app, hoạt ảnh splash giật thấy rõ.
        // Google cũng khuyến nghị đúng như vậy trong tài liệu khởi tạo SDK.
        val appContext = activity.applicationContext
        val main = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)
        fun settle() {
            if (done.compareAndSet(false, true)) main.post { onReady() }
        }
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            // Callback của SDK có thể về ở thread nào cũng được -> luôn đẩy lại main.
            runCatching { MobileAds.initialize(appContext) { settle() } }
                .onFailure { settle() }
        }
        worker.shutdown()
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

    /**
     * **Nạp rồi mới hiện** interstitial [adName]: chưa có ad sẵn thì nạp ngay và hiện màn chờ
     * ([style]), quá [timeoutMs] vẫn chưa xong thì bỏ qua và đi tiếp.
     *
     * Khác [showInterstitial] — hàm đó chỉ hiện cái đã preload, chưa sẵn là đi luôn. Ở những vị
     * trí người dùng chỉ ghé một lần (mở màn X lần đầu) thì preload hầu như chưa kịp xong, nên
     * [showInterstitial] sẽ chẳng bao giờ hiện gì; dùng hàm này cho các vị trí đó.
     *
     * [onClosed] LUÔN chạy đúng một lần — kể cả khi tắt quảng cáo, cooldown, lỗi hay quá hạn —
     * nên điều hướng không bao giờ kẹt. [onState] theo dõi chi tiết hơn (xem [AdState]), kết
     * thúc bằng đúng một [AdState.Done].
     *
     * @param loadingLabel chữ hiện dưới spinner; null = chỉ spinner. Module KHÔNG mang chuỗi
     *        dịch nào, app tự truyền chuỗi đã dịch.
     */
    fun loadAndShowInterstitial(
        activity: Activity,
        adName: String,
        style: AdLoadingStyle = AdLoadingStyle.Dialog,
        loadingLabel: String? = null,
        timeoutMs: Long = 8_000L,
        onState: (AdState) -> Unit = {},
        onClosed: () -> Unit,
    ) {
        AdManager.loadAndShowInterstitial(
            activity = activity,
            adName = adName,
            style = style,
            loadingLabel = loadingLabel,
            timeoutMs = timeoutMs,
            onState = onState,
            onClosed = onClosed,
        )
    }

    /** true nếu Rewarded [adName] đã load sẵn (gọi [showRewarded] sẽ hiện ngay). */
    fun isRewardedReady(adName: String): Boolean = AdManager.isRewardedReady(adName)

    /**
     * Hiện Rewarded [adName]. [onReward] CHỈ gọi khi người dùng xem đủ và nhận thưởng;
     * [onClosed] luôn gọi khi ad đóng (hoặc khi không hiện được). Nếu chưa sẵn / không bật
     * thì preload cho lượt sau rồi gọi [onClosed] ngay, KHÔNG phát thưởng — nên kiểm tra
     * [isRewardedReady] để bật/tắt nút "Xem để nhận".
     */
    fun showRewarded(
        activity: Activity,
        adName: String,
        onReward: () -> Unit,
        onClosed: () -> Unit = {},
    ) {
        AdManager.showRewarded(activity, adName, onReward, onClosed)
    }

    /** true nếu App Open [adName] đã load sẵn & chưa hết hạn (gọi [showAppOpen] sẽ hiện ngay). */
    fun isAppOpenReady(adName: String): Boolean = AdManager.isAppOpenReady(adName)

    /**
     * Hiện App Open [adName] nếu đủ điều kiện (đã sẵn, chưa hết hạn, không chồng full-screen khác,
     * qua cooldown), sau đó gọi [onClosed]; nếu không thì gọi [onClosed] ngay (không chặn user).
     */
    fun showAppOpen(activity: Activity, adName: String, onClosed: () -> Unit = {}) {
        AdManager.showAppOpen(activity, adName, onClosed)
    }

    /**
     * **Nạp rồi mới hiện** App Open [adName] — dùng cho màn splash lúc mở app.
     *
     * App Open là định dạng Google làm ra đúng cho màn chờ khởi động; dùng interstitial ở chỗ
     * này vừa sai ý đồ định dạng vừa dễ bị coi là "quảng cáo nhảy ra bất ngờ lúc mở app".
     *
     * [timeoutMs] là trần CỨNG: hết giờ mà chưa nạp xong thì bỏ qua, [onClosed] vẫn chạy —
     * splash không bao giờ dài thêm vì quảng cáo.
     */
    fun loadAndShowAppOpen(
        activity: Activity,
        adName: String,
        timeoutMs: Long = 5_000L,
        onState: (AdState) -> Unit = {},
        onClosed: () -> Unit,
    ) {
        AdManager.loadAndShowAppOpen(activity, adName, timeoutMs, onState, onClosed)
    }

    /**
     * Bật quảng cáo **return-to-app**: tự hiện App Open [adName] mỗi khi người dùng quay lại app
     * từ background (bỏ qua lần mở đầu tiên). Gọi 1 lần, vd trong `Application.onCreate`.
     * Lưu ý: phải [init] trước và App Open chỉ load sau khi SDK đã khởi tạo qua [start].
     */
    fun setupAppOpen(application: Application, adName: String) {
        AppOpenManager.register(application, adName)
    }

    /** true nếu đã đủ điều kiện request ad (consent obtained / không bắt buộc). */
    fun canRequestAds(activity: Activity): Boolean = AdConsent.canRequestAds(activity)

    /**
     * Lượt [start] gần nhất có bị UMP chặn không (`canRequestAds() == false`). true ⇒ SDK chưa
     * init và mọi request bị bỏ qua; app nên coi như quảng cáo đang tắt.
     */
    val isConsentBlocked: Boolean get() = AdManager.consentBlocked

    /** Giải phóng toàn bộ ad đang cache (vd khi tắt ads / đổi user). */
    fun clear() = AdCache.clear()
}
