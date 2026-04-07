# JS 源解析器实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Venera 兼容的 JS 漫画源解析器，支持搜索、浏览、章节获取等功能

**Architecture:** 使用 QuickJS 引擎执行 JavaScript 源文件，通过独立的 Context 隔离每个源的操作，用完即焚以优化 E-ink 设备内存

**Tech Stack:** Kotlin, QuickJS (taoweiji), OkHttp 3.12, Gson

---

## 文件结构

```
app/src/main/java/com/comicink/
├── data/
│   ├── model/
│   │   └── ComicModels.kt          # 数据模型（Comic, Chapter, etc.）
│   └── source/
│       ├── JsSourceException.kt    # 异常定义
│       ├── JsSourceManager.kt      # 漫画源管理器
│       ├── JsEngineBridge.kt       # QuickJS 引擎封装
│       ├── JsGlobalBridge.kt       # JS 全局 API 桥接
│       └── JsComicSourceAdapter.kt # Venera API 适配器
└── domain/
    └── repository/
        └── ComicSourceRepository.kt # 漫画源仓库（对外接口）

app/src/main/assets/
└── sources/
    ├── _venera_.js                  # Venera 类型定义
    └── jm18c.js                     # 示例漫画源
```

---

## Task 1: 数据模型

**Files:**
- Create: `app/src/main/java/com/comicink/data/model/ComicModels.kt`
- Test: N/A (data class)

- [ ] **Step 1: 创建数据模型文件**

```kotlin
// app/src/main/java/com/comicink/data/model/ComicModels.kt
package com.comicink.data.model

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
    val images: List<String>
)

data class SearchResult(
    val comics: List<Comic>,
    val maxPage: Int,
    val currentPage: Int
)

data class SourceMeta(
    val key: String,
    val name: String,
    val version: String,
    val minAppVersion: String
)
```

- [ ] **Step 2: 验证代码格式**
Run: 检查文件是否创建成功

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/model/ComicModels.kt
git commit -m "feat: add comic data models"
```

---

## Task 2: 异常定义

**Files:**
- Create: `app/src/main/java/com/comicink/data/source/JsSourceException.kt`

- [ ] **Step 1: 创建异常类**

```kotlin
// app/src/main/java/com/comicink/data/source/JsSourceException.kt
package com.comicink.data.source

sealed class JsSourceException(message: String) : Exception(message) {
    class SourceNotFound(val key: String) : JsSourceException("Source not found: $key")
    class ScriptError(val message: String, val stack: String? = null) : JsSourceException("Script error: $message")
    class NetworkError(val message: String) : JsSourceException("Network error: $message")
    class ParseError(val message: String) : JsSourceException("Parse error: $message")
    class LoginRequired(val message: String = "Login required") : JsSourceException(message)
}
```

- [ ] **Step 2: Commit**
```bash
git add app/src/main/java/com/comicink/data/source/JsSourceException.kt
git commit -m "feat: add JsSourceException"
```

---

## Task 3: QuickJS 引擎封装

**Files:**
- Create: `app/src/main/java/com/comicink/data/source/JsEngineBridge.kt`

- [ ] **Step 1: 创建 JsEngineBridge**

```kotlin
// app/src/main/java/com/comicink/data/source/JsEngineBridge.kt
package com.comicink.data.source

import com.eclipsesource.quickjs.QuickJS
import com.eclipsesource.quickjs.JSContext
import com.eclipsesource.quickjs.JSValue

/**
 * QuickJS 引擎封装
 * 提供 Context 隔离，每个操作创建独立 Context 用完即焚
 */
class JsEngineBridge {

    private val quickJS: QuickJS by lazy {
        QuickJS.createRuntime()
    }

    /**
     * 创建独立的 JS Context
     * 每次操作后必须调用 close() 释放内存
     */
    fun createContext(): JSContext {
        return quickJS.createContext()
    }

    /**
     * 执行 JS 代码
     * @param context JS 上下文
     * @param code JS 代码
     * @param fileName 文件名（用于错误信息）
     */
    fun evaluate(context: JSContext, code: String, fileName: String): JSValue {
        return context.evaluate(code, fileName)
    }

    /**
     * 调用 JS 对象方法
     * @param context JS 上下文
     * @param obj JS 对象
     * @param method 方法名
     * @param args 参数
     */
    fun callMethod(context: JSContext, obj: JSValue, method: String, vararg args: Any?): JSValue {
        val methodValue = obj.getProperty(method)
        return methodValue.call(*args)
    }

    /**
     * 关闭引擎，释放所有资源
     */
    fun destroy() {
        quickJS.close()
    }
}
```

- [ ] **Step 2: 验证编译**
Run: `gradle :app:compileDebugKotlin` 确认无错误

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/source/JsEngineBridge.kt
git commit -m "feat: add JsEngineBridge for QuickJS"
```

---

## Task 4: JS 全局 API 桥接

**Files:**
- Create: `app/src/main/java/com/comicink/data/source/JsGlobalBridge.kt`
- Modify: `app/build.gradle` (添加 OkHttp 依赖到 source 模块)

- [ ] **Step 1: 创建 JsGlobalBridge**

```kotlin
// app/src/main/java/com/comicink/data/source/JsGlobalBridge.kt
package com.comicink.data.source

import android.content.Context
import android.content.SharedPreferences
import com.eclipsesource.quickjs.JSContext
import com.eclipsesource.quickjs.JSObject
import com.eclipsesource.quickjs.JSFunction
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * JS 全局 API 桥接
 * 提供 Network.get/post, saveData/loadData 等 Venera API
 */
class JsGlobalBridge(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val localStorage: SharedPreferences
) {

    companion object {
        const val GLOBAL_NAME = "comicSource"
    }

    /**
     * 注册全局对象到 JS Context
     */
    fun register(context: JSContext) {
        val globalObject = context.globalObject

        // 创建 Network 对象
        val networkObject = JSObject(context)
        networkObject.setProperty("get", createGetFunction(context))
        networkObject.setProperty("post", createPostFunction(context))

        // 创建 this 对象（Venera 风格）
        val thisObject = JSObject(context)
        thisObject.setProperty("Network", networkObject)
        thisObject.setProperty("saveData", createSaveDataFunction(context))
        thisObject.setProperty("loadData", createLoadDataFunction(context))
        thisObject.setProperty("deleteData", createDeleteDataFunction(context))

        globalObject.setProperty(GLOBAL_NAME, thisObject)
    }

    private fun createGetFunction(context: JSContext): JSFunction {
        return JSFunction(context, "get") { args ->
            val url = args[0] as? String ?: throw IllegalArgumentException("URL required")
            val headers = (args.getOrNull(1) as? JSObject)?.let { parseHeaders(it) } ?: emptyMap()
            executeGet(url, headers)
        }
    }

    private fun createPostFunction(context: JSContext): JSFunction {
        return JSFunction(context, "post") { args ->
            val url = args[0] as? String ?: throw IllegalArgumentException("URL required")
            val headers = (args.getOrNull(1) as? JSObject)?.let { parseHeaders(it) } ?: emptyMap()
            val body = args[2] as? String ?: ""
            executePost(url, headers, body)
        }
    }

    private fun createSaveDataFunction(context: JSContext): JSFunction {
        return JSFunction(context, "saveData") { args ->
            val key = args[0] as? String ?: return@JSFunction
            val value = args[1] as? String ?: return@JSFunction
            saveData(key, value)
        }
    }

    private fun createLoadDataFunction(context: JSContext): JSFunction {
        return JSFunction(context, "loadData") { args ->
            val key = args[0] as? String ?: null
            key?.let { loadData(it) }
        }
    }

    private fun createDeleteDataFunction(context: JSContext): JSFunction {
        return JSFunction(context, "deleteData") { args ->
            val key = args[0] as? String ?: return@JSFunction
            deleteData(key)
        }
    }

    private fun parseHeaders(obj: JSObject): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        obj.propertyNames.forEach { name ->
            headers[name] = obj.getProperty(name).toString()
        }
        return headers
    }

    private fun executeGet(url: String, headers: Map<String, String>): Map<String, Any?> {
        return try {
            val request = Request.Builder()
                .url(url)
                .also { req -> headers.forEach { (k, v) -> req.addHeader(k, v) } }
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                mapOf(
                    "status" to response.code,
                    "body" to response.body?.string(),
                    "headers" to response.headers.toMap()
                )
            }
        } catch (e: Exception) {
            mapOf(
                "status" to -1,
                "body" to e.message,
                "headers" to emptyMap<String, String>()
            )
        }
    }

    private fun executePost(url: String, headers: Map<String, String>, body: String): Map<String, Any?> {
        return try {
            val contentType = headers["content-type"] ?: "application/x-www-form-urlencoded"
            val request = Request.Builder()
                .url(url)
                .also { req -> headers.forEach { (k, v) -> if (k != "content-type") req.addHeader(k, v) } }
                .post(body.toRequestBody(contentType.toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                mapOf(
                    "status" to response.code,
                    "body" to response.body?.string(),
                    "headers" to response.headers.toMap()
                )
            }
        } catch (e: Exception) {
            mapOf(
                "status" to -1,
                "body" to e.message,
                "headers" to emptyMap<String, String>()
            )
        }
    }

    fun saveData(key: String, value: String) {
        localStorage.edit().putString(key, value).apply()
    }

    fun loadData(key: String): String? {
        return localStorage.getString(key, null)
    }

    fun deleteData(key: String) {
        localStorage.edit().remove(key).apply()
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }
}
```

- [ ] **Step 2: 验证编译**

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/source/JsGlobalBridge.kt
git commit -m "feat: add JsGlobalBridge for JS API"
```

---

## Task 5: 漫画源管理器

**Files:**
- Create: `app/src/main/java/com/comicink/data/source/JsSourceManager.kt`

- [ ] **Step 1: 创建 JsSourceManager**

```kotlin
// app/src/main/java/com/comicink/data/source/JsSourceManager.kt
package com.comicink.data.source

import android.content.Context
import com.comicink.data.model.SourceMeta
import java.io.File

/**
 * 漫画源管理器
 * 从 assets/sources/ 目录加载 JS 源文件
 */
class JsSourceManager(
    private val context: Context
) {

    private var sourcesCache: List<SourceMeta>? = null
    private val sourceCodeCache = mutableMapOf<String, String>()

    /**
     * 加载所有可用漫画源
     */
    fun loadSources(): List<SourceMeta> {
        sourcesCache?.let { return it }

        val sources = mutableListOf<SourceMeta>()
        try {
            val assets = context.assets.list("sources") ?: emptyArray()
            assets.filter { it.endsWith(".js") && !it.startsWith("_") }.forEach { fileName ->
                val key = fileName.removeSuffix(".js")
                val content = getSourceContent(key)
                val meta = parseSourceMeta(content, key)
                sources.add(meta)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sourcesCache = sources
        return sources
    }

    /**
     * 获取指定源的内容
     */
    fun getSourceContent(key: String): String {
        sourceCodeCache[key]?.let { return it }

        return try {
            val fileName = "sources/$key.js"
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw JsSourceException.SourceNotFound(key)
        }.also { sourceCodeCache[key] = it }
    }

    /**
     * 列出所有源 key
     */
    fun listSourceKeys(): List<String> {
        return loadSources().map { it.key }
    }

    /**
     * 解析源文件元数据
     * 从 JS 代码中提取 name, key, version
     */
    private fun parseSourceMeta(code: String, key: String): SourceMeta {
        val name = extractProperty(code, "name") ?: key
        val version = extractProperty(code, "version") ?: "1.0.0"
        val minAppVersion = extractProperty(code, "minAppVersion") ?: "1.0.0"

        return SourceMeta(
            key = key,
            name = name,
            version = version,
            minAppVersion = minAppVersion
        )
    }

    private fun extractProperty(code: String, propName: String): String? {
        val regex = Regex("$propName\\s*=\\s*[\"']?([^\"';\\n]+)[\"']?")
        return regex.find(code)?.groupValues?.getOrNull(1)?.trim()
    }
}
```

- [ ] **Step 2: 验证编译**

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/source/JsSourceManager.kt
git commit -m "feat: add JsSourceManager"
```

---

## Task 6: Venera API 适配器

**Files:**
- Create: `app/src/main/java/com/comicink/data/source/JsComicSourceAdapter.kt`

- [ ] **Step 1: 创建 JsComicSourceAdapter**

```kotlin
// app/src/main/java/com/comicink/data/source/JsComicSourceAdapter.kt
package com.comicink.data.source

import android.content.Context
import com.comicink.data.model.Chapter
import com.comicink.data.model.ChapterImages
import com.comicink.data.model.Comic
import com.comicink.data.model.ComicDetails
import com.comicink.data.model.SearchResult
import com.eclipsesource.quickjs.JSContext
import com.eclipsesource.quickjs.JSValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Venera 漫画源适配器
 * 实现 Venera 风格的 JS 源 API
 */
class JsComicSourceAdapter(
    private val context: Context,
    private val sourceKey: String,
    private val engineBridge: JsEngineBridge,
    private val sourceManager: JsSourceManager,
    private val okHttpClient: OkHttpClient,
    private val localStorage: android.content.SharedPreferences
) {

    private val gson = Gson()

    /**
     * 搜索漫画
     * @param keyword 搜索关键词
     * @param page 页码
     */
    suspend fun search(keyword: String, page: Int = 1): SearchResult = withContext(Dispatchers.IO) {
        val context = engineBridge.createContext()
        try {
            // 注册全局 API
            val globalBridge = JsGlobalBridge(context, okHttpClient, localStorage)
            globalBridge.register(context)

            // 加载源文件
            val sourceCode = sourceManager.getSourceContent(sourceKey)
            context.evaluate(sourceCode, "$sourceKey.js")

            // 调用 search.load(keyword, options, page)
            val searchObj = context.globalObject.getProperty("search")
            val loadMethod = searchObj.getProperty("load")
            
            val result = loadMethod.call(keyword, gson.toJson(emptyList<String>()), page)
            parseSearchResult(result)
        } catch (e: Exception) {
            throw JsSourceException.ScriptError(e.message ?: "Unknown error", null)
        } finally {
            context.close()
        }
    }

    /**
     * 获取漫画详情
     */
    suspend fun getComicInfo(comicId: String): ComicDetails = withContext(Dispatchers.IO) {
        val context = engineBridge.createContext()
        try {
            val globalBridge = JsGlobalBridge(context, okHttpClient, localStorage)
            globalBridge.register(context)

            val sourceCode = sourceManager.getSourceContent(sourceKey)
            context.evaluate(sourceCode, "$sourceKey.js")

            // 调用 comic.loadInfo(id)
            val comicObj = context.globalObject.getProperty("comic")
            val loadInfoMethod = comicObj.getProperty("loadInfo")
            
            val result = loadInfoMethod.call(comicId)
            parseComicDetails(result)
        } catch (e: Exception) {
            throw JsSourceException.ScriptError(e.message ?: "Unknown error", null)
        } finally {
            context.close()
        }
    }

    /**
     * 获取章节图片
     */
    suspend fun getChapterImages(comicId: String, chapterId: String): ChapterImages = withContext(Dispatchers.IO) {
        val context = engineBridge.createContext()
        try {
            val globalBridge = JsGlobalBridge(context, okHttpClient, localStorage)
            globalBridge.register(context)

            val sourceCode = sourceManager.getSourceContent(sourceKey)
            context.evaluate(sourceCode, "$sourceKey.js")

            // 调用 comic.loadEp(comicId, epId)
            val comicObj = context.globalObject.getProperty("comic")
            val loadEpMethod = comicObj.getProperty("loadEp")
            
            val result = loadEpMethod.call(comicId, chapterId)
            parseChapterImages(result, chapterId)
        } catch (e: Exception) {
            throw JsSourceException.ScriptError(e.message ?: "Unknown error", null)
        } finally {
            context.close()
        }
    }

    // ========== 解析方法 ==========

    private fun parseSearchResult(value: JSValue): SearchResult {
        val jsonStr = value.toString()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(jsonStr, type) ?: emptyMap()

        val comicsList = (map["comics"] as? List<*>)?.mapNotNull { parseComic(it as? Map<String, Any?>) } ?: emptyList()
        val maxPage = (map["maxPage"] as? Number)?.toInt() ?: 1
        val currentPage = (map["currentPage"] as? Number)?.toInt() ?: 1

        return SearchResult(comics = comicsList, maxPage = maxPage, currentPage = currentPage)
    }

    private fun parseComicDetails(value: JSValue): ComicDetails {
        val jsonStr = value.toString()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(jsonStr, type) ?: emptyMap()

        val chaptersList = (map["episodes"] as? List<*>)?.mapNotNull { 
            parseChapter(it as? Map<String, Any?>) 
        } ?: emptyList()

        return ComicDetails(
            id = map["id"]?.toString() ?: "",
            title = map["title"]?.toString() ?: "",
            cover = map["cover"]?.toString() ?: "",
            author = map["author"]?.toString(),
            tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            description = map["description"]?.toString(),
            chapters = chaptersList
        )
    }

    private fun parseChapterImages(value: JSValue, chapterId: String): ChapterImages {
        val jsonStr = value.toString()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(jsonStr, type) ?: emptyMap()

        val imagesList = (map["images"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        return ChapterImages(chapterId = chapterId, images = imagesList)
    }

    private fun parseComic(map: Map<String, Any?>?): Comic? {
        if (map == null) return null
        return Comic(
            id = map["id"]?.toString() ?: return null,
            title = map["title"]?.toString() ?: return null,
            cover = map["cover"]?.toString() ?: "",
            author = map["subTitle"]?.toString(),
            tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            description = map["description"]?.toString(),
            url = map["url"]?.toString()
        )
    }

    private fun parseChapter(map: Map<String, Any?>?): Chapter? {
        if (map == null) return null
        return Chapter(
            id = map["id"]?.toString() ?: map["epId"]?.toString() ?: "",
            title = map["title"]?.toString() ?: "",
            url = map["url"]?.toString() ?: ""
        )
    }
}
```

- [ ] **Step 2: 验证编译**

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/comicink/data/source/JsComicSourceAdapter.kt
git commit -m "feat: add JsComicSourceAdapter for Venera API"
```

---

## Task 7: 漫画源仓库（对外接口）

**Files:**
- Create: `app/src/main/java/com/comicink/domain/repository/ComicSourceRepository.kt`

- [ ] **Step 1: 创建 ComicSourceRepository**

```kotlin
// app/src/main/java/com/comicink/domain/repository/ComicSourceRepository.kt
package com.comicink.domain.repository

import android.content.Context
import com.comicink.data.model.ChapterImages
import com.comicink.data.model.ComicDetails
import com.comicink.data.model.SearchResult
import com.comicink.data.model.SourceMeta
import com.comicink.data.source.JsComicSourceAdapter
import com.comicink.data.source.JsEngineBridge
import com.comicink.data.source.JsSourceManager
import okhttp3.OkHttpClient

/**
 * 漫画源仓库
 * 对外统一接口，封装所有 JS 源操作
 */
class ComicSourceRepository(
    context: Context,
    okHttpClient: OkHttpClient
) {

    private val sourceManager = JsSourceManager(context)
    private val engineBridge = JsEngineBridge()
    private val localStorage = context.getSharedPreferences("js_source_storage", Context.MODE_PRIVATE)

    /**
     * 列出所有可用漫画源
     */
    fun listSources(): List<SourceMeta> {
        return sourceManager.loadSources()
    }

    /**
     * 搜索漫画
     * @param sourceKey 源 key
     * @param keyword 关键词
     * @param page 页码
     */
    suspend fun search(sourceKey: String, keyword: String, page: Int = 1): SearchResult {
        val adapter = createAdapter(sourceKey)
        return adapter.search(keyword, page)
    }

    /**
     * 获取漫画详情
     */
    suspend fun getComicInfo(sourceKey: String, comicId: String): ComicDetails {
        val adapter = createAdapter(sourceKey)
        return adapter.getComicInfo(comicId)
    }

    /**
     * 获取章节图片
     */
    suspend fun getChapterImages(sourceKey: String, comicId: String, chapterId: String): ChapterImages {
        val adapter = createAdapter(sourceKey)
        return adapter.getChapterImages(comicId, chapterId)
    }

    private fun createAdapter(sourceKey: String): JsComicSourceAdapter {
        return JsComicSourceAdapter(
            context = (sourceManager as JsSourceManager).let { /* 需要 Context */ throw IllegalStateException("Use constructor with context") },
            sourceKey = sourceKey,
            engineBridge = engineBridge,
            sourceManager = sourceManager,
            okHttpClient = okHttpClient,
            localStorage = localStorage
        )
    }

    // 修复：使用 Lazy 延迟获取 Context
    private val context: Context by lazy {
        throw IllegalStateException("Context not available")
    }

    fun withContext(ctx: Context): ComicSourceRepository {
        return ComicSourceRepository(ctx, okHttpClient)
    }

    private class ComicSourceRepositoryImpl(
        private val ctx: Context,
        private val client: OkHttpClient
    ) : ComicSourceRepository(ctx, client)
}
```

- [ ] **Step 2: 简化 Repository 实现（修正上述代码）**

```kotlin
// 重新创建简化版本
// app/src/main/java/com/comicink/domain/repository/ComicSourceRepository.kt
package com.comicink.domain.repository

import android.content.Context
import com.comicink.data.model.ChapterImages
import com.comicink.data.model.ComicDetails
import com.comicink.data.model.SearchResult
import com.comicink.data.model.SourceMeta
import com.comicink.data.source.JsComicSourceAdapter
import com.comicink.data.source.JsEngineBridge
import com.comicink.data.source.JsSourceManager
import okhttp3.OkHttpClient

/**
 * 漫画源仓库
 * 对外统一接口，封装所有 JS 源操作
 */
class ComicSourceRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val sourceManager by lazy { JsSourceManager(context) }
    private val engineBridge by lazy { JsEngineBridge() }
    private val localStorage by lazy { 
        context.getSharedPreferences("js_source_storage", Context.MODE_PRIVATE) 
    }
    private val adapterCache = mutableMapOf<String, JsComicSourceAdapter>()

    /**
     * 列出所有可用漫画源
     */
    fun listSources(): List<SourceMeta> {
        return sourceManager.loadSources()
    }

    /**
     * 搜索漫画
     */
    suspend fun search(sourceKey: String, keyword: String, page: Int = 1): SearchResult {
        val adapter = getOrCreateAdapter(sourceKey)
        return adapter.search(keyword, page)
    }

    /**
     * 获取漫画详情
     */
    suspend fun getComicInfo(sourceKey: String, comicId: String): ComicDetails {
        val adapter = getOrCreateAdapter(sourceKey)
        return adapter.getComicInfo(comicId)
    }

    /**
     * 获取章节图片
     */
    suspend fun getChapterImages(sourceKey: String, comicId: String, chapterId: String): ChapterImages {
        val adapter = getOrCreateAdapter(sourceKey)
        return adapter.getChapterImages(comicId, chapterId)
    }

    private fun getOrCreateAdapter(sourceKey: String): JsComicSourceAdapter {
        return adapterCache.getOrPut(sourceKey) {
            JsComicSourceAdapter(
                context = context,
                sourceKey = sourceKey,
                engineBridge = engineBridge,
                sourceManager = sourceManager,
                okHttpClient = okHttpClient,
                localStorage = localStorage
            )
        }
    }
}
```

- [ ] **Step 3: 验证编译**

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/comicink/domain/repository/ComicSourceRepository.kt
git commit -m "feat: add ComicSourceRepository"
```

---

## Task 8: 创建示例漫画源

**Files:**
- Create: `app/src/main/assets/sources/jm18c.js`
- Create: `app/src/main/assets/sources/_venera_.js`

- [ ] **Step 1: 创建 _venera_.js 类型定义**

从 https://raw.githubusercontent.com/venera-app/venera-configs/master/_venera_.js 下载或创建简化版

```javascript
// app/src/main/assets/sources/_venera_.js
/**
 * Venera 类型定义
 * 供 IDE 智能提示
 */
class ComicSource {}
class Comic {
    constructor(data) {
        this.id = data.id;
        this.title = data.title;
        this.cover = data.cover;
        this.subTitle = data.subTitle;
        this.tags = data.tags || [];
        this.description = data.description;
        this.url = data.url;
    }
}
class ComicDetails {
    constructor(data) {
        this.id = data.id;
        this.title = data.title;
        this.cover = data.cover;
        this.author = data.author;
        this.tags = data.tags || [];
        this.description = data.description;
        this.episodes = data.episodes || [];
    }
}
class Comment {
    constructor(data) {
        this.userName = data.userName;
        this.avatar = data.avatar;
        this.content = data.content;
        this.time = data.time;
        this.replyCount = data.replyCount;
        this.id = data.id;
    }
}
```

- [ ] **Step 2: 创建 jm18c.js 示例源**

```javascript
// app/src/main/assets/sources/jm18c.js
/**
 * JM18C 漫画源
 * 示例：展示基本 API 实现
 */

class Jm18cSource extends ComicSource {
    name = "JM18C"
    key = "jm18c"
    version = "1.0.0"
    minAppVersion = "1.0.0"
    url = "https://jm18c.com"

    // 搜索
    search = {
        load: async (keyword, options, page) => {
            try {
                let res = await Network.get(
                    `https://jm18c.com/search?q=${encodeURIComponent(keyword)}&page=${page}`,
                    { "User-Agent": "Mozilla/5.0" }
                )
                
                if (res.status !== 200) {
                    throw `Error: ${res.status}`
                }
                
                // 解析 HTML（简化示例）
                let comics = this.parseSearchResults(res.body)
                
                return {
                    comics: comics,
                    maxPage: 10
                }
            } catch (e) {
                throw e
            }
        }
    }

    // 漫画详情
    comic = {
        loadInfo: async (id) => {
            let res = await Network.get(
                `https://jm18c.com/comic/${id}`,
                { "User-Agent": "Mozilla/5.0" }
            )
            
            return this.parseComicInfo(id, res.body)
        },
        
        loadEp: async (comicId, epId) => {
            let res = await Network.get(
                `https://jm18c.com/ep/${comicId}/${epId}`,
                { "User-Agent": "Mozilla/5.0" }
            )
            
            return this.parseChapterImages(res.body)
        }
    }

    parseSearchResults(html) {
        // 简化解析 - 实际需要根据网站 HTML 结构实现
        // 返回 Comic 数组
        return []
    }

    parseComicInfo(id, html) {
        return new ComicDetails({
            id: id,
            title: "漫画标题",
            cover: "",
            author: "作者",
            tags: [],
            description: "",
            episodes: []
        })
    }

    parseChapterImages(html) {
        // 返回图片 URL 数组
        return { images: [] }
    }
}

// 导出源实例
new Jm18cSource()
```

- [ ] **Step 3: Commit**
```bash
git add app/src/main/assets/sources/
git commit -m "feat: add sample comic sources"
```

---

## Task 9: 构建测试

**Files:**
- Modify: `app/build.gradle` (如需要)

- [ ] **Step 1: 运行 assembleDebug**
Run: `gradle assembleDebug`

- [ ] **Step 2: 检查 APK 输出**
Run: `ls app/build/outputs/apk/debug/`

- [ ] **Step 3: Commit**
```bash
git add .
git commit -m "build: verify JS source parser compiles"
```

---

## 验证清单

完成所有任务后，请验证：

- [ ] `JsEngineBridge` 能创建和关闭 Context
- [ ] `JsGlobalBridge` 能处理 Network.get/post 请求
- [ ] `JsSourceManager` 能加载 assets/sources/ 下的 .js 文件
- [ ] `JsComicSourceAdapter` 能调用 JS 函数并解析结果
- [ ] `ComicSourceRepository` 提供统一的对外接口
- [ ] APK 构建成功且大小 < 15MB

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-07-js-source-parser-plan.md`. Two execution options:**

1. **Subagent-Driven (recommended)** - 我为每个任务派遣一个子代理，任务间进行审查，快速迭代
2. **Inline Execution** - 在当前会话中执行任务，带审查检查点的批量执行

**Which approach?**