package com.anhnn.iap

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verify chữ ký giao dịch mua offline bằng RSA public key của app (Play Console).
 * Tương đương lớp Security mẫu của Google Play Billing.
 */
internal object Security {

    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

    /**
     * @return true nếu [signedData] (Purchase.originalJson) khớp [signature] theo [base64PublicKey].
     *         Nếu [base64PublicKey] rỗng → coi như bỏ qua verify (trả true).
     */
    fun verifyPurchase(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank()) return true
        if (signedData.isBlank() || signature.isBlank()) return false
        return try {
            val key = generatePublicKey(base64PublicKey)
            verify(key, signedData, signature)
        } catch (e: Exception) {
            false
        }
    }

    private fun generatePublicKey(encodedPublicKey: String): PublicKey {
        val decoded = Base64.decode(encodedPublicKey, Base64.DEFAULT)
        return KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(decoded))
    }

    private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        val signatureBytes = try {
            Base64.decode(signature, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(signedData.toByteArray())
        return sig.verify(signatureBytes)
    }
}
