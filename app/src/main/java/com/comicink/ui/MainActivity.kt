package com.comicink.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.comicink.data.source.JsSourceManager
import com.comicink.databinding.ActivityMainBinding
import com.comicink.ui.adapter.SourceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 - E-ink 优化版
 * 默认使用高对比黑白主题，禁用所有动画
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sourceManager: JsSourceManager
    private lateinit var sourceAdapter: SourceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        // 禁用过渡动画
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSourceManager()
        initRecyclerView()
        initClickListeners()
    }

    private fun initSourceManager() {
        sourceManager = JsSourceManager(this)

        // 在后台线程初始化
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    sourceManager.initialize()
                }
                // 更新列表
                val sources = sourceManager.getAllMetadata()
                sourceAdapter.updateData(sources)

                if (sources.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "未找到漫画源，请添加 JS 源文件到 assets/sources/",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "加载漫画源失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun initRecyclerView() {
        sourceAdapter = SourceAdapter { metadata ->
            // 点击漫画源项 - 暂时显示提示
            Toast.makeText(
                this,
                "点击了: ${metadata.name}",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.rvSources.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = sourceAdapter
            // 禁用 E-ink 上的动画
            itemAnimator = null
        }
    }

    private fun initClickListeners() {
        // 设置按钮 - 跳转到设置界面
        binding.tvSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }

    override fun onPause() {
        super.onPause()
        // 退出时也无动画
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        // 刷新漫画源列表
        val sources = sourceManager.getAllMetadata()
        sourceAdapter.updateData(sources)
    }
}