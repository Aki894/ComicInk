package com.comicink.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.comicink.R

/**
 * 收藏 Fragment
 * 显示本地收藏和网络收藏
 */
class FavoritesFragment : Fragment() {

    private lateinit var tvTabAll: TextView
    private lateinit var tvTabLocal: TextView
    private lateinit var tvTabNetwork: TextView
    private var currentTab = 0 // 0=all, 1=local, 2=network

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTabAll = view.findViewById(R.id.tvTabAll)
        tvTabLocal = view.findViewById(R.id.tvTabLocal)
        tvTabNetwork = view.findViewById(R.id.tvTabNetwork)

        tvTabAll.setOnClickListener { switchTab(0) }
        tvTabLocal.setOnClickListener { switchTab(1) }
        tvTabNetwork.setOnClickListener { switchTab(2) }

        updateTabUI()
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        updateTabUI()
        // TODO: 加载对应数据
    }

    private fun updateTabUI() {
        val primaryColor = requireContext().getColor(R.color.eink_text_primary)
        val secondaryColor = requireContext().getColor(R.color.eink_text_secondary)

        tvTabAll.setTextColor(if (currentTab == 0) primaryColor else secondaryColor)
        tvTabLocal.setTextColor(if (currentTab == 1) primaryColor else secondaryColor)
        tvTabNetwork.setTextColor(if (currentTab == 2) primaryColor else secondaryColor)

        tvTabAll.setTypeface(null, if (currentTab == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tvTabLocal.setTypeface(null, if (currentTab == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tvTabNetwork.setTypeface(null, if (currentTab == 2) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    companion object {
        fun newInstance() = FavoritesFragment()
    }
}