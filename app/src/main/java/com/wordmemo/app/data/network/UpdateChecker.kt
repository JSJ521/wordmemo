package com.wordmemo.app.data.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * GitHub Release 版本检查与 APK 下载安装。
 * 仓库: JSJ521/wordmemo
 */
class UpdateChecker(private val context: Context) {

    private val owner = "JSJ521"
    private val repo = "wordmemo"
    private val gson = Gson()

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = "",
        val releaseUrl: String = ""
    )

    /** 检查最新版本 */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val response = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(okhttp3.Request.Builder().url(url).build())
                    .execute()

                if (!response.isSuccessful) return@withContext UpdateInfo(false)

                val body = response.body?.string() ?: return@withContext UpdateInfo(false)
                val root = JsonParser.parseString(body).asJsonObject

                val tagName = root.get("tag_name")?.asString ?: ""
                val releaseNotes = root.get("body")?.asString ?: ""
                val releaseUrl = root.get("html_url")?.asString ?: ""

                var apkUrl = ""
                val assets = root.getAsJsonArray("assets")
                if (assets != null) {
                    for (asset in assets) {
                        val name = asset.asJsonObject.get("name")?.asString ?: ""
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.asJsonObject.get("browser_download_url")?.asString ?: ""
                            break
                        }
                    }
                }

                val remoteVer = tagName.removePrefix("v")
                val localVer = currentVersion.removePrefix("v")
                val hasUpdate = compareVersions(remoteVer, localVer) > 0

                UpdateInfo(hasUpdate, tagName, apkUrl, releaseNotes, releaseUrl)
            } catch (e: Exception) {
                android.util.Log.w("UpdateChecker", "检查更新失败: ${e.message}")
                UpdateInfo(false)
            }
        }
    }

    /** 检查安装权限 */
    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    /** 跳转系统设置开启安装权限 */
    fun openInstallSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 用 OkHttp 下载 APK 到缓存目录。
     * 可返回下载进度 (bytesRead / totalBytes)。
     */
    suspend fun downloadApk(downloadUrl: String, fileName: String): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载失败: HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("响应体为空"))
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()

                val destFile = File(context.cacheDir, fileName)
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                }
                inputStream.close()

                if (!destFile.exists() || destFile.length() == 0L) {
                    return@withContext Result.failure(Exception("下载文件为空"))
                }
                android.util.Log.i("UpdateChecker", "下载完成: ${destFile.absolutePath} (${destFile.length() / 1024}KB)")
                Result.success(destFile)
            } catch (e: Exception) {
                android.util.Log.e("UpdateChecker", "下载异常: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /** 用 FileProvider 安装 APK */
    fun installApk(apkFile: File): Boolean {
        try {
            if (!canInstallPackages()) {
                openInstallSettings()
                return false
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "安装异常: ${e.message}", e)
            return false
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}
