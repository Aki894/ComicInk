package com.comicink.domain.repository

import android.content.Context
import com.comicink.data.model.ChapterImages
import com.comicink.data.model.ComicDetails
import com.comicink.data.model.SearchResult
import com.comicink.data.model.SourceMeta
import com.comicink.data.source.JsComicSourceAdapter
import com.comicink.data.source.JsEngineBridge
import com.comicink.data.source.JsSourceException
import com.comicink.data.source.JsSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 漫画源仓库
 * 作为对外统一接口，提供漫画源的搜索、详情、章节图片获取等功能
 * 使用 OkHttpClient 进行网络请求（部分源可能需要直接 HTTP 请求）
 * 使用 QuickJS 引擎执行 JavaScript 源
 */
class ComicSourceRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val sourceManager: JsSourceManager by lazy {
        JsSourceManager(context).also { it.initialize() }
    }

    private val engineBridge: JsEngineBridge by lazy {
        JsEngineBridge()
    }

    /**
     * 获取指定源的适配器
     * @param sourceKey 漫画源 key
     * @return JsComicSourceAdapter 实例
     */
    private fun getAdapter(sourceKey: String): JsComicSourceAdapter {
        return JsComicSourceAdapter(context, sourceKey, engineBridge, sourceManager)
    }

    /**
     * 列出所有可用漫画源
     * @return 漫画源元数据列表
     */
    suspend fun listSources(): List<SourceMeta> = withContext(Dispatchers.IO) {
        sourceManager.getAllMetadata().map { metadata ->
            SourceMeta(
                key = metadata.key,
                name = metadata.name,
                version = metadata.version,
                minAppVersion = metadata.minAppVersion
            )
        }
    }

    /**
     * 搜索漫画
     * @param keyword 搜索关键词
     * @param sourceKey 漫画源 key（可选，默认搜索所有源）
     * @param page 页码，默认为 1
     * @return 搜索结果
     * @throws JsSourceException.SourceNotFound 当指定源不存在时
     * @throws JsSourceException.ScriptError 当搜索执行出错时
     */
    suspend fun search(
        keyword: String,
        sourceKey: String? = null,
        page: Int = 1
    ): SearchResult = withContext(Dispatchers.IO) {
        if (sourceKey != null) {
            // 搜索指定源
            if (!sourceManager.hasSource(sourceKey)) {
                throw JsSourceException.SourceNotFound(sourceKey)
            }
            getAdapter(sourceKey).search(keyword, page)
        } else {
            // 搜索第一个可用的源（目前仅支持单源搜索）
            val availableSources = sourceManager.getAllSourceKeys()
            if (availableSources.isEmpty()) {
                // 返回空结果
                SearchResult(
                    comics = emptyList(),
                    maxPage = 1,
                    currentPage = page
                )
            } else {
                // 使用第一个可用的源进行搜索
                getAdapter(availableSources.first()).search(keyword, page)
            }
        }
    }

    /**
     * 获取漫画详情
     * @param comicId 漫画 ID
     * @param sourceKey 漫画源 key（可选）
     * @return 漫画详情
     * @throws JsSourceException.SourceNotFound 当指定源不存在时
     * @throws JsSourceException.ScriptError 当获取详情执行出错时
     */
    suspend fun getComicInfo(
        comicId: String,
        sourceKey: String? = null
    ): ComicDetails = withContext(Dispatchers.IO) {
        val key = sourceKey ?: sourceManager.getAllSourceKeys().firstOrNull()
            ?: throw JsSourceException.SourceNotFound("no source available")

        if (!sourceManager.hasSource(key)) {
            throw JsSourceException.SourceNotFound(key)
        }

        getAdapter(key).getComicInfo(comicId)
    }

    /**
     * 获取章节图片
     * @param comicId 漫画 ID
     * @param chapterId 章节 ID
     * @param sourceKey 漫画源 key（可选）
     * @return 章节图片列表
     * @throws JsSourceException.SourceNotFound 当指定源不存在时
     * @throws JsSourceException.ScriptError 当获取图片执行出错时
     */
    suspend fun getChapterImages(
        comicId: String,
        chapterId: String,
        sourceKey: String? = null
    ): ChapterImages = withContext(Dispatchers.IO) {
        val key = sourceKey ?: sourceManager.getAllSourceKeys().firstOrNull()
            ?: throw JsSourceException.SourceNotFound("no source available")

        if (!sourceManager.hasSource(key)) {
            throw JsSourceException.SourceNotFound(key)
        }

        getAdapter(key).getChapterImages(comicId, chapterId)
    }

    /**
     * 检查指定源是否存在
     * @param sourceKey 漫画源 key
     * @return 是否存在
     */
    fun hasSource(sourceKey: String): Boolean {
        return sourceManager.hasSource(sourceKey)
    }

    /**
     * 获取 OkHttpClient 实例
     * 供外部进行 HTTP 请求使用
     * @return OkHttpClient 实例
     */
    fun getHttpClient(): OkHttpClient = okHttpClient

    /**
     * 释放资源
     * 应在不再需要仓库时调用
     */
    fun release() {
        engineBridge.destroy()
        sourceManager.clearCache()
    }
}