package com.anhnn.analytics

/**
 * Nơi nhận event ngoài Firebase (vd gửi backend riêng, log...). App tự cài thêm qua
 * [AnalyticsConfig.extraSinks]. Mỗi event đã hiện gửi tới Firebase cũng được chuyển tiếp tới
 * các sink này (tên + params đã chuẩn hóa).
 */
fun interface AnalyticsSink {
    fun event(name: String, params: Map<String, Any?>)
}

/**
 * Cấu hình hệ analytics dùng chung. App agnostic — module không biết event cụ thể của app nào;
 * app tự khai báo tên event và gọi [Analytics.logEvent].
 *
 * @param firebaseEnabled    bật sink Firebase Analytics mặc định (cần google-services.json).
 * @param extraSinks         sink bổ sung do app cấp (vd backend riêng).
 * @param enabledByDefault   trạng thái bật/tắt thu thập ban đầu (gắn với consent qua [Analytics.setEnabled]).
 */
class AnalyticsConfig(
    val firebaseEnabled: Boolean = true,
    val extraSinks: List<AnalyticsSink> = emptyList(),
    val enabledByDefault: Boolean = true,
)
