# WebDAV 同步功能设计文档

**版本**: 1.0
**日期**: 2026-04-08

## 1. 概述

WebDAV 同步功能用于将 ComicInk 的收藏夹和阅读历史同步到 WebDAV 服务器，实现多设备数据共享。

**注意**: 本设计采用独立的 JSON 格式，与 Venera 不兼容。后续可添加 Venera 兼容模式转换器。

## 2. Venera 备份格式分析（参考）

> 2026-04-08 分析记录

### 2.1 Venera 备份结构

Venera 使用 `.venera` 文件（实际是 ZIP 归档），包含：

```
.venera 文件 = ZIP 归档，包含：
├── history.db           # 阅读历史（SQLite）
├── local_favorite.db    # 本地收藏（SQLite）
├── appdata.json         # 设置 + 搜索历史
├── cookie.db            # Cookie 数据（SQLite）
└── comic_source/        # JS 源文件目录
```

### 2.2 Venera 同步逻辑

| 触发时机 | 行为 |
|---------|------|
| App 启动 | 获取最新 .venera 文件，如果版本 > 本地则覆盖导入 |
| 数据变化 | 导出 ZIP + 上传，文件名 `${天}-${version}.venera` |

**冲突处理**: 远程完全覆盖本地（不是合并）

### 2.3 数据库表结构

**history.db - 阅读历史**
```sql
CREATE TABLE history (
  id text primary key,
  title text,
  subtitle text,
  cover text,
  time int,           -- 毫秒时间戳
  type int,           -- ComicType 枚举值
  ep int,             -- 章节索引 (1-based)
  page int,           -- 页面索引 (1-based)
  readEpisode text,   -- 已读章节，逗号分隔
  max_page int,       -- 最大页数
  chapter_group int   -- 分组章节
);
```

### 2.4 备份命名格式

- 格式: `{dayTimestamp}-{dataVersion}.venera`
- 示例: `20546-2505.venera`
- 保留策略: 同一天最新 + 最多 10 个备份

### 2.5 兼容性评估结论

实现 Venera 兼容格式需要：
- 解析/生成 SQLite 数据库（难度：高）
- 实现相同的 ZIP 打包/解包逻辑（难度：中）
- 版本号管理策略（难度：中）

**结论**: 先实现独立 JSON 格式，后续可添加转换器。

## 3. 本设计采用方案 B

### 3.1 同步数据模型（JSON 格式）

```kotlin
// 收藏夹项
data class FavoriteItem(
    val id: String,           // 漫画 ID
    val sourceKey: String,    // 源 key
    val title: String,        // 标题
    val cover: String,        // 封面 URL
    val addedAt: Long         // 添加时间戳（毫秒）
)

// 阅读历史项
data class HistoryItem(
    val id: String,           // 漫画 ID
    val sourceKey: String,    // 源 key
    val title: String,        // 标题
    val cover: String,        // 封面 URL
    val episodeId: String,    // 章节 ID
    val episodeTitle: String, // 章节标题
    val page: Int,            // 当前阅读页码 (0-based)
    val lastReadAt: Long      // 最后阅读时间戳（毫秒）
)

// 同步数据结构
data class SyncData(
    val version: Int = 1,     // 数据格式版本
    val appVersion: String = "1.0.0",  // App 版本
    val favorites: List<FavoriteItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val lastModified: Long = System.currentTimeMillis()
)
```

### 3.2 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│ Kotlin 层                                                 │
├─────────────────────────────────────────────────────────────┤
│ WebDavManager                                               │
│ - configure(url, username, password)                        │
│ - testConnection(): Boolean                                  │
│ - download(): SyncData?                                      │
│ - upload(data: SyncData): Boolean                            │
├─────────────────────────────────────────────────────────────┤
│ SyncManager                                                  │
│ - autoSync()              // 数据变化时自动触发              │
│ - forceSync()             // 手动强制同步                    │
│ - addFavorite() / removeFavorite()                           │
│ - updateHistory()                                             │
├─────────────────────────────────────────────────────────────┤
│ LocalStorage (SharedPreferences)                            │
│ - sync_data.json          // 本地同步缓存                    │
│ - settings.json           // WebDAV 配置                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ WebDAV 服务器                                               │
│ /ComicInk/sync.json       // PUT/GET 同步文件                │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 同步流程

```
用户操作（阅读/收藏）
    │
    ▼
更新本地 SharedPreferences (sync_data.json)
    │
    ▼
触发自动同步（协程后台执行）
    │
    ├─> 获取远程 sync.json (GET)
    │
    ├─> 合并数据
    │   - 收藏：union（合并，保留最新的 addedAt）
    │   - 历史：merge（合并，保留最新的 lastReadAt 和 page）
    │
    ├─> 上传合并后的 sync.json (PUT)
    │
    └─> 更新本地 lastModified
```

### 3.4 冲突处理（合并策略）

| 数据类型 | 合并规则 |
|---------|---------|
| **收藏** | 双方union，相同 id 保留 `addedAt` 最新的 |
| **历史** | 双方union，相同 id 保留 `lastReadAt` 最新的 |

### 3.5 文件命名

- 文件名: `sync.json`
- 路径: `/ComicInk/sync.json`

## 4. 关键 API

```kotlin
class WebDavManager(private val context: Context) {
    // 配置 WebDAV 服务器
    suspend fun configure(url: String, username: String, password: String): Boolean
    
    // 测试连接
    suspend fun testConnection(): Boolean
    
    // 下载远程数据
    suspend fun download(): SyncData?
    
    // 上传本地数据
    suspend fun upload(data: SyncData): Boolean
}

class SyncManager(
    private val webDavManager: WebDavManager,
    private val localStorage: SharedPreferences
) {
    // 自动同步（数据变化时调用）
    suspend fun sync()
    
    // 手动强制同步
    suspend fun forceSync()
    
    // 收藏操作
    fun addFavorite(comic: Comic)
    fun removeFavorite(comicId: String)
    
    // 历史操作
    fun updateHistory(comic: Comic, chapter: Chapter, page: Int)
}
```

## 5. 依赖

| 库 | 用途 |
|----|------|
| `com.github.thegrizzlylabs:sardine-android:0.9` | WebDAV 客户端 |
| `com.squareup.okhttp3:okhttp:3.12.13` | HTTP 客户端 |
| `com.google.code.gson:gson:2.10.1` | JSON 序列化 |

## 6. 后续扩展

- [ ] 实现 Venera 兼容模式转换器
- [ ] 支持选择同步方向（仅上传/仅下载/双向）
- [ ] 添加冲突解决策略选择（本地优先/远程优先/合并）