package com.example.comicreader.model

/**
 * WebDAV 服务器配置
 */
data class WebDavServerConfig(
    val id: String,              // UUID
    val name: String,            // 显示名称（如 "我的NAS"）
    val url: String,             // WebDAV 地址，如 https://example.com/dav/
    val username: String,
    val password: String,
    val lastPath: String = "/"   // 最后浏览的目录路径
)
