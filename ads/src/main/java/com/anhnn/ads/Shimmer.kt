package com.anhnn.ads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Brush gradient quét ngang lặp vô hạn — hiệu ứng "shimmer" thuần Compose (không cần thư viện
 * ngoài). Màu lấy từ [MaterialTheme.colorScheme] nên hợp theme dark/light.
 */
@Composable
internal fun shimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val base = baseColor.copy(alpha = 0.10f)
    val highlight = baseColor.copy(alpha = 0.22f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    val band = 600f
    val start = -band + progress * (2 * band)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + band, 0f),
    )
}

@Composable
private fun ShimmerBlock(modifier: Modifier, brush: Brush, radius: Int = 6) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(brush))
}

/** Khung skeleton cho native ad: icon + headline/advertiser + media + nút CTA. */
@Composable
internal fun NativeAdSkeleton(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.primary.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShimmerBlock(Modifier.size(40.dp), brush, radius = 8)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(14.dp), brush, radius = 4)
                ShimmerBlock(Modifier.fillMaxWidth(0.4f).height(11.dp), brush, radius = 4)
            }
        }
        ShimmerBlock(Modifier.fillMaxWidth().height(120.dp), brush, radius = 10)
        ShimmerBlock(Modifier.fillMaxWidth().height(44.dp), brush, radius = 8)
    }
}

/** Khung skeleton cho banner: dải chiếm đúng chiều cao banner để không nhảy layout. */
@Composable
internal fun BannerAdSkeleton(heightDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(shimmerBrush()),
    )
}
