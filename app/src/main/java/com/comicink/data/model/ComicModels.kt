package com.comicink.data.model

data class Comic(
    val id: String,
    val title: String,
    val cover: String,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val url: String? = null
)

data class ComicDetails(
    val id: String,
    val title: String,
    val cover: String,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val chapters: List<Chapter> = emptyList()
)

data class Chapter(
    val id: String,
    val title: String,
    val url: String
)

data class ChapterImages(
    val chapterId: String,
    val images: List<String>
)

data class SearchResult(
    val comics: List<Comic>,
    val maxPage: Int,
    val currentPage: Int
)

data class SourceMeta(
    val key: String,
    val name: String,
    val version: String,
    val minAppVersion: String
)