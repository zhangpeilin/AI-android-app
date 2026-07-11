package com.example.comicreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comicreader.model.Chapter
import com.example.comicreader.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComicReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ComicRepository.getInstance(application)

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentImages = MutableStateFlow<List<String>>(emptyList())
    val currentImages: StateFlow<List<String>> = _currentImages.asStateFlow()

    private val _currentChapter = MutableStateFlow<String?>(null)
    val currentChapter: StateFlow<String?> = _currentChapter.asStateFlow()

    private val _comicTitle = MutableStateFlow("")
    val comicTitle: StateFlow<String> = _comicTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 记录已处理过进度对话框的 comicId+chapter 组合，防止重复弹出
    private val handledProgressSessions = mutableSetOf<String>()

    fun markProgressDialogHandled(comicId: String, chapter: String) {
        handledProgressSessions.add("$comicId#$chapter")
    }

    fun hasProgressDialogBeenHandled(comicId: String, chapter: String): Boolean {
        return handledProgressSessions.contains("$comicId#$chapter")
    }

    fun loadChapters(comicId: String, title: String) {
        _comicTitle.value = title
        viewModelScope.launch {
            _isLoading.value = true
            _chapters.value = repository.getChapters(comicId)
            _isLoading.value = false
        }
    }

    fun loadImages(comicId: String, chapter: String) {
        _currentChapter.value = chapter
        viewModelScope.launch {
            _isLoading.value = true
            _currentImages.value = repository.getImages(comicId, chapter)
            _isLoading.value = false
        }
    }

    suspend fun getImageBytes(comicId: String, chapter: String, imagePath: String): ByteArray? {
        return repository.getImageBytes(comicId, chapter, imagePath)
    }

    fun saveReadingProgress(comicId: String, chapter: String, pageIndex: Int) {
        Log.d("ComicReader", "saveProgress: comicId=$comicId, ch=$chapter, page=$pageIndex")
        repository.saveReadingProgress(comicId, chapter, pageIndex)
    }

    fun clearProgressDialogState(comicId: String, chapter: String) {
        handledProgressSessions.remove("$comicId#$chapter")
    }

    fun getReadingProgress(comicId: String): Triple<String, Int, Long>? {
        return repository.getReadingProgress(comicId)
    }

    /** 获取指定漫画的已保存页码（用于 composable 重建时恢复） */
    fun getLastSavedPageIndex(comicId: String): Int {
        return repository.getReadingProgress(comicId)?.second ?: 0
    }
}
