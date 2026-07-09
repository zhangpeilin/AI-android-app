package com.example.comicreader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.comicreader.model.Comic
import com.example.comicreader.util.ZipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ComicRepository private constructor(private val context: Context) {

    companion object {
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

    // 缓存目录：存放从 SAF 复制过来的 zip 文件
    private val zipCacheDir: File by lazy {
        File(context.cacheDir, "comic_zips").also { it.mkdirs() }
    }

    /**
     * 扫描用户通过 SAF 选择的文件夹
     * 扫描时将 zip 文件复制到本地缓存目录，后续直接用 File 操作
     */
    suspend fun scanFolder(uri: Uri): List<Comic> = withContext(Dispatchers.IO) {
        if (scannedUris.contains(uri.toString())) {
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
        comicCache.values.toList()
    }

    /**
     * 递归扫描目录中的 zip 文件，复制到本地缓存
     */
    private fun scanDirectory(directory: DocumentFile) {
        val files = directory.listFiles() ?: return
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
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
            Log.d("ComicRepo", "缓存命中: $fileName, size=${cacheFile.length()}")
            return cacheFile
        }

        return try {
            Log.d("ComicRepo", "开始复制: $fileName, 原始大小=${documentFile.length()}")
            context.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("ComicRepo", "复制完成: $fileName, 缓存大小=${cacheFile.length()}")
            cacheFile
        } catch (e: Exception) {
            Log.e("ComicRepo", "复制失败: $fileName", e)
            null
        }
    }

    /**
     * 获取漫画列表
     */
    fun getComics(): List<Comic> = comicCache.values.toList()

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
            val comic = comicCache[comicId] ?: return@withContext emptyList()
            val file = File(comic.filePath)
            if (!file.exists()) {
                return@withContext emptyList()
            }
            ZipHelper.getChapters(file)
        }

    /**
     * 获取漫画中某章节的图片列表
     */
    suspend fun getImages(comicId: String, chapter: String): List<String> =
        withContext(Dispatchers.IO) {
            val comic = comicCache[comicId] ?: return@withContext emptyList()
            val file = File(comic.filePath)
            if (!file.exists()) {
                return@withContext emptyList()
            }
            val chapters = ZipHelper.getChapters(file)
            chapters.find { it.number == chapter }?.images ?: emptyList()
        }

    /**
     * 获取图片字节数据（带内存缓存）
     */
    suspend fun getImageBytes(comicId: String, chapter: String, imagePath: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val cacheKey = "$comicId#$chapter#$imagePath"
            coverCache[cacheKey] ?: run {
                val comic = comicCache[comicId] ?: return@withContext null
                val file = File(comic.filePath)
                if (!file.exists()) return@withContext null
                val bytes = ZipHelper.getImageBytes(file, imagePath, chapter)
                if (bytes != null) {
                    coverCache[cacheKey] = bytes
                    // 限制缓存大小
                    if (coverCache.size > 200) {
                        val oldestKey = coverCache.keys.first()
                        coverCache.remove(oldestKey)
                    }
                }
                bytes
            }
        }

    /**
     * 获取封面图片字节
     */
    suspend fun getCoverBytes(comicId: String): ByteArray? = withContext(Dispatchers.IO) {
        coverCache["cover_$comicId"] ?: run {
            val comic = comicCache[comicId] ?: return@withContext null
            val file = File(comic.filePath)
            if (!file.exists()) return@withContext null
            val bytes = ZipHelper.extractCover(file)
            if (bytes != null) {
                coverCache["cover_$comicId"] = bytes
            }
            bytes
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        comicCache.clear()
        coverCache.clear()
        scannedUris.clear()
    }
}
