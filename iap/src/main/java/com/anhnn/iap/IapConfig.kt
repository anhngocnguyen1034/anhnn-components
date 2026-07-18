package com.anhnn.iap

/**
 * Cấu hình IAP do app cung cấp khi gọi [IapManager.init].
 *
 * @param nonConsumableIds sản phẩm mua-một-lần, giữ vĩnh viễn (vd "remove_ads"). Trao quyền premium.
 * @param consumableIds    sản phẩm mua-một-lần dùng-rồi-hết (vd "coins_100"). KHÔNG trao premium.
 * @param subscriptionIds  gói thuê bao (vd "premium_monthly"). Trao quyền premium khi còn hiệu lực.
 * @param base64PublicKey  RSA public key (Play Console → Monetization setup) để verify chữ ký mua
 *                         offline. Bỏ trống thì bỏ qua bước verify.
 * @param enableLogging    bật log debug của module.
 */
class IapConfig(
    val nonConsumableIds: List<String> = emptyList(),
    val consumableIds: List<String> = emptyList(),
    val subscriptionIds: List<String> = emptyList(),
    val base64PublicKey: String = "",
    val enableLogging: Boolean = false,
) {
    /** Các id trao quyền premium (non-consumable + subscription). */
    internal val premiumIds: Set<String> = (nonConsumableIds + subscriptionIds).toSet()

    /** Tất cả id mua-một-lần (in-app). */
    internal val inAppIds: List<String> = nonConsumableIds + consumableIds

    internal fun typeOf(productId: String): IapProductType =
        if (productId in subscriptionIds) IapProductType.SUBS else IapProductType.INAPP
}

/**
 * Lắng nghe các sự kiện IAP. Tất cả callback được gọi trên **main thread**.
 * Cài đặt phương thức nào cần dùng (đều có body mặc định rỗng).
 */
interface IapListener {
    /** Giá/mô tả sản phẩm vừa được cập nhật từ Play. */
    fun onPricesUpdated(products: Map<String, IapProduct>) {}

    /** Mua/khôi phục thành công một sản phẩm hoặc subscription. */
    fun onPurchased(purchase: IapPurchase) {}

    /** Mua thất bại (bị huỷ, lỗi mạng, …). [responseCode] theo BillingClient.BillingResponseCode. */
    fun onPurchaseFailed(productId: String?, responseCode: Int) {}

    /** Trạng thái premium thay đổi (đã lọc, chỉ gọi khi giá trị đổi). */
    fun onPremiumChanged(isPremium: Boolean) {}

    /** Kết nối Play Billing thay đổi. */
    fun onConnected(connected: Boolean, responseCode: Int) {}
}
