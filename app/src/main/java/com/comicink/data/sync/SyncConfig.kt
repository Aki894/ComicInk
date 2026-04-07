package com.comicink.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * WebDAV 配置管理 */
class SyncConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "webdav_config",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SYNC = "last_sync"
        const val DEFAULT_PATH = "/ComicInk/sync.json"
    }

    var webDavUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /**
     * 检查配置是否完整 */
    fun isConfigured(): Boolean {
        return webDavUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    /**
     * 获取完整的文件路径 */
    fun getFullPath(): String {
        val baseUrl = webDavUrl.trimEnd('/')
        return "$baseUrl$DEFAULT_PATH"
    }

    /**
     * 保存配置 */
    fun save(url: String, username: String, password: String) {
        this.webDavUrl = url
        this.username = username
        this.password = password
        this.isEnabled = true
    }

    /**
     * 清除配置 */
    fun clear() {
        prefs.edit().clear().apply()
    }
}