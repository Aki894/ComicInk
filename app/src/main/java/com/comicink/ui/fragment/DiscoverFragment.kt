package com.comicink.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comicink.R

/**
 * 发现 Fragment
 * 显示漫画列表，支持源切换和翻页
 */
class DiscoverFragment : Fragment() {

    private lateinit var llSourceTabs: LinearLayout
    private lateinit var rvComics: RecyclerView
    private lateinit var llPagination: LinearLayout
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var tvPageInfo: TextView
    private lateinit var progressBar: ProgressBar

    private var currentSource: String = ""
    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var currentUiPage: Int = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llSourceTabs = view.findViewById(R.id.llSourceTabs)
        rvComics = view.findViewById(R.id.rvComics)
        llPagination = view.findViewById(R.id.llPagination)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        tvPageInfo = view.findViewById(R.id.tvPageInfo)
        progressBar = view.findViewById(R.id.progressBar)

        rvComics.layoutManager = LinearLayoutManager(context)

        btnPrev.setOnClickListener { navigatePage(-1) }
        btnNext.setOnClickListener { navigatePage(1) }

        // TODO: 加载源列表并创建 Tab
    }

    private fun navigatePage(delta: Int) {
        val newUiPage = currentUiPage + delta
        // TODO: 根据两层分页逻辑计算并加载数据
    }

    private fun updatePaginationUI() {
        btnPrev.isEnabled = currentUiPage > 1
        btnNext.isEnabled = currentUiPage < 14 // TODO: 计算实际总页数
        val pageText = "Page " + currentUiPage + "/14 JS " + currentPage
        tvPageInfo.text = pageText
    }

    companion object {
        fun newInstance() = DiscoverFragment()
    }
}