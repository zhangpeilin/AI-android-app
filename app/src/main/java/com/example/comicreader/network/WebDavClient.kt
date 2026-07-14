package com.example.comicreader.network

import android.util.Log
import android.util.Xml
import com.example.comicreader.model.WebDavEntry
import com.example.comicreader.model.WebDavServerConfig
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Route
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端
 * 使用 OkHttp 实现 PROPFIND（列目录）和 GET（下载文件）
 */
class WebDavClient {

    companion object {
        private const val TAG = "WebDavClient"
    }

    private fun createClient(config: WebDavServerConfig): OkHttpClient {
        // 支持 Basic 和 Digest 两种认证
        val basicAuthenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                if (response.request.header("Authorization") != null) {
                    return null // 已经尝试过认证，不再重试
                }
                val credential = Credentials.basic(config.username, config.password)
                return response.request.newBuilder()
                    .header("Authorization", credential)
                    .build()
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .authenticator(basicAuthenticator)
            .build()
    }

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 列出 WebDAV 目录下的内容
     * @param config 服务器配置
     * @param path 目录路径（如 /comics/）
     * @return 目录下的文件和子目录列表
     */
    fun listDirectory(config: WebDavServerConfig, path: String): List<WebDavEntry> {
        Log.d(TAG, "listDirectory: url=${config.url}, path=$path")

        val fullUrl = buildFullUrl(config.url, path)
        Log.d(TAG, "listDirectory: fullUrl=$fullUrl")

        val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:displayname/>
                    <D:resourcetype/>
                    <D:getcontentlength/>
                </D:prop>
            </D:propfind>
        """.trimIndent()

        val client = createClient(config)
        val request = Request.Builder()
            .url(fullUrl)
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .header("Authorization", Credentials.basic(config.username, config.password))
            .method("PROPFIND", RequestBody.create("application/xml; charset=utf-8".toMediaType(), propfindBody))
            .build()

        return try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "listDirectory: response code=${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "listDirectory: 请求失败, code=${response.code}, body=${response.body?.string()}")
                throw IOException("WebDAV PROPFIND failed: ${response.code}")
            }

            val body = response.body?.string() ?: ""
            Log.d(TAG, "listDirectory: response body length=${body.length}")
            Log.d(TAG, "listDirectory: XML content=${body.take(2000)}")

            val entries = parsePropfindResponse(body, path)
            Log.d(TAG, "listDirectory: found ${entries.size} entries")
            entries
        } catch (e: Exception) {
            Log.e(TAG, "listDirectory: 异常", e)
            throw e
        }
    }

    /**
     * 下载文件到本地（支持进度回调）
     * @param config 服务器配置
     * @param remotePath 远程文件路径
     * @param localFile 本地目标文件
     * @param onProgress 进度回调 (downloaded, total) -> Unit
     * @return 是否下载成功
     */
    fun downloadFile(
        config: WebDavServerConfig,
        remotePath: String,
        localFile: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean {
        Log.d(TAG, "downloadFile: remotePath=$remotePath, localFile=${localFile.absolutePath}")

        val fullUrl = buildFullUrl(config.url, remotePath)
        Log.d(TAG, "downloadFile: fullUrl=$fullUrl")
        val client = createClient(config)
        val request = Request.Builder()
            .url(fullUrl)
            .header("Authorization", Credentials.basic(config.username, config.password))
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "downloadFile: response code=${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "downloadFile: 请求失败, code=${response.code}")
                return false
            }

            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()
            Log.d(TAG, "downloadFile: totalBytes=$totalBytes")

            localFile.parentFile?.mkdirs()

            var downloadedBytes = 0L
            FileOutputStream(localFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        onProgress?.invoke(downloadedBytes, totalBytes)
                    }
                }
            }

            Log.d(TAG, "downloadFile: 下载完成, size=$downloadedBytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile: 异常", e)
            // 删除不完整的文件
            if (localFile.exists()) {
                localFile.delete()
            }
            false
        }
    }

    /**
     * 测试服务器连接
     */
    fun testConnection(config: WebDavServerConfig): Boolean {
        Log.d(TAG, "testConnection: url=${config.url}")
        return try {
            listDirectory(config, "/")
            true
        } catch (e: Exception) {
            Log.e(TAG, "testConnection: 连接失败", e)
            false
        }
    }

    /**
     * 从 URL 中提取 scheme+host 部分
     * 例如 http://192.168.3.39/webdav/ -> http://192.168.3.39
     */
    private fun getSchemeAndHost(baseUrl: String): String {
        val idx = baseUrl.indexOf('/', baseUrl.indexOf("//") + 2)
        return if (idx > 0) baseUrl.substring(0, idx) else baseUrl.trimEnd('/')
    }

    /**
     * 从 baseUrl 中提取路径部分（如 /webdav）
     */
    private fun getBasePath(baseUrl: String): String {
        val schemeHost = getSchemeAndHost(baseUrl)
        return baseUrl.substring(schemeHost.length).trimEnd('/')
    }

    /**
     * 构建完整 URL
     * 处理两种情况：
     * 1. path 是相对路径（如 /）-> baseUrl + path
     * 2. path 是服务器绝对路径（如 /webdav/xxx.zip，来自 href）-> scheme+host + path
     */
    private fun buildFullUrl(baseUrl: String, path: String): String {
        val basePath = getBasePath(baseUrl)
        val schemeHost = getSchemeAndHost(baseUrl)

        // 如果 basePath 非空且 path 以 basePath 开头，说明 href 已包含基础路径，直接用 scheme+host
        if (basePath.isNotEmpty() && path.startsWith(basePath)) {
            return schemeHost + path
        }
        // 否则正常拼接
        val base = baseUrl.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return base + p
    }

    /**
     * 解析 PROPFIND XML 响应
     * WebDAV 返回 multistatus XML，每个 response 包含一个资源的信息
     */
    private fun parsePropfindResponse(xml: String, requestPath: String): List<WebDavEntry> {
        val entries = mutableListOf<WebDavEntry>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())

            var eventType = parser.eventType
            var currentHref = ""
            var currentDisplayName = ""
            var isCollection = false
            var contentLength = 0L

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        // 忽略命名空间前缀，取冒号后的本地名
                        val rawName = parser.name
                        val localName = if (rawName.contains(':')) rawName.substringAfter(':') else rawName
                        Log.d(TAG, "parsePropfind: START_TAG localName=$localName, raw=$rawName")
                        when (localName) {
                            "response" -> {
                                currentHref = ""
                                currentDisplayName = ""
                                isCollection = false
                                contentLength = 0L
                            }
                            "href" -> currentHref = parser.nextText()
                            "displayname" -> {
                                val text = parser.nextText()
                                if (text.isNotEmpty()) currentDisplayName = text
                            }
                            "collection" -> isCollection = true
                            "getcontentlength" -> {
                                contentLength = parser.nextText().toLongOrNull() ?: 0L
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val rawName = parser.name
                        val localName = if (rawName.contains(':')) rawName.substringAfter(':') else rawName
                        if (localName == "response") {
                            // 排除请求路径本身（目录自身）
                            val normalizedHref = currentHref.trimEnd('/')
                            val normalizedRequest = requestPath.trimEnd('/')
                            if (normalizedHref != normalizedRequest && normalizedHref.isNotEmpty()) {
                                // 如果 displayname 为空，从 href 提取文件名并 URL 解码
                                val displayName = if (currentDisplayName.isNotEmpty()) {
                                    currentDisplayName
                                } else {
                                    try {
                                        URLDecoder.decode(currentHref.trimEnd('/').substringAfterLast('/'), "UTF-8")
                                    } catch (e: Exception) {
                                        currentHref.trimEnd('/').substringAfterLast('/')
                                    }
                                }
                                if (displayName.isNotEmpty()) {
                                    val entry = WebDavEntry(
                                        name = displayName,
                                        path = currentHref,
                                        isDirectory = isCollection,
                                        size = contentLength
                                    )
                                    entries.add(entry)
                                    Log.d(TAG, "parsePropfind: entry=${entry.name}, isDir=${entry.isDirectory}, size=${entry.size}")
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePropfindResponse: XML解析异常", e)
        }

        // 不在此排序，由 ViewModel 根据用户选择的排序模式处理
        return entries
    }
}
