package com.anhnn.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Consent (UMP / Google User Messaging Platform) — bắt buộc cho AdMob, nhất là user EEA/UK.
 * Luồng chuẩn của Google: thu thập consent TRƯỚC, rồi mới init Mobile Ads SDK.
 */
internal object AdConsent {

    private const val TAG = "AnhnnAds/Consent"

    fun canRequestAds(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).canRequestAds()

    /** Người dùng ở vùng áp dụng (EEA/UK/CH) ⇒ app BẮT BUỘC có lối vào form privacy options. */
    fun isPrivacyOptionsRequired(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** [onClosed] luôn được gọi đúng 1 lần (kể cả khi form lỗi) để UI gọi không treo. */
    fun showPrivacyOptionsForm(activity: Activity, onClosed: () -> Unit) {
        var done = false
        val finishOnce = {
            if (!done) {
                done = true
                onClosed()
            }
        }
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError != null) {
                    Log.w(TAG, "privacy options form error ${formError.errorCode}: ${formError.message}")
                }
                finishOnce()
            }
        }.onFailure {
            Log.w(TAG, "showPrivacyOptionsForm failed: $it")
            finishOnce()
        }
    }

    /** [onReady] luôn được gọi đúng 1 lần (kể cả khi lỗi) để không chặn flow khởi động ads. */
    fun gather(activity: Activity, onReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        val consentInfo: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)

        var done = false
        val finishOnce = {
            if (!done) {
                done = true
                onReady()
            }
        }

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "form error ${formError.errorCode}: ${formError.message}")
                    }
                    finishOnce()
                }
            },
            { requestError ->
                Log.w(TAG, "consent update failed ${requestError.errorCode}: ${requestError.message}")
                finishOnce()
            }
        )
    }
}
