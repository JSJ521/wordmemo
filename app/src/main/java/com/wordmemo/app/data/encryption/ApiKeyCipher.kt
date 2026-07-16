package com.wordmemo.app.data.encryption

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加密/解密工具。
 * 使用 AES-256-GCM 通过 Android Keystore 保护的密钥进行加密。
 */
class ApiKeyCipher {
    private val transformation = "AES/GCM/NoPadding"
    private val ivSize = 12 // GCM 推荐 12 字节
    private val tagSize = 128 // GCM 认证标签位数

    fun encrypt(plaintext: String): String {
        val secretKey = KeyStoreProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 格式: base64(iv):base64(ciphertext)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivBase64:$cipherBase64"
    }

    fun decrypt(encrypted: String): String {
        if (!encrypted.contains(":")) return encrypted // 非加密格式

        val parts = encrypted.split(":")
        if (parts.size != 2) return encrypted

        val secretKey = KeyStoreProvider.getOrCreateKey()
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(tagSize, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun isEncrypted(value: String): Boolean {
        return value.contains(":") && value.length > 40
    }
}
