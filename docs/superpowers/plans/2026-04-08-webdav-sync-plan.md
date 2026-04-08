# WebDAV 同步功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 WebDAV 同步功能，支持收藏夹和阅读历史的自动同步

**Architecture:** 使用 sardine-android 库实现 WebDAV 客户端，通过 SharedPreferences 存储本地数据，使用协程实现后台同步

**Tech Stack:** Kotlin, sardine-android 0.9, OkHttp 3.12, Gson

---

## 文件结构

```
app/src/main/java/com/comicink/
├── data/
│   └── sync/
│       ├── SyncModels.kt           # 同步数据模型
│       ├── SyncConfig.kt           # WebDAV 配置管理
│       └── LocalSyncStorage.kt     # 本地同步存储
├── network/
│   └── WebDavManager.kt            # WebDAV 客户端封装
└── domain/
    └── SyncManager.kt              # 同步管理器（对外接口）
```

---

## Task 1: 同步数据模型

**Files:**
- Create: `app/src/main/java/com/comicink/data/sync/SyncModels.kt`

- [ ] **Step 1: 创建 SyncModels.kt**

```kotlin
// app/src/main/java/com/comicink/data/sync/SyncModels.kt
package com.comicink.data.sync

import com.google.gson.annotations.SerializedName

/**
 * 收藏夹项
 */
data class FavoriteItem(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("sourceKey")
    val sourceKey: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("cover")
    val cover: String,
    
    @SerializedName("addedAt")
    val addedAt: Long
)

/**
 * 阅读历史项
 */
data class HistoryItem(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("sourceKey")
    val sourceKey: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("cover")
    val cover: String,
    
    @SerializedName("episodeId")
    val episodeId: String,
    
    @SerializedName("episodeTitle")
    val episodeTitle: String,
    
    @SerializedName("page")
    val page: Int,
    
    @SerializedName("lastReadAt")
    val lastReadAt: Long
)

/**
 * 同步数据结构
 */
data class SyncData(
    @SerializedName("version")
    val version: Int = 1,
    
    @SerializedName("appVersion")
    val appVersion: String = "1.0.0",
    
    @SerializedName("favorites")
    val favorites: List<FavoriteItem> = emptyList(),
    
    @SerializedName("history")
    val history: List<HistoryItem> = emptyList(),
    
    @SerializedName("lastModified")
    val lastModified: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 验证编译**
Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/sync/SyncModels.kt
git commit -m "feat: add sync data models"
```

---

## Task 2: WebDAV 配置管理

**Files:**
- Create: `app/src/main/java/com/comicink/data/sync/SyncConfig.kt`

- [ ] **Step 1: 创建 SyncConfig.kt**

```kotlin
// app/src/main/java/com/comicink/data/sync/SyncConfig.kt
package com.comicink.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * WebDAV 配置管理
 */
class SyncConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "webdav_config", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SYNC = "last_sync"
        
        const val DEFAULT_PATH = "/ComicInk/sync.json"
    }

    var webDavUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /**
     * 检查配置是否完整
     */
    fun isConfigured(): Boolean {
        return webDavUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    /**
     * 获取完整的文件路径
     */
    fun getFullPath(): String {
        val baseUrl = webDavUrl.trimEnd('/')
        return "$baseUrl$DEFAULT_PATH"
    }

    /**
     * 保存配置
     */
    fun save(url: String, username: String, password: String) {
        this.webDavUrl = url
        this.username = username
        this.password = password
        this.isEnabled = true
    }

    /**
     * 清除配置
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
```

- [ ] **Step 2: 验证编译**
Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/sync/SyncConfig.kt
git commit -m "feat: add SyncConfig for WebDAV settings"
```

---

## Task 3: 本地同步存储

**Files:**
- Create: `app/src/main/java/com/comicink/data/sync/LocalSyncStorage.kt`

- [ ] **Step 1: 创建 LocalSyncStorage.kt**

```kotlin
// app/src/main/java/com/comicink/data/sync/LocalSyncStorage.kt
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
        "sync_data", Context.MODE_PRIVATE
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
        // 移除已存在的相同 id
        current.removeAll { it.id == item.id && it.sourceKey == item.sourceKey }
        // 添加新的
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
        // 移除已存在的相同 id
        current.removeAll { it.id == item.id && it.sourceKey == item.sourceKey }
        // 添加新的到最前面
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
```

- [ ] **Step 2: 验证编译**
Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/sync/LocalSyncStorage.kt
git commit -m "feat: add LocalSyncStorage for local data management"
```

---

## Task 4: WebDAV 客户端封装

**Files:**
- Create: `app/src/main/java/com/comicink/network/WebDavManager.kt`

- [ ] **Step 1: 创建 WebDavManager.kt**

```kotlin
// app/src/main/java/com/comicink/network/WebDavManager.kt
package com.comicink.network

import android.content.Context
import com.comicink.data.sync.SyncConfig
import com.comicink.data.sync.SyncData
import com.google.gson.Gson
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.okhttp.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials

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

            // 下载文件
            val response = sardine.get(url)
            if (response.status == 200 || response.status == 201) {
                val body = response.content?.readText() ?: "{}"
                val syncData = gson.fromJson(body, SyncData::class.java)
                Result.success(syncData)
            } else {
                Result.failure(Exception("HTTP ${response.status}"))
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
            sardine.put(url, json.toByteArray(), "application/json")
            
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
```

- [ ] **Step 2: 验证编译**
Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/network/WebDavManager.kt
git commit -m "feat: add WebDavManager for WebDAV client"
```

---

## Task 5: 同步管理器

**Files:**
- Create: `app/src/main/java/com/comicink/domain/SyncManager.kt`

- [ ] **Step 1: 创建 SyncManager.kt**

```kotlin
// app/src/main/java/com/comicink/domain/SyncManager.kt
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
```

- [ ] **Step 2: 验证编译**
Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/domain/SyncManager.kt
git commit -m "feat: add SyncManager for sync orchestration"
```

---

## Task 6: 构建测试

**Files:**
- N/A

- [ ] **Step 1: 运行 assembleDebug**
Run: `gradle assembleDebug`

- [ ] **Step 2: 检查 APK 输出**
Run: `ls app/build/outputs/apk/debug/`

- [ ] **Step 3: Commit**
```bash
git add .
git commit -m "build: verify WebDAV sync compiles"
```

---

## 验证清单

完成所有任务后，请验证：

- [ ] `SyncModels` 能正确序列化/反序列化 JSON
- [ ] `SyncConfig` 能保存和读取 WebDAV 配置
- [ ] `LocalSyncStorage` 能正确读写收藏和历史
- [ ] `WebDavManager` 能连接 WebDAV 并上传/下载
- [ ] `SyncManager` 能自动和手动触发同步
- [ ] APK 构建成功且大小 < 15MB

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-08-webdav-sync-plan.md`. Two execution options:**

1. **Subagent-Driven (recommended)** - 我为每个任务派遣一个子代理，任务间进行审查，快速迭代
2. **Inline Execution** - 在当前会话中执行任务，带审查检查点的批量执行

**Which approach?**