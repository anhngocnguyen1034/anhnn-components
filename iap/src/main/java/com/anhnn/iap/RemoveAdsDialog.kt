package com.anhnn.iap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Dialog "Gỡ quảng cáo" dựng sẵn (tuỳ chọn dùng). Tự đọc giá qua [IapManager.products] và bấm mua
 * gọi [IapManager.purchase]. Theme-aware — dùng `MaterialTheme.colorScheme`, không hardcode màu.
 *
 * @param productId id sản phẩm non-consumable gỡ quảng cáo (vd "remove_ads").
 * @param onPurchase gọi khi bấm nút mua — host truyền activity vào [IapManager.purchase].
 *                   Tách ra để module không phụ thuộc cách host lấy Activity.
 */
@Composable
fun RemoveAdsDialog(
    productId: String,
    onDismiss: () -> Unit,
    onPurchase: (productId: String) -> Unit,
    title: String = "Gỡ quảng cáo",
    message: String = "Nâng cấp để trải nghiệm không quảng cáo.",
    buyLabelPrefix: String = "Mua",
    dismissLabel: String = "Để sau",
    restoreLabel: String = "Khôi phục",
) {
    val products by IapManager.products.collectAsStateWithLifecycle()
    val isPremium by IapManager.isPremium.collectAsStateWithLifecycle()
    val product = products[productId]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    isPremium -> Text(
                        "Bạn đã nâng cấp. Cảm ơn bạn!",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    product == null -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Đang tải giá…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    else -> Text(
                        message,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!isPremium) {
                Button(
                    onClick = { onPurchase(productId) },
                    enabled = product != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val price = product?.formattedPrice.orEmpty()
                    Text(if (price.isBlank()) buyLabelPrefix else "$buyLabelPrefix · $price")
                }
            }
        },
        dismissButton = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!isPremium) {
                    TextButton(
                        onClick = { IapManager.restorePurchases() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(restoreLabel) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(dismissLabel)
                }
            }
        },
    )
}
