package com.anhnn.ads

/**
 * Trạng thái của một lượt quảng cáo, báo về qua callback `onState` của
 * [Ads.loadAndShowInterstitial] / [Ads.loadAndShowRewarded].
 *
 * Trước đây module chỉ có `onClosed` — đủ để điều hướng nhưng KHÔNG đủ để biết vì sao không có
 * quảng cáo (hết hàng? mạng chậm? đang cooldown?), nên app không log/đo được gì.
 *
 * Thứ tự thường gặp của một lượt thành công:
 * `Loading` → `Loaded` (hoặc `Cached` nếu lấy từ kho preload) → `Show` → (`Click`/`Paid`) →
 * `Close` → `Done`.
 *
 * [Done] LUÔN là trạng thái cuối, kể cả khi hỏng — đó là chỗ điều hướng tiếp.
 */
enum class AdState {
    /** Bắt đầu nạp (chỉ phát khi phải nạp mới, lấy từ cache thì không). */
    Loading,

    /** Nạp xong. */
    Loaded,

    /** Lấy được ad đã preload sẵn trong kho — không tốn thời gian chờ. */
    Cached,

    /** Đã hiện lên màn hình. */
    Show,

    /** Người dùng bấm vào quảng cáo. */
    Click,

    /** Có doanh thu được ghi nhận (`OnPaidEventListener`). */
    Paid,

    /** Người dùng đóng quảng cáo. */
    Close,

    /** Hết quãng chờ [Ads.loadAndShowInterstitial] mà vẫn chưa nạp xong — bỏ qua, đi tiếp. */
    Timeout,

    /** Nạp hoặc hiện hỏng. */
    Error,

    /** Quảng cáo đang tắt, đang cooldown, hoặc vị trí không có ad unit id. */
    Skipped,

    /** Kết thúc lượt — luôn phát đúng một lần, kể cả khi [Error]/[Timeout]/[Skipped]. */
    Done,
}
