package com.anhnn.ads

/** Định dạng quảng cáo — quyết định kho cache + cách nạp/hiện của từng vị trí. */
enum class AdFormat { INTERSTITIAL, NATIVE, BANNER, APP_OPEN }

/**
 * Cấu hình quảng cáo do **app tiêu thụ** cung cấp khi gọi [Ads.init]. Module không phụ thuộc
 * Firebase / mạng riêng của app nào — mọi thứ thay đổi theo app đều bơm vào qua các lambda dưới,
 * nên cùng 1 module dùng lại được cho nhiều app.
 *
 * @param adsEnabled       có bật quảng cáo không (vd đọc từ Remote Config / mua gói no-ads).
 * @param adUnitId         tra ad unit id theo tên vị trí (app tự map sang test/production).
 * @param adFormat         định dạng của từng tên vị trí; trả null nếu tên không hợp lệ.
 * @param interCooldownMs  khoảng cách tối thiểu (ms) giữa 2 interstitial bất kỳ.
 */
class AdsConfig(
    val adsEnabled: () -> Boolean = { true },
    val adUnitId: (adName: String) -> String,
    val adFormat: (adName: String) -> AdFormat?,
    val interCooldownMs: () -> Long = { 30_000L },
)
