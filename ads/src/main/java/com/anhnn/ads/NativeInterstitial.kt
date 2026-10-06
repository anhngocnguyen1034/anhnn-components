package com.anhnn.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Native ad chiếm trọn màn, thay cho interstitial.
 *
 * Vì sao dùng thay interstitial: interstitial là ad full-screen do SDK dựng, app không kiểm soát
 * được gì và dễ bị người dùng thấy là "chặn đường". Bản native full màn vẫn do app vẽ nên hợp
 * giao diện, và có nút "Tiếp tục" mở sau [delaySeconds] giây — người dùng luôn thấy lối ra.
 *
 * Không có ad (tắt quảng cáo / nạp hỏng) thì gọi [onContinue] NGAY, không giam người dùng ở
 * một màn trống.
 *
 * @param onContinue chạy khi người dùng bấm "Tiếp tục" (hoặc khi không có ad để hiện).
 * @param delaySeconds số giây trước khi nút "Tiếp tục" bật. 0 = bật ngay.
 * @param continueText nhãn nút; [countdownText] nhận số giây còn lại để tự ghép chuỗi
 *        (module không mang chuỗi dịch nào, app tự lo đa ngôn ngữ).
 */
@Composable
fun NativeInterstitialTransition(
    adName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    delaySeconds: Int = 5,
    continueText: String = "Continue",
    countdownText: (secondsLeft: Int) -> String = { "Continue in ${it}s" },
    adContent: @Composable (com.google.android.gms.ads.nativead.NativeAd) -> Unit = { ad ->
        DefaultNativeInterstitialContent(ad)
    },
) {
    var secondsLeft by remember(adName) { mutableIntStateOf(delaySeconds) }
    var hasAd by remember(adName) { mutableStateOf(false) }

    LaunchedEffect(adName) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            NativeAdContainer(
                adName = adName,
                modifier = Modifier.fillMaxWidth(),
                loading = { NativeAdSkeleton(size = NativeAdSize.MEDIUM) },
            ) { ad ->
                hasAd = true
                adContent(ad)
            }
        }

        if (secondsLeft > 0) {
            TextButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(countdownText(secondsLeft))
            }
        } else {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(continueText)
            }
        }
    }

    // Không có ad để hiện thì đừng bắt người dùng ngồi chờ hết đếm ngược trước một màn trống.
    LaunchedEffect(hasAd, secondsLeft) {
        if (!hasAd && secondsLeft <= 0) onContinue()
    }
}

/** Bố cục mặc định khi app không truyền `adContent` — dùng đúng bộ slot của [NativeAdSlots]. */
@Composable
private fun DefaultNativeInterstitialContent(ad: com.google.android.gms.ads.nativead.NativeAd) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NativeAdChoicesView(modifier = Modifier.align(Alignment.End))
        NativeAdHeadlineView {
            Text(
                text = ad.headline.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        NativeAdBodyView {
            Text(
                text = ad.body.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        NativeAdMediaView(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        // KHÔNG dùng Button: Button nuốt chạm nên SDK (gắn listener vào view của slot) không bao
        // giờ thấy cú bấm ⇒ CTA không mở quảng cáo, click không được ghi nhận. Vẽ "nút" bằng Box.
        NativeAdCallToActionView(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ad.callToAction.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
