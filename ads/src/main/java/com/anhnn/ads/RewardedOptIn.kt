package com.anhnn.ads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Hộp hỏi ý người dùng trước khi hiện Rewarded — xem [Ads.showRewardedWithOptIn].
 *
 * AdMob bắt buộc Rewarded phải do người dùng **chủ động đồng ý** sau khi biết rõ phần thưởng;
 * tự bật Rewarded (vd khi mở màn) là vi phạm. Dựng bằng View thuần cùng lý do với
 * [AdLoadingOverlay]: gọi từ `Activity`, ngoài mọi composition, chạy được ở mọi app tiêu thụ.
 *
 * Khác màn chờ, hộp này CHO bấm back / chạm ra ngoài để huỷ — từ chối phải dễ như đồng ý.
 */
internal object RewardedOptInDialog {

    /**
     * [onConfirm] hoặc [onCancel] chạy đúng một lần. Activity đã chết thì [onCancel] chạy ngay.
     */
    fun show(
        activity: Activity,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onCancel()
            return
        }
        var settled = false
        fun settle(action: () -> Unit) {
            if (settled) return
            settled = true
            action()
        }
        val shown = runCatching {
            val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.setContentView(
                buildContent(
                    activity = activity,
                    title = title,
                    message = message,
                    confirmText = confirmText,
                    cancelText = cancelText,
                    onConfirm = {
                        // dismiss (không phải cancel) ⇒ không kích hoạt onCancelListener bên dưới.
                        runCatching { dialog.dismiss() }
                        settle(onConfirm)
                    },
                    onCancel = { dialog.cancel() },
                    onScrimClick = { dialog.cancel() },
                )
            )
            dialog.setCancelable(true)
            dialog.setOnCancelListener { settle(onCancel) }
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(SCRIM))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                // Giữ immersive của màn gọi — xem chú thích cùng chỗ ở [AdLoadingOverlay].
                runCatching {
                    decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
                }
            }
            dialog.show()
        }.isSuccess
        if (!shown) settle(onCancel)
    }

    private const val SCRIM = 0xB3000000.toInt()
    private const val CARD = 0xFF1C1C1E.toInt()
    private const val ACCENT = 0xFF0A84FF.toInt()

    private fun buildContent(
        activity: Activity,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        onScrimClick: () -> Unit,
    ): View {
        val d = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        fun button(text: String, filled: Boolean, onClick: () -> Unit) = TextView(activity).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(px(16), 0, px(16), 0)
            background = GradientDrawable().apply {
                cornerRadius = px(12).toFloat()
                if (filled) setColor(ACCENT) else setStroke(px(1), 0x66FFFFFF)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(22), px(24), px(20))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(CARD)
            }
            // Chạm vào thẻ không được lọt xuống nền (nền chạm = huỷ).
            isClickable = true
            addView(
                TextView(activity).apply {
                    text = title
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                },
            )
            addView(
                TextView(activity).apply {
                    text = message
                    textSize = 14f
                    setTextColor(0xCCFFFFFF.toInt())
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = px(10) },
            )
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    // Hai nút NGANG HÀNG, cùng kích thước: không được làm nút huỷ nhỏ/mờ hơn
                    // để dụ bấm đồng ý.
                    addView(
                        button(cancelText, filled = false, onClick = onCancel),
                        LinearLayout.LayoutParams(0, px(44), 1f).apply { marginEnd = px(6) },
                    )
                    addView(
                        button(confirmText, filled = true, onClick = onConfirm),
                        LinearLayout.LayoutParams(0, px(44), 1f).apply { marginStart = px(6) },
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = px(20) },
            )
        }

        return FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setOnClickListener { onScrimClick() }
            addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER
                    marginStart = px(32)
                    marginEnd = px(32)
                },
            )
        }
    }
}
