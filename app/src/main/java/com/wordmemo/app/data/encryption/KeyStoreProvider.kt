package com.wordmemo.app.data.encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android Keystore 密钥管理。
 * 提供硬件级的 AES-256 密钥保护（在支持 TEE 的设备上）。
 */
object KeyStoreProvider {
    private const val KEYSTORE_ALIAS = "wordmemo_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private var cachedKey: SecretKey? = null

    fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            cachedKey = (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            return cachedKey!!
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        cachedKey = keyGenerator.generateKey()
        return cachedKey!!
    }
}
