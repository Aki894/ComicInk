package com.comicink.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comicink.R

/**
 * 分类 Fragment
 * 按字母索引显示分类
 */
class CategoriesFragment : Fragment() {

    private lateinit var llSourceTabs: LinearLayout
    private lateinit var llLetterIndex: LinearLayout
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var currentSource: String = ""
    private var currentLetter: String = "A"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llSourceTabs = view.findViewById(R.id.llSourceTabs)
        llLetterIndex = view.findViewById(R.id.llLetterIndex)
        rvCategories = view.findViewById(R.id.rvCategories)
        progressBar = view.findViewById(R.id.progressBar)

        rvCategories.layoutManager = LinearLayoutManager(context)

        // TODO: 初始化源 Tab 和字母索引
    }

    companion object {
        fun newInstance() = CategoriesFragment()
    }
}