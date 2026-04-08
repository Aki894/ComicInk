package com.comicink.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comicink.R
import com.comicink.data.source.JsSourceManager

/**
 * 源管理界面
 * 允许用户启用/禁用各个漫画源
 */
class SourceManagerActivity : AppCompatActivity() {

    private lateinit var rvSources: RecyclerView
    private lateinit var btnEnableAll: Button
    private lateinit var btnDisableAll: Button

    private lateinit var sourceManager: JsSourceManager
    private lateinit var sourceAdapter: SourceManageAdapter

    // 存储源的启用状态
    private val sourceEnabledState = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_source_manager)

        sourceManager = JsSourceManager(this)

        initViews()
        loadSources()
    }

    private fun initViews() {
        findViewById<TextView>(R.id.tvBack).setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }

        rvSources = findViewById(R.id.rvSources)
        btnEnableAll = findViewById(R.id.btnEnableAll)
        btnDisableAll = findViewById(R.id.btnDisableAll)

        rvSources.layoutManager = LinearLayoutManager(this)

        btnEnableAll.setOnClickListener { setAllEnabled(true) }
        btnDisableAll.setOnClickListener { setAllEnabled(false) }
    }

    private fun loadSources() {
        sourceManager.initialize()
        val sources = sourceManager.getAllMetadata()

        // 加载保存的状态，默认全部启用
        val prefs = getSharedPreferences("source_settings", Context.MODE_PRIVATE)
        for (source in sources) {
            val enabled = prefs.getBoolean(source.key, true)
            sourceEnabledState[source.key] = enabled
        }

        sourceAdapter = SourceManageAdapter(sources, sourceEnabledState) { key, enabled ->
            // 更新状态
            sourceEnabledState[key] = enabled
            saveSourceSettings()
        }

        rvSources.adapter = sourceAdapter
    }

    private fun setAllEnabled(enabled: Boolean) {
        for (key in sourceEnabledState.keys) {
            sourceEnabledState[key] = enabled
        }
        sourceAdapter.notifyDataSetChanged()
        saveSourceSettings()
    }

    private fun saveSourceSettings() {
        val prefs = getSharedPreferences("source_settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for ((key, enabled) in sourceEnabledState) {
            editor.putBoolean(key, enabled)
        }
        editor.apply()
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }

    /**
     * 源管理适配器
     */
    inner class SourceManageAdapter(
        private val sources: List<JsSourceManager.SourceMetadata>,
        private val enabledState: Map<String, Boolean>,
        private val onCheckedChange: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<SourceManageAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cbEnabled: CheckBox = itemView.findViewById(R.id.cbEnabled)
            val tvSourceName: TextView = itemView.findViewById(R.id.tvSourceName)
            val tvSourceInfo: TextView = itemView.findViewById(R.id.tvSourceInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_source_manage, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val source = sources[position]
            val enabled = enabledState[source.key] ?: true

            holder.tvSourceName.text = source.name
            holder.tvSourceInfo.text = "Key: ${source.key} | Version: ${source.version}"
            holder.cbEnabled.isChecked = enabled

            holder.cbEnabled.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(source.key, isChecked)
            }

            holder.itemView.setOnClickListener {
                holder.cbEnabled.isChecked = !holder.cbEnabled.isChecked
            }
        }

        override fun getItemCount(): Int = sources.size
    }
}