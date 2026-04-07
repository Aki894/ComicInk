package com.comicink.data.sync

import com.google.gson.annotations.SerializedName

/**
 * 收藏夹项
 */
data class FavoriteItem(
    @SerializedName("id") val id: String,
    @SerializedName("sourceKey") val sourceKey: String,
    @SerializedName("title") val title: String,
    @SerializedName("cover") val cover: String,
    @SerializedName("addedAt") val addedAt: Long
)

/**
 * 阅读历史项
 */
data class HistoryItem(
    @SerializedName("id") val id: String,
    @SerializedName("sourceKey") val sourceKey: String,
    @SerializedName("title") val title: String,
    @SerializedName("cover") val cover: String,
    @SerializedName("episodeId") val episodeId: String,
    @SerializedName("episodeTitle") val episodeTitle: String,
    @SerializedName("page") val page: Int,
    @SerializedName("lastReadAt") val lastReadAt: Long
)

/**
 * 同步数据结构
 */
data class SyncData(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("appVersion") val appVersion: String = "1.0.0",
    @SerializedName("favorites") val favorites: List<FavoriteItem> = emptyList(),
    @SerializedName("history") val history: List<HistoryItem> = emptyList(),
    @SerializedName("lastModified") val lastModified: Long = System.currentTimeMillis()
)