package com.anhnn.ads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Banner ad (adaptive anchored) cho vị trí [adName]. Đo đúng bề rộng container bằng
 * [BoxWithConstraints] rồi xin ad size theo width đó → ad luôn **full width** của khung.
 *
 * Banner load **inline** (không cache như native/interstitial) vì kích thước phụ thuộc bề rộng
 * thật của khung lúc chạy, và cache AdView sẽ giữ tham chiếu context gây leak. Banner vốn nhẹ &
 * load nhanh nên inline là đủ. Tắt ads / chưa init → rỗng.
 *
 * @param onFailedChange báo khi banner nạp hỏng (`true`, banner thu về cao 0) hoặc nạp lại được
 *        ở lượt tự làm mới (`false`) — để app bỏ phần đệm quanh banner, không để lại dải trống.
 */
@Composable
fun BannerAd(
    adName: String,
    modifier: Modifier = Modifier,
    onFailedChange: (failed: Boolean) -> Unit = {},
) {
    val unitId = AdManager.bannerUnitId(adName) ?: return
    val context = LocalContext.current
    val currentOnFailedChange by rememberUpdatedState(onFailedChange)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)

        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }

        var loaded by remember(adName, widthDp) { mutableStateOf(false) }
        var failed by remember(adName, widthDp) { mutableStateOf(false) }

        val adView = remember(adName, widthDp) {
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = unitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        loaded = true
                        // Lượt tự làm mới (60s) của AdView có thể nạp được sau một lần hỏng.
                        if (failed) {
                            failed = false
                            currentOnFailedChange(false)
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Đã có ad đang hiện thì một lượt làm mới hỏng không làm mất ad đó.
                        if (loaded) return
                        failed = true
                        currentOnFailedChange(true)
                    }

                    override fun onAdClicked() {
                        AdManager.notifyClick(adName)
                    }
                }
            }
        }

        DisposableEffect(adView) {
            // AdView mới (đổi bề rộng khung) bắt đầu lại từ trạng thái "chưa hỏng" — báo cho app,
            // nếu không nó vẫn giữ báo "hỏng" của AdView cũ và bỏ phần đệm quanh banner đang hiện.
            currentOnFailedChange(false)
            adView.loadAd(AdRequest.Builder().build())
            onDispose { adView.destroy() }
        }

        // Chiếm sẵn đúng chiều cao banner trong lúc load -> không nhảy layout; fade ad khi xong.
        // Nếu load fail thì thu về 0 để không để lại khoảng trống.
        val reservedHeight = if (failed) 0 else adSize.height
        val adAlpha by animateFloatAsState(if (loaded) 1f else 0f, tween(300), label = "banner-alpha")

        Box(modifier = Modifier.fillMaxWidth().height(reservedHeight.dp)) {
            if (!loaded && !failed) {
                BannerAdSkeleton(heightDp = adSize.height)
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().alpha(adAlpha),
                factory = { adView },
            )
        }
    }
}
