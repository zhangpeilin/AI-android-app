package com.example.comicreader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.comicreader.viewmodel.ComicReaderViewModel
import android.util.Log
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val images by viewModel.currentImages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    // 阅读进度状态（每次 comicId 变化时重置）
    var savedProgress by remember(comicId) { mutableStateOf<Triple<String, Int, Long>?>(null) }
    var showProgressDialog by remember(comicId) { mutableStateOf(false) }
    // 使用 rememberSaveable 跟踪对话框状态：导航离开时自动清除，配置变更时保留
    var hasHandledProgressDialog by rememberSaveable { mutableStateOf(false) }
    // 阅读模式状态（默认垂直模式）
    var readingMode by remember(comicId) { mutableStateOf(ReadingMode.VERTICAL) }
    // 当前页码：composable 重建时从 ViewModel 同步恢复真实页码，避免第一帧渲染 page 0
    val initialPageIndex = if (hasHandledProgressDialog) {
        viewModel.getLastSavedPageIndex(comicId)
    } else {
        0
    }
    var currentPageIndex by remember(comicId, chapter) { mutableIntStateOf(initialPageIndex) }
    // 跳转请求：仅在外部触发（对话框/进度条点击）时设置，避免 onPageChanged 反馈循环
    var jumpTargetPage by remember(comicId, chapter) { mutableStateOf<Int?>(null) }
    // 顶部栏是否显示
    var showTopBar by remember { mutableStateOf(true) }
    // 所有图片的预计算高度（按比例），确保 LazyColumn 项高度固定不变
    var pageHeights by remember(comicId, chapter) { mutableStateOf<Map<Int, Dp>?>(null) }
    // 图片是否已加载完成
    var isImagesReady by remember(comicId, chapter) { mutableStateOf(false) }
    // 进度是否已恢复（防止恢复前误保存）
    var isProgressRestored by remember(comicId, chapter) { mutableStateOf(false) }

    // 加载图片
    LaunchedEffect(comicId, chapter) {
        Log.d("ReaderScreen", "[加载] comicId=$comicId, chapter=$chapter")
        isImagesReady = false
        isProgressRestored = false
        viewModel.loadImages(comicId, chapter)
    }

    // 图片加载完成后：检查阅读进度（不等待缓存完成，弹窗要立刻出现）→ 预缓存
    LaunchedEffect(images) {
        if (images.isNotEmpty()) {
            val snapshot = images.toList()
            val widthPx = context.resources.displayMetrics.widthPixels

            // 1. 先检查阅读进度
            if (!hasHandledProgressDialog && savedProgress == null) {
                savedProgress = viewModel.getReadingProgress(comicId)
                Log.d("ReaderScreen", "[查进度] savedProgress=${if(savedProgress!=null) "ch=${savedProgress!!.first},p=${savedProgress!!.second}" else "null"}")
                if (savedProgress != null) {
                    showProgressDialog = true
                } else {
                    isProgressRestored = true
                    hasHandledProgressDialog = true
                }
            } else if (hasHandledProgressDialog) {
                val restoredPage = viewModel.getLastSavedPageIndex(comicId)
                Log.d("ReaderScreen", "[重建恢复] hasHandled=true, restoredPage=$restoredPage, imagesSize=${images.size}")
                currentPageIndex = restoredPage.coerceIn(0, images.size - 1)
            }

            // 2. 顺序缓存图片 + 计算高度（避免并发 OOM）
            withContext(Dispatchers.IO) {
                val pixelHeightList = mutableListOf<Pair<Int, Float>>()
                snapshot.forEachIndexed { i, imagePath ->
                    try {
                        val cacheFile = File(context.cacheDir, "comic_pages/${comicId}_${chapter}_$i")
                        if (!cacheFile.exists()) {
                            cacheFile.parentFile?.mkdirs()
                            viewModel.writeImageToFile(comicId, chapter, imagePath, cacheFile)
                        }
                        if (cacheFile.exists()) {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(cacheFile.absolutePath, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                val heightPx = widthPx * options.outHeight.toFloat() / options.outWidth
                                pixelHeightList.add(i to heightPx)
                            }
                        }
                    } catch (e: Exception) {
                        // 跳过失败图片
                    }
                }
                pageHeights = pixelHeightList.map { (index, heightPx) ->
                    index to with(density) { heightPx.toDp() }
                }.toMap()
            }

            Log.d("ReaderScreen", "[图片就绪] comicId=$comicId, images=${images.size}, hasHandled=$hasHandledProgressDialog, heights=${pageHeights?.size}")
            isImagesReady = true
        }
    }

    // 保存阅读进度（仅在进度恢复后才保存，避免覆盖原有进度）
    LaunchedEffect(currentPageIndex, isImagesReady, isProgressRestored) {
        if (isImagesReady && isProgressRestored && images.isNotEmpty()) {
            Log.d("ReaderScreen", "[保存进度] comicId=$comicId, chapter=$chapter, page=$currentPageIndex")
            viewModel.saveReadingProgress(comicId, chapter, currentPageIndex)
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
                        IconButton(onClick = {
                            Log.d("ReaderScreen", "[返回] comicId=$comicId, chapter=$chapter, currentPageIndex=$currentPageIndex")
                            onBackClick()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 首页按钮
                        IconButton(onClick = onHomeClick) {
                            Icon(Icons.Default.Home, contentDescription = "返回首页")
                        }
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
                        jumpTargetPage = jumpTargetPage,
                        onJumpConsumed = { jumpTargetPage = null },
                        onPageChanged = { currentPageIndex = it },
                        onTapCenter = { showTopBar = !showTopBar }
                    )
                    ReadingMode.VERTICAL -> {
                        if (pageHeights != null) {
                            VerticalReader(
                                comicId = comicId,
                                chapter = chapter,
                                images = images,
                                pageHeights = pageHeights ?: emptyMap(),
                                viewModel = viewModel,
                                initialPage = currentPageIndex,
                                jumpTargetPage = jumpTargetPage,
                                onJumpConsumed = { jumpTargetPage = null },
                                onPageChanged = { currentPageIndex = it },
                                onTapCenter = { showTopBar = !showTopBar }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                // 水平模式下页码在 HorizontalReader 内部显示
            }
        }
    }

    // 阅读进度选择对话框
    if (showProgressDialog && savedProgress != null) {
        val progress = savedProgress!!
        AlertDialog(
            onDismissRequest = {
                showProgressDialog = false
                savedProgress = null  // 清除旧进度，防止下次重入时残留
                hasHandledProgressDialog = true
                isProgressRestored = true
            },
            title = { Text("继续阅读") },
            text = {
                Text("上次读到第 ${progress.first} 话，第 ${progress.second + 1} 页\n是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showProgressDialog = false
                    savedProgress = null  // 清除旧进度，防止下次重入时残留
                    hasHandledProgressDialog = true
                    val targetPage = progress.second.coerceIn(0, images.size - 1)
                    currentPageIndex = targetPage
                    jumpTargetPage = targetPage
                    isProgressRestored = true
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProgressDialog = false
                    savedProgress = null  // 清除旧进度，防止下次重入时残留
                    hasHandledProgressDialog = true
                    isProgressRestored = true
                }) {
                    Text("从头开始")
                }
            }
        )
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
    jumpTargetPage: Int?,
    onJumpConsumed: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, images.size - 1), pageCount = { images.size })
    val coroutineScope = rememberCoroutineScope()

    // 用 CompletableDeferred 确保初始滚动完成后再触发 onPageChanged 回调
    val initialScrollDone = remember { CompletableDeferred<Unit>() }

    // 监听跳转请求（仅来自对话框/进度条点击，避免 onPageChanged 反馈循环）
    LaunchedEffect(jumpTargetPage) {
        val target = jumpTargetPage
        if (target != null) {
            Log.d("ReaderScreen", "[H跳转] jumpTarget=$target, currentPage=${pagerState.currentPage}")
            pagerState.scrollToPage(target.coerceIn(0, images.size - 1))
            onJumpConsumed()
        }
        initialScrollDone.complete(Unit)
    }

    // 页码变化时回调（等待初始滚动完成）
    LaunchedEffect(pagerState.currentPage) {
        initialScrollDone.await()
        Log.d("ReaderScreen", "[H页码变化] pagerPage=${pagerState.currentPage}")
        onPageChanged(pagerState.currentPage)
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

        Box(modifier = Modifier.fillMaxSize()) {
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

            // 右侧进度条
            SideProgressBar(
                totalPages = images.size,
                currentPage = pagerState.currentPage,
                onPageJump = { newPage ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(newPage)
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
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
    pageHeights: Map<Int, Dp>,
    viewModel: ComicReaderViewModel,
    initialPage: Int,
    jumpTargetPage: Int?,
    onJumpConsumed: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, images.size - 1))
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 基于屏幕宽度的默认高度（2:3 宽高比）
    val defaultHeight = with(LocalDensity.current) {
        (context.resources.displayMetrics.widthPixels * 1.5f).toDp()
    }

    // 用 CompletableDeferred 确保初始滚动完成后再触发 onPageChanged 回调
    val initialScrollDone = remember { CompletableDeferred<Unit>() }

    // 监听跳转请求（仅来自对话框/进度条点击，避免 onPageChanged 反馈循环）
    LaunchedEffect(jumpTargetPage) {
        val target = jumpTargetPage
        if (target != null) {
            Log.d("ReaderScreen", "[V跳转] jumpTarget=$target, firstVisible=${listState.firstVisibleItemIndex}")
            listState.scrollToItem(target.coerceIn(0, images.size - 1))
            onJumpConsumed()
        }
        initialScrollDone.complete(Unit)
    }

    // 跟踪滚动任务，避免并发滚动
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 检测快速滑动（速度阈值）
    var isFastScrolling by remember { mutableStateOf(false) }
    var lastPosition by remember { mutableStateOf(0f) }
    var lastTime by remember { mutableStateOf(0L) }

    // 监控滚动速度 + 检测是否到达末尾（firstVisibleItemIndex 可能在最后页不更新）
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            isFastScrolling = false
            lastTime = 0L
            // 滚动停止时检查最后可见项是否已是最后一页
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            if (lastVisibleIndex != null && lastVisibleIndex == images.size - 1) {
                if (lastVisibleIndex > listState.firstVisibleItemIndex) {
                    Log.d("ReaderScreen", "[V到达末尾] 强制设为第 ${images.size} 页 (fvi=${listState.firstVisibleItemIndex})")
                    onPageChanged(lastVisibleIndex)
                }
            }
            return@LaunchedEffect
        }

        while (listState.isScrollInProgress) {
            val currentPosition = listState.firstVisibleItemIndex * 10000f + listState.firstVisibleItemScrollOffset
            val currentTime = System.currentTimeMillis()
            val timeDelta = currentTime - lastTime

            if (timeDelta > 0 && lastTime > 0) {
                val velocity = kotlin.math.abs(currentPosition - lastPosition) / timeDelta * 1000
                // 阈值：每秒滚动超过 2000 单位（约 20% 屏幕高度）
                isFastScrolling = velocity > 2000
            }

            lastPosition = currentPosition
            lastTime = currentTime
            delay(50) // 每 50ms 检测一次
        }
    }

    // 页码变化时回调（等待初始滚动完成）
    LaunchedEffect(listState.firstVisibleItemIndex) {
        initialScrollDone.await()
        Log.d("ReaderScreen", "[V页码变化] firstVisible=${listState.firstVisibleItemIndex}")
        onPageChanged(listState.firstVisibleItemIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    viewModel = viewModel,
                    fixedHeight = pageHeights[index] ?: defaultHeight
                )
            }
        }

        // 右侧进度条
        SideProgressBar(
            totalPages = images.size,
            currentPage = listState.firstVisibleItemIndex,
            onPageJump = { newPage ->
                // 取消之前的滚动任务，避免并发滚动
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    listState.scrollToItem(newPage)
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd),
            isScrolling = isFastScrolling
        )
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
    viewModel: ComicReaderViewModel,
    fixedHeight: Dp
) {
    val context = LocalContext.current
    val cacheFile = remember(comicId, chapter, pageIndex) {
        File(context.cacheDir, "comic_pages/${comicId}_${chapter}_${pageIndex}")
    }
    var fileReady by remember { mutableStateOf(cacheFile.exists()) }

    // 日志：当前页面渲染的图片
    LaunchedEffect(fileReady) {
        if (fileReady) {
            Log.d("ReaderScreen", "[V渲染] index=$pageIndex, image=$imagePath")
        }
    }

    // 仅在缓存不存在时下载（多数情况下在 ReaderScreen 中已预缓存）
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
            .height(fixedHeight)
            .graphicsLayer { clip = true }
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
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
        // 调试：左上角显示当前页序号
        Text(
            "${pageIndex + 1}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
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

/**
 * 右侧隐藏式进度条
 * - 滑动时显示（30%透明度）
 * - 停止滑动3秒后隐藏
 * - 点击时显示100%透明度
 * - 拖动可快速跳转
 */
@Composable
fun SideProgressBar(
    totalPages: Int,
    currentPage: Int,
    onPageJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    var isVisible by remember { mutableStateOf(false) }
    var isFullOpacity by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    // dragPosition: 进度条上的绝对位置（像素），用于小球定位
    var dragPositionPx by remember { mutableFloatStateOf(0f) }
    // 上次触发跳转的页码，用于避免重复滚动
    var lastJumpPage by remember { mutableIntStateOf(-1) }

    // 当前实际进度（非拖动时跟随页码）
    val currentProgress = currentPage.toFloat() / (totalPages - 1).coerceAtLeast(1)

    // 监听页码变化或滚动状态，显示进度条
    LaunchedEffect(currentPage, isScrolling) {
        if (isScrolling || isVisible) {
            isVisible = true
            isFullOpacity = false
            // 3秒后隐藏
            delay(3000)
            if (!isDragging && !isScrolling) {
                isVisible = false
            }
        }
    }

    // 小球的显示进度：拖动时用拖动位置，否则跟随页码
    val displayProgress = if (isDragging) {
        (dragPositionPx / barHeightPx.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    } else {
        currentProgress
    }

    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 40.dp)
            .onSizeChanged { barHeightPx = it.height }
            .alpha(if (isVisible) (if (isFullOpacity) 1f else 0.5f) else 0f)
            .pointerInput(isVisible, totalPages) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val pos = change.position

                        when {
                            // 手指按下
                            change.pressed && !change.previousPressed -> {
                                isDragging = true
                                isFullOpacity = true
                                dragPositionPx = pos.y
                                val progress = (pos.y / barHeightPx.toFloat()).coerceIn(0f, 1f)
                                val newPage = (progress * (totalPages - 1)).toInt()
                                Log.d("SideProgressBar", "按下: pos=${pos.y}, progress=$progress, page=$newPage")
                                lastJumpPage = newPage
                                onPageJump(newPage)
                                change.consume()
                            }
                            // 手指拖动（页码变化时触发）
                            isDragging && change.pressed -> {
                                dragPositionPx = pos.y.coerceIn(0f, barHeightPx.toFloat())
                                val progress = (dragPositionPx / barHeightPx.toFloat()).coerceIn(0f, 1f)
                                val newPage = (progress * (totalPages - 1)).toInt()
                                // 只在页码变化时触发
                                if (newPage != lastJumpPage) {
                                    lastJumpPage = newPage
                                    onPageJump(newPage)
                                }
                                change.consume()
                            }
                            // 手指抬起
                            isDragging && !change.pressed -> {
                                isDragging = false
                                isFullOpacity = false
                                change.consume()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // 胶囊形进度指示器（无滑轨，带边框）
        val density = LocalDensity.current
        val capsuleHeightPx = with(density) { 64.dp.toPx() }
        // 限制偏移范围，确保指示器始终完整显示
        val rawOffsetPx = barHeightPx * displayProgress - capsuleHeightPx / 2
        val clampedOffsetPx = rawOffsetPx.coerceIn(0f, (barHeightPx - capsuleHeightPx).coerceAtLeast(0f))
        val capsuleOffsetY = with(density) { clampedOffsetPx.toDp() }
        Box(
            modifier = Modifier
                .offset(y = capsuleOffsetY)
                .width(14.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(7.dp)
                )
                .border(
                    width = 2.dp,
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(7.dp)
                )
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(7.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
        )

        // 页码文字（拖动时显示）
        if (isDragging) {
            val pageNum = (displayProgress * (totalPages - 1)).toInt() + 1
            Text(
                text = "$pageNum / $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
