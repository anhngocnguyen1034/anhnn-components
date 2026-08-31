package com.anhnn.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Các chủ đề phản hồi mặc định của [FeedbackDialog]. */
val DefaultFeedbackTags: List<String> = listOf(
    "Bugs / Crashes",
    "UI Design",
    "Performance",
    "Missing Features",
    "Ads",
    "Hard to Use",
    "Other",
)

/**
 * Dialog thu thập phản hồi: chọn nhanh chủ đề bằng chip + ô nhập nội dung, gửi xong hiện màn
 * cảm ơn rồi tự đóng.
 *
 * Khác [FeedbackScreen] (một màn hình đầy đủ, gửi email): đây là dialog nổi, trả nội dung về qua
 * [onSubmit] nên app tự quyết định gửi đi đâu (email, backend, analytics…):
 * ```
 * if (showFeedback) {
 *     FeedbackDialog(
 *         onSubmit = { text -> Analytics.logEvent("submit_feedback", mapOf("content" to text)) },
 *         onDismiss = { showFeedback = false },
 *     )
 * }
 * ```
 *
 * Nội dung trả về đã gộp chủ đề đã chọn vào đầu chuỗi, dạng `"[UI Design, Ads] chữ user nhập"`.
 * Nút gửi chỉ bật khi user đã chọn chủ đề hoặc đã nhập chữ. Toàn bộ màu lấy từ
 * [MaterialTheme.colorScheme] nên tự ăn theme của app.
 *
 * @param onSubmit             nhận nội dung phản hồi đã gộp; chỉ gọi khi nội dung khác rỗng.
 * @param onDismiss            đóng dialog (bấm ra ngoài, nút Back, hoặc sau màn cảm ơn).
 * @param title                tiêu đề dialog.
 * @param subtitle             dòng mô tả dưới tiêu đề; null = ẩn.
 * @param tags                 danh sách chủ đề chọn nhanh; rỗng = ẩn phần chip.
 * @param hint                 placeholder của ô nhập nội dung.
 * @param submitText           nhãn nút gửi.
 * @param dismissOnOutside     cho phép đóng khi bấm ra ngoài / nút Back.
 * @param thanksTitle          tiêu đề màn cảm ơn; null = gửi xong đóng luôn, không hiện cảm ơn.
 * @param thanksMessage        nội dung màn cảm ơn.
 * @param thanksDurationMillis thời gian giữ màn cảm ơn trước khi tự đóng.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Anything that can be improved?",
    subtitle: String? = "Select topics that apply",
    tags: List<String> = DefaultFeedbackTags,
    hint: String = "Your feedback (Optional)",
    submitText: String = "Submit",
    dismissOnOutside: Boolean = true,
    thanksTitle: String? = "Thank you!",
    thanksMessage: String = "We appreciate your feedback",
    thanksDurationMillis: Long = 1_500,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val selected = remember(tags) { mutableStateListOf(*Array(tags.size) { false }) }
    var showThanks by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnOutside,
            dismissOnClickOutside = dismissOnOutside,
        ),
    ) {
        AnimatedContent(
            targetState = showThanks,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "feedback-content",
        ) { thanks ->
            if (thanks) {
                ThanksCard(title = thanksTitle.orEmpty(), message = thanksMessage)
                LaunchedEffect(Unit) {
                    delay(thanksDurationMillis)
                    onDismiss()
                }
            } else {
                FeedbackCard(
                    title = title,
                    subtitle = subtitle,
                    tags = tags,
                    selected = selected,
                    text = text,
                    hint = hint,
                    submitText = submitText,
                    onToggleTag = { index -> selected[index] = !selected[index] },
                    onTextChange = { text = it },
                    onSubmit = {
                        val payload = buildFeedback(tags, selected, text)
                        if (payload.isNotEmpty()) onSubmit(payload)
                        if (thanksTitle != null) showThanks = true else onDismiss()
                    },
                )
            }
        }
    }
}

/** Gộp chủ đề đã chọn và chữ user nhập thành một chuỗi: `"[tag1, tag2] nội dung"`. */
internal fun buildFeedback(tags: List<String>, selected: List<Boolean>, text: String): String =
    buildString {
        val picked = tags.filterIndexed { index, _ -> selected.getOrElse(index) { false } }
        if (picked.isNotEmpty()) append(picked.joinToString(", ", "[", "] "))
        append(text)
    }.trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackCard(
    title: String,
    subtitle: String?,
    tags: List<String>,
    selected: List<Boolean>,
    text: String,
    hint: String,
    submitText: String,
    onToggleTag: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )

            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEachIndexed { index, tag ->
                        FilterChip(
                            selected = selected.getOrElse(index) { false },
                            onClick = { onToggleTag(index) },
                            label = { Text(tag, style = MaterialTheme.typography.bodySmall) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                maxLines = 5,
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSubmit,
                enabled = selected.any { it } || text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(submitText)
            }
        }
    }
}

@Composable
internal fun ThanksCard(title: String, message: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
