package com.example.comicreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comicreader.model.Comic
import com.example.comicreader.model.WebDavEntry
import com.example.comicreader.model.WebDavServerConfig
import com.example.comicreader.network.WebDavClient
import com.example.comicreader.repository.ComicRepository
import com.example.comicreader.repository.WebDavServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebDavBrowseViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WebDavBrowseVM"
    }

    private val serverRepo = WebDavServerRepository.getInstance(application)
    private val comicRepo = ComicRepository.getInstance(application)
    private val webDavClient = WebDavClient()

    private val _entries = MutableStateFlow<List<WebDavEntry>>(emptyList())
    val entries: StateFlow<List<WebDavEntry>> = _entries.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var serverConfig: WebDavServerConfig? = null
    private var currentServerId: String? = null

    fun init(serverId: String) {
        Log.d(TAG, "init: serverId=$serverId")
        currentServerId = serverId
        serverConfig = serverRepo.getServer(serverId)
        if (serverConfig == null) {
            Log.e(TAG, "init: 未找到服务器配置, id=$serverId")
            _error.value = "服务器配置不存在"
            return
        }
        // 从上次浏览的目录开始，而不是根目录
        val startPath = serverConfig?.lastPath ?: "/"
        Log.d(TAG, "init: server=${serverConfig?.name}, url=${serverConfig?.url}, lastPath=$startPath")
        loadDirectory(startPath)
    }

    fun loadDirectory(path: String) {
        val config = serverConfig ?: return
        Log.d(TAG, "loadDirectory: path=$path")

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val result = webDavClient.listDirectory(config, path)
                _entries.value = result
                _currentPath.value = path
                // 保存当前浏览路径，下次进入时直接定位到这里
                currentServerId?.let { serverRepo.updateLastPath(it, path) }
                Log.d(TAG, "loadDirectory: 成功, ${result.size} 个条目, 已保存路径")
            } catch (e: Exception) {
                Log.e(TAG, "loadDirectory: 失败", e)
                _error.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun enterDirectory(entry: WebDavEntry) {
        Log.d(TAG, "enterDirectory: name=${entry.name}, isDir=${entry.isDirectory}")
        if (entry.isDirectory) {
            loadDirectory(entry.path)
        }
    }

    fun goBack(): Boolean {
        val path = _currentPath.value
        if (path == "/") return false

        val parentPath = path.trimEnd('/').substringBeforeLast('/', "/")
        val normalizedParent = if (!parentPath.endsWith("/")) "$parentPath/" else parentPath
        Log.d(TAG, "goBack: $path -> $normalizedParent")
        loadDirectory(normalizedParent)
        return true
    }

    /**
     * 点击 zip 文件：后台下载并打开
     */
    fun openComic(entry: WebDavEntry, onComicReady: (Comic) -> Unit) {
        val config = serverConfig ?: return
        Log.d(TAG, "openComic: name=${entry.name}, path=${entry.path}")

        viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0f
            try {
                val comic = comicRepo.addWebDavComic(
                    config,
                    entry.path,
                    entry.name
                ) { downloaded, total ->
                    if (total > 0) {
                        val progress = downloaded.toFloat() / total.toFloat()
                        _downloadProgress.value = progress
                        if ((downloaded % (1024 * 1024)) < 8192) {
                            Log.d(TAG, "openComic: 下载进度 ${(progress * 100).toInt()}%")
                        }
                    }
                }

                if (comic != null) {
                    Log.d(TAG, "openComic: 成功, id=${comic.id}, title=${comic.title}")
                    _downloadProgress.value = null
                    withContext(Dispatchers.Main) {
                        onComicReady(comic)
                    }
                } else {
                    Log.e(TAG, "openComic: 下载或注册失败")
                    _error.value = "下载失败"
                    _downloadProgress.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "openComic: 异常", e)
                _error.value = "打开失败: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
