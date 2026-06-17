package com.anhnn.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner ad (adaptive anchored) cho vị trí [adName]. Đo đúng bề rộng container bằng
 * [BoxWithConstraints] rồi xin ad size theo width đó → ad luôn **full width** của khung.
 *
 * Banner load **inline** (không cache như native/interstitial) vì kích thước phụ thuộc bề rộng
 * thật của khung lúc chạy, và cache AdView sẽ giữ tham chiếu context gây leak. Banner vốn nhẹ &
 * load nhanh nên inline là đủ. Tắt ads / chưa init → rỗng.
 */
@Composable
fun BannerAd(adName: String, modifier: Modifier = Modifier) {
    val unitId = AdManager.bannerUnitId(adName) ?: return
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)

        val adView = remember(adName, widthDp) {
            AdView(context).apply {
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
                )
                adUnitId = unitId
            }
        }

        DisposableEffect(adView) {
            adView.loadAd(AdRequest.Builder().build())
            onDispose { adView.destroy() }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { adView },
        )
    }
}
