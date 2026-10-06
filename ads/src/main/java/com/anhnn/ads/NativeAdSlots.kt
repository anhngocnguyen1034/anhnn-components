package com.anhnn.ads

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.NativeAd as GmsNativeAd

/**
 * Bộ "slot" để app **tự dựng layout native ad bằng Compose**, thay cho hai template cứng
 * [NativeAdSize.SMALL]/[NativeAdSize.MEDIUM] của [NativeAd].
 *
 * Vì sao phải có: template cứng lấy màu từ `MaterialTheme.colorScheme`, hợp với app Material
 * thường nhưng không theo được app có bảng màu riêng (gradient, card bo góc, font riêng...).
 * Với bộ này app muốn xếp thế nào cũng được, miễn mỗi tài sản nằm trong đúng slot của nó.
 *
 * **Luật của AdMob, không phải của module này**: mỗi tài sản (headline, icon, body, CTA, media)
 * phải nằm trong view đã đăng ký với [NativeAdView], và cụm "AdChoices" phải hiện. Đặt chữ
 * headline ra ngoài [NativeAdHeadlineView] thì ad vẫn vẽ ra nhưng **không tính click/doanh thu**
 * và có thể bị coi là vi phạm chính sách.
 *
 * ```
 * NativeAdContainer(adName = "home_native") { ad ->
 *     Row(Modifier.background(AppTheme.colors.surfaceCard)) {
 *         NativeAdIconView { AppImage(ad.icon?.drawable) }
 *         Column {
 *             NativeAdHeadlineView { AppText(ad.headline.orEmpty()) }
 *             NativeAdBodyView { AppText(ad.body.orEmpty()) }
 *         }
 *         NativeAdCallToActionView {
 *             // Box + Text KHÔNG clickable — xem cảnh báo bên dưới.
 *             Box(Modifier.background(AppTheme.colors.accent)) { AppText(ad.callToAction.orEmpty()) }
 *         }
 *     }
 *     NativeAdChoicesView()
 * }
 * ```
 *
 * **Đừng đặt composable clickable (`Button`, `Modifier.clickable`...) trong slot nào.** SDK gắn
 * listener chạm vào view của slot; composable bên trong mà nhận chạm trước thì SDK không thấy
 * gì ⇒ bấm vào CTA không mở quảng cáo và click không được ghi nhận.
 */

/** [NativeAdView] của khối native đang dựng — các slot bên dưới đăng ký view của chúng vào đây. */
val LocalNativeAdView = compositionLocalOf<NativeAdView?> { null }

/**
 * Ad đang được dựng. Cần riêng vì [NativeAdView] KHÔNG có getter trả lại ad đã set, mà
 * [NativeAdMediaView] thì phải lấy `mediaContent` từ ad.
 */
val LocalNativeAd = compositionLocalOf<GmsNativeAd?> { null }

/**
 * Khung ngoài của một native ad tự dựng: lo nạp ad (ưu tiên cache, không có thì load inline),
 * tạo [NativeAdView] bọc ngoài và hủy ad khi rời màn.
 *
 * [content] chỉ được gọi khi đã có ad. Đang nạp thì vẽ [loading] (mặc định: không chiếm chỗ),
 * nạp hỏng / tắt quảng cáo thì không vẽ gì.
 */
@Composable
fun NativeAdContainer(
    adName: String,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = {},
    content: @Composable (GmsNativeAd) -> Unit,
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<GmsNativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(adName) {
        // Ô quảng cáo có thể bị huỷ (đóng dialog) TRƯỚC khi lượt nạp inline về; không có cờ này
        // thì ad về muộn được gán vào state của composition đã chết và không bao giờ bị destroy.
        var disposed = false
        val cached = AdManager.acquireNative(context, adName)
        if (cached != null) {
            nativeAd = cached
            isLoading = false
        } else {
            AdManager.loadNativeNow(
                context = context,
                adName = adName,
                onLoaded = {
                    if (disposed) {
                        it.destroy()
                    } else {
                        nativeAd = it
                        isLoading = false
                    }
                },
                onFailed = { isLoading = false },
            )
        }
        onDispose {
            disposed = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd
    when {
        ad != null -> NativeAdRoot(ad = ad, modifier = modifier) { content(ad) }
        isLoading -> Box(modifier = modifier) { loading() }
    }
}

/**
 * Bọc [content] trong một [NativeAdView] thật.
 *
 * Cách làm: `AndroidView` dựng `NativeAdView` rồi nhét một `ComposeView` vào trong nó —
 * composition con vẫn thấy mọi `CompositionLocal` của cha (kể cả theme của app), nên nội dung
 * viết y như Compose bình thường. Đây cũng là cách mẫu chính thức của Google.
 */
@Composable
private fun NativeAdRoot(
    ad: GmsNativeAd,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val adView = NativeAdView(ctx)
            val composeView = ComposeView(ctx).apply { id = View.generateViewId() }
            // PHẢI đặt WRAP_CONTENT cho chiều cao. NativeAdView là FrameLayout, `addView(view)`
            // dùng layout params mặc định MATCH_PARENT x MATCH_PARENT ⇒ ô quảng cáo nở kín chỗ
            // trống của màn và đẩy hết nội dung thật ra ngoài.
            adView.addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            adView
        },
        update = { adView ->
            val composeView = adView.getChildAt(0) as ComposeView
            composeView.setContent {
                CompositionLocalProvider(
                    LocalNativeAdView provides adView,
                    LocalNativeAd provides ad,
                ) { content() }
            }
            // `post` chứ KHÔNG gọi thẳng: AdMob yêu cầu đăng ký xong MỌI view tài sản rồi mới
            // `setNativeAd`. Nội dung bên trong là một composition con, các slot chỉ đăng ký ở
            // lượt bố cục sau — gọi thẳng ở đây thì `setNativeAd` chạy trước lúc `mediaView`
            // kịp gán, và ảnh/video của quảng cáo ra một khoảng TRỐNG (các tài sản chữ vẫn hiện
            // nên lỗi rất dễ bị bỏ qua).
            adView.post { adView.setNativeAd(ad) }
        },
    )
}

/**
 * Mỗi slot = một `ComposeView` được đăng ký vào [NativeAdView] đúng vai trò của nó.
 * Nội dung bên trong là Compose thuần nên app muốn vẽ gì cũng được.
 */
@Composable
private fun NativeAdAssetView(
    modifier: Modifier,
    register: (NativeAdView, View) -> Unit,
    content: @Composable () -> Unit,
) {
    val adView = LocalNativeAdView.current
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ComposeView(ctx).apply { id = View.generateViewId() } },
        update = { view ->
            view.setContent(content)
            adView?.let { register(it, view) }
        },
    )
}

/** Tiêu đề quảng cáo. BẮT BUỘC có — AdMob không trả ad nếu thiếu headline. */
@Composable
fun NativeAdHeadlineView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.headlineView = v }, content)

/** Mô tả. */
@Composable
fun NativeAdBodyView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.bodyView = v }, content)

/** Icon ứng dụng/thương hiệu. */
@Composable
fun NativeAdIconView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.iconView = v }, content)

/** Nút hành động ("Cài đặt", "Tìm hiểu thêm"...). Đây là chỗ DUY NHẤT tính click hợp lệ. */
@Composable
fun NativeAdCallToActionView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.callToActionView = v }, content)

/** Tên nhà quảng cáo. */
@Composable
fun NativeAdAdvertiserView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.advertiserView = v }, content)

/** Tên cửa hàng (ad kiểu cài app). */
@Composable
fun NativeAdStoreView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.storeView = v }, content)

/** Giá (ad kiểu cài app). */
@Composable
fun NativeAdPriceView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.priceView = v }, content)

/** Điểm đánh giá sao. */
@Composable
fun NativeAdStarRatingView(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    NativeAdAssetView(modifier, { adView, v -> adView.starRatingView = v }, content)

/**
 * Ảnh/video quảng cáo. Khác các slot trên: nội dung do SDK tự vẽ ([MediaView]) nên không nhận
 * `content` — app chỉ quyết định kích thước bằng [modifier].
 */
@Composable
fun NativeAdMediaView(modifier: Modifier = Modifier) {
    val adView = LocalNativeAdView.current
    val ad = LocalNativeAd.current
    AndroidView(
        modifier = modifier,
        factory = { ctx -> MediaView(ctx) },
        update = { media ->
            adView?.mediaView = media
            ad?.mediaContent?.let { media.mediaContent = it }
        },
    )
}

/**
 * Cụm "AdChoices" (biểu tượng ⓘ của Google). **Bắt buộc theo chính sách AdMob** — thiếu nó là
 * vi phạm. SDK tự vẽ và tự đặt vị trí trong khung này.
 */
@Composable
fun NativeAdChoicesView(modifier: Modifier = Modifier) {
    val adView = LocalNativeAdView.current
    AndroidView(
        modifier = modifier,
        factory = { ctx -> com.google.android.gms.ads.nativead.AdChoicesView(ctx) },
        update = { view -> adView?.adChoicesView = view },
    )
}
