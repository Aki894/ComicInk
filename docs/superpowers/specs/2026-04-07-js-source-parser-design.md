# JS 源解析器设计文档

**版本**: 1.0
**日期**: 2026-04-07
**功能**: JS 源解析器（Venera 兼容）

## 1. 概述

JS 源解析器是 ComicInk 的核心模块，负责加载和执行 Venera 兼容的 JavaScript 漫画源，实现漫画搜索、浏览、章节获取等功能。

**设计目标**:
- 完整兼容 Venera 漫画源 API
- E-ink 设备内存优化（独立 Context 用完即焚）
- 从本地 assets 加载固定漫画源列表

## 2. 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    Kotlin 层                                │
├─────────────────────────────────────────────────────────────┤
│  JsSourceManager          漫画源管理器                       │
│  - loadSources()          从 assets 加载源文件               │
│  - getSource(key)         获取指定源                        │
│  - listSources()          列出所有可用源                     │
├─────────────────────────────────────────────────────────────┤
│  JsEngineBridge           QuickJS 引擎封装                  │
│  - createContext()        创建独立 Context                  │
│  - executeScript()        执行 JS 代码                      │
│  - callFunction()         调用 JS 函数                      │
│  - close()                关闭 Context 释放内存             │
├─────────────────────────────────────────────────────────────┤
│  JsComicSource            Venera API 适配器                 │
│  - search()               搜索漫画                          │
│  - getComicInfo()         获取漫画详情                       │
│  - getChapters()          获取章节列表                       │
│  - getImages()            获取章节图片                       │
│  - getFavorites()         获取收藏列表                       │
│  - addFavorite()          添加收藏                          │
│  - removeFavorite()       删除收藏                          │
├─────────────────────────────────────────────────────────────┤
│  JsGlobalBridge           JS 全局 API 桥接                  │
│  - Network.get/post       HTTP 请求                        │
│  - this.saveData/loadData 本地存储                          │
│  - Comic/ComicDetails     数据类型构造                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    QuickJS 引擎                             │
│  ┌───────────────────────────────────────���─────────────┐   │
│  │  JS 源文件 (extends ComicSource)                    │   │
│  │  - name, key, version                               │   │
│  │  - account: { login, logout }                       │   │
│  │  - explore: [{ title, type, load }]                 │   │
│  │  - search: { load }                                 │   │
│  │  - favorites: { loadComics, addOrDelFavorite }     │   │
│  │  - comic: { loadInfo, loadEp }                      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 3. 核心组件

### 3.1 JsSourceManager

**职责**: 管理漫画源的生命周期

```kotlin
class JsSourceManager(
    private val engineBridge: JsEngineBridge
) {
    // 漫画源元数据
    data class SourceMeta(
        val key: String,
        val name: String,
        val version: String,
        val minAppVersion: String
    )

    // 加载 assets/sources/ 目录下所有 .js 文件
    fun loadSources(context: Context): List<SourceMeta>

    // 获取指定源的内容
    fun getSourceContent(context: Context, key: String): String
}
```

### 3.2 JsEngineBridge

**职责**: 封装 QuickJS 引擎操作，提供 Context 隔离

```kotlin
class JsEngineBridge {
    // 创建独立 Context（每次操作创建，用完关闭）
    fun createContext(): QuickJSContext

    // 执行 JS 脚本
    fun evaluate(context: QuickJSContext, code: String, fileName: String): JSValue

    // 调用 JS 对象方法
    fun callMethod(context: QuickJSContext, obj: JSValue, method: String, vararg args: Any): JSValue
}
```

**内存优化**: 每个漫画源操作创建独立 Context，操作完成后立即调用 `context.close()` 释放内存。

### 3.3 JsComicSourceAdapter

**职责**: 实现 Venera 风格的漫画源 API

```kotlin
class JsComicSourceAdapter(
    private val engineBridge: JsEngineBridge,
    private val sourceCode: String,
    private val sourceKey: String
) {
    // 搜索漫画
    suspend fun search(keyword: String, page: Int = 1): SearchResult

    // 获取漫画详情
    suspend fun getComicInfo(comicId: String): ComicDetails

    // 获取章节图片
    suspend fun getChapterImages(comicId: String, chapterId: String): ChapterImages

    // 获取收藏列表
    suspend fun getFavorites(page: Int = 1): List<Comic>

    // 添加/删除收藏
    suspend fun toggleFavorite(comicId: String, isAdding: Boolean): Boolean
}
```

### 3.4 JsGlobalBridge

**职责**: 提供 JS 运行时的全局 API（模拟 Venera 的 this 对象）

```kotlin
class JsGlobalBridge(
    private val okHttpClient: OkHttpClient,
    private val localStorage: SharedPreferences
) {
    // JS 全局对象名称
    companion object {
        const val GLOBAL_NAME = "comicSource"
    }

    // 注册到 QuickJS 的全局对象
    fun register(context: QuickJSContext)

    // Network.get(url, headers) -> { status, body, headers }
    fun handleGet(url: String, headers: Map<String, String>): Map<String, Any?>

    // Network.post(url, headers, body) -> { status, body, headers }
    fun handlePost(url: String, headers: Map<String, String>, body: String): Map<String, Any?>

    // this.saveData(key, value)
    fun saveData(key: String, value: String)

    // this.loadData(key) -> value
    fun loadData(key: String): String?

    // this.deleteData(key)
    fun deleteData(key: String)
}
```

## 4. 数据模型

### 4.1 Kotlin 侧数据类

```kotlin
data class Comic(
    val id: String,
    val title: String,
    val cover: String,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val url: String? = null
)

data class ComicDetails(
    val id: String,
    val title: String,
    val cover: String,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val chapters: List<Chapter> = emptyList()
)

data class Chapter(
    val id: String,
    val title: String,
    val url: String
)

data class ChapterImages(
    val chapterId: String,
    val images: List<String>  // 图片 URL 列表
)

data class SearchResult(
    val comics: List<Comic>,
    val maxPage: Int,
    val currentPage: Int
)
```

### 4.2 JS 侧类型转换

| JS 类型 | Kotlin 类型 |
|---------|-------------|
| `Comic` object | `Comic` data class |
| `ComicDetails` object | `ComicDetails` data class |
| `Chapter` object | `Chapter` data class |
| `string[]` | `List<String>` |
| `{status, body, headers}` object | `Map<String, Any>` |

## 5. API 实现范围

完整实现 Venera API（按优先级）:

| 优先级 | API | 说明 |
|--------|-----|------|
| P0 | `search.load(keyword, options, page)` | 搜索漫画 |
| P0 | `comic.loadInfo(id)` | 获取漫画详情 |
| P0 | `comic.loadEp(comicId, epId)` | 获取章节图片 |
| P1 | `explore` | 首页推荐 |
| P1 | `category` | 分类浏览 |
| P1 | `favorites` | 收藏管理 |
| P2 | `account` | 登录功能 |
| P2 | `comic.loadComments` | 评论功能 |

## 6. 错误处理

```kotlin
sealed class JsSourceException(message: String) : Exception(message) {
    class SourceNotFound(val key: String) : JsSourceException("Source not found: $key")
    class ScriptError(val message: String, val stack: String?) : JsSourceException("Script error: $message")
    class NetworkError(val message: String) : JsSourceException("Network error: $message")
    class ParseError(val message: String) : JsSourceException("Parse error: $message")
    class LoginRequired(val message: String = "Login required") : JsSourceException(message)
}
```

## 7. 源文件结构

```
app/src/main/assets/
└── sources/
    ├── _venera_.js      # 类型定义（供 IDE 智能提示）
    └── jm18c.js         # 示例漫画源
```

## 8. 测试策略

- **单元测试**: 测试 JsEngineBridge 的 Context 管理
- **集成测试**: 测试完整的搜索流程（加载源 → 执行搜索 → 解析结果）
- **Mock**: 使用 MockWebServer 模拟漫画网站响应

## 9. 依赖

| 库 | 用途 |
|----|------|
| `io.github.taoweiji.quickjs:quickjs-android:1.3.0` | QuickJS 引擎 |
| `com.squareup.okhttp3:okhttp:3.12.13` | HTTP 请求 |
| `org.conscrypt:conscrypt-android:2.5.2` | TLS 1.2 支持 |
| `com.google.code.gson:gson:2.10.1` | JSON 解析 |

## 10. 后续扩展

- [ ] 从 WebDAV 动态下载源文件
- [ ] 支持源文件在线更新
- [ ] 添加更多预置源