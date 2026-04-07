package com.comicink.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.comicink.databinding.ActivityReaderBinding

/**
 * 漫画阅读器 - E-ink 优化版
 * - 强制全刷新
 * - 禁用动画
 * - 简单翻页逻辑
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding

    // 当前页码
    private var currentPage = 0
    private var totalPages = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // 禁用所有动画
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 隐藏状态栏和导航栏（适合阅读）
        hideSystemUI()

        initUI()
        loadComic()
    }

    private fun hideSystemUI() {
        // 全面沉浸模式
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun initUI() {
        // TODO: 初始化 ViewPager + 触摸翻页
    }

    private fun loadComic() {
        // TODO: 从 Intent 获取漫画信息，加载章节图片
    }

    /**
     * E-ink 全刷新 - 每翻页调用
     * 强制重绘整个屏幕，消除残影
     */
    private fun fullRefresh() {
        binding.readerView?.let { view ->
            view.invalidate()
            view.postDelayed({
                // 延迟确保刷新完成
            }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}