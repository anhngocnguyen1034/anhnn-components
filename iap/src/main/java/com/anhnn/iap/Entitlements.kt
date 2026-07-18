package com.anhnn.iap

import android.content.Context
import android.content.SharedPreferences

/**
 * Lưu trạng thái sở hữu (entitlement) vào SharedPreferences để premium còn hiệu lực ngay cả khi
 * offline / trước khi Billing kết nối xong. Nguồn sự thật vẫn là Play; store này chỉ là cache bền.
 */
internal class Entitlements(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Tập id sản phẩm đang sở hữu (đã trao quyền premium). */
    fun ownedIds(): Set<String> = prefs.getStringSet(KEY_OWNED, emptySet()) ?: emptySet()

    /** Ghi đè toàn bộ tập sở hữu; trả về true nếu có thay đổi. */
    fun setOwnedIds(ids: Set<String>): Boolean {
        if (ids == ownedIds()) return false
        prefs.edit().putStringSet(KEY_OWNED, ids).apply()
        return true
    }

    companion object {
        private const val PREFS = "anhnn_iap"
        private const val KEY_OWNED = "owned_premium_ids"
    }
}
