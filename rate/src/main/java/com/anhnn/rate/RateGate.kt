package com.anhnn.rate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit

private const val PREFS_FILE = "anhnn_rate"
private const val KEY_RATED = "is_rated"
private const val KEY_SESSIONS = "session_count"

/** Đếm session một lần cho mỗi lần app chạy, không phải mỗi lần gọi hàm. */
@Volatile
private var sessionCounted = false

/** Đã hỏi trong lần chạy app này chưa. */
@Volatile
private var askedThisSession = false

private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

/**
 * Quyết định có nên hiện dialog đánh giá ở thời điểm này không, đồng thời tự đếm session.
 *
 * Trả `false` khi: user đã đánh giá, đã hỏi trong lần chạy app này (nếu [oncePerSession]), hoặc
 * app mới chạy chưa đủ [minSessions] lần. Mặc định `minSessions = 2` nên **lần cài đặt đầu tiên
 * không bao giờ bị hỏi** — hỏi quá sớm là cách nhanh nhất để ăn 1 sao.
 *
 * ```
 * var showRate by rememberSaveable { mutableStateOf(shouldAskRate(context)) }
 * ```
 *
 * @param context        context bất kỳ (tự lấy application context).
 * @param minSessions    số lần mở app tối thiểu trước khi được phép hỏi.
 * @param oncePerSession chỉ hỏi tối đa một lần cho mỗi lần chạy app.
 */
fun shouldAskRate(
    context: Context,
    minSessions: Int = 2,
    oncePerSession: Boolean = true,
): Boolean {
    val prefs = prefs(context)

    if (!sessionCounted) {
        sessionCounted = true
        prefs.edit { putInt(KEY_SESSIONS, prefs.getInt(KEY_SESSIONS, 0) + 1) }
    }

    if (isRated(context)) return false
    if (oncePerSession && askedThisSession) return false
    if (prefs.getInt(KEY_SESSIONS, 0) < minSessions) return false

    askedThisSession = true
    return true
}

/** User đã đánh giá app chưa (đã bấm nút mở Store). */
fun isRated(context: Context): Boolean = prefs(context).getBoolean(KEY_RATED, false)

/**
 * Đánh dấu user đã đánh giá — gọi trong `onRate` để [shouldAskRate] không hỏi lại nữa.
 */
fun setRated(context: Context, rated: Boolean = true) {
    prefs(context).edit { putBoolean(KEY_RATED, rated) }
}

/** Xoá toàn bộ trạng thái đã lưu (cờ đã đánh giá + số session). Hữu ích khi test. */
fun resetRateState(context: Context) {
    sessionCounted = false
    askedThisSession = false
    prefs(context).edit { clear() }
}

/**
 * Mở trang app trên Play Store: thử app Play Store trước (`market://`), không có thì mở trình duyệt.
 *
 * @param context     context dùng để `startActivity`.
 * @param packageName package của app cần mở (mặc định chính app đang chạy).
 */
fun openPlayStore(context: Context, packageName: String = context.packageName) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }
    runCatching { context.startActivity(market) }.onFailure {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            )
        )
    }
}
