package com.comicink.network

import android.content.Context
import com.comicink.data.sync.SyncConfig
import com.comicink.data.sync.SyncData
import com.google.gson.Gson
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WebDAV 管理器
 * 使用 sardine-android 库实现 WebDAV 客户端
 */
class WebDavManager(private val context: Context) {

    private val config = SyncConfig(context)
    private val gson = Gson()

    /**
     * 测试 WebDAV 连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!config.isConfigured()) {
                return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
            }

            val sardine = createSardine()
            val baseUrl = config.webDavUrl.trimEnd('/')

            // 尝试列出根目录
            sardine.list(baseUrl)
            Result.success(true)
        } catch (e: Exception) {
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

            val sardine = createSardine()
            val url = config.getFullPath()

            // 检查文件是否存在
            try {
                val exists = sardine.exists(url)
                if (!exists) {
                    // 文件不存在，返回空数据
                    return@withContext Result.success(SyncData())
                }
            } catch (e: Exception) {
                // 如果检查失败，尝试直接获取
            }

            // 下载文件 - sardine.get() 返回 InputStream
            val inputStream = sardine.get(url)
            val body = inputStream.bufferedReader().use { it.readText() }

            if (body.isNotEmpty()) {
                val syncData = gson.fromJson(body, SyncData::class.java)
                Result.success(syncData)
            } else {
                Result.success(SyncData())
            }
        } catch (e: Exception) {
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

            val sardine = createSardine()
            val url = config.getFullPath()
            val json = gson.toJson(data)

            // 上传文件
            sardine.put(url, json.toByteArray(Charsets.UTF_8), "application/json")

            // 更新最后同步时间
            config.lastSyncTime = System.currentTimeMillis()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建 sardine 客户端
     */
    private fun createSardine(): Sardine {
        val sardine = OkHttpSardine()
        sardine.setCredentials(config.username, config.password)
        return sardine
    }

    /**
     * 获取配置
     */
    fun getConfig(): SyncConfig = config
}