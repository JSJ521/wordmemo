package com.wordmemo.app

import android.app.Application
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.entity.AppConfigEntity
import com.wordmemo.app.data.encryption.ApiKeyCipher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class WordMemoApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动时检查并恢复 API Key（数据库被重置时丢失）
        appScope.launch {
            try {
                val db = WordMemoDatabase.getInstance(this@WordMemoApp)
                val existing = db.appConfigDao().getValue("api_key")
                if (existing == null || existing.value.isBlank()) {
                    android.util.Log.w("WordMemoApp", "⚠️ API Key 未找到，请用户在设置页手动配置")
                }
                // 同时确保 baseUrl 和 model 有默认值
                if (db.appConfigDao().getValue("api_base_url") == null) {
                    db.appConfigDao().setValue(AppConfigEntity("api_base_url", "https://api.deepseek.com"))
                }
                if (db.appConfigDao().getValue("api_model") == null) {
                    db.appConfigDao().setValue(AppConfigEntity("api_model", "deepseek-chat"))
                }
            } catch (_: Exception) { }
        }
    }
}
