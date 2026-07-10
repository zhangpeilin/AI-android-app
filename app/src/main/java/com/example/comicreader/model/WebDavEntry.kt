package com.example.comicreader.model

/**
 * WebDAV 目录条目
 */
data class WebDavEntry(
    val name: String,
    val path: String,            // 完整路径（相对于服务器根目录）
    val isDirectory: Boolean,
    val size: Long = 0L          // 文件大小（目录为0）
)
