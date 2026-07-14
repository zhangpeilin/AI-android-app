package com.example.comicreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comicreader.model.Comic
import com.example.comicreader.repository.ComicRepository
import com.example.comicreader.repository.ComicSortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ComicListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ComicRepository.getInstance(application)

    private val _comics = MutableStateFlow<List<Comic>>(emptyList())
    val comics: StateFlow<List<Comic>> = _comics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolderName = MutableStateFlow<String?>(null)
    val selectedFolderName: StateFlow<String?> = _selectedFolderName.asStateFlow()

    private val _sortMode = MutableStateFlow(ComicSortMode.TITLE)
    val sortMode: StateFlow<ComicSortMode> = _sortMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: Boolean get() = _selectedIds.value.isNotEmpty()

    init {
        loadComics()
    }

    fun loadComics() {
        val raw = repository.getComics()
        // 异步获取页数缓存，然后排序
        viewModelScope.launch {
            raw.forEach { comic ->
                if (comic.pageCount == 0) {
                    repository.getPageCount(comic.id)
                }
            }
            // 重新获取（getPageCount 已更新 comicCache）
            val updated = repository.getComics()
            _comics.value = repository.sortComics(updated, _sortMode.value)
        }
    }

    fun scanFolder(uri: Uri, folderName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.scanFolder(uri)
                _selectedFolderName.value = folderName
                loadComics()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        val results = if (query.isBlank()) {
            repository.getComics()
        } else {
            repository.searchComics(query)
        }
        _comics.value = repository.sortComics(results, _sortMode.value)
    }

    fun setSortMode(mode: ComicSortMode) {
        _sortMode.value = mode
        // 对当前列表重新排序
        _comics.value = repository.sortComics(_comics.value, mode)
    }

    fun toggleSelection(comicId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(comicId)) {
            current.remove(comicId)
        } else {
            current.add(comicId)
        }
        _selectedIds.value = current
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        repository.deleteComics(ids)
        _selectedIds.value = emptySet()
        loadComics()
    }

    fun deleteComic(comicId: String) {
        repository.deleteComic(comicId)
        loadComics()
    }

    fun deleteComics(comicIds: List<String>) {
        repository.deleteComics(comicIds)
        loadComics()
    }

    fun getCoverBytes(comicId: String) = viewModelScope.launch {
        repository.getCoverBytes(comicId)
    }

    /**
     * 获取漫画封面文件（用于 Coil 加载）
     */
    suspend fun getCoverFile(comicId: String): File? {
        return repository.getCoverFile(comicId)
    }

    /**
     * 获取格式化阅读进度文本，未读返回 null
     */
    fun getReadingProgressText(comicId: String): String? {
        return repository.getReadingProgressText(comicId)
    }

    /**
     * 获取上次阅读时间戳
     */
    fun getLastReadTime(comicId: String): Long {
        return repository.getLastReadTime(comicId)
    }
}
