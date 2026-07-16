package com.wordmemo.app.data.local.converter

import android.util.Base64
import androidx.room.TypeConverter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加密转换器。
 * 使用 AES-256-GCM 对存储的 API Key 进行透明加密/解密。
 * 密钥在当前设备上生成，不导出。
 */
class EncryptedStringConverter {
    companion object {
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // GCM 推荐 12 字节 IV
        private const val TAG_SIZE = 128 // GCM 认证标签位数

        private var secretKey: SecretKey? = null

        private fun getOrCreateKey(): SecretKey {
            if (secretKey == null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                secretKey = keyGen.generateKey()
            }
            return secretKey!!
        }
    }

    @TypeConverter
    fun fromEncryptedString(encrypted: String?): String? {
        if (encrypted.isNullOrBlank() || !encrypted.contains(":")) return encrypted

        try {
            val parts = encrypted.split(":")
            if (parts.size != 2) return encrypted

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

            return String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            return encrypted // 回退到明文
        }
    }

    @TypeConverter
    fun toEncryptedString(plaintext: String?): String? {
        if (plaintext.isNullOrBlank()) return plaintext

        // 如果已经是加密格式，不解密
        if (plaintext.contains(":")) return plaintext

        try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            return "$ivBase64:$cipherBase64"
        } catch (e: Exception) {
            return plaintext
        }
    }
}
