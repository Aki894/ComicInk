package com.comicink

import android.app.Application
import org.conscrypt.Conscrypt
import java.security.Security

/**
 * ComicInk Application
 * - 初始化 Conscrypt 以支持 API 19 上的 TLS 1.2
 * - E-ink 模式默认开启
 */
class ComicInkApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化 Conscrypt - 启用 TLS 1.2 支持（API 19 必需）
        try {
            Security.insertProviderAt(Conscrypt.newProviderBuilder().build(), 1)
        } catch (e: Exception) {
            // 如果失败，记录日志但继续运行（部分设备可能不需要）
            e.printStackTrace()
        }
    }
}