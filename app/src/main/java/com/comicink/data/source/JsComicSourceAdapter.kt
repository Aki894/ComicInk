package com.comicink.data.source

import android.content.Context
import com.comicink.data.model.Chapter
import com.comicink.data.model.ChapterImages
import com.comicink.data.model.Comic
import com.comicink.data.model.ComicDetails
import com.comicink.data.model.SearchResult
import com.quickjs.JSContext
import com.quickjs.JSObject
import com.quickjs.JSValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Venera 漫画源适配器
 * 实现 Venera 风格的 JS 源 API：search、getComicInfo、getChapterImages
 * 使用 JsEngineBridge 和 JsGlobalBridge 桥接 JS 环境
 * 使用协程在 IO 线程执行
 */
class JsComicSourceAdapter(
    private val context: Context,
    private val sourceKey: String,
    private val engineBridge: JsEngineBridge,
    private val sourceManager: JsSourceManager
) {
    private val gson = Gson()

    companion object {
        private val EMPTY_OPTIONS: Map<String, Any> = emptyMap()
    }

    /**
     * 在 JS 上下文中执行代码的公共方法
     * 处理创建 context、注册 bridge、加载源文件的重复逻辑
     *
     * @param block 在 JS 上下文中执行的代码块
     * @return 执行结果
     */
    private suspend fun <T> executeInJsContext(block: (JSContext) -> T): T = withContext(Dispatchers.IO) {
        val closeableContext = engineBridge.createContext()
        closeableContext.use { jsContext ->
            // 注册全局 API
            val globalBridge = JsGlobalBridge(context, jsContext)
            globalBridge.use {
                it.registerGlobalApis()

                // 加载源文件
                try {
                    val sourceCode = sourceManager.getSourceContent(sourceKey)
                    engineBridge.evaluate(jsContext, sourceCode, "$sourceKey.js")
                } catch (e: Exception) {
                    throw JsSourceException.SourceNotFound(sourceKey)
                }

                // 执行用户代码
                try {
                    block(jsContext)
                } catch (e: JsSourceException) {
                    throw e
                } catch (e: Exception) {
                    throw JsSourceException.ScriptError(e.message ?: "Unknown error", e.stackTrace.toString())
                }
            }
        }
    }

    /**
     * 搜索漫画
     * 调用 JS 源的 search.load(keyword, options, page) 方法
     *
     * @param keyword 搜索关键词
     * @param page 页码，默认为 1
     * @return SearchResult 搜索结果
     */
    suspend fun search(keyword: String, page: Int = 1): SearchResult {
        val escapedKeyword = keyword.replace("'", "\\'")
        val options = gson.toJson(EMPTY_OPTIONS)
        val jsCode = "search.load('$escapedKeyword', $options, $page)"

        return executeInJsContext { jsContext ->
            try {
                val resultValue = engineBridge.evaluate(jsContext, jsCode, "search.js")
                val result = resultValue as? JSObject
                    ?: throw JsSourceException.ScriptError("search.load did not return an object", null)
                parseSearchResult(result)
            } catch (e: JsSourceException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceException.ScriptError("search failed: ${e.message}", e.stackTrace.toString())
            }
        }
    }

    /**
     * 获取漫画详情
     * 调用 JS 源的 comic.loadInfo(id) 方法
     *
     * @param comicId 漫画 ID
     * @return ComicDetails 漫画详情
     */
    suspend fun getComicInfo(comicId: String): ComicDetails {
        val escapedId = comicId.replace("'", "\\'")
        val jsCode = "comic.loadInfo('$escapedId')"

        return executeInJsContext { jsContext ->
            try {
                val resultValue = engineBridge.evaluate(jsContext, jsCode, "comic_info.js")
                val result = resultValue as? JSObject
                    ?: throw JsSourceException.ScriptError("comic.loadInfo did not return an object", null)
                parseComicDetails(result)
            } catch (e: JsSourceException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceException.ScriptError("getComicInfo failed: ${e.message}", e.stackTrace.toString())
            }
        }
    }

    /**
     * 获取章节图片
     * 调用 JS 源的 comic.loadEp(comicId, epId) 方法
     *
     * @param comicId 漫画 ID
     * @param chapterId 章节 ID
     * @return ChapterImages 章节图片列表
     */
    suspend fun getChapterImages(comicId: String, chapterId: String): ChapterImages {
        val escapedComicId = comicId.replace("'", "\\'")
        val escapedChapterId = chapterId.replace("'", "\\'")
        val jsCode = "comic.loadEp('$escapedComicId', '$escapedChapterId')"

        return executeInJsContext { jsContext ->
            try {
                val resultValue = engineBridge.evaluate(jsContext, jsCode, "chapter_images.js")
                val result = resultValue as? JSObject
                    ?: throw JsSourceException.ScriptError("comic.loadEp did not return an object", null)
                parseChapterImages(result, chapterId)
            } catch (e: JsSourceException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceException.ScriptError("getChapterImages failed: ${e.message}", e.stackTrace.toString())
            }
        }
    }

    // ========== 解析方法 ==========

    /**
     * 解析搜索结果
     */
    private fun parseSearchResult(obj: JSObject): SearchResult {
        // 使用 JSON.stringify 将对象转为字符串再解析
        val jsonStr = obj.toString()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(jsonStr, type) ?: emptyMap()

        val comicsList = (map["comics"] as? List<*>)?.mapNotNull { parseComic(it as? Map<String, Any?>) } ?: emptyList()
        val maxPage = (map["maxPage"] as? Number)?.toInt() ?: 1
        val currentPage = (map["currentPage"] as? Number)?.toInt() ?: 1

        return SearchResult(comics = comicsList, maxPage = maxPage, currentPage = currentPage)
    }

    /**
     * 解析漫画详情
     * 兼容 episodes 和 chapters 字段名
     */
    private fun parseComicDetails(obj: JSObject): ComicDetails {
        val jsonStr = obj.toString()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(jsonStr, type) ?: emptyMap()

        // 兼容 episodes 和 chapters 两种字段名
        val chaptersList = (map["episodes"] as? List<*> ?: map["chapters"] as? List<*>)
            ?.mapNotNull { parseChapter(it as? Map<String, Any?>) } ?: emptyList()

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

    /**
     * 解析章节图片
     * 兼容 images 和 pics 字段名
     */
    private fun parseChapterImages(obj: JSObject, chapterId: String): ChapterImages {
        val jsonStr = obj.toString()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(jsonStr, type) ?: emptyMap()

        // 兼容 images 和 pics 两种字段名
        val imagesList = (map["images"] as? List<*> ?: map["pics"] as? List<*>)
            ?.mapNotNull { it?.toString() } ?: emptyList()

        return ChapterImages(chapterId = chapterId, images = imagesList)
    }

    /**
     * 解析单个漫画
     */
    private fun parseComic(map: Map<String, Any?>?): Comic? {
        if (map == null) return null
        return Comic(
            id = map["id"]?.toString() ?: return null,
            title = map["title"]?.toString() ?: return null,
            cover = map["cover"]?.toString() ?: "",
            author = map["subTitle"]?.toString() ?: map["author"]?.toString(),
            tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            description = map["description"]?.toString(),
            url = map["url"]?.toString()
        )
    }

    /**
     * 解析单个章节
     * 兼容 id 和 epId 字段名
     */
    private fun parseChapter(map: Map<String, Any?>?): Chapter? {
        if (map == null) return null
        return Chapter(
            id = map["id"]?.toString() ?: map["epId"]?.toString() ?: "",
            title = map["title"]?.toString() ?: "",
            url = map["url"]?.toString() ?: ""
        )
    }
}