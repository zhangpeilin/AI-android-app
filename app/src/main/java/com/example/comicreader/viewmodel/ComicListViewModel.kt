package com.example.comicreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comicreader.model.Comic
import com.example.comicreader.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    init {
        // 加载上次扫描的结果
        loadComics()
    }

    fun loadComics() {
        _comics.value = repository.getComics()
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
        _comics.value = repository.searchComics(query)
    }

    fun getCoverBytes(comicId: String) = viewModelScope.launch {
        repository.getCoverBytes(comicId)
    }
}
