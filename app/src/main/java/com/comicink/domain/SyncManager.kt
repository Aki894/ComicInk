package com.comicink.domain

import android.content.Context
import com.comicink.data.sync.FavoriteItem
import com.comicink.data.sync.HistoryItem
import com.comicink.data.sync.SyncData
import com.comicink.data.sync.SyncConfig
import com.comicink.data.sync.LocalSyncStorage
import com.comicink.network.WebDavManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 同步管理器
 * 负责自动同步和手动同步
 */
class SyncManager(context: Context) {

    private val webDavManager = WebDavManager(context)
    private val localStorage = LocalSyncStorage(context)
    private val config = SyncConfig(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 测试 WebDAV 连接
     */
    suspend fun testConnection(): Result<Boolean> {
        return webDavManager.testConnection()
    }

    /**
     * 配置 WebDAV
     */
    fun configure(url: String, username: String, password: String) {
        config.save(url, username, password)
    }

    /**
     * 获取配置
     */
    fun getConfig(): SyncConfig = config

    /**
     * 自动同步（数据变化时调用）
     */
    fun autoSync() {
        if (!config.isEnabled || !config.isConfigured()) {
            return
        }
        scope.launch {
            sync()
        }
    }

    /**
     * 强制同步
     */
    suspend fun sync(): Result<Boolean> {
        if (!config.isConfigured()) {
            return Result.failure(IllegalStateException("WebDAV not configured"))
        }

        return try {
            // 1. 下载远程数据
            val remoteResult = webDavManager.download()
            val remoteData = remoteResult.getOrNull() ?: SyncData()

            // 2. 获取本地数据
            val localData = localStorage.getSyncData()

            // 3. 合并数据
            val mergedData = mergeSyncData(localData, remoteData)

            // 4. 保存合并后的数据到本地
            localStorage.saveSyncData(mergedData)

            // 5. 上传到服务器
            val uploadResult = webDavManager.upload(mergedData)

            Result.success(uploadResult.isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 合并本地和远程数据
     * - 收藏：union，相同 id 保留 addedAt 最新的
     * - 历史：union，相同 id 保留 lastReadAt 最新的
     */
    private fun mergeSyncData(local: SyncData, remote: SyncData): SyncData {
        // 合并收藏
        val mergedFavorites = mutableMapOf<String, FavoriteItem>()

        // 先添加远程的
        for (item in remote.favorites) {
            val key = "${item.sourceKey}:${item.id}"
            mergedFavorites[key] = item
        }

        // 再添加本地的（覆盖远程的，保留最新的）
        for (item in local.favorites) {
            val key = "${item.sourceKey}:${item.id}"
            val existing = mergedFavorites[key]
            if (existing == null || item.addedAt > existing.addedAt) {
                mergedFavorites[key] = item
            }
        }

        // 合并历史
        val mergedHistory = mutableMapOf<String, HistoryItem>()

        for (item in remote.history) {
            val key = "${item.sourceKey}:${item.id}"
            mergedHistory[key] = item
        }

        for (item in local.history) {
            val key = "${item.sourceKey}:${item.id}"
            val existing = mergedHistory[key]
            if (existing == null || item.lastReadAt > existing.lastReadAt) {
                mergedHistory[key] = item
            }
        }

        return SyncData(
            favorites = mergedFavorites.values.toList().sortedByDescending { it.addedAt },
            history = mergedHistory.values.toList().sortedByDescending { it.lastReadAt },
            lastModified = System.currentTimeMillis()
        )
    }

    /**
     * 添加收藏
     */
    fun addFavorite(id: String, sourceKey: String, title: String, cover: String) {
        val item = FavoriteItem(
            id = id,
            sourceKey = sourceKey,
            title = title,
            cover = cover,
            addedAt = System.currentTimeMillis()
        )
        localStorage.addFavorite(item)
        autoSync()
    }

    /**
     * 移除收藏
     */
    fun removeFavorite(comicId: String) {
        localStorage.removeFavorite(comicId)
        autoSync()
    }

    /**
     * 更新历史
     */
    fun updateHistory(
        id: String,
        sourceKey: String,
        title: String,
        cover: String,
        episodeId: String,
        episodeTitle: String,
        page: Int
    ) {
        val item = HistoryItem(
            id = id,
            sourceKey = sourceKey,
            title = title,
            cover = cover,
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            page = page,
            lastReadAt = System.currentTimeMillis()
        )
        localStorage.updateHistory(item)
        autoSync()
    }

    /**
     * 获取本地收藏
     */
    fun getFavorites() = localStorage.getFavorites()

    /**
     * 获取本地历史
     */
    fun getHistory() = localStorage.getHistory()
}