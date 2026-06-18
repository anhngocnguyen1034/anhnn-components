package com.anhnn.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController

/**
 * Tự bắn `screen_view` mỗi khi [navController] đổi destination. Đặt 1 lần cạnh NavHost:
 * ```
 * TrackScreenViews(navController)
 * ```
 * Tên màn lấy từ route, bỏ phần tham số (`?...` và `/{...}`) cho gọn. Có thể tùy biến tên qua
 * [screenName].
 */
@Composable
fun TrackScreenViews(
    navController: NavController,
    screenName: (route: String?) -> String? = { it?.substringBefore("?")?.substringBefore("/{") },
) {
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val name = screenName(destination.route) ?: return@OnDestinationChangedListener
            Analytics.setScreen(name)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}
