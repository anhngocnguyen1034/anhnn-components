package com.anhnn.rate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
                ThanksCard(title = thanksTitle.orEmpty(), message = thanksMessage)
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroHeader {
                EmojiBadge(
                    emoji = if (rate > 0) emojis[rate - 1] else emojis[emojis.size / 2],
                    muted = rate == 0,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))
                AnimatedContent(
                    targetState = if (rate > 0) labels[rate - 1] else "",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "rate-label",
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))
                EmojiRow(rate = rate, emojis = emojis, labels = labels, onRateChange = onRateChange)

                AnimatedVisibility(visible = isLowRate) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (tags.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                tags.forEachIndexed { index, tag ->
                                    FeedbackChip(
                                        text = tag,
                                        selected = selected.getOrElse(index) { false },
                                        onClick = { onToggleTag(index) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FeedbackField(value = text, hint = hint, onValueChange = onTextChange)
                    }
                }

                AnimatedVisibility(visible = rate > 0) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(20.dp))
                        PrimaryButton(
                            text = submitText,
                            enabled = !isLowRate || selected.any { it } || text.isNotBlank(),
                            onClick = onSubmit,
                        )
                    }
                }

                if (dismissText != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = dismissText,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Vùng đầu dialog: dải gradient tông primary, nội dung (emoji/icon) nổi ở giữa. */
@Composable
private fun HeroHeader(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .background(
                Brush.verticalGradient(
                    listOf(colors.primaryContainer.copy(alpha = 0.75f), colors.surface),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Emoji lớn đặt trong huy hiệu tròn nổi trên nền gradient. */
@Composable
private fun EmojiBadge(emoji: String, muted: Boolean) {
    Surface(
        modifier = Modifier.size(84.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = emoji,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.7f))
                },
                label = "emoji-badge",
            ) { value ->
                Text(
                    text = value,
                    fontSize = 40.sp,
                    modifier = Modifier.alpha(if (muted) 0.35f else 1f),
                )
            }
        }
    }
}

/** Hàng 5 emoji chấm điểm; mức đang chọn được phóng to và bọc viền tông primary. */
@Composable
private fun EmojiRow(
    rate: Int,
    emojis: List<String>,
    labels: List<String>,
    onRateChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // SpaceBetween thay vì spacedBy: 5 vòng tròn luôn vừa bề ngang dialog kể cả máy 360dp.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emojis.forEachIndexed { index, emoji ->
            val active = rate == index + 1
            val scale by animateFloatAsState(
                targetValue = if (active) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "emoji-scale",
            )
            val container by animateColorAsState(
                targetValue = if (active) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.5f),
                label = "emoji-container",
            )
            val border by animateColorAsState(
                targetValue = if (active) colors.primary else Color.Transparent,
                label = "emoji-border",
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(container)
                    .border(width = 2.dp, color = border, shape = CircleShape)
                    .clickable(onClickLabel = labels.getOrNull(index)) { onRateChange(index + 1) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emoji,
                    fontSize = 24.sp,
                    modifier = Modifier.alpha(if (rate == 0 || active) 1f else 0.45f),
                )
            }
        }
    }
}

/** Chip chọn chủ đề, bo tròn hoàn toàn, bỏ viền khi đang chọn. */
@Composable
private fun FeedbackChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.bodySmall) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceVariant.copy(alpha = 0.5f),
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

/** Ô nhập góp ý nền tonal, bo 16dp, viền nhạt để không "cắt" khối card. */
@Composable
private fun FeedbackField(value: String, hint: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.35f),
            unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.35f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = colors.primary,
        ),
        minLines = 3,
        maxLines = 5,
    )
}

/** Nút hành động chính: cao 54dp, bo 16dp, chữ đậm. */
@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Màn cảm ơn: huy hiệu tick bật lên theo spring rồi tới tiêu đề và lời cảm ơn. */
@Composable
private fun ThanksCard(title: String, message: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CheckBadge()
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
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

@Composable
private fun CheckBadge() {
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check-scale",
    )
    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp),
        )
    }
}
