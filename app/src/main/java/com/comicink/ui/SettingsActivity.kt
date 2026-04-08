package com.comicink.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.comicink.R
import com.comicink.data.sync.SyncConfig
import com.comicink.domain.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置界面
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var syncConfig: SyncConfig
    private lateinit var syncManager: SyncManager

    private lateinit var tvWebDavStatus: TextView
    private lateinit var tvThemeStatus: TextView
    private lateinit var tvVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        syncConfig = SyncConfig(this)
        syncManager = SyncManager(this)

        initViews()
        updateStatus()
    }

    private fun initViews() {
        // 返回按钮
        findViewById<TextView>(R.id.tvBack).setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }

        // WebDAV 设置
        tvWebDavStatus = findViewById(R.id.tvWebDavStatus)
        findViewById<LinearLayout>(R.id.llWebDav).setOnClickListener {
            showWebDavDialog()
        }

        // 主题设置
        tvThemeStatus = findViewById(R.id.tvThemeStatus)
        findViewById<LinearLayout>(R.id.llTheme).setOnClickListener {
            toggleTheme()
        }

        // 版本信息
        tvVersion = findViewById(R.id.tvVersion)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "版本 ${packageInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "版本 1.0.0"
        }
    }

    private fun updateStatus() {
        // 更新 WebDAV 状态
        if (syncConfig.isConfigured()) {
            tvWebDavStatus.text = "已配置 (${syncConfig.webDavUrl})"
        } else {
            tvWebDavStatus.text = "未配置"
        }

        // 更新主题状态
        val isDarkMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        tvThemeStatus.text = if (isDarkMode) "E-ink 模式（高对比）" else "普通模式"
    }

    private fun showWebDavDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_webdav_config, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val etUrl = dialogView.findViewById<EditText>(R.id.etUrl)
        val etUsername = dialogView.findViewById<EditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val tvTestStatus = dialogView.findViewById<TextView>(R.id.tvTestStatus)
        val btnTest = dialogView.findViewById<Button>(R.id.btnTest)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        // 填充已有配置
        etUrl.setText(syncConfig.webDavUrl)
        etUsername.setText(syncConfig.username)
        etPassword.setText(syncConfig.password)

        // 测试连接
        btnTest.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (url.isBlank() || username.isBlank() || password.isBlank()) {
                tvTestStatus.text = "请填写完整信息"
                return@setOnClickListener
            }

            tvTestStatus.text = "测试中..."
            btnTest.isEnabled = false

            // 临时配置并测试
            syncConfig.save(url, username, password)

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    syncManager.testConnection()
                }

                btnTest.isEnabled = true
                if (result.isSuccess) {
                    tvTestStatus.text = "连接成功 ✓"
                    tvTestStatus.setTextColor(getColor(R.color.eink_text_primary))
                } else {
                    tvTestStatus.text = "连接失败: ${result.exceptionOrNull()?.message}"
                    tvTestStatus.setTextColor(getColor(android.R.color.holo_red_light))
                }
            }
        }

        // 保存
        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (url.isBlank() || username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            syncConfig.save(url, username, password)
            updateStatus()
            dialog.dismiss()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun toggleTheme() {
        // 切换主题（简单实现：重启生效）
        val prefs = getSharedPreferences("theme", MODE_PRIVATE)
        val currentMode = prefs.getInt("mode", 0) // 0 = E-ink (dark), 1 = normal

        val newMode = if (currentMode == 0) 1 else 0
        prefs.edit().putInt("mode", newMode).apply()

        Toast.makeText(this, "主题切换将在下次启动生效", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}