package com.anhnn.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Giữ nguyên trạng thái thanh hệ thống của màn gọi khi mở dialog.
 *
 * `Dialog` của Compose tạo CỬA SỔ RIÊNG; cửa sổ mới không yêu cầu ẩn gì cả nên app đang chạy
 * immersive (ẩn navigation bar) sẽ bị navbar nhảy ra mỗi lần mở dialog rồi mất khi đóng.
 *
 * Cách dùng: gọi [rememberHostBarsState] TRONG composition của màn (trước khi `Dialog` dựng cửa
 * sổ, lúc này insets đọc được vẫn là của màn), rồi gọi [ApplyHostBars] bên TRONG nội dung dialog.
 */
internal class HostBarsState(val navHidden: Boolean, val statusHidden: Boolean) {
    val anyHidden: Boolean get() = navHidden || statusHidden
}

@Composable
internal fun rememberHostBarsState(): HostBarsState {
    val view = LocalView.current
    return remember(view) {
        val insets = ViewCompat.getRootWindowInsets(view)
        HostBarsState(
            navHidden = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) == false,
            statusHidden = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == false,
        )
    }
}

@Composable
internal fun ApplyHostBars(state: HostBarsState) {
    val view = LocalView.current
    SideEffect {
        if (!state.anyHidden) return@SideEffect
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowInsetsControllerCompat(window, view).apply {
            if (state.navHidden) hide(WindowInsetsCompat.Type.navigationBars())
            if (state.statusHidden) hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
