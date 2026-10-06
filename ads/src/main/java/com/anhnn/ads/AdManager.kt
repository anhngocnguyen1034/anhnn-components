package com.anhnn.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd as GmsNativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

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

    // Native nạp quá ~1 giờ thì Google khuyến nghị bỏ: impression có thể không được tính.
    private const val NATIVE_EXPIRY_MS = 60L * 60L * 1000L

    /**
     * UMP trả `canRequestAds() == false` sau lượt thu thập consent ([Ads.start]) — thường là user
     * EEA/UK mà form consent lỗi / mất mạng. Hướng dẫn tích hợp UMP của Google: lúc đó KHÔNG
     * được init SDK hay gửi request nào, nên mọi lối vào đều đi qua [enabled] và bị chặn ở đây.
     */
    @Volatile var consentBlocked: Boolean = false

    private fun enabled(): Boolean = !consentBlocked && config?.adsEnabled() == true

    /**
     * Có lượt bấm quảng cáo nào chưa được [consumeAdClick] tiêu thụ — [AppOpenManager] dùng để
     * bỏ qua lần quay lại app ngay sau khi quảng cáo dẫn người dùng đi.
     */
    @Volatile private var pendingAdClick: Boolean = false

    /** Lấy và xoá cờ "vừa bấm quảng cáo". */
    fun consumeAdClick(): Boolean {
        val clicked = pendingAdClick
        pendingAdClick = false
        return clicked
    }

    /** Báo cho app biết người dùng vừa bấm quảng cáo [adName] (xem [AdsConfig.onAdClicked]). */
    fun notifyClick(adName: String) {
        pendingAdClick = true
        runCatching { config?.onAdClicked?.invoke(adName) }
            .onFailure { Log.w(TAG, "[$adName] onAdClicked lỗi: $it") }
    }

    /**
     * Activity còn đang hiển thị không. Lượt nạp-rồi-hiện có thể xong SAU khi người dùng đã bấm
     * Home (splash, màn chờ interstitial): gọi `show()` lúc đó là bật quảng cáo toàn màn hình
     * lên khi app đang ở nền — đúng mẫu "quảng cáo bất ngờ" mà AdMob cấm. Mẫu App Open của
     * Google cũng chỉ hiện khi app ở foreground.
     *
     * Dùng STARTED chứ không phải RESUMED: `ActivityLifecycleCallbacks.onActivityResumed` (chỗ
     * return-to-app gọi [showAppOpen]) chạy TRƯỚC khi Lifecycle chuyển sang RESUMED.
     */
    private fun isForeground(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val owner = activity as? LifecycleOwner ?: return true
        return owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

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
                AdFormat.REWARDED -> preloadRewarded(context, name)
                AdFormat.BANNER -> Unit // banner load inline trong BannerAd composable
                null -> Log.w(TAG, "preload: unknown adName '$name'")
            }
        }
    }

    // ---------------------------------------------------------------- interstitial

    fun isInterstitialReady(adName: String): Boolean = AdCache.inter(adName).ad != null

    /**
     * Những ai đang đợi kết quả nạp interstitial của từng vị trí.
     *
     * Cần vì preload và [loadAndShowInterstitial] có thể cùng muốn một vị trí: preload chạy ngay
     * khi SDK init xong, rồi màn hình gọi loadAndShow chỉ vài trăm ms sau. Không có chỗ xếp hàng
     * này thì lượt sau thấy `loading == true` và sẽ bắn THÊM một request nữa cho cùng ad unit —
     * tốn quota, và callback của hai lượt ghi đè lẫn nhau.
     */
    private val interWaiters =
        java.util.concurrent.ConcurrentHashMap<String, MutableList<(InterstitialAd?) -> Unit>>()

    private fun addInterWaiter(adName: String, waiter: (InterstitialAd?) -> Unit) {
        interWaiters.getOrPut(adName) { java.util.Collections.synchronizedList(mutableListOf()) }
            .add(waiter)
    }

    /**
     * Giao kết quả cho những ai đang đợi. Một ad CHỈ hiện được một lần nên chỉ người đợi ĐẦU
     * TIÊN nhận nó, những người sau nhận null (đi tiếp không quảng cáo). Giao cùng một ad cho
     * nhiều người thì người sau gán đè `fullScreenContentCallback` của người trước ⇒ onClosed của
     * người trước không bao giờ chạy, điều hướng kẹt.
     */
    private fun notifyInterWaiters(adName: String, ad: InterstitialAd?) {
        val waiters = interWaiters.remove(adName) ?: return
        synchronized(waiters) { waiters.toList() }.forEachIndexed { i, waiter ->
            runCatching { waiter(if (i == 0) ad else null) }
        }
    }

    /**
     * Nạp interstitial vào kho. [notify] (nếu có) được gọi khi có kết quả; đang có lượt nạp dở
     * thì chỉ xếp hàng chờ chứ KHÔNG bắn thêm request.
     */
    private fun loadInterstitial(
        context: Context,
        adName: String,
        notify: ((InterstitialAd?) -> Unit)? = null,
    ) {
        if (!enabled()) {
            notify?.invoke(null)
            return
        }
        val slot = AdCache.inter(adName)
        slot.ad?.let { ready ->
            notify?.invoke(ready)
            return
        }
        notify?.let { addInterWaiter(adName, it) }
        if (slot.loading) return // đã có lượt đang chạy -> chỉ đứng chờ
        slot.loading = true
        InterstitialAd.load(
            context.applicationContext,
            unitId(adName),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    slot.ad = loaded
                    slot.loading = false
                    notifyInterWaiters(adName, loaded)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] interstitial load failed: ${error.message}")
                    slot.ad = null
                    slot.loading = false
                    notifyInterWaiters(adName, null)
                }
            }
        )
    }

    private fun preloadInterstitial(context: Context, adName: String) = loadInterstitial(context, adName)

    /**
     * Nạp-rồi-hiện interstitial: chưa có sẵn thì nạp ngay và hiện màn chờ, quá [timeoutMs] thì
     * bỏ qua đi tiếp.
     *
     * Khác [showInterstitial] (chỉ hiện cái đã preload, chưa có là đi luôn): ở các vị trí người
     * dùng chỉ ghé một lần — lần đầu mở một màn chẳng hạn — preload gần như chắc chắn chưa kịp
     * xong, nên [showInterstitial] sẽ không bao giờ hiện gì. Đây là hàm cho những chỗ đó.
     *
     * Bất biến: [onState] luôn kết thúc bằng đúng một [AdState.Done], và [onClosed] gọi đúng
     * một lần — hỏng hay timeout cũng vậy, để điều hướng không bao giờ kẹt.
     */
    fun loadAndShowInterstitial(
        activity: Activity,
        adName: String,
        style: AdLoadingStyle,
        loadingLabel: String?,
        timeoutMs: Long,
        onState: (AdState) -> Unit,
        onClosed: () -> Unit,
    ) {
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(last: AdState) {
            if (!finished.compareAndSet(false, true)) return
            runCatching { onState(last) }
            runCatching { onState(AdState.Done) }
            runCatching(onClosed)
        }

        if (!enabled()) {
            finish(AdState.Skipped)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterShownAt < (config?.interCooldownMs() ?: 30_000L)) {
            finish(AdState.Skipped)
            return
        }

        val cached = AdCache.inter(adName).ad
        if (cached != null) {
            runCatching { onState(AdState.Cached) }
            present(activity, adName, cached, onState) { finish(it) }
            return
        }

        // Phải nạp mới -> hiện màn chờ, đặt chuông báo quá hạn.
        runCatching { onState(AdState.Loading) }
        val overlay = AdLoadingOverlay.show(activity, style, loadingLabel)
        val handler = Handler(Looper.getMainLooper())
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
        val timeoutTask = Runnable {
            if (finished.get()) return@Runnable
            timedOut.set(true)
            overlay?.dismiss()
            Log.w(TAG, "[$adName] interstitial quá ${timeoutMs}ms -> bỏ qua")
            finish(AdState.Timeout)
        }
        handler.postDelayed(timeoutTask, timeoutMs)

        loadInterstitial(activity, adName) { ad ->
            if (timedOut.get() || finished.get()) return@loadInterstitial
            handler.removeCallbacks(timeoutTask)
            overlay?.dismiss()
            if (ad == null) {
                finish(AdState.Error)
                return@loadInterstitial
            }
            runCatching { onState(AdState.Loaded) }
            present(activity, adName, ad, onState) { finish(it) }
        }
    }

    /**
     * Hiện một interstitial đã có trong tay và nối các trạng thái show/click/paid/close.
     * [onFinished] nhận trạng thái kết thúc: [AdState.Close], hoặc [AdState.Skipped] khi activity
     * đã rời foreground — lúc đó ad được TRẢ LẠI kho để lượt sau dùng, không phí.
     */
    private fun present(
        activity: Activity,
        adName: String,
        ad: InterstitialAd,
        onState: (AdState) -> Unit,
        onFinished: (AdState) -> Unit,
    ) {
        val slot = AdCache.inter(adName)
        if (!isForeground(activity)) {
            Log.d(TAG, "[$adName] interstitial skip: activity không ở foreground")
            slot.ad = ad
            onFinished(AdState.Skipped)
            return
        }
        ad.setOnPaidEventListener { runCatching { onState(AdState.Paid) } }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
                runCatching { onState(AdState.Show) }
            }

            override fun onAdClicked() {
                runCatching { onState(AdState.Click) }
                notifyClick(adName)
            }

            override fun onAdDismissedFullScreenContent() {
                slot.ad = null
                showingFullScreen = false
                lastInterShownAt = System.currentTimeMillis()
                preloadInterstitial(activity, adName)
                onFinished(AdState.Close)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "[$adName] interstitial show failed: ${error.message}")
                slot.ad = null
                showingFullScreen = false
                preloadInterstitial(activity, adName)
                runCatching { onState(AdState.Error) }
                onFinished(AdState.Close)
            }
        }
        slot.ad = null // đang tiêu thụ, không để lượt khác lấy trùng
        // Bật cờ NGAY khi gọi show, không đợi onAdShowedFullScreenContent: giữa hai thời điểm đó
        // (vài trăm ms) một lượt App Open khác vẫn thấy cờ false và hiện chồng lên.
        showingFullScreen = true
        ad.show(activity)
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
        if (ad == null || inCooldown || !isForeground(activity)) {
            if (ad == null) preloadInterstitial(activity, adName)
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
            }

            override fun onAdClicked() {
                notifyClick(adName)
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
        // Bật cờ NGAY khi gọi show, không đợi onAdShowedFullScreenContent: giữa hai thời điểm đó
        // (vài trăm ms) một lượt App Open khác vẫn thấy cờ false và hiện chồng lên.
        showingFullScreen = true
        ad.show(activity)
    }

    // ---------------------------------------------------------------- app open

    fun isAppOpenReady(adName: String): Boolean {
        val slot = AdCache.appOpen(adName)
        return slot.ad != null &&
            System.currentTimeMillis() - slot.loadedAt < APP_OPEN_EXPIRY_MS
    }

    /**
     * Nạp sẵn App Open vào kho. Uỷ quyền cho [loadAppOpen] — KHÔNG được tự load riêng:
     * bản trước làm vậy và sinh ra deadlock, [loadAndShowAppOpen] thấy `loading == true` nên
     * chỉ xếp hàng chờ, còn lượt preload xong lại không đánh thức ai ⇒ splash luôn Timeout và
     * quảng cáo không bao giờ hiện.
     */
    private fun preloadAppOpen(context: Context, adName: String) = loadAppOpen(context, adName)

    private val appOpenWaiters =
        java.util.concurrent.ConcurrentHashMap<String, MutableList<(AppOpenAd?) -> Unit>>()

    /**
     * Nạp App Open vào kho, [notify] nhận kết quả; đang có lượt nạp dở thì xếp hàng chờ chứ
     * không bắn thêm request (xem [interWaiters] cho lý do).
     */
    private fun loadAppOpen(
        context: Context,
        adName: String,
        notify: ((AppOpenAd?) -> Unit)? = null,
    ) {
        if (!enabled()) {
            notify?.invoke(null)
            return
        }
        val slot = AdCache.appOpen(adName)
        val fresh = slot.ad != null &&
            System.currentTimeMillis() - slot.loadedAt < APP_OPEN_EXPIRY_MS
        if (fresh) {
            notify?.invoke(slot.ad)
            return
        }
        notify?.let {
            appOpenWaiters.getOrPut(adName) { java.util.Collections.synchronizedList(mutableListOf()) }
                .add(it)
        }
        if (slot.loading) return
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
                    notifyAppOpenWaiters(adName, loaded)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] app open load failed: ${error.message}")
                    slot.ad = null
                    slot.loading = false
                    notifyAppOpenWaiters(adName, null)
                }
            }
        )
    }

    /** Chỉ người đợi đầu tiên nhận ad — xem [notifyInterWaiters]. */
    private fun notifyAppOpenWaiters(adName: String, ad: AppOpenAd?) {
        val waiters = appOpenWaiters.remove(adName) ?: return
        synchronized(waiters) { waiters.toList() }.forEachIndexed { i, waiter ->
            runCatching { waiter(if (i == 0) ad else null) }
        }
    }

    /**
     * **Nạp rồi mới hiện** App Open — dành cho màn splash lúc mở app.
     *
     * Đây là định dạng Google làm RA cho đúng tình huống này ("monetize your app load screen"),
     * khác [loadAndShowInterstitial] vốn hợp với các bước chuyển màn giữa chừng.
     *
     * Khác [showAppOpen] (chỉ hiện cái đã sẵn): ở lần mở app đầu tiên gần như chắc chắn chưa có
     * ad nào trong kho, nên [showAppOpen] sẽ không hiện gì.
     *
     * [timeoutMs] là trần CỨNG cho cả lượt — splash không bao giờ được dài hơn thế vì quảng cáo.
     */
    fun loadAndShowAppOpen(
        activity: Activity,
        adName: String,
        timeoutMs: Long,
        onState: (AdState) -> Unit,
        onClosed: () -> Unit,
    ) {
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(last: AdState) {
            if (!finished.compareAndSet(false, true)) return
            runCatching { onState(last) }
            runCatching { onState(AdState.Done) }
            runCatching(onClosed)
        }
        if (!enabled() || showingFullScreen) {
            finish(AdState.Skipped)
            return
        }

        val slot = AdCache.appOpen(adName)
        val fresh = slot.ad != null &&
            System.currentTimeMillis() - slot.loadedAt < APP_OPEN_EXPIRY_MS
        if (fresh) {
            runCatching { onState(AdState.Cached) }
            presentAppOpen(activity, adName, slot.ad!!, onState) { finish(it) }
            return
        }

        runCatching { onState(AdState.Loading) }
        val handler = Handler(Looper.getMainLooper())
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
        val timeoutTask = Runnable {
            if (finished.get()) return@Runnable
            timedOut.set(true)
            Log.w(TAG, "[$adName] app open quá ${timeoutMs}ms -> bỏ qua")
            finish(AdState.Timeout)
        }
        handler.postDelayed(timeoutTask, timeoutMs)

        loadAppOpen(activity, adName) { ad ->
            if (timedOut.get() || finished.get()) return@loadAppOpen
            handler.removeCallbacks(timeoutTask)
            if (ad == null) {
                finish(AdState.Error)
                return@loadAppOpen
            }
            runCatching { onState(AdState.Loaded) }
            presentAppOpen(activity, adName, ad, onState) { finish(it) }
        }
    }

    /** Như [present] nhưng cho App Open; activity rời foreground thì trả ad về kho, báo Skipped. */
    private fun presentAppOpen(
        activity: Activity,
        adName: String,
        ad: AppOpenAd,
        onState: (AdState) -> Unit,
        onFinished: (AdState) -> Unit,
    ) {
        val slot = AdCache.appOpen(adName)
        if (!isForeground(activity)) {
            Log.d(TAG, "[$adName] app open skip: activity không ở foreground")
            slot.ad = ad
            onFinished(AdState.Skipped)
            return
        }
        ad.setOnPaidEventListener { runCatching { onState(AdState.Paid) } }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
                runCatching { onState(AdState.Show) }
            }

            override fun onAdClicked() {
                runCatching { onState(AdState.Click) }
                notifyClick(adName)
            }

            override fun onAdDismissedFullScreenContent() {
                slot.ad = null
                showingFullScreen = false
                // Cooldown dùng chung cho MỌI full-screen (như [showAppOpen]): thiếu dòng này thì
                // App Open ở splash vừa đóng, tap một cái là ăn tiếp interstitial.
                lastInterShownAt = System.currentTimeMillis()
                preloadAppOpen(activity, adName)
                onFinished(AdState.Close)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "[$adName] app open show failed: ${error.message}")
                slot.ad = null
                showingFullScreen = false
                preloadAppOpen(activity, adName)
                runCatching { onState(AdState.Error) }
                onFinished(AdState.Close)
            }
        }
        slot.ad = null
        // Bật cờ NGAY khi gọi show, không đợi onAdShowedFullScreenContent: giữa hai thời điểm đó
        // (vài trăm ms) một lượt App Open khác vẫn thấy cờ false và hiện chồng lên.
        showingFullScreen = true
        ad.show(activity)
    }

    /**
     * Hiện App Open [adName] nếu đã sẵn, chưa hết hạn, không đang có full-screen khác và qua
     * cooldown; sau đó gọi [onClosed]. Nếu không hiện được thì gọi [onClosed] ngay (không chặn
     * user) và preload cho lượt sau.
     */
    fun showAppOpen(activity: Activity, adName: String, onClosed: () -> Unit) {
        if (!enabled()) {
            Log.d(TAG, "[$adName] app open skip: ads disabled")
            onClosed()
            return
        }
        if (showingFullScreen) {
            Log.d(TAG, "[$adName] app open skip: another full-screen ad showing")
            onClosed()
            return
        }
        if (!isForeground(activity)) {
            Log.d(TAG, "[$adName] app open skip: activity không ở foreground")
            onClosed()
            return
        }
        // App Open KHÔNG bị chặn bởi cooldown của interstitial — quay lại app thì nên hiện ngay.
        // Việc chống chồng đã có cờ showingFullScreen lo; tần suất do Google kiểm soát.
        val slot = AdCache.appOpen(adName)
        val now = System.currentTimeMillis()
        val ad = slot.ad
        val expired = ad != null && now - slot.loadedAt >= APP_OPEN_EXPIRY_MS
        if (ad == null || expired) {
            Log.d(TAG, "[$adName] app open not ready (loaded=${ad != null}, expired=$expired) -> preload")
            slot.ad = null
            preloadAppOpen(activity, adName)
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
            }

            override fun onAdClicked() {
                notifyClick(adName)
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
        // Bật cờ NGAY khi gọi show, không đợi onAdShowedFullScreenContent: giữa hai thời điểm đó
        // (vài trăm ms) một lượt App Open khác vẫn thấy cờ false và hiện chồng lên.
        showingFullScreen = true
        ad.show(activity)
    }

    // ---------------------------------------------------------------- rewarded

    fun isRewardedReady(adName: String): Boolean = AdCache.rewarded(adName).ad != null

    private fun preloadRewarded(context: Context, adName: String) {
        if (!enabled()) return
        val slot = AdCache.rewarded(adName)
        if (slot.ad != null || slot.loading) return
        slot.loading = true
        RewardedAd.load(
            context.applicationContext,
            unitId(adName),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    slot.ad = loaded
                    slot.loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] rewarded load failed: ${error.message}")
                    slot.ad = null
                    slot.loading = false
                }
            }
        )
    }

    /**
     * Hiện Rewarded [adName]. [onReward] CHỈ được gọi khi người dùng xem đủ và nhận thưởng;
     * [onClosed] luôn được gọi khi ad đóng (hoặc khi không hiện được). Nếu chưa sẵn/không bật
     * thì preload cho lượt sau rồi gọi [onClosed] ngay (KHÔNG phát thưởng).
     */
    fun showRewarded(
        activity: Activity,
        adName: String,
        onReward: () -> Unit,
        onClosed: () -> Unit,
    ) {
        if (!enabled()) {
            onClosed()
            return
        }
        val slot = AdCache.rewarded(adName)
        val ad = slot.ad
        if (ad == null || showingFullScreen || !isForeground(activity)) {
            if (ad == null) preloadRewarded(activity, adName)
            onClosed()
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                showingFullScreen = true
            }

            override fun onAdClicked() {
                notifyClick(adName)
            }

            override fun onAdDismissedFullScreenContent() {
                slot.ad = null
                showingFullScreen = false
                preloadRewarded(activity, adName)
                if (earned) onReward()
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "[$adName] rewarded show failed: ${error.message}")
                slot.ad = null
                showingFullScreen = false
                preloadRewarded(activity, adName)
                onClosed()
            }
        }
        showingFullScreen = true // xem chú thích ở present()
        ad.show(activity, OnUserEarnedRewardListener { earned = true })
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
                slot.loadedAt = System.currentTimeMillis()
                slot.loading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "[$adName] native load failed: ${error.message}")
                    slot.loading = false
                }

                override fun onAdClicked() {
                    notifyClick(adName)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Lấy native đã preload ra dùng — **chuyển quyền sở hữu** cho caller (caller phải `destroy()`
     * khi xong) rồi tự nạp lượt kế vào cache. Trả null nếu chưa có sẵn (caller tự load inline).
     *
     * Ad nằm trong kho quá [NATIVE_EXPIRY_MS] bị huỷ và coi như kho trống: process sống lâu mà
     * người dùng chưa tới vị trí đó (vd dialog thoát) thì ad preload đã cũ.
     */
    fun acquireNative(context: Context, adName: String): GmsNativeAd? {
        if (!enabled()) return null
        val slot = AdCache.nat(adName)
        var ad = slot.ad
        slot.ad = null
        if (ad != null && System.currentTimeMillis() - slot.loadedAt >= NATIVE_EXPIRY_MS) {
            Log.d(TAG, "[$adName] native trong kho đã quá hạn -> bỏ")
            ad.destroy()
            ad = null
        }
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

                override fun onAdClicked() {
                    notifyClick(adName)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    // ---------------------------------------------------------------- banner (inline)

    fun bannerUnitId(adName: String): String? = if (enabled()) unitId(adName) else null
}
