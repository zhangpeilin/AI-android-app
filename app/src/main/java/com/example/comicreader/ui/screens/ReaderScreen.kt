package com.example.comicreader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.comicreader.viewmodel.ComicReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    val context = LocalContext.current

    LaunchedEffect(comicId, chapter) {
        viewModel.loadImages(comicId, chapter)
    }

    // 预加载相邻页面的图片到缓存文件
    LaunchedEffect(pagerState.currentPage, images) {
        if (images.isNotEmpty()) {
            val currentPage = pagerState.currentPage
            for (offset in -1..1) {
                val pageIndex = currentPage + offset
                if (pageIndex in images.indices) {
                    val cacheFile = File(context.cacheDir, "comic_pages/${comicId}_${chapter}_${pageIndex}")
                    if (!cacheFile.exists()) {
                        cacheFile.parentFile?.mkdirs()
                        withContext(Dispatchers.IO) {
                            val bytes = viewModel.getImageBytes(comicId, chapter, images[pageIndex])
                            if (bytes != null) {
                                cacheFile.writeBytes(bytes)
                            }
                        }
                    }
                }
            }
        }
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

                // 横向滑动翻页，预加载前后1页
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 1
                ) { page ->
                    ComicPage(
                        comicId = comicId,
                        chapter = chapter,
                        imagePath = images[page],
                        pageIndex = page,
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
    pageIndex: Int,
    viewModel: ComicReaderViewModel
) {
    val context = LocalContext.current
    // 缓存文件路径
    val cacheFile = remember(comicId, chapter, pageIndex) {
        File(context.cacheDir, "comic_pages/${comicId}_${chapter}_${pageIndex}")
    }
    var fileReady by remember { mutableStateOf(cacheFile.exists()) }

    // 如果缓存文件不存在，从 ViewModel 加载并写入缓存
    LaunchedEffect(imagePath, cacheFile) {
        if (!cacheFile.exists()) {
            cacheFile.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                val bytes = viewModel.getImageBytes(comicId, chapter, imagePath)
                if (bytes != null) {
                    cacheFile.writeBytes(bytes)
                }
            }
        }
        fileReady = cacheFile.exists()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (fileReady) {
            ZoomableImage(
                model = cacheFile,
                contentDescription = "漫画第 ${pageIndex + 1} 页"
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * 支持双指缩放和拖拽的图片组件
 * 未放大时不拦截触摸（让 HorizontalPager 处理翻页）
 * 放大后才拦截进行缩放/平移
 * 双击始终可用以切换缩放
 */
@Composable
fun ZoomableImage(
    model: File,
    contentDescription: String?
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val isZoomed = scale > 1.01f

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 放大时才启用缩放/平移手势，未放大时不拦截（翻页正常）
            .pointerInput(isZoomed) {
                if (isZoomed) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            }
            // 双击始终可用
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.5f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(false)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
    }
}
