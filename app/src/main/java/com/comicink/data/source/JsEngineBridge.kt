package com.comicink.data.source

import com.quickjs.QuickJS
import com.quickjs.JSContext
import com.quickjs.JSValue
import com.quickjs.JSObject

/**
 * QuickJS 引擎封装
 * 提供 Context 隔离，每个操作创建独立 Context 用完即焚
 */
class JsEngineBridge {
    private val quickJS: QuickJS by lazy { QuickJS.createRuntime() }

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
        return context.executeScript(code, fileName) as JSValue
    }

    /**
     * 调用 JS 对象方法
     * @param context JS 上下文
     * @param obj JS 对象
     * @param method 方法名
     * @param args 参数
     */
    fun callMethod(context: JSContext, obj: JSValue, method: String, vararg args: Any?): JSValue {
        val jsObj = obj as? JSObject ?: throw IllegalArgumentException("obj must be JSObject")
        return jsObj.executeFunction2(method, *args) as JSValue
    }

    /**
     * 关闭引擎，释放所有资源
     */
    fun destroy() {
        quickJS.close()
    }
}