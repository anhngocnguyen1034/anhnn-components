package com.anhnn.exit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Bộ xử lý thoát app dùng-một-dòng (giống `askExitApp` của Taymay ADX), nhưng thuần Compose,
 * độc lập hệ quảng cáo, và hiển thị **một màn full-screen** (không phải dialog nhỏ) nên gắn
 * được nhiều quảng cáo.
 *
 * Tự bắt nút Back, hiện [ExitScreen] rồi gọi [onExit] khi user xác nhận. Quảng cáo truyền vào
 * qua slot [adContent] (nhận [ColumnScope]) — xếp bao nhiêu ad cũng được, vùng ad tự cuộn:
 * ```
 * ExitAppHandler(
 *     onExit = { activity.finish() },
 *     adContent = {
 *         NativeAdCard(adName = "exit_native_1")
 *         NativeAdCard(adName = "exit_native_2")
 *         BannerAd(adName = "exit_banner")
 *     },
 * )
 * ```
 *
 * @param onExit      chạy khi user xác nhận thoát (vd `activity.finish()`).
 * @param enabled     bật/tắt chặn Back.
 * @param appIcon     icon app hiện ở đầu màn (vd `painterResource(R.mipmap.ic_launcher)`); null = ẩn.
 * @param appName     tên app hiện cạnh icon; null = ẩn.
 * @param title       tiêu đề màn.
 * @param message     nội dung màn.
 * @param confirmText nhãn nút xác nhận thoát.
 * @param dismissText nhãn nút ở lại.
 * @param topContent  slot cố định trên cùng màn (vd banner ad) — mặc định rỗng.
 * @param adContent   slot quảng cáo (ColumnScope) ở giữa, tự cuộn — mặc định rỗng.
 */
@Composable
fun ExitAppHandler(
    onExit: () -> Unit,
    enabled: Boolean = true,
    appIcon: Painter? = null,
    appName: String? = null,
    title: String = "Exit app?",
    message: String = "Are you sure you want to exit?",
    confirmText: String = "Exit",
    dismissText: String = "Stay",
    topContent: @Composable () -> Unit = {},
    adContent: @Composable ColumnScope.() -> Unit = {},
) {
    var show by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = enabled) { show = true }
    if (show) {
        ExitScreen(
            appIcon = appIcon,
            appName = appName,
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            topContent = topContent,
            adContent = adContent,
            onConfirm = {
                show = false
                onExit()
            },
            onDismiss = { show = false },
        )
    }
}

/**
 * Màn xác nhận thoát full-screen, thuần UI. Dựng bằng full-screen [Dialog] nên luôn nằm trên
 * cùng và không phụ thuộc Navigation. Dùng [MaterialTheme.colorScheme] nên tự ăn theme của app.
 *
 * Vùng giữa màn dành cho quảng cáo ([adContent], nhận [ColumnScope]) và tự cuộn khi nhiều ad.
 */
@Composable
fun ExitScreen(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    appIcon: Painter? = null,
    appName: String? = null,
    topContent: @Composable () -> Unit = {},
    adContent: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                topContent()

                if (appIcon != null || appName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (appIcon != null) {
                            Image(
                                painter = appIcon,
                                contentDescription = appName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                        if (appName != null) {
                            Text(
                                text = appName,
                                color = colors.onBackground,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.onBackground.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )

                // Vùng quảng cáo: chiếm hết không gian còn lại, cuộn khi nhiều ad.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = adContent,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
                    ) {
                        Text(text = dismissText, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Text(text = confirmText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
