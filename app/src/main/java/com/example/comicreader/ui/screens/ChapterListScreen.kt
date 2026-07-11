package com.example.comicreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.comicreader.model.Chapter
import com.example.comicreader.viewmodel.ComicReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListScreen(
    comicId: String,
    comicTitle: String,
    viewModel: ComicReaderViewModel,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onChapterClick: (String) -> Unit
) {
    val chapters by viewModel.chapters.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(comicId) {
        viewModel.loadChapters(comicId, comicTitle)
    }

    // 如果只有 1 话，直接打开阅读器
    LaunchedEffect(chapters) {
        if (chapters.size == 1) {
            onChapterClick(chapters.first().number)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        comicTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onHomeClick) {
                        Icon(Icons.Default.Home, contentDescription = "返回首页")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (chapters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无章节")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    ChapterItem(
                        chapter = chapter,
                        index = index,
                        onClick = { onChapterClick(chapter.number) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterItem(
    chapter: Chapter,
    index: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                "第 ${chapter.number} 话",
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text("${chapter.images.size} 页")
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    if (index < chapter.images.size - 1) {
        Divider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
