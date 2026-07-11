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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.comicreader.viewmodel.ComicReaderViewModel
import android.util.Log
import kotlinx.coroutines.Dispatchers
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

    // 阅读进度状态
    var savedProgress by remember { mutableStateOf<Triple<String, Int, Long>?>(null) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var shouldRestoreProgress by remember { mutableStateOf(false) }

    // 检查是否有保存的阅读进度
    LaunchedEffect(comicId) {
        savedProgress = viewModel.getReadingProgress(comicId)
        if (savedProgress != null) {
            showProgressDialog = true
        }
    }

    // 阅读模式状态（默认垂直模式）
    var readingMode by remember { mutableStateOf(ReadingMode.VERTICAL) }
    // 当前页码（切换模式时保持）
    var currentPageIndex by remember { mutableIntStateOf(0) }
    // 顶部栏是否显示
    var showTopBar by remember { mutableStateOf(true) }

    LaunchedEffect(comicId, chapter) {
        viewModel.loadImages(comicId, chapter)
    }

    // 当用户选择继续时，跳转到保存的页码
    LaunchedEffect(shouldRestoreProgress, savedProgress, images) {
        if (shouldRestoreProgress && savedProgress != null && images.isNotEmpty()) {
            val targetPage = savedProgress!!.second.coerceIn(0, images.size - 1)
            currentPageIndex = targetPage
            shouldRestoreProgress = false
        }
    }

    // 保存阅读进度
    LaunchedEffect(currentPageIndex) {
        if (images.isNotEmpty()) {
            viewModel.saveReadingProgress(comicId, chapter, currentPageIndex)
        }
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

    // 阅读进度选择对话框
    if (showProgressDialog && savedProgress != null) {
        val progress = savedProgress!!
        AlertDialog(
            onDismissRequest = {
                showProgressDialog = false
                // 关闭对话框默认从头开始
            },
            title = { Text("继续阅读") },
            text = {
                Text("上次读到第 ${progress.first} 话，第 ${progress.second + 1} 页\n是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showProgressDialog = false
                    shouldRestoreProgress = true
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProgressDialog = false
                    // 从头开始，不设置 shouldRestoreProgress
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
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { images.size })
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 监听 initialPage 变化，跳转到指定页面
    LaunchedEffect(initialPage) {
        if (initialPage > 0 && initialPage != pagerState.currentPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

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
    viewModel: ComicReaderViewModel,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 监听 initialPage 变化，跳转到指定页面
    LaunchedEffect(initialPage) {
        if (initialPage > 0 && initialPage != listState.firstVisibleItemIndex) {
            listState.scrollToItem(initialPage)
        }
    }

    // 跟踪上次滚动到的页面，避免重复滚动
    var lastScrolledPage by remember { mutableIntStateOf(-1) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 检测快速滑动（速度阈值）
    var isFastScrolling by remember { mutableStateOf(false) }
    var lastPosition by remember { mutableStateOf(0f) }
    var lastTime by remember { mutableStateOf(0L) }

    // 监控滚动速度
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            isFastScrolling = false
            lastTime = 0L
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
                    viewModel = viewModel
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
                    Log.d("SideProgressBar", "开始滚动: page=$newPage, 当前firstVisible=${listState.firstVisibleItemIndex}")
                    listState.scrollToItem(newPage)
                    Log.d("SideProgressBar", "滚动完成: firstVisible=${listState.firstVisibleItemIndex}")
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
                                    Log.d("SideProgressBar", "拖动: pos=${dragPositionPx}, progress=$progress, page=$newPage")
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
