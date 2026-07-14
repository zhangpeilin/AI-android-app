package com.example.comicreader.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.comicreader.model.Chapter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object ZipHelper {

    /**
     * 从文件路径生成唯一 ID (MD5)
     */
    fun generateId(filePath: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(filePath.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            filePath.hashCode().toString()
        }
    }

    /**
     * 解析 zip 文件，返回章节列表
     * zip 结构可能是:
     * - 扁平: 所有图片在根目录 -> 单章节
     * - 有子目录: 每个子目录是一个章节
     */
    fun getChapters(zipFile: File): List<Chapter> {
        Log.d("ZipHelper", "getChapters: file=${zipFile.absolutePath}, exists=${zipFile.exists()}, size=${zipFile.length()}")
        return try {
            if (!zipFile.exists() || zipFile.length() == 0L) {
                Log.e("ZipHelper", "文件不存在或为空: ${zipFile.absolutePath}")
                return emptyList()
            }
            ZipFile(zipFile).use { zf ->
                val entries = zf.entries().toList()
                Log.d("ZipHelper", "zip 条目总数: ${entries.size}")
                val imageEntries = entries.filter { entry ->
                    !entry.isDirectory && isImageFile(entry.name)
                }
                Log.d("ZipHelper", "图片条目数: ${imageEntries.size}")
                if (imageEntries.isNotEmpty()) {
                    Log.d("ZipHelper", "第一个图片条目: ${imageEntries.first().name}")
                }

                // 检查是否有子目录结构
                val hasSubDirs = imageEntries.any { it.name.contains("/") }
                Log.d("ZipHelper", "hasSubDirs=$hasSubDirs")

                if (!hasSubDirs) {
                    // 扁平结构：所有图片归为一个章节
                    val sorted = imageEntries
                        .map { it.name }
                        .sortedWith(ImageComparator)
                    Log.d("ZipHelper", "扁平结构，章节1有 ${sorted.size} 张图片")
                    // 日志验证排序
                    if (sorted.isNotEmpty()) {
                        val first10 = sorted.take(10).joinToString()
                        val last10 = sorted.takeLast(10).joinToString()
                        Log.d("ZipHelper", "排序验证(前10): $first10")
                        Log.d("ZipHelper", "排序验证(后10): $last10")
                    }
                    listOf(Chapter("1", sorted))
                } else {
                    // 有子目录：每个子目录是一个章节
                    val chapterMap = mutableMapOf<String, MutableList<String>>()
                    for (entry in imageEntries) {
                        val parts = entry.name.split("/")
                        if (parts.size >= 2) {
                            val chapterName = parts[0]
                            val fileName = parts.last()
                            chapterMap.getOrPut(chapterName) { mutableListOf() }.add(fileName)
                        }
                    }
                    val chapters = chapterMap.map { (name, images) ->
                        Chapter(name, images.sortedWith(ImageComparator))
                    }.sortedBy { it.number.toIntOrNull() ?: 0 }
                    Log.d("ZipHelper", "目录结构，共 ${chapters.size} 个章节")
                    chapters
                }
            }
        } catch (e: Exception) {
            Log.e("ZipHelper", "解析zip失败: ${zipFile.absolutePath}", e)
            emptyList()
        }
    }

    /**
     * 从 zip 中读取指定图片的字节数据
     */
    fun getImageBytes(zipFile: File, imagePath: String, chapter: String? = null): ByteArray? {
        return try {
            ZipFile(zipFile).use { zf ->
                var entry = zf.getEntry(imagePath)
                if (entry == null && chapter != null) {
                    entry = zf.getEntry("$chapter/$imagePath")
                }
                if (entry == null) return null

                val bos = ByteArrayOutputStream()
                zf.getInputStream(entry).use { input ->
                    input.copyTo(bos, bufferSize = 8192)
                }
                bos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 zip 中的图片直接流式写入目标文件（避免 ByteArray 大块内存分配）
     */
    fun writeImageToFile(zipFile: File, imagePath: String, destFile: File, chapter: String? = null): Boolean {
        return try {
            ZipFile(zipFile).use { zf ->
                var entry = zf.getEntry(imagePath)
                if (entry == null && chapter != null) {
                    entry = zf.getEntry("$chapter/$imagePath")
                }
                if (entry == null) return false

                zf.getInputStream(entry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 提取 zip 中第一张图片作为封面 (缩放到 300x400)
     */
    fun extractCover(zipFile: File): ByteArray? {
        return try {
            val chapters = getChapters(zipFile)
            if (chapters.isEmpty()) return null

            val firstChapter = chapters.first()
            if (firstChapter.images.isEmpty()) return null

            val firstImage = firstChapter.images.first()
            val bytes = getImageBytes(zipFile, firstImage, firstChapter.number) ?: return null

            // 缩放为封面尺寸
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = Bitmap.createScaledBitmap(bitmap, 300, 400, true)
            if (scaled !== bitmap) bitmap.recycle()

            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            scaled.recycle()
            bos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".webp") || lower.endsWith(".bmp")
    }
}

/**
 * 图片文件名排序：按数字顺序而非字典序
 */
object ImageComparator : Comparator<String> {
    override fun compare(s1: String, s2: String): Int {
        val num1 = extractNumber(s1)
        val num2 = extractNumber(s2)
        return when {
            num1 != null && num2 != null -> {
                val cmp = num1.compareTo(num2)
                if (cmp != 0) cmp else s1.compareTo(s2)
            }
            num1 != null -> -1  // 有数字的排前面
            num2 != null -> 1   // 有数字的排前面
            else -> s1.compareTo(s2)
        }
    }

    private fun extractNumber(fileName: String): Int? {
        val name = fileName.substringBeforeLast('.')
        // 只提取文件名最前面的数字序号（在第一个 _ 之前），而非拼合所有数字
        // 例: "022_05_20_05_00.jpg" → "022" → 22，"133_32_06_01.jpg" → "133" → 133
        val leading = name.substringBefore('_').filter { it.isDigit() }
        return leading.toIntOrNull()
    }
}
