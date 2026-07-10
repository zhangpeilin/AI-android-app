package com.example.comicreader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewStream
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

/** 阅读模式枚举 */
enum class ReadingMode { HORIZONTAL, VERTICAL }

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
    val context = LocalContext.current

    // 阅读模式状态
    var readingMode by remember { mutableStateOf(ReadingMode.HORIZONTAL) }
    // 当前页码（切换模式时保持）
    var currentPageIndex by remember { mutableIntStateOf(0) }
    // 顶部栏是否显示
    var showTopBar by remember { mutableStateOf(true) }

    LaunchedEffect(comicId, chapter) {
        viewModel.loadImages(comicId, chapter)
    }

    // 预加载图片缓存
    LaunchedEffect(images) {
        if (images.isNotEmpty()) {
            for (i in images.indices) {
                val cacheFile = File(context.cacheDir, "comic_pages/${comicId}_${chapter}_$i")
                if (!cacheFile.exists()) {
                    cacheFile.parentFile?.mkdirs()
                    withContext(Dispatchers.IO) {
                        val bytes = viewModel.getImageBytes(comicId, chapter, images[i])
                        if (bytes != null) cacheFile.writeBytes(bytes)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
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
                    },
                    actions = {
                        // 阅读模式切换按钮
                        IconButton(onClick = {
                            readingMode = if (readingMode == ReadingMode.HORIZONTAL)
                                ReadingMode.VERTICAL else ReadingMode.HORIZONTAL
                        }) {
                            Icon(
                                if (readingMode == ReadingMode.HORIZONTAL)
                                    Icons.Default.ViewStream
                                else
                                    Icons.Default.ViewDay,
                                contentDescription = if (readingMode == ReadingMode.HORIZONTAL)
                                    "切换竖屏阅读" else "切换横屏阅读"
                            )
                        }
                    }
                )
            }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (readingMode) {
                    ReadingMode.HORIZONTAL -> HorizontalReader(
                        comicId = comicId,
                        chapter = chapter,
                        images = images,
                        viewModel = viewModel,
                        initialPage = currentPageIndex,
                        onPageChanged = { currentPageIndex = it },
                        onTapCenter = { showTopBar = !showTopBar }
                    )
                    ReadingMode.VERTICAL -> VerticalReader(
                        comicId = comicId,
                        chapter = chapter,
                        images = images,
                        viewModel = viewModel,
                        initialPage = currentPageIndex,
                        onPageChanged = { currentPageIndex = it },
                        onTapCenter = { showTopBar = !showTopBar }
                    )
                }

                // 底部页码指示器（水平模式）
                if (readingMode == ReadingMode.HORIZONTAL && showTopBar) {
                    // 页码在 HorizontalReader 内部显示
                }
            }
        }
    }
}

/**
 * 水平翻页阅读模式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HorizontalReader(
    comicId: String,
    chapter: String,
    images: List<String>,
    viewModel: ComicReaderViewModel,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
    val context = LocalContext.current

    // 页码变化时回调
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        // 预加载相邻页
        val currentPage = pagerState.currentPage
        for (offset in -1..1) {
            val pageIndex = currentPage + offset
            if (pageIndex in images.indices) {
                val cacheFile = File(context.cacheDir, "comic_pages/${comicId}_${chapter}_$pageIndex")
                if (!cacheFile.exists()) {
                    cacheFile.parentFile?.mkdirs()
                    withContext(Dispatchers.IO) {
                        val bytes = viewModel.getImageBytes(comicId, chapter, images[pageIndex])
                        if (bytes != null) cacheFile.writeBytes(bytes)
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 页码指示器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${pagerState.currentPage + 1} / ${images.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // 点击屏幕中央区域切换顶栏
                        val width = this.size.width
                        val height = this.size.height
                        val centerX = width / 2
                        val centerY = height / 2
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        // 中央 60% 区域
                        if (kotlin.math.abs(dx) < width * 0.3 && kotlin.math.abs(dy) < height * 0.3) {
                            onTapCenter()
                        }
                    }
                },
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

/**
 * 垂直无缝阅读模式（长条漫画）
 */
@Composable
private fun VerticalReader(
    comicId: String,
    chapter: String,
    images: List<String>,
    viewModel: ComicReaderViewModel,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val context = LocalContext.current

    // 页码变化时回调
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChanged(listState.firstVisibleItemIndex)
        // 预加载前后5张图片
        val first = listState.firstVisibleItemIndex
        for (offset in -2..5) {
            val pageIndex = first + offset
            if (pageIndex in images.indices) {
                val cacheFile = File(context.cacheDir, "comic_pages/${comicId}_${chapter}_$pageIndex")
                if (!cacheFile.exists()) {
                    cacheFile.parentFile?.mkdirs()
                    withContext(Dispatchers.IO) {
                        val bytes = viewModel.getImageBytes(comicId, chapter, images[pageIndex])
                        if (bytes != null) cacheFile.writeBytes(bytes)
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = this.size.width
                    val height = this.size.height
                    val centerX = width / 2
                    val centerY = height / 2
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    if (kotlin.math.abs(dx) < width * 0.3 && kotlin.math.abs(dy) < height * 0.3) {
                        onTapCenter()
                    }
                }
            }
    ) {
        items(images.size) { index ->
            VerticalComicPage(
                comicId = comicId,
                chapter = chapter,
                imagePath = images[index],
                pageIndex = index,
                viewModel = viewModel
            )
        }
    }
}

/**
 * 垂直模式下的单页图片（宽度填满，高度自适应）
 */
@Composable
fun VerticalComicPage(
    comicId: String,
    chapter: String,
    imagePath: String,
    pageIndex: Int,
    viewModel: ComicReaderViewModel
) {
    val context = LocalContext.current
    val cacheFile = remember(comicId, chapter, pageIndex) {
        File(context.cacheDir, "comic_pages/${comicId}_${chapter}_${pageIndex}")
    }
    var fileReady by remember { mutableStateOf(cacheFile.exists()) }

    LaunchedEffect(imagePath, cacheFile) {
        if (!cacheFile.exists()) {
            cacheFile.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                val bytes = viewModel.getImageBytes(comicId, chapter, imagePath)
                if (bytes != null) cacheFile.writeBytes(bytes)
            }
        }
        fileReady = cacheFile.exists()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (fileReady) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cacheFile)
                    .crossfade(false)
                    .build(),
                contentDescription = "漫画第 ${pageIndex + 1} 页",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
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
    val cacheFile = remember(comicId, chapter, pageIndex) {
        File(context.cacheDir, "comic_pages/${comicId}_${chapter}_${pageIndex}")
    }
    var fileReady by remember { mutableStateOf(cacheFile.exists()) }

    LaunchedEffect(imagePath, cacheFile) {
        if (!cacheFile.exists()) {
            cacheFile.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                val bytes = viewModel.getImageBytes(comicId, chapter, imagePath)
                if (bytes != null) cacheFile.writeBytes(bytes)
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
