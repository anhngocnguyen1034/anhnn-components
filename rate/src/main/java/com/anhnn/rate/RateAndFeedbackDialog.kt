package com.anhnn.rate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Emoji mặc định cho 5 mức đánh giá, từ 1 sao đến 5 sao. */
val DefaultRateEmojis: List<String> = listOf("😞", "🙁", "😐", "🙂", "😍")

/** Nhãn mặc định của 5 mức đánh giá, từ 1 sao đến 5 sao. */
val DefaultRateLabels: List<String> = listOf("Terrible", "Bad", "Okay", "Good!", "Great!")

/** Các chủ đề mặc định hiện khi user đánh giá thấp. */
val DefaultRateFeedbackTags: List<String> = listOf(
    "Bugs / Crashes",
    "UI Design",
    "Performance",
    "Missing Features",
    "Ads",
    "Hard to Use",
)

/**
 * Dialog đánh giá 2 nhánh: user chấm 1–5 mức, chấm thấp thì chuyển sang thu thập phản hồi
 * ngay trong app, chấm cao mới đẩy ra Store.
 *
 * Đây là pattern giữ điểm Store: chỉ user hài lòng ([goodRateThreshold] trở lên) mới thấy nút
 * mở Store ([onRate]), user chưa hài lòng thì chọn chủ đề + nhập góp ý và nội dung về qua
 * [onSubmitFeedback], gửi xong hiện màn cảm ơn rồi tự đóng.
 *
 * Dùng cùng [shouldAskRate] và [requestInAppReview] để có luồng đầy đủ:
 * ```
 * var showRate by remember { mutableStateOf(shouldAskRate(context)) }
 * if (showRate) {
 *     RateAndFeedbackDialog(
 *         onRate = {
 *             setRated(context, true)
 *             requestInAppReview(activity) { openStore(activity) }
 *         },
 *         onSubmitFeedback = { text -> Analytics.logEvent("submit_feedback", mapOf("content" to text)) },
 *         onDismiss = { showRate = false },
 *     )
 * }
 * ```
 *
 * Toàn bộ màu lấy từ [MaterialTheme.colorScheme] nên tự ăn theme của app.
 *
 * @param onRate               chạy khi user chấm từ [goodRateThreshold] trở lên và bấm nút Store.
 * @param onDismiss            đóng dialog (bỏ qua, bấm ra ngoài, hoặc sau màn cảm ơn).
 * @param onSubmitFeedback     nhận góp ý đã gộp chủ đề khi user chấm thấp; chỉ gọi khi khác rỗng.
 * @param goodRateThreshold    mức từ đó trở lên coi là hài lòng (mặc định 4/5).
 * @param emojis               emoji của 5 mức; thay bằng bộ khác nếu app có icon riêng.
 * @param labels               nhãn của 5 mức, hiện dưới emoji đang chọn.
 * @param tags                 chủ đề chọn nhanh khi chấm thấp; rỗng = ẩn phần chip.
 * @param title                tiêu đề khi chưa chấm hoặc chấm cao.
 * @param lowRateTitle         tiêu đề khi chấm thấp.
 * @param hint                 placeholder ô nhập góp ý.
 * @param sendText             nhãn nút khi chấm thấp (gửi góp ý).
 * @param rateText             nhãn nút khi chấm cao (mở Store).
 * @param dismissText          nhãn nút bỏ qua; null = ẩn, dialog thành bắt buộc chọn.
 * @param dismissOnOutside     cho phép đóng khi bấm ra ngoài / nút Back.
 * @param thanksTitle          tiêu đề màn cảm ơn; null = gửi xong đóng luôn.
 * @param thanksMessage        nội dung màn cảm ơn.
 * @param thanksDurationMillis thời gian giữ màn cảm ơn trước khi tự đóng.
 */
@Composable
fun RateAndFeedbackDialog(
    onRate: () -> Unit,
    onDismiss: () -> Unit,
    onSubmitFeedback: (String) -> Unit = {},
    goodRateThreshold: Int = 4,
    emojis: List<String> = DefaultRateEmojis,
    labels: List<String> = DefaultRateLabels,
    tags: List<String> = DefaultRateFeedbackTags,
    title: String = "How was your\nexperience with us?",
    lowRateTitle: String = "What is\nsomething we can improve?",
    hint: String = "Your feedback...",
    sendText: String = "Send",
    rateText: String = "Rate us on Google Play",
    dismissText: String? = "Maybe later",
    dismissOnOutside: Boolean = true,
    thanksTitle: String? = "Thank you!",
    thanksMessage: String = "We appreciate your feedback",
    thanksDurationMillis: Long = 1_500,
) {
    var rate by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    val selected = remember(tags) { mutableStateListOf(*Array(tags.size) { false }) }
    var showThanks by rememberSaveable { mutableStateOf(false) }

    val isLowRate = rate in 1 until goodRateThreshold

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
            label = "rate-content",
        ) { thanks ->
            if (thanks) {
                RateThanksCard(title = thanksTitle.orEmpty(), message = thanksMessage)
                LaunchedEffect(Unit) {
                    delay(thanksDurationMillis)
                    onDismiss()
                }
            } else {
                RateCard(
                    rate = rate,
                    isLowRate = isLowRate,
                    emojis = emojis,
                    labels = labels,
                    tags = tags,
                    selected = selected,
                    text = text,
                    title = if (isLowRate) lowRateTitle else title,
                    hint = hint,
                    submitText = if (isLowRate) sendText else rateText,
                    dismissText = dismissText,
                    onRateChange = { rate = it },
                    onToggleTag = { index -> selected[index] = !selected[index] },
                    onTextChange = { text = it },
                    onDismiss = onDismiss,
                    onSubmit = {
                        if (isLowRate) {
                            val payload = buildRateFeedback(tags, selected, text)
                            if (payload.isNotEmpty()) onSubmitFeedback(payload)
                            if (thanksTitle != null) showThanks = true else onDismiss()
                        } else {
                            onRate()
                        }
                    },
                )
            }
        }
    }
}

/** Gộp chủ đề đã chọn và chữ user nhập thành một chuỗi: `"[tag1, tag2] nội dung"`. */
internal fun buildRateFeedback(tags: List<String>, selected: List<Boolean>, text: String): String =
    buildString {
        val picked = tags.filterIndexed { index, _ -> selected.getOrElse(index) { false } }
        if (picked.isNotEmpty()) append(picked.joinToString(", ", "[", "] "))
        append(text)
    }.trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateCard(
    rate: Int,
    isLowRate: Boolean,
    emojis: List<String>,
    labels: List<String>,
    tags: List<String>,
    selected: List<Boolean>,
    text: String,
    title: String,
    hint: String,
    submitText: String,
    dismissText: String?,
    onRateChange: (Int) -> Unit,
    onToggleTag: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
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

            AnimatedVisibility(visible = rate > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Text(text = emojis[rate - 1], fontSize = 44.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = labels[rate - 1],
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            EmojiRow(rate = rate, emojis = emojis, labels = labels, onRateChange = onRateChange)

            AnimatedVisibility(visible = isLowRate) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(hint) },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5,
                    )
                }
            }

            AnimatedVisibility(visible = rate > 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onSubmit,
                        enabled = !isLowRate || selected.any { it } || text.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(submitText)
                    }
                }
            }

            if (dismissText != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(dismissText, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Hàng 5 emoji chấm điểm; emoji chưa đạt tới mức đang chọn bị làm mờ. */
@Composable
private fun EmojiRow(
    rate: Int,
    emojis: List<String>,
    labels: List<String>,
    onRateChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emojis.forEachIndexed { index, emoji ->
            val active = rate > 0 && index < rate
            val scale by animateFloatAsState(if (active) 1.12f else 1f, label = "emoji-scale")
            Text(
                text = emoji,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(if (active) 1f else 0.35f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = labels.getOrNull(index)) { onRateChange(index + 1) },
            )
        }
    }
}

@Composable
private fun RateThanksCard(title: String, message: String) {
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
