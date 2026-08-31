package com.anhnn.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * @param headerEmoji          emoji trong huy hiệu tròn ở đầu dialog; null = ẩn cả vùng đầu.
 * @param tags                 danh sách chủ đề chọn nhanh; rỗng = ẩn phần chip.
 * @param hint                 placeholder của ô nhập nội dung.
 * @param submitText           nhãn nút gửi.
 * @param dismissOnOutside     cho phép đóng khi bấm ra ngoài / nút Back.
 * @param thanksTitle          tiêu đề màn cảm ơn; null = gửi xong đóng luôn, không hiện cảm ơn.
 * @param thanksMessage        nội dung màn cảm ơn.
 * @param thanksDurationMillis thời gian giữ màn cảm ơn trước khi tự đóng.
 */
@Composable
fun FeedbackDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Anything that can be improved?",
    subtitle: String? = "Select topics that apply",
    headerEmoji: String? = "💬",
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
            // Cửa sổ full-screen: chiều cao cửa sổ không đổi khi nội dung dài ra nên không bị
            // giật; scrim và vùng bấm-ra-ngoài do DialogScaffold tự lo.
            usePlatformDefaultWidth = false,
        ),
    ) {
        DialogScaffold(onScrimClick = if (dismissOnOutside) onDismiss else null) {
            AnimatedContent(
                targetState = showThanks,
                // `using null` để AnimatedContent không animate size — chiều cao do
                // animateContentSize trong DialogScaffold lo, tránh 2 animation đánh nhau.
                transitionSpec = { fadeIn() togetherWith fadeOut() using null },
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
                        headerEmoji = headerEmoji,
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
    headerEmoji: String?,
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (headerEmoji != null) {
                HeroHeader { EmojiBadge(emoji = headerEmoji) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (headerEmoji != null) 20.dp else 28.dp,
                        bottom = 20.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

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

                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = submitText,
                    enabled = selected.any { it } || text.isNotBlank(),
                    onClick = onSubmit,
                )
            }
        }
    }
}

/**
 * Khung chung cho dialog: cửa sổ full-screen nên **kích thước cửa sổ không đổi**, chỉ card bên
 * trong co giãn — nhờ vậy các bước chuyển (hiện màn cảm ơn, bàn phím bật lên) chạy mượt thay vì
 * giật theo từng lần cửa sổ đo lại.
 *
 * Mọi thay đổi chiều cao đi qua đúng một [animateContentSize] để các animation không chồng nhau.
 *
 * @param onScrimClick bấm ra ngoài card thì gọi; null = không cho đóng bằng cách bấm ngoài.
 */
@Composable
private fun DialogScaffold(onScrimClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .then(
                if (onScrimClick != null) {
                    Modifier.clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onScrimClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp)
                // Nuốt tap để bấm vào card không lọt xuống scrim. Dùng pointerInput thay
                // clickable để không tạo thêm node "nút" thừa cho TalkBack.
                .pointerInput(Unit) { detectTapGestures {} }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                ),
        ) {
            content()
        }
    }
}

/** Vùng đầu dialog: dải gradient tông primary, huy hiệu emoji nổi ở giữa. */
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

@Composable
private fun EmojiBadge(emoji: String) {
    Surface(
        modifier = Modifier.size(84.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = 40.sp)
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

/** Ô nhập nội dung nền tonal, bo 16dp, viền nhạt để không "cắt" khối card. */
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
internal fun ThanksCard(title: String, message: String) {
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
