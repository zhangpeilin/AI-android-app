package com.example.comicreader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.comicreader.model.Comic
import com.example.comicreader.model.WebDavServerConfig
import com.example.comicreader.network.WebDavClient
import com.example.comicreader.util.ZipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ComicRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ComicRepo"

        @Volatile
        private var instance: ComicRepository? = null

        fun getInstance(context: Context): ComicRepository {
            return instance ?: synchronized(this) {
                instance ?: ComicRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val comicCache = ConcurrentHashMap<String, Comic>()
    private val scannedUris = mutableSetOf<String>()
    private val coverCache = ConcurrentHashMap<String, ByteArray>()

    // WebDAV 下载任务跟踪
    private val downloadingComics = ConcurrentHashMap<String, Boolean>()

    // WebDAV 漫画持久化存储
    private val prefs by lazy {
        context.getSharedPreferences("webdav_comics", Context.MODE_PRIVATE)
    }

    // 缓存目录：存放从 SAF 复制过来的 zip 文件
    private val zipCacheDir: File by lazy {
        File(context.cacheDir, "comic_zips").also { it.mkdirs() }
    }

    /**
     * 扫描用户通过 SAF 选择的文件夹
     * 扫描时将 zip 文件复制到本地缓存目录，后续直接用 File 操作
     */
    suspend fun scanFolder(uri: Uri): List<Comic> = withContext(Dispatchers.IO) {
        Log.d(TAG, "scanFolder: uri=$uri")
        if (scannedUris.contains(uri.toString())) {
            Log.d(TAG, "scanFolder: 已扫描过, 返回缓存 ${comicCache.size} 个漫画")
            return@withContext comicCache.values.toList()
        }

        val documentFile = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList<Comic>()
        // 获取 URI 权限，确保可以读取子文件
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        scanDirectory(documentFile)
        scannedUris.add(uri.toString())
        Log.d(TAG, "scanFolder: 扫描完成, 共 ${comicCache.size} 个漫画")
        comicCache.values.toList()
    }

    /**
     * 递归扫描目录中的 zip 文件，复制到本地缓存
     */
    private fun scanDirectory(directory: DocumentFile) {
        val files = directory.listFiles() ?: return
        Log.d(TAG, "scanDirectory: 扫描目录, ${files.size} 个文件")
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file)
            } else if (file.isFile && file.name?.lowercase()?.endsWith(".zip") == true) {
                try {
                    // 将 zip 文件复制到本地缓存目录
                    val localFile = copyToCache(file)
                    if (localFile != null && localFile.length() > 0) {
                        val filePath = localFile.absolutePath
                        val id = ZipHelper.generateId(filePath)
                        if (!comicCache.containsKey(id)) {
                            val title = file.name?.replace(Regex("\\.zip$"), "") ?: "Unknown"
                            val comic = Comic(
                                id = id,
                                title = title,
                                filePath = filePath,
                                fileSize = localFile.length()
                            )
                            comicCache[id] = comic
                            Log.d(TAG, "scanDirectory: 添加漫画 id=$id, title=$title")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "scanDirectory: 处理文件失败 ${file.name}", e)
                }
            }
        }
    }

    /**
     * 将 DocumentFile 复制到本地缓存目录
     */
    private fun copyToCache(documentFile: DocumentFile): File? {
        val fileName = documentFile.name ?: return null
        val cacheFile = File(zipCacheDir, fileName)

        // 如果已存在且大小一致，跳过复制
        if (cacheFile.exists() && cacheFile.length() == documentFile.length()) {
            Log.d(TAG, "copyToCache: 缓存命中 $fileName, size=${cacheFile.length()}")
            return cacheFile
        }

        return try {
            Log.d(TAG, "copyToCache: 开始复制 $fileName, 原始大小=${documentFile.length()}")
            context.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "copyToCache: 复制完成 $fileName, 缓存大小=${cacheFile.length()}")
            cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "copyToCache: 复制失败 $fileName", e)
            null
        }
    }

    /**
     * 从 WebDAV 下载漫画到缓存并注册
     * 支持后台下载，不阻塞前台浏览
     * @param serverConfig WebDAV 服务器配置
     * @param remotePath 远程 zip 文件路径
     * @param fileName 文件名
     * @param onProgress 下载进度回调
     * @return 下载并注册后的 Comic 对象，失败返回 null
     */
    suspend fun addWebDavComic(
        serverConfig: WebDavServerConfig,
        remotePath: String,
        fileName: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Comic? = withContext(Dispatchers.IO) {
        Log.d(TAG, "addWebDavComic: remotePath=$remotePath, fileName=$fileName")

        // 生成缓存文件名（避免与本地文件冲突）
        val cacheFileName = "webdav_${remotePath.hashCode().toUInt()}_$fileName"
        val cacheFile = File(zipCacheDir, cacheFileName)

        // 检查是否已在缓存中
        val existingComic = comicCache.values.find { it.filePath.endsWith(cacheFileName) }
        if (existingComic != null) {
            val file = File(existingComic.filePath)
            if (file.exists()) {
                Log.d(TAG, "addWebDavComic: 已在缓存中, id=${existingComic.id}")
                // 确保持久化（之前可能未保存）
                persistWebDavComic(existingComic)
                return@withContext existingComic
            }
        }

        // 检查是否正在下载
        val downloadKey = "${serverConfig.id}#$remotePath"
        if (downloadingComics.containsKey(downloadKey)) {
            Log.d(TAG, "addWebDavComic: 已在下载队列中")
            return@withContext null
        }

        downloadingComics[downloadKey] = true

        try {
            // 下载文件
            Log.d(TAG, "addWebDavComic: 开始下载到 ${cacheFile.absolutePath}")
            val client = WebDavClient()
            val success = client.downloadFile(serverConfig, remotePath, cacheFile, onProgress)

            if (!success || !cacheFile.exists() || cacheFile.length() == 0L) {
                Log.e(TAG, "addWebDavComic: 下载失败")
                return@withContext null
            }

            Log.d(TAG, "addWebDavComic: 下载完成, size=${cacheFile.length()}")

            // 注册到 comicCache
            val id = ZipHelper.generateId(cacheFile.absolutePath)
            val title = fileName.replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), "")
            val comic = Comic(
                id = id,
                title = title,
                filePath = cacheFile.absolutePath,
                fileSize = cacheFile.length()
            )
            comicCache[id] = comic
            Log.d(TAG, "addWebDavComic: 注册成功, id=$id, title=$title")

            // 持久化保存
            persistWebDavComic(comic)

            comic
        } catch (e: Exception) {
            Log.e(TAG, "addWebDavComic: 异常", e)
            null
        } finally {
            downloadingComics.remove(downloadKey)
        }
    }

    /**
     * 预缓存指定章节的图片
     * 在后台加载图片字节到内存缓存，提升翻页速度
     */
    suspend fun preCacheChapter(comicId: String, chapter: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "preCacheChapter: comicId=$comicId, chapter=$chapter")
        val comic = comicCache[comicId] ?: return@withContext
        val file = File(comic.filePath)
        if (!file.exists()) return@withContext

        try {
            val chapters = ZipHelper.getChapters(file)
            val targetChapter = chapters.find { it.number == chapter } ?: return@withContext

            var cachedCount = 0
            for (imagePath in targetChapter.images) {
                val cacheKey = "$comicId#$chapter#$imagePath"
                if (!coverCache.containsKey(cacheKey)) {
                    val bytes = ZipHelper.getImageBytes(file, imagePath, chapter)
                    if (bytes != null) {
                        coverCache[cacheKey] = bytes
                        cachedCount++
                    }
                }
            }
            Log.d(TAG, "preCacheChapter: 预缓存 $cachedCount 张图片")
        } catch (e: Exception) {
            Log.e(TAG, "preCacheChapter: 预缓存失败", e)
        }
    }

    /**
     * 获取漫画列表（包含持久化的 WebDAV 漫画）
     */
    fun getComics(): List<Comic> {
        // 加载持久化的 WebDAV 漫画到缓存
        loadPersistedWebDavComics()
        Log.d(TAG, "getComics: 返回 ${comicCache.size} 个漫画")
        return comicCache.values.toList()
    }

    /**
     * 从 SharedPreferences 加载已持久化的 WebDAV 漫画
     */
    private fun loadPersistedWebDavComics() {
        val jsonStr = prefs.getString("comic_list", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val filePath = obj.getString("filePath")
                // 只加载文件仍然存在的漫画
                if (File(filePath).exists() && !comicCache.containsKey(id)) {
                    val comic = Comic(
                        id = id,
                        title = obj.getString("title"),
                        filePath = filePath,
                        fileSize = obj.getLong("fileSize")
                    )
                    comicCache[id] = comic
                    Log.d(TAG, "loadPersistedWebDavComics: 加载 ${comic.title}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPersistedWebDavComics: 解析失败", e)
        }
    }

    /**
     * 持久化保存 WebDAV 漫画信息
     */
    private fun persistWebDavComic(comic: Comic) {
        try {
            val jsonStr = prefs.getString("comic_list", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            // 检查是否已存在
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getJSONObject(i).getString("id") == comic.id) return
            }
            val obj = JSONObject().apply {
                put("id", comic.id)
                put("title", comic.title)
                put("filePath", comic.filePath)
                put("fileSize", comic.fileSize)
            }
            jsonArray.put(obj)
            prefs.edit().putString("comic_list", jsonArray.toString()).apply()
            Log.d(TAG, "persistWebDavComic: 保存 ${comic.title}")
        } catch (e: Exception) {
            Log.e(TAG, "persistWebDavComic: 保存失败", e)
        }
    }

    /**
     * 搜索漫画（按标题和作者）
     */
    fun searchComics(query: String): List<Comic> {
        if (query.isBlank()) return comicCache.values.toList()

        val keyword = query.lowercase()
        return comicCache.values.filter { comic ->
            matchText(comic.title, keyword)
        }
    }

    /**
     * 简繁匹配：同时匹配简体和繁体
     */
    private fun matchText(text: String, keyword: String): Boolean {
        val textLower = text.lowercase()
        if (textLower.contains(keyword)) return true

        // 简单的简繁映射检查
        val textTraditional = toTraditional(textLower)
        val textSimplified = toSimplified(textLower)
        val keywordTraditional = toTraditional(keyword)
        val keywordSimplified = toSimplified(keyword)

        return textTraditional.contains(keywordTraditional) ||
                textTraditional.contains(keywordSimplified) ||
                textSimplified.contains(keywordTraditional) ||
                textSimplified.contains(keywordSimplified)
    }

    /**
     * 简体转繁体（简化版，覆盖常用字）
     */
    private fun toTraditional(text: String): String {
        val map = mapOf(
            '发' to "發", '国' to "國", '学' to "學", '会' to "會",
            '时' to "時", '实' to "實", '体' to "體", '关' to "關",
            '东' to "東", '门' to "門", '间' to "間", '后' to "後",
            '图' to "圖", '录' to "錄", '转' to "轉", '译' to "譯",
            '气' to "氣", '黑' to "黒", '画' to "畫"
        )
        return text.map { map[it]?.firstOrNull() ?: it }.joinToString("")
    }

    /**
     * 繁体转简体（简化版，覆盖常用字）
     */
    private fun toSimplified(text: String): String {
        val map = mapOf(
            '發' to "发", '國' to "国", '學' to "学", '會' to "会",
            '時' to "时", '實' to "实", '體' to "体", '關' to "关",
            '東' to "东", '門' to "门", '間' to "间", '後' to "后",
            '圖' to "图", '錄' to "录", '轉' to "转", '譯' to "译",
            '氣' to "气", '黒' to "黑", '畫' to "画"
        )
        return text.map { map[it]?.firstOrNull() ?: it }.joinToString("")
    }

    /**
     * 获取漫画的章节列表
     */
    suspend fun getChapters(comicId: String): List<com.example.comicreader.model.Chapter> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "getChapters: comicId=$comicId")
            val comic = comicCache[comicId]
            if (comic == null) {
                Log.w(TAG, "getChapters: 未找到漫画, id=$comicId")
                return@withContext emptyList()
            }
            val file = File(comic.filePath)
            if (!file.exists()) {
                Log.w(TAG, "getChapters: 文件不存在, path=${comic.filePath}")
                return@withContext emptyList()
            }
            Log.d(TAG, "getChapters: 解析文件, size=${file.length()}")
            val chapters = ZipHelper.getChapters(file)
            Log.d(TAG, "getChapters: 找到 ${chapters.size} 个章节")
            chapters
        }

    /**
     * 获取漫画中某章节的图片列表
     */
    suspend fun getImages(comicId: String, chapter: String): List<String> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "getImages: comicId=$comicId, chapter=$chapter")
            val comic = comicCache[comicId]
            if (comic == null) {
                Log.w(TAG, "getImages: 未找到漫画")
                return@withContext emptyList()
            }
            val file = File(comic.filePath)
            if (!file.exists()) {
                Log.w(TAG, "getImages: 文件不存在")
                return@withContext emptyList()
            }
            val chapters = ZipHelper.getChapters(file)
            val images = chapters.find { it.number == chapter }?.images ?: emptyList()
            Log.d(TAG, "getImages: 章节 $chapter 有 ${images.size} 张图片")
            images
        }

    /**
     * 获取图片字节数据（带内存缓存）
     */
    suspend fun getImageBytes(comicId: String, chapter: String, imagePath: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val cacheKey = "$comicId#$chapter#$imagePath"
            coverCache[cacheKey]?.let {
                Log.d(TAG, "getImageBytes: 内存缓存命中, key=$cacheKey")
                return@withContext it
            }

            val comic = comicCache[comicId]
            if (comic == null) {
                Log.w(TAG, "getImageBytes: 未找到漫画, id=$comicId")
                return@withContext null
            }
            val file = File(comic.filePath)
            if (!file.exists()) {
                Log.w(TAG, "getImageBytes: 文件不存在")
                return@withContext null
            }

            Log.d(TAG, "getImageBytes: 从ZIP读取, key=$cacheKey")
            val bytes = ZipHelper.getImageBytes(file, imagePath, chapter)
            if (bytes != null) {
                coverCache[cacheKey] = bytes
                // 限制缓存大小
                if (coverCache.size > 200) {
                    val oldestKey = coverCache.keys.first()
                    coverCache.remove(oldestKey)
                    Log.d(TAG, "getImageBytes: 缓存超限, 移除 $oldestKey")
                }
            }
            bytes
        }

    /**
     * 将 zip 中的图片直接流式写入缓存文件（避免 ByteArray 大块内存分配）
     */
    suspend fun writeImageToFile(comicId: String, chapter: String, imagePath: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val comic = comicCache[comicId] ?: return@withContext false
            val file = File(comic.filePath)
            if (!file.exists()) return@withContext false
            ZipHelper.writeImageToFile(file, imagePath, destFile, chapter)
        }

    /**
     * 获取封面图片字节
     */
    suspend fun getCoverBytes(comicId: String): ByteArray? = withContext(Dispatchers.IO) {
        coverCache["cover_$comicId"]?.let { return@withContext it }

        val comic = comicCache[comicId]
        if (comic == null) {
            Log.w(TAG, "getCoverBytes: 未找到漫画, id=$comicId")
            return@withContext null
        }
        val file = File(comic.filePath)
        if (!file.exists()) {
            Log.w(TAG, "getCoverBytes: 文件不存在")
            return@withContext null
        }
        val bytes = ZipHelper.extractCover(file)
        if (bytes != null) {
            coverCache["cover_$comicId"] = bytes
            Log.d(TAG, "getCoverBytes: 提取封面成功, id=$comicId")
        }
        bytes
    }

    /**
     * 获取封面图片文件（用于 Coil 加载）
     * 将封面提取到缓存目录并返回文件路径
     */
    suspend fun getCoverFile(comicId: String): File? = withContext(Dispatchers.IO) {
        val coverDir = File(context.cacheDir, "comic_covers")
        coverDir.mkdirs()
        val coverFile = File(coverDir, "$comicId.jpg")

        // 如果已存在，直接返回
        if (coverFile.exists()) {
            return@withContext coverFile
        }

        // 提取封面并写入文件
        val bytes = getCoverBytes(comicId)
        if (bytes != null) {
            coverFile.writeBytes(bytes)
            Log.d(TAG, "getCoverFile: 封面已保存, id=$comicId")
            coverFile
        } else {
            null
        }
    }

    // 阅读进度持久化存储
    private val readingProgressPrefs by lazy {
        context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)
    }

    /**
     * 保存阅读进度
     */
    fun saveReadingProgress(comicId: String, chapter: String, pageIndex: Int) {
        val key = "progress_$comicId"
        val json = JSONObject().apply {
            put("chapter", chapter)
            put("pageIndex", pageIndex)
            put("timestamp", System.currentTimeMillis())
        }
        readingProgressPrefs.edit().putString(key, json.toString()).apply()
        Log.d(TAG, "saveReadingProgress: comicId=$comicId, chapter=$chapter, page=$pageIndex")
    }

    /**
     * 获取阅读进度
     * @return Triple(chapter, pageIndex, timestamp) 或 null
     */
    fun getReadingProgress(comicId: String): Triple<String, Int, Long>? {
        val key = "progress_$comicId"
        val jsonStr = readingProgressPrefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            Triple(
                json.getString("chapter"),
                json.getInt("pageIndex"),
                json.getLong("timestamp")
            )
        } catch (e: Exception) {
            Log.w(TAG, "getReadingProgress: 解析失败", e)
            null
        }
    }

    /**
     * 清除阅读进度
     */
    fun clearReadingProgress(comicId: String) {
        val key = "progress_$comicId"
        readingProgressPrefs.edit().remove(key).apply()
        Log.d(TAG, "clearReadingProgress: comicId=$comicId")
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        Log.d(TAG, "clearCache: 清除 ${comicCache.size} 个漫画, ${coverCache.size} 个封面")
        comicCache.clear()
        coverCache.clear()
        scannedUris.clear()
    }
}
