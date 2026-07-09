package com.example.comicreader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.comicreader.viewmodel.ComicReaderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    comicId: String,
    comicTitle: String,
    chapter: String,
    viewModel: ComicReaderViewModel,
    onBackClick: () -> Unit
) {
    val images by viewModel.currentImages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pagerState = rememberPagerState(pageCount = { images.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(comicId, chapter) {
        viewModel.loadImages(comicId, chapter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "$comicTitle - 第 $chapter 话",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
        } else if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无图片")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 页码指示器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 横向滑动翻页
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ComicPage(
                        comicId = comicId,
                        chapter = chapter,
                        imagePath = images[page],
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun ComicPage(
    comicId: String,
    chapter: String,
    imagePath: String,
    viewModel: ComicReaderViewModel
) {
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(imagePath) {
        coroutineScope.launch {
            imageBytes = viewModel.getImageBytes(comicId, chapter, imagePath)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (imageBytes != null) {
            val bitmap = android.graphics.BitmapFactory
                .decodeByteArray(imageBytes, 0, imageBytes!!.size)
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
