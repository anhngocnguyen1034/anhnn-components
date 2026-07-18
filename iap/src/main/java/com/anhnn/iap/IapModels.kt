package com.anhnn.iap

import androidx.compose.runtime.Immutable

/** Loại sản phẩm Play Billing. */
enum class IapProductType { INAPP, SUBS }

/** Trạng thái kết nối tới Google Play Billing. */
enum class IapConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Thông tin một sản phẩm (giá, mô tả) lấy từ Play — dùng để hiển thị.
 *
 * - [formattedPrice]: giá đã format sẵn theo tiền tệ thiết bị (vd "39.000₫", "$4.99").
 *   Với subscription là giá của pha đầu tiên trong offer đầu tiên.
 * - [offers]: chỉ có ở subscription (mỗi base-plan/offer là 1 phần tử). Sản phẩm mua-một-lần
 *   có `offers` rỗng.
 */
@Immutable
data class IapProduct(
    val productId: String,
    val type: IapProductType,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val offers: List<IapOffer> = emptyList(),
)

/** Một offer/base-plan của subscription. [offerToken] truyền lại vào [IapManager.subscribe]. */
@Immutable
data class IapOffer(
    val offerToken: String,
    val basePlanId: String?,
    val offerId: String?,
    val tags: List<String>,
    val pricingPhases: List<IapPricingPhase>,
)

/** Một pha giá của subscription (vd: dùng thử miễn phí → giá khuyến mãi → giá thường). */
@Immutable
data class IapPricingPhase(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    /** Chu kỳ tính phí ISO-8601, vd "P1M" (1 tháng), "P1Y" (1 năm), "P1W". */
    val billingPeriod: String,
    val billingCycleCount: Int,
    val recurrenceMode: Int,
)

/** Bản ghi một giao dịch mua đã sở hữu. */
@Immutable
data class IapPurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    val orderId: String?,
    val purchaseTime: Long,
    /** 0 = UNSPECIFIED_STATE, 1 = PURCHASED, 2 = PENDING (theo Purchase.PurchaseState). */
    val purchaseState: Int,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val originalJson: String,
    val signature: String,
) {
    /** Chỉ giao dịch đã PURCHASED mới trao quyền. */
    val isPurchased: Boolean get() = purchaseState == 1
}
