package com.comicink.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.comicink.R

/**
 * 主页 Fragment
 * 显示同步按钮和最近阅读历史
 */
class HomeFragment : Fragment() {

    private lateinit var tvSyncStatus: TextView
    private lateinit var llSyncData: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        llSyncData = view.findViewById(R.id.llSyncData)

        // 同步按钮点击事件
        llSyncData.setOnClickListener {
            // TODO: 实现同步逻辑
        }
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}