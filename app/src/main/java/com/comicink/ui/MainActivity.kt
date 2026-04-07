package com.comicink.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.comicink.databinding.ActivityMainBinding

/**
 * 主界面 - E-ink 优化版
 * 默认使用高对比黑白主题，禁用所有动画
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // 禁用过渡动画
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()
    }

    private fun initUI() {
        // E-ink 模式 UI 初始化
        // TODO: 实现漫画源列表、收藏、历史等界面
    }

    override fun onPause() {
        super.onPause()
        // 退出时也无动画
        overridePendingTransition(0, 0)
    }
}