package com.comicink.network

import android.content.Context
import android.util.Log
import com.comicink.data.sync.SyncConfig
import com.comicink.data.sync.SyncData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/**
 * WebDAV 管理器
 * 使用 HttpURLConnection 实现，兼容 API 19
 */
class WebDavManager(private val context: Context) {

    private val config = SyncConfig(context)
    private val gson = Gson()

    companion object {
        private const val TAG = "WebDavManager"
        private const val TIMEOUT_MS = 30000
    }

    /**
     * 测试 WebDAV 连接
     * 使用 GET 请求测试，因为 HttpURLConnection 不支持 PROPFIND
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!config.isConfigured()) {
                return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
            }

            val baseUrl = config.webDavUrl.trimEnd('/')
            Log.d(TAG, "Testing connection to: $baseUrl")

            // 使用 GET 请求测试连接
            val connection = createConnection(baseUrl, "GET")
            connection.setRequestProperty("Depth", "0")

            val responseCode = connection.responseCode
            Log.d(TAG, "GET response: $responseCode")

            val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?: connection.inputStream?.bufferedReader()?.use { it.readText() }

            connection.disconnect()

            return@withContext when (responseCode) {
                in 200..299 -> Result.success(true)
                301, 302 -> Result.success(true)  // 重定向说明服务器可达
                401, 403 -> Result.failure(Exception("认证失败，请检查用户名和密码"))
                404 -> Result.success(true)  // 404 说明服务器可达但路径不存在
                else -> Result.failure(Exception("服务器返回: $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Result.failure(e)
        }
    }

    /**
     * 下载同步数据
     */
    suspend fun download(): Result<SyncData> = withContext(Dispatchers.IO) {
        try {
            if (!config.isConfigured()) {
                return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
            }

            val url = config.getFullPath()
            val connection = createConnection(url, "GET")

            val responseCode = connection.responseCode
            Log.d(TAG, "GET response: $responseCode")

            return@withContext when (responseCode) {
                200, 201 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    if (body.isNotEmpty()) {
                        val syncData = gson.fromJson(body, SyncData::class.java)
                        Result.success(syncData)
                    } else {
                        Result.success(SyncData())
                    }
                }
                404 -> {
                    connection.disconnect()
                    Result.success(SyncData())  // 文件不存在，返回空数据
                }
                else -> {
                    connection.disconnect()
                    Result.failure(Exception("下载失败: HTTP $responseCode"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    /**
     * 上传同步数据
     */
    suspend fun upload(data: SyncData): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!config.isConfigured()) {
                return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
            }

            val url = config.getFullPath()
            val connection = createConnection(url, "PUT")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = gson.toJson(data)
            val bytes = json.toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Content-Length", bytes.size.toString())

            // 写入数据
            connection.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "PUT response: $responseCode")

            connection.disconnect()

            return@withContext when (responseCode) {
                200, 201, 204 -> {
                    config.lastSyncTime = System.currentTimeMillis()
                    Result.success(true)
                }
                else -> Result.failure(Exception("上传失败: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * 创建 HTTP 连接并设置认证
     */
    private fun createConnection(urlString: String, method: String): HttpURLConnection {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.doInput = true

        // 设置认证
        val credentials = "${config.username}:${config.password}"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        connection.setRequestProperty("Authorization", "Basic $encoded")

        // WebDAV 需要这些头
        connection.setRequestProperty("Accept", "*/*")

        return connection
    }

    /**
     * 获取配置
     */
    fun getConfig(): SyncConfig = config
}