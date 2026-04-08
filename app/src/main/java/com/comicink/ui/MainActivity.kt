package com.comicink.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.comicink.R
import com.comicink.data.source.JsSourceManager
import com.comicink.ui.fragment.CategoriesFragment
import com.comicink.ui.fragment.DiscoverFragment
import com.comicink.ui.fragment.FavoritesFragment
import com.comicink.ui.fragment.HomeFragment
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 - E-ink 优化版 + 侧边栏导航
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var tvTitle: TextView
    private lateinit var tvMenu: TextView

    private lateinit var sourceManager: JsSourceManager

    // 当前显示的 Fragment
    private var currentFragment: Fragment? = null
    private val fragmentMap = mutableMapOf<String, Fragment>()

    // 页面标题
    private val pageTitles = mapOf(
        "nav_home" to "主页",
        "nav_favorites" to "收藏",
        "nav_discover" to "发现",
        "nav_categories" to "分类"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initSourceManager()
        initNavigation()

        // 默认显示主页
        if (savedInstanceState == null) {
            switchFragment("nav_home")
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        tvTitle = findViewById(R.id.tvTitle)
        tvMenu = findViewById(R.id.tvSettings)
        val tvSearch = findViewById<TextView>(R.id.tvSearch)
        val tvSettings = findViewById<TextView>(R.id.tvSettings)

        // 打开侧边栏
        tvMenu.setOnClickListener {
            drawerLayout.openDrawer(navigationView)
        }

        // 搜索
        tvSearch.setOnClickListener {
            // TODO: 实现搜索
        }

        // 设置
        tvSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }

    private fun initSourceManager() {
        sourceManager = JsSourceManager(this)

        // 初始化源管理器
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    sourceManager.initialize()
                } catch (e: Exception) {
                    // 静默失败，不影响主界面
                }
            }
        }
    }

    private fun initNavigation() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            val itemId = menuItem.itemId.toString()
            switchFragment(itemId)
            drawerLayout.closeDrawer(navigationView)
            true
        }
    }

    private fun switchFragment(itemId: String) {
        // 获取或创建 Fragment
        val fragment = fragmentMap.getOrPut(itemId) {
            when (itemId) {
                "nav_home" -> HomeFragment.newInstance()
                "nav_favorites" -> FavoritesFragment.newInstance()
                "nav_discover" -> DiscoverFragment.newInstance()
                "nav_categories" -> CategoriesFragment.newInstance()
                else -> HomeFragment.newInstance()
            }
        }

        // 更新标题
        tvTitle.text = pageTitles[itemId] ?: "ComicInk"

        // 切换 Fragment
        val transaction = supportFragmentManager.beginTransaction()

        // 隐藏当前 Fragment
        currentFragment?.let { transaction.hide(it) }

        // 显示或添加新 Fragment
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.contentContainer, fragment)
        }

        transaction.commit()
        currentFragment = fragment
    }

    /**
     * 获取启用的漫画源列表
     */
    fun getEnabledSources(): List<JsSourceManager.SourceMetadata> {
        return sourceManager.getAllMetadata()
    }

    /**
     * 获取所有漫画源
     */
    fun getAllSources(): List<JsSourceManager.SourceMetadata> {
        return sourceManager.getAllMetadata()
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            super.onBackPressed()
        }
    }
}