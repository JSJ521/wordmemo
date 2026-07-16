package com.wordmemo.app.data.network

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

                if (!response.isSuccessful) {
                    return@withContext UpdateInfo(false)
                }

                val body = response.body?.string() ?: return@withContext UpdateInfo(false)
                val root = JsonParser.parseString(body).asJsonObject

                val tagName = root.get("tag_name")?.asString ?: ""
                val releaseNotes = root.get("body")?.asString ?: ""
                val releaseUrl = root.get("html_url")?.asString ?: ""

                // 找 APK 下载链接
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

                // 版本比较：去掉 v 前缀后比较
                val remoteVer = tagName.removePrefix("v")
                val localVer = currentVersion.removePrefix("v")
                val hasUpdate = compareVersions(remoteVer, localVer) > 0

                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    releaseUrl = releaseUrl
                )
            } catch (e: Exception) {
                android.util.Log.w("UpdateChecker", "检查更新失败: ${e.message}")
                UpdateInfo(false)
            }
        }
    }

    /** 使用 DownloadManager 下载 APK 到应用私目录 */
    fun downloadApk(downloadUrl: String, fileName: String): Long {
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val destination = Uri.fromFile(File(destDir, fileName))
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("书鼠词记 更新")
            .setDescription("正在下载新版本...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(destination)
            .setMimeType("application/vnd.android.package-archive")
        return downloadManager.enqueue(request)
    }

    /** 通过 DownloadManager 查询下载文件 URI 并安装 */
    fun installDownloadedApk(downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uriStr = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    val fileUri = Uri.parse(uriStr)

                    if (fileUri.scheme == "file") {
                        // Android 7+ 禁止 file:// Intent → 走 FileProvider
                        val file = File(fileUri.path ?: "")
                        if (file.exists()) installApk(file)
                    } else {
                        // content:// URI 可直接 Intent
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }
            cursor.close()
        } catch (e: Exception) {
            android.util.Log.w("UpdateChecker", "安装失败: ${e.message}")
        }
    }

    /** 安装已下载的 APK（回退方案：FileProvider） */
    fun installApk(apkFile: File) {
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
    }

    /** 语义化版本比较：1.0.0 < 1.1.0 返回负值 */
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
