package com.anhnn.rate

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Dialog fallback yêu cầu người dùng đánh giá app trên Store.
 *
 * @param packageName   Package name của app (dùng để mở Play Store).
 * @param title         Tiêu đề dialog.
 * @param message       Nội dung dialog.
 * @param confirmText   Nhãn nút xác nhận (mặc định "Rate Now").
 * @param dismissText   Nhãn nút bỏ qua (mặc định "Later").
 * @param onDismiss     Callback khi đóng dialog.
 */
@Composable
fun RateDialog(
    packageName: String,
    title: String = "Enjoying the app?",
    message: String = "Please take a moment to rate us on the Play Store.",
    confirmText: String = "Rate Now",
    dismissText: String = "Later",
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                val uri = Uri.parse("market://details?id=$packageName")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                }
                runCatching { context.startActivity(intent) }.onFailure {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                    )
                }
                onDismiss()
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}
