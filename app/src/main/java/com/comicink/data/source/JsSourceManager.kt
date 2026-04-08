package com.comicink.data.source

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 漫画源管理器
 * 负责从 assets/sources/ 目录加载 JS 源文件并解析元数据
 */
class JsSourceManager(private val context: Context) {

    companion object {
        private const val TAG = "JsSourceManager"
        private const val SOURCES_DIR = "sources"
    }

    /**
     * 源文件元数据
     */
    data class SourceMetadata(
        val name: String,
        val key: String,
        val version: String,
        val minAppVersion: String
    )

    /**
     * 源文件信息，包含元数据和内容
     */
    data class SourceInfo(
        val metadata: SourceMetadata,
        val content: String
    )

    /**
     * 缓存已加载的源文件内容
     * Key: 源的唯一标识 key
     * Value: 源文件信息
     */
    private val sourceCache = mutableMapOf<String, SourceInfo>()

    /**
     * 是否已初始化
     */
    @Volatile
    private var isInitialized = false

    /**
     * 初始化，加载所有源文件
     * 应在后台线程调用
     */
    fun initialize() {
        if (isInitialized) {
            return
        }

        try {
            loadAllSources()
            isInitialized = true
            Log.d(TAG, "JsSourceManager initialized with ${sourceCache.size} sources")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize JsSourceManager", e)
            throw e
        }
    }

    /**
     * 加载 assets/sources/ 目录下的所有 JS 源文件
     */
    private fun loadAllSources() {
        try {
            val assets = context.assets.list(SOURCES_DIR)
            if (assets == null || assets.isEmpty()) {
                Log.w(TAG, "No sources found in assets/$SOURCES_DIR")
                return
            }

            assets.filter { it.endsWith(".js") }.forEach { fileName ->
                try {
                    val sourceInfo = loadSourceFile(fileName)
                    if (sourceInfo != null) {
                        sourceCache[sourceInfo.metadata.key] = sourceInfo
                        Log.d(TAG, "Loaded source: ${sourceInfo.metadata.key} (${sourceInfo.metadata.name})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load source file: $fileName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list sources directory", e)
            throw e
        }
    }

    /**
     * 加载单个源文件并解析元数据
     * @param fileName 源文件名
     * @return 源文件信息，如果解析失败返回 null
     */
    private fun loadSourceFile(fileName: String): SourceInfo? {
        val content = readAssetFile("$SOURCES_DIR/$fileName")
        if (content.isNullOrBlank()) {
            Log.w(TAG, "Empty source file: $fileName")
            return null
        }

        val metadata = parseMetadata(content, fileName)
        if (metadata == null) {
            Log.w(TAG, "Failed to parse metadata for: $fileName")
            return null
        }

        return SourceInfo(metadata, content)
    }

    /**
     * 读取 assets 目录下的文件内容
     * @param path 文件路径（相对于 assets 目录）
     * @return 文件内容，如果读取失败返回 null
     */
    private fun readAssetFile(path: String): String? {
        return try {
            context.assets.open(path).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read asset file: $path", e)
            null
        }
    }

    /**
     * 解析源文件中的元数据
     * 支持两种格式：
     * 1. /* {...} */ 格式的元数据注释
     * 2. class XXX extends ComicSource { name = "..."; key = "..."; }
     */
    private fun parseMetadata(content: String, fileName: String): SourceMetadata? {
        // 方式1: 尝试匹配 /* {...} */ 格式
        try {
            val commentRegex = """/\*\{([\s\S]*?)\}\*/"""
            val commentMatch = Regex(commentRegex).find(content)
            if (commentMatch != null) {
                val metadataJson = commentMatch.groupValues.getOrNull(1)
                if (metadataJson != null) {
                    return parseFromJson(metadataJson, fileName)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Comment format parse failed for: $fileName")
        }

        // 方式2: 尝试匹配 class 格式
        try {
            // 提取 class 后面的类名作为 key
            val classRegex = """class\s+(\w+)\s+extends\s+ComicSource\s*\{"""
            val classMatch = Regex(classRegex).find(content)
            val className = classMatch?.groupValues?.getOrNull(1) ?: fileName.removeSuffix(".js")

            // 提取属性值
            val name = extractProperty(content, "name") ?: className
            val key = extractProperty(content, "key") ?: className.lowercase()
            val version = extractProperty(content, "version") ?: "1.0.0"
            val minAppVersion = extractProperty(content, "minAppVersion") ?: "1.0.0"

            if (key.isNotBlank()) {
                Log.d(TAG, "Parsed class format: key=$key, name=$name, version=$version")
                return SourceMetadata(
                    name = name,
                    key = key,
                    version = version,
                    minAppVersion = minAppVersion
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Class format parse failed for: $fileName", e)
        }

        Log.w(TAG, "No metadata found in $fileName")
        return null
    }

    /**
     * 从源码中提取属性值
     */
    private fun extractProperty(content: String, propertyName: String): String? {
        // 匹配 propertyName = "value" 或 propertyName = 'value' 或 propertyName = 1.0.0
        val patterns = listOf(
            """$propertyName\s*=\s*"([^"]*)"""".toRegex(),
            """$propertyName\s*=\s*'([^']*)'""".toRegex(),
            """$propertyName\s*=\s*(\d+\.\d+\.\d+)""".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) {
                return match.groupValues.getOrNull(1)
            }
        }
        return null
    }

    /**
     * 从 JSON 字符串解析元数据
     */
    private fun parseFromJson(metadataJson: String, fileName: String): SourceMetadata? {
        return try {
            val json = JSONObject(metadataJson)

            val name = json.optString("name", "")
            val key = json.optString("key", "")
            val version = json.optString("version", "")
            val minAppVersion = json.optString("minAppVersion", "")

            // 验证必需字段
            if (name.isBlank() || key.isBlank() || version.isBlank()) {
                Log.w(TAG, "Missing required fields in metadata: $fileName")
                return null
            }

            SourceMetadata(
                name = name,
                key = key,
                version = version,
                minAppVersion = minAppVersion
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse metadata JSON: $fileName", e)
            null
        }
    }

    /**
     * 获取源文件内容
     * @param key 源的唯一标识
     * @return 源文件内容，如果不存在抛出异常
     * @throws JsSourceException.SourceNotFound 当源不存在时
     */
    fun getSourceContent(key: String): String {
        val sourceInfo = sourceCache[key]
            ?: throw JsSourceException.SourceNotFound(key)
        return sourceInfo.content
    }

    /**
     * 获取源文件信息（包含元数据）
     * @param key 源的唯一标识
     * @return 源文件信息，如果不存在抛出异常
     * @throws JsSourceException.SourceNotFound 当源不存在时
     */
    fun getSourceInfo(key: String): SourceInfo {
        return sourceCache[key]
            ?: throw JsSourceException.SourceNotFound(key)
    }

    /**
     * 获取所有已加载的源
     * @return 源 key 列表
     */
    fun getAllSourceKeys(): List<String> {
        return sourceCache.keys.toList()
    }

    /**
     * 获取所有源的元数据
     * @return 元数据列表
     */
    fun getAllMetadata(): List<SourceMetadata> {
        return sourceCache.values.map { it.metadata }
    }

    /**
     * 检查源是否存在
     * @param key 源的唯一标识
     * @return 是否存在
     */
    fun hasSource(key: String): Boolean {
        return sourceCache.containsKey(key)
    }

    /**
     * 重新加载指定源
     * @param key 源的唯一标识
     */
    fun reloadSource(key: String) {
        try {
            val fileName = "$key.js"
            val sourceInfo = loadSourceFile(fileName)
            if (sourceInfo != null) {
                sourceCache[key] = sourceInfo
                Log.d(TAG, "Reloaded source: $key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload source: $key", e)
            throw e
        }
    }

    /**
     * 重新加载所有源
     */
    fun reloadAll() {
        sourceCache.clear()
        isInitialized = false
        initialize()
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        sourceCache.clear()
        isInitialized = false
    }
}