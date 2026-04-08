package com.comicink.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.comicink.R
import com.comicink.data.source.JsSourceManager

/**
 * 漫画源列表适配器
 */
class SourceAdapter(
    private val onItemClick: (JsSourceManager.SourceMetadata) -> Unit
) : RecyclerView.Adapter<SourceAdapter.SourceViewHolder>() {

    private val sources = mutableListOf<JsSourceManager.SourceMetadata>()

    fun updateData(newSources: List<JsSourceManager.SourceMetadata>) {
        sources.clear()
        sources.addAll(newSources)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_source, parent, false)
        return SourceViewHolder(view)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(sources[position])
    }

    override fun getItemCount(): Int = sources.size

    inner class SourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvSourceName)
        private val tvKey: TextView = itemView.findViewById(R.id.tvSourceKey)
        private val tvVersion: TextView = itemView.findViewById(R.id.tvSourceVersion)

        fun bind(metadata: JsSourceManager.SourceMetadata) {
            tvName.text = metadata.name
            tvKey.text = metadata.key
            tvVersion.text = "v${metadata.version}"

            itemView.setOnClickListener {
                onItemClick(metadata)
            }
        }
    }
}