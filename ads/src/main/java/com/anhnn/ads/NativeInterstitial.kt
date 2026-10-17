package com.anhnn.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Native ad chiếm trọn màn, thay cho interstitial.
 *
 * Vì sao dùng thay interstitial: interstitial là ad full-screen do SDK dựng, app không kiểm soát
 * được gì và dễ bị người dùng thấy là "chặn đường". Bản native full màn vẫn do app vẽ nên hợp
 * giao diện, và có nút "Tiếp tục" mở sau [delaySeconds] giây.
 *
 * Nút đóng ✕ ở góc trên-phải hiện **ngay từ đầu**, không chờ đếm ngược: quảng cáo chiếm trọn màn
 * mà không có lối thoát tức thì là mẫu AdMob coi là ép xem. Nhãn "Ad" do [NativeAdContainer] lo
 * (bố cục mặc định đặt [NativeAdBadge]; `adContent` tự dựng mà quên thì container tự vẽ).
 *
 * Không có ad (tắt quảng cáo / nạp hỏng) thì gọi [onContinue] NGAY, không giam người dùng ở
 * một màn trống.
 *
 * @param onContinue chạy đúng một lần: khi bấm "Tiếp tục" / ✕, hoặc khi không có ad để hiện.
 * @param delaySeconds số giây trước khi nút "Tiếp tục" bật. 0 = bật ngay.
 * @param continueText nhãn nút; [countdownText] nhận số giây còn lại để tự ghép chuỗi
 *        (module không mang chuỗi dịch nào, app tự lo đa ngôn ngữ).
 * @param closeContentDescription mô tả cho TalkBack của nút ✕.
 */
@Composable
fun NativeInterstitialTransition(
    adName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    delaySeconds: Int = 5,
    continueText: String = "Continue",
    countdownText: (secondsLeft: Int) -> String = { "Continue in ${it}s" },
    closeContentDescription: String = "Close",
    adContent: @Composable (com.google.android.gms.ads.nativead.NativeAd) -> Unit = { ad ->
        DefaultNativeInterstitialContent(ad)
    },
) {
    var secondsLeft by remember(adName) { mutableIntStateOf(delaySeconds) }
    var hasAd by remember(adName) { mutableStateOf(false) }
    var failed by remember(adName) { mutableStateOf(false) }
    // ✕, "Tiếp tục" và hai nhánh tự thoát có thể trùng nhau trong một frame — chỉ điều hướng một lần.
    var continued by remember(adName) { mutableStateOf(false) }
    val currentOnContinue by rememberUpdatedState(onContinue)
    val finish = {
        if (!continued) {
            continued = true
            currentOnContinue()
        }
    }

    LaunchedEffect(adName) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NativeAdContainer(
                    adName = adName,
                    modifier = Modifier.fillMaxWidth(),
                    loading = { NativeAdSkeleton(size = NativeAdSize.MEDIUM) },
                    onFailed = { failed = true },
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
                Button(onClick = finish, modifier = Modifier.fillMaxWidth()) {
                    Text(continueText)
                }
            }
        }

        TextButton(
            onClick = finish,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = closeContentDescription },
        ) {
            Text(text = "✕", style = MaterialTheme.typography.titleLarge)
        }
    }

    // Nạp hỏng / tắt quảng cáo: đi tiếp ngay, không bắt ngồi chờ hết đếm ngược trước màn trống.
    LaunchedEffect(failed) {
        if (failed) finish()
    }
    // Hết đếm ngược mà vẫn chưa có ad (mạng chậm) thì cũng không giữ người dùng trước khung chờ.
    LaunchedEffect(hasAd, secondsLeft) {
        if (!hasAd && secondsLeft <= 0) finish()
    }
}

/** Bố cục mặc định khi app không truyền `adContent` — dùng đúng bộ slot của [NativeAdSlots]. */
@Composable
private fun DefaultNativeInterstitialContent(ad: com.google.android.gms.ads.nativead.NativeAd) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeAdBadge()
            NativeAdChoicesView()
        }
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
