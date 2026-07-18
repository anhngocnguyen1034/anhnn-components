package com.anhnn.iap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Điểm vào duy nhất của module IAP — bọc Google Play Billing (v7).
 *
 * Vòng đời điển hình:
 * 1. `IapManager.init(context, IapConfig(nonConsumableIds = listOf("remove_ads"), ...))` — 1 lần
 *    trong `Application.onCreate`. Tự kết nối Play, query giá + khôi phục sở hữu.
 * 2. Đọc [isPremium] (StateFlow) để bật/tắt quảng cáo: `AdsConfig(adsEnabled = { !IapManager.isPremium.value })`.
 * 3. `IapManager.purchase(activity, "remove_ads")` khi người dùng bấm mua.
 * 4. Kết quả về qua [isPremium] / [IapListener.onPurchased].
 *
 * Toàn bộ callback/StateFlow cập nhật trên main thread.
 */
object IapManager {

    private const val TAG = "IapManager"

    private lateinit var appContext: Context
    private lateinit var config: IapConfig
    private var entitlements: Entitlements? = null
    private var billingClient: BillingClient? = null
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<IapListener>()

    /** Cache ProductDetails theo productId để launch billing flow. */
    private val productDetailsMap = HashMap<String, ProductDetails>()

    /** Bucket sở hữu premium theo loại (INAPP/SUBS) — nguồn để hợp nhất tính [isPremium]. */
    private val lastOwned = HashMap<String, Set<String>>()

    private var reconnectAttempts = 0
    private var initialized = false

    // ---- State công khai ----

    private val _isPremium = MutableStateFlow(false)
    /** True nếu đang sở hữu bất kỳ non-consumable/subscription nào. Bền qua SharedPreferences. */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _products = MutableStateFlow<Map<String, IapProduct>>(emptyMap())
    /** productId → thông tin giá/mô tả để hiển thị. */
    val products: StateFlow<Map<String, IapProduct>> = _products.asStateFlow()

    private val _connectionState = MutableStateFlow(IapConnectionState.DISCONNECTED)
    val connectionState: StateFlow<IapConnectionState> = _connectionState.asStateFlow()

    // ---- Khởi tạo ----

    /** Gọi 1 lần (idempotent). Kết nối Play, query giá + sở hữu. */
    @Synchronized
    fun init(context: Context, config: IapConfig) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        this.config = config
        val store = Entitlements(appContext)
        entitlements = store
        _isPremium.value = store.ownedIds().isNotEmpty()
        // Seed bucket theo loại từ cache để giảm nháy trước khi Play trả snapshot có thẩm quyền.
        for (id in store.ownedIds()) {
            val key = ownedKey(id)
            lastOwned[key] = (lastOwned[key] ?: emptySet()) + id
        }

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        connect()
    }

    fun addListener(listener: IapListener) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: IapListener) { listeners.remove(listener) }

    // ---- Kết nối ----

    private fun connect() {
        val client = billingClient ?: return
        if (client.isReady) return
        _connectionState.value = IapConnectionState.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                log("onBillingSetupFinished ok=$ok code=${result.responseCode}")
                _connectionState.value =
                    if (ok) IapConnectionState.CONNECTED else IapConnectionState.ERROR
                dispatch { it.onConnected(ok, result.responseCode) }
                if (ok) {
                    reconnectAttempts = 0
                    queryProducts()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                log("onBillingServiceDisconnected")
                _connectionState.value = IapConnectionState.DISCONNECTED
                retryConnect()
            }
        })
    }

    private fun retryConnect() {
        if (reconnectAttempts >= MAX_RECONNECT) return
        reconnectAttempts++
        val delay = (1000L shl (reconnectAttempts - 1)).coerceAtMost(30_000L)
        main.postDelayed({ connect() }, delay)
    }

    // ---- Query giá ----

    /** Query lại giá/mô tả sản phẩm (in-app + subscription). */
    fun queryProducts() {
        queryDetails(BillingClient.ProductType.INAPP, config.inAppIds)
        queryDetails(BillingClient.ProductType.SUBS, config.subscriptionIds)
    }

    private fun queryDetails(type: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(type)
                    .build()
            })
            .build()
        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                log("queryProductDetails($type) failed code=${result.responseCode}")
                return@queryProductDetailsAsync
            }
            val current = _products.value.toMutableMap()
            for (details in detailsList) {
                productDetailsMap[details.productId] = details
                current[details.productId] = details.toIapProduct()
            }
            main.post {
                _products.value = current
                val snapshot = current.toMap()
                dispatch { it.onPricesUpdated(snapshot) }
            }
        }
    }

    // ---- Mua ----

    /** Mua sản phẩm in-app (mua-một-lần). [offerToken] thường null cho in-app. */
    fun purchase(activity: Activity, productId: String, offerToken: String? = null) =
        launchFlow(activity, productId, offerToken)

    /**
     * Mua subscription. Nếu [offerToken] null, tự chọn offer đầu tiên của gói.
     * Muốn chọn base-plan/offer cụ thể thì truyền [IapOffer.offerToken] lấy từ [products].
     */
    fun subscribe(activity: Activity, productId: String, offerToken: String? = null) =
        launchFlow(activity, productId, offerToken ?: firstOfferToken(productId))

    private fun firstOfferToken(productId: String): String? =
        _products.value[productId]?.offers?.firstOrNull()?.offerToken

    private fun launchFlow(activity: Activity, productId: String, offerToken: String?) {
        val client = billingClient
        val details = productDetailsMap[productId]
        if (client == null || !client.isReady || details == null) {
            log("launchFlow bỏ qua: client sẵn sàng=${client?.isReady} details=${details != null}")
            dispatch { it.onPurchaseFailed(productId, BillingClient.BillingResponseCode.DEVELOPER_ERROR) }
            return
        }
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (offerToken != null) paramsBuilder.setOfferToken(offerToken)
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            dispatch { it.onPurchaseFailed(productId, result.responseCode) }
        }
    }

    /** Mở màn quản lý subscription trên Play Store. */
    fun manageSubscriptions(context: Context, productId: String? = null) {
        val pkg = context.packageName
        val url = if (productId != null) {
            "https://play.google.com/store/account/subscriptions?sku=$productId&package=$pkg"
        } else {
            "https://play.google.com/store/account/subscriptions"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    // ---- Khôi phục sở hữu ----

    /**
     * Query lại các giao dịch đang sở hữu (in-app + subscription) và đồng bộ [isPremium].
     * Đây là snapshot **có thẩm quyền**: nếu subscription đã huỷ, nó không còn trong kết quả
     * và quyền premium bị gỡ tương ứng.
     */
    fun restorePurchases() {
        queryOwned(BillingClient.ProductType.INAPP)
        queryOwned(BillingClient.ProductType.SUBS)
    }

    private fun queryOwned(type: String) {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(type).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onOwnedSnapshot(type, purchases)
            }
        }
    }

    // ---- Xử lý giao dịch ----

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            onNewPurchases(purchases)
        } else {
            dispatch { it.onPurchaseFailed(null, result.responseCode) }
        }
    }

    /** Giao dịch mới từ luồng mua: xử lý ack/consume + bật quyền lạc quan (cộng dồn theo loại). */
    private fun onNewPurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (!accept(purchase)) continue
            for (id in purchase.products.filter { it in config.premiumIds }) {
                val key = ownedKey(id)
                lastOwned[key] = (lastOwned[key] ?: emptySet()) + id
            }
            val info = purchase.toIapPurchase()
            dispatch { it.onPurchased(info) }
        }
        applyOwned(unionOwned())
    }

    /** Snapshot có thẩm quyền cho một loại: THAY THẾ toàn bộ bucket của loại đó. */
    private fun onOwnedSnapshot(type: String, purchases: List<Purchase>) {
        val ownedOfType = mutableSetOf<String>()
        for (purchase in purchases) {
            if (!accept(purchase)) continue
            ownedOfType.addAll(purchase.products.filter { it in config.premiumIds })
            dispatch { it.onPurchased(purchase.toIapPurchase()) }
        }
        lastOwned[type] = ownedOfType
        applyOwned(unionOwned())
    }

    /**
     * Verify + xử lý ack/consume một giao dịch.
     * @return true nếu là giao dịch PURCHASED hợp lệ trao quyền (không phải consumable).
     */
    private fun accept(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        if (!Security.verifyPurchase(config.base64PublicKey, purchase.originalJson, purchase.signature)) {
            log("Bỏ qua giao dịch sai chữ ký: ${purchase.products}")
            return false
        }
        if (purchase.products.any { it in config.consumableIds }) {
            consume(purchase)
            return false
        }
        if (!purchase.isAcknowledged) acknowledge(purchase)
        return true
    }

    private fun unionOwned(): Set<String> = lastOwned.values.flatten().toSet()

    /** Khoá bucket theo loại Billing của [productId]. */
    private fun ownedKey(productId: String): String =
        if (productId in config.subscriptionIds) BillingClient.ProductType.SUBS
        else BillingClient.ProductType.INAPP

    /** Với subscription: sở hữu = có purchase active; đồng bộ lại để phản ánh huỷ gói. */
    private fun applyOwned(owned: Set<String>) {
        val store = entitlements ?: return
        val changed = store.setOwnedIds(owned)
        val premium = owned.isNotEmpty()
        main.post {
            if (_isPremium.value != premium) {
                _isPremium.value = premium
                dispatch { it.onPremiumChanged(premium) }
            } else if (changed) {
                _isPremium.value = premium
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { result -> log("acknowledge code=${result.responseCode}") }
    }

    private fun consume(purchase: Purchase) {
        val client = billingClient ?: return
        client.consumeAsync(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { result, _ -> log("consume code=${result.responseCode}") }
    }

    /** Giải phóng kết nối Billing (thường không cần gọi — giữ suốt vòng đời app). */
    fun release() {
        billingClient?.endConnection()
        billingClient = null
        _connectionState.value = IapConnectionState.DISCONNECTED
        initialized = false
    }

    // ---- Helpers ----

    private inline fun dispatch(crossinline action: (IapListener) -> Unit) {
        main.post { listeners.forEach { runCatching { action(it) } } }
    }

    private fun log(msg: String) {
        if (::config.isInitialized && config.enableLogging) Log.d(TAG, msg)
    }

    private const val MAX_RECONNECT = 5
}

// ---- Mapper Billing → model của module ----

private fun ProductDetails.toIapProduct(): IapProduct {
    val subs = subscriptionOfferDetails
    return if (productType == BillingClient.ProductType.SUBS && !subs.isNullOrEmpty()) {
        val offers = subs.map { offer ->
            IapOffer(
                offerToken = offer.offerToken,
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                tags = offer.offerTags,
                pricingPhases = offer.pricingPhases.pricingPhaseList.map { p ->
                    IapPricingPhase(
                        formattedPrice = p.formattedPrice,
                        priceAmountMicros = p.priceAmountMicros,
                        currencyCode = p.priceCurrencyCode,
                        billingPeriod = p.billingPeriod,
                        billingCycleCount = p.billingCycleCount,
                        recurrenceMode = p.recurrenceMode,
                    )
                },
            )
        }
        val firstPhase = offers.firstOrNull()?.pricingPhases?.firstOrNull()
        IapProduct(
            productId = productId,
            type = IapProductType.SUBS,
            title = title,
            description = description,
            formattedPrice = firstPhase?.formattedPrice.orEmpty(),
            priceAmountMicros = firstPhase?.priceAmountMicros ?: 0L,
            currencyCode = firstPhase?.currencyCode.orEmpty(),
            offers = offers,
        )
    } else {
        val one = oneTimePurchaseOfferDetails
        IapProduct(
            productId = productId,
            type = IapProductType.INAPP,
            title = title,
            description = description,
            formattedPrice = one?.formattedPrice.orEmpty(),
            priceAmountMicros = one?.priceAmountMicros ?: 0L,
            currencyCode = one?.priceCurrencyCode.orEmpty(),
            offers = emptyList(),
        )
    }
}

private fun Purchase.toIapPurchase(): IapPurchase = IapPurchase(
    productIds = products,
    purchaseToken = purchaseToken,
    orderId = orderId,
    purchaseTime = purchaseTime,
    purchaseState = purchaseState,
    isAcknowledged = isAcknowledged,
    isAutoRenewing = isAutoRenewing,
    originalJson = originalJson,
    signature = signature,
)
