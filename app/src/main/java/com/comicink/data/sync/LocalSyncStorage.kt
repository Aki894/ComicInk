package com.comicink.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * 本地同步存储
 * 管理本地收藏夹和历史的读写
 */
class LocalSyncStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "sync_data",
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HISTORY = "history"
        private const val KEY_LAST_MODIFIED = "last_modified"
    }

    /**
     * 获取所有收藏
     */
    fun getFavorites(): List<FavoriteItem> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<FavoriteItem>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存收藏
     */
    fun saveFavorites(favorites: List<FavoriteItem>) {
        val json = gson.toJson(favorites)
        prefs.edit()
            .putString(KEY_FAVORITES, json)
            .putLong(KEY_LAST_MODIFIED, System.currentTimeMillis())
            .apply()
    }

    /**
     * 添加收藏
     */
    fun addFavorite(item: FavoriteItem) {
        val current = getFavorites().toMutableList()
        current.removeAll { it.id == item.id && it.sourceKey == item.sourceKey }
        current.add(0, item)
        saveFavorites(current)
    }

    /**
     * 移除收藏
     */
    fun removeFavorite(comicId: String) {
        val current = getFavorites().toMutableList()
        current.removeAll { it.id == comicId }
        saveFavorites(current)
    }

    /**
     * 获取所有历史
     */
    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<HistoryItem>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存历史
     */
    fun saveHistory(history: List<HistoryItem>) {
        val json = gson.toJson(history)
        prefs.edit()
            .putString(KEY_HISTORY, json)
            .putLong(KEY_LAST_MODIFIED, System.currentTimeMillis())
            .apply()
    }

    /**
     * 更新历史记录
     */
    fun updateHistory(item: HistoryItem) {
        val current = getHistory().toMutableList()
        current.removeAll { it.id == item.id && it.sourceKey == item.sourceKey }
        current.add(0, item)
        saveHistory(current)
    }

    /**
     * 获取完整的 SyncData
     */
    fun getSyncData(): SyncData {
        return SyncData(
            favorites = getFavorites(),
            history = getHistory(),
            lastModified = prefs.getLong(KEY_LAST_MODIFIED, System.currentTimeMillis())
        )
    }

    /**
     * 保存完整的 SyncData
     */
    fun saveSyncData(data: SyncData) {
        saveFavorites(data.favorites)
        saveHistory(data.history)
    }

    /**
     * 获取最后修改时间
     */
    fun getLastModified(): Long {
        return prefs.getLong(KEY_LAST_MODIFIED, 0)
    }
}