package com.comicink.data.source

import android.content.Context
import com.quickjs.JSArray
import com.quickjs.JSContext
import com.quickjs.JSFunction
import com.quickjs.JSObject
import com.quickjs.JSValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * JS 全局 API 桥接类
 * 提供 Venera API 兼容的全局对象到 JS Context
 */
class JsGlobalBridge(
    private val context: Context,
    private val jsContext: JSContext
) {
    private val prefs by lazy {
        context.getSharedPreferences("js_source_data", Context.MODE_PRIVATE)
    }

    /**
     * 注册所有全局 API 到 JS Context
     */
    fun registerGlobalApis() {
        registerNetworkApi()
        registerStorageApi()
    }

    /**
     * 从 Content-Type header 中提取字符集
     */
    private fun getCharsetFromContentType(contentType: String?): Charset {
        if (contentType == null) return StandardCharsets.UTF_8
        val regex = """charset=([^;\s]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(contentType)
        return try {
            match?.groupValues?.get(1)?.let { Charset.forName(it) } ?: StandardCharsets.UTF_8
        } catch (e: Exception) {
            StandardCharsets.UTF_8
        }
    }

    /**
     * 从响应头中获取 Content-Type
     */
    private fun getContentType(connection: HttpURLConnection): String? {
        return connection.getHeaderField("Content-Type")
    }

    /**
     * 注册 Network API
     * - Network.get(url, headers) -> { status, body, headers, error }
     * - Network.post(url, headers, body) -> { status, body, headers, error }
     */
    private fun registerNetworkApi() {
        val networkObj = JSObject(jsContext)

        // Network.get(url, headers)
        val getFunction = JSFunction(jsContext) { _, args ->
            val url = args.getString(0) ?: throw IllegalArgumentException("url is required")
            val headers = args.getObject(1)

            // 使用 runBlocking 在 IO 线程执行网络请求
            val result = runBlocking(Dispatchers.IO) {
                executeHttpRequest("GET", url, headers, null)
            }
            mapToJSObject(result)
        }
        networkObj.set("get", getFunction)

        // Network.post(url, headers, body)
        val postFunction = JSFunction(jsContext) { _, args ->
            val url = args.getString(0) ?: throw IllegalArgumentException("url is required")
            val headers = args.getObject(1)
            val body = args.getString(2)

            // 使用 runBlocking 在 IO 线程执行网络请求
            val result = runBlocking(Dispatchers.IO) {
                executeHttpRequest("POST", url, headers, body)
            }
            mapToJSObject(result)
        }
        networkObj.set("post", postFunction)

        // 注册到全局 - 使用 context.set()
        jsContext.set("Network", networkObj)
    }

    /**
     * 将 Map 转换为 JSObject
     */
    private fun mapToJSObject(map: Map<String, Any>): JSObject {
        val jsObj = JSObject(jsContext)
        map.forEach { (key, value) ->
            when (value) {
                is String -> jsObj.set(key, value)
                is Int -> jsObj.set(key, value)
                is Boolean -> jsObj.set(key, value)
                is Long -> jsObj.set(key, value.toDouble())
                is Double -> jsObj.set(key, value)
                is Map<*, *> -> jsObj.set(key, mapToJSObject(value as Map<String, Any>))
                is List<*> -> jsObj.set(key, listToJSArray(value))
            }
        }
        return jsObj
    }

    /**
     * 将 List 转换为 JSArray
     */
    private fun listToJSArray(list: List<*>): JSArray {
        val jsArray = JSArray(jsContext)
        list.forEach { value ->
            when (value) {
                is String -> jsArray.push(value)
                is Int -> jsArray.push(value)
                is Boolean -> jsArray.push(value)
                is Long -> jsArray.push(value.toDouble())
                is Double -> jsArray.push(value)
                is Map<*, *> -> jsArray.push(mapToJSObject(value as Map<String, Any>))
                is List<*> -> jsArray.push(listToJSArray(value))
                else -> jsArray.push(value?.toString() ?: "")
            }
        }
        return jsArray
    }

    /**
     * 执行 HTTP 请求（在 IO 线程执行）
     * 返回包含 status, body, headers, error 的 Map
     */
    private suspend fun executeHttpRequest(
        method: String,
        url: String,
        headers: JSObject?,
        body: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val parsedUrl = URL(url)
            connection = parsedUrl.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // 设置自定义 headers
            if (headers != null) {
                val keys = headers.getKeys()
                for (key in keys) {
                    val value = headers.getString(key)
                    if (value != null) {
                        connection.setRequestProperty(key, value)
                    }
                }
            }

            // 设置请求体
            if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode

            // 读取响应头
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.joinToString(", ")
                }
            }

            // 从 Content-Type 获取字符集
            val charset = getCharsetFromContentType(getContentType(connection))

            // 读取响应体
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream?.let { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                        reader.readText()
                    }
                } ?: ""
            } else {
                connection.errorStream?.let { errorStream ->
                    BufferedReader(InputStreamReader(errorStream, charset)).use { reader ->
                        reader.readText()
                    }
                } ?: ""
            }

            mapOf(
                "status" to responseCode,
                "body" to responseBody,
                "headers" to responseHeaders,
                "error" to ""
            )
        } catch (e: UnknownHostException) {
            // 网络不可达/DNS 解析失败
            mapOf(
                "status" to -1,
                "body" to "",
                "headers" to emptyMap<String, String>(),
                "error" to "Network unreachable: ${e.message}"
            )
        } catch (e: SocketTimeoutException) {
            // 连接超时或读取超时
            mapOf(
                "status" to -2,
                "body" to "",
                "headers" to emptyMap<String, String>(),
                "error" to "Request timeout: ${e.message}"
            )
        } catch (e: MalformedURLException) {
            // URL 格式错误
            mapOf(
                "status" to -3,
                "body" to "",
                "headers" to emptyMap<String, String>(),
                "error" to "Invalid URL: ${e.message}"
            )
        } catch (e: java.io.IOException) {
            // 其他 IO 错误（网络中断、连接被拒绝等）
            mapOf(
                "status" to -4,
                "body" to "",
                "headers" to emptyMap<String, String>(),
                "error" to "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            // 其他未知错误
            mapOf(
                "status" to -99,
                "body" to "",
                "headers" to emptyMap<String, String>(),
                "error" to "Unknown error: ${e.message}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 注册存储 API
     * - saveData(key, value)
     * - loadData(key)
     * - deleteData(key)
     */
    private fun registerStorageApi() {
        // saveData(key, value)
        val saveDataFunction = JSFunction(jsContext) { _, args ->
            val key = args.getString(0) ?: throw IllegalArgumentException("key is required")
            val value = args.getString(1) ?: throw IllegalArgumentException("value is required")

            prefs.edit().putString(key, value).apply()
            // 返回 true
            val result = JSObject(jsContext)
            result.set("value", true)
            result
        }
        jsContext.set("saveData", saveDataFunction)

        // loadData(key)
        val loadDataFunction = JSFunction(jsContext) { _, args ->
            val key = args.getString(0) ?: throw IllegalArgumentException("key is required")

            val value = prefs.getString(key, null)
            if (value != null) {
                // 返回 { value: "..." }
                val result = JSObject(jsContext)
                result.set("value", value)
                result
            } else {
                // 返回 null - 使用 JSValue.NULL()
                JSValue.NULL()
            }
        }
        jsContext.set("loadData", loadDataFunction)

        // deleteData(key)
        val deleteDataFunction = JSFunction(jsContext) { _, args ->
            val key = args.getString(0) ?: throw IllegalArgumentException("key is required")

            prefs.edit().remove(key).apply()
            // 返回 true
            val result = JSObject(jsContext)
            result.set("value", true)
            result
        }
        jsContext.set("deleteData", deleteDataFunction)
    }
}