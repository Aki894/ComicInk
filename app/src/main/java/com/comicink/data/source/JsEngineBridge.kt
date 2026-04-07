package com.comicink.data.source

import com.quickjs.QuickJS
import com.quickjs.JSContext
import com.quickjs.JSValue
import com.quickjs.JSObject
import java.io.Closeable

/**
 * QuickJS 引擎封装
 * 提供 Context 隔离，每个操作创建独立 Context 用完即焚
 */
class JsEngineBridge {
    private val quickJS: QuickJS by lazy { QuickJS.createRuntime() }

    @Volatile
    private var isDestroyed = false

    /**
     * 创建独立的 JS Context
     * 每次操作后必须调用 close() 释放内存
     */
    @Synchronized
    fun createContext(): CloseableJsContext {
        check(!isDestroyed) { "Engine already destroyed" }
        return CloseableJsContext(quickJS.createContext())
    }

    /**
     * 执行 JS 代码
     * @param context JS 上下文
     * @param code JS 代码
     * @param fileName 文件名（用于错误信息）
     */
    @Synchronized
    fun evaluate(context: JSContext, code: String, fileName: String): JSValue {
        check(!isDestroyed) { "Engine already destroyed" }
        return context.executeScript(code, fileName) as JSValue
    }

    /**
     * 调用 JS 对象方法
     * @param context JS 上下文
     * @param obj JS 对象
     * @param method 方法名
     * @param args 参数
     */
    @Synchronized
    fun callMethod(context: JSContext, obj: JSValue, method: String, vararg args: Any?): JSValue {
        check(!isDestroyed) { "Engine already destroyed" }
        val jsObj = obj as? JSObject ?: throw IllegalArgumentException("obj must be JSObject")
        return jsObj.executeFunction2(method, *args) as JSValue
    }

    /**
     * 关闭引擎，释放所有资源
     */
    @Synchronized
    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        try {
            quickJS.close()
        } catch (e: Exception) {
            // Log error in production, silently fail here to prevent crash
            e.printStackTrace()
        }
    }
}

/**
 * 可关闭的 JS Context 包装类
 * 实现 AutoCloseable 接口，支持 try-with-resources
 */
class CloseableJsContext(val context: JSContext) : Closeable, AutoCloseable {
    override fun close() {
        try {
            context.close()
        } catch (e: Exception) {
            // Log error in production, silently fail here to prevent crash
            e.printStackTrace()
        }
    }
}

/**
 * 便捷扩展函数，简化资源管理
 * 在 lambda 执行完毕后自动关闭 Context
 */
inline fun <T> CloseableJsContext.use(block: (JSContext) -> T): T {
    return try {
        block(context)
    } finally {
        close()
    }
}