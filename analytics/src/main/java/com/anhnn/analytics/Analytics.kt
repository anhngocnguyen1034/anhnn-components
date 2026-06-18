package com.anhnn.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Cổng vào analytics dùng chung cho mọi app (hạ tầng — không chứa event cụ thể).
 * Mặc định bắn **Firebase Analytics**; app có thể thêm sink riêng qua [AnalyticsConfig].
 *
 * App tự định nghĩa danh sách event của mình rồi gọi:
 * ```
 * Analytics.init(context)                      // 1 lần, vd Application.onCreate
 * Analytics.logEvent("chart_create", mapOf("gender" to g))
 * Analytics.setUserProperty("theme", "dark")
 * ```
 * Tên/khóa được chuẩn hóa theo ràng buộc Firebase (snake_case, cắt độ dài) trước khi gửi.
 */
object Analytics {

    private const val MAX_EVENT_NAME = 40
    private const val MAX_PARAM_KEY = 40
    private const val MAX_STRING_VALUE = 100
    private const val MAX_USER_PROP_VALUE = 36

    @Volatile private var enabled: Boolean = true
    private var firebase: FirebaseAnalytics? = null
    private var sinks: List<AnalyticsSink> = emptyList()

    /** Khởi tạo. Gọi 1 lần (vd `Application.onCreate`). */
    fun init(context: Context, config: AnalyticsConfig = AnalyticsConfig()) {
        if (config.firebaseEnabled) {
            firebase = FirebaseAnalytics.getInstance(context.applicationContext)
        }
        sinks = config.extraSinks
        enabled = config.enabledByDefault
        firebase?.setAnalyticsCollectionEnabled(enabled)
    }

    /** Bật/tắt thu thập (gắn với consent / mua gói no-track). */
    fun setEnabled(value: Boolean) {
        enabled = value
        firebase?.setAnalyticsCollectionEnabled(value)
    }

    /** Ghi 1 event. [params] hỗ trợ String/Long/Int/Double/Boolean; kiểu khác sẽ toString(). */
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val cleanName = sanitizeName(name)
        val clean = sanitizeParams(params)
        firebase?.logEvent(cleanName, clean.toBundle())
        sinks.forEach { runCatching { it.event(cleanName, clean) } }
    }

    /** Ghi event màn hình (Firebase `screen_view`). Gọi khi điều hướng đổi màn. */
    fun setScreen(screenName: String, screenClass: String? = null) {
        logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            mapOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                FirebaseAnalytics.Param.SCREEN_CLASS to (screenClass ?: screenName),
            )
        )
    }

    /** Đặt user property (vd theme, locale). null = xóa. */
    fun setUserProperty(key: String, value: String?) {
        if (!enabled) return
        firebase?.setUserProperty(sanitizeKey(key), value?.take(MAX_USER_PROP_VALUE))
    }

    // ------------------------------------------------------------------ sanitize

    private fun sanitizeName(name: String): String =
        name.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").take(MAX_EVENT_NAME)

    private fun sanitizeKey(key: String): String =
        key.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").take(MAX_PARAM_KEY)

    private fun sanitizeParams(params: Map<String, Any?>): Map<String, Any> =
        params.entries.mapNotNull { (k, v) ->
            val value = v ?: return@mapNotNull null
            sanitizeKey(k) to value
        }.toMap()

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (k, v) ->
            when (v) {
                is String -> putString(k, v.take(MAX_STRING_VALUE))
                is Long -> putLong(k, v)
                is Int -> putLong(k, v.toLong())
                is Boolean -> putString(k, v.toString())
                is Double -> putDouble(k, v)
                is Float -> putDouble(k, v.toDouble())
                else -> putString(k, v.toString().take(MAX_STRING_VALUE))
            }
        }
    }
}
