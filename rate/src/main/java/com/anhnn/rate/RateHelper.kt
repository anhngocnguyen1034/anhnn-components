package com.anhnn.rate

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Kích hoạt luồng In-App Review của Google Play.
 * Nếu không khả dụng (môi trường test hoặc lỗi), callback [onFallback] được gọi.
 *
 * @param activity    Activity hiện tại.
 * @param onFallback  Gọi khi không thể hiển thị In-App Review (mở Store thay thế).
 */
fun requestInAppReview(activity: Activity, onFallback: () -> Unit = {}) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { request ->
        if (request.isSuccessful) {
            manager.launchReviewFlow(activity, request.result)
                .addOnCompleteListener { /* flow finished, no action needed */ }
        } else {
            onFallback()
        }
    }
}
