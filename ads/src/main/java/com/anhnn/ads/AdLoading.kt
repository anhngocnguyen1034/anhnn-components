package com.anhnn.ads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Kiểu màn chờ hiện trong lúc nạp quảng cáo cho [Ads.loadAndShowInterstitial]. */
enum class AdLoadingStyle {
    /** Không hiện gì — màn gọi tự lo UI chờ. */
    None,

    /** Hộp nhỏ giữa màn (spinner + nhãn), nền mờ. */
    Dialog,

    /** Phủ kín màn, nền đặc — dùng khi chuyển màn hẳn (vd Splash → Home). */
    FullScreen,
}

/**
 * Màn chờ lúc nạp quảng cáo.
 *
 * Cố tình dựng bằng **View thuần** chứ không phải Compose: nó được gọi từ `Activity` (ngoài mọi
 * composition) và sống trong một cửa sổ Dialog riêng, mà `ComposeView` ở cửa sổ riêng phải tự
 * gắn `ViewTreeLifecycleOwner`/`SavedStateRegistryOwner` mới chạy — thêm một nguồn crash cho
 * thứ chỉ là cái spinner. View thuần thì chạy ở mọi app tiêu thụ, không cần điều kiện gì.
 *
 * KHÔNG cho người dùng bấm back để tắt: quãng chờ đã có trần thời gian ở
 * [Ads.loadAndShowInterstitial], tắt giữa chừng chỉ làm luồng điều hướng rối.
 */
internal class AdLoadingOverlay private constructor(private val dialog: Dialog) {

    fun dismiss() {
        runCatching { if (dialog.isShowing) dialog.dismiss() }
    }

    companion object {

        /** Trả null nếu [style] là [AdLoadingStyle.None] hoặc activity đã chết. */
        fun show(activity: Activity, style: AdLoadingStyle, label: String?): AdLoadingOverlay? {
            if (style == AdLoadingStyle.None) return null
            if (activity.isFinishing || activity.isDestroyed) return null
            return runCatching {
                val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
                    setContentView(buildContent(activity, style, label))
                    setCancelable(false)
                    window?.apply {
                        setBackgroundDrawable(
                            ColorDrawable(if (style == AdLoadingStyle.FullScreen) SCRIM_SOLID else SCRIM_DIM)
                        )
                        setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                        )
                        // Giữ nguyên trạng thái immersive của màn gọi: cửa sổ Dialog không kế
                        // thừa flag của Activity nên nav bar sẽ nhảy ra nếu không copy sang.
                        runCatching {
                            decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
                        }
                    }
                    show()
                }
                AdLoadingOverlay(dialog)
            }.getOrNull()
        }

        private const val SCRIM_DIM = 0xB3000000.toInt()
        private const val SCRIM_SOLID = 0xF2000000.toInt()

        private fun buildContent(activity: Activity, style: AdLoadingStyle, label: String?): View {
            val d = activity.resources.displayMetrics.density
            fun px(v: Int) = (v * d).toInt()

            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(px(28), px(24), px(28), px(24))
                if (style == AdLoadingStyle.Dialog) {
                    background = GradientDrawable().apply {
                        cornerRadius = px(20).toFloat()
                        setColor(0xFF1C1C1E.toInt())
                    }
                }
                addView(
                    ProgressBar(activity).apply { isIndeterminate = true },
                    LinearLayout.LayoutParams(px(44), px(44)),
                )
                if (!label.isNullOrBlank()) {
                    addView(
                        TextView(activity).apply {
                            text = label
                            setTextColor(Color.WHITE)
                            textSize = 14f
                            gravity = Gravity.CENTER
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = px(14) },
                    )
                }
            }

            return FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(
                    column,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER },
                )
            }
        }
    }
}
