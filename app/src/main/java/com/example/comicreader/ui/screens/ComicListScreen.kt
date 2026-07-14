package com.example.comicreader.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.comicreader.model.Comic
import com.example.comicreader.repository.ComicSortMode
import com.example.comicreader.viewmodel.ComicListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicListScreen(
    viewModel: ComicListViewModel,
    onComicClick: (String, String) -> Unit,
    onWebDavClick: () -> Unit
) {
    val comics by viewModel.comics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolder by viewModel.selectedFolderName.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()

    var isGridView by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuComicId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) } // null=关闭, "single_id"=单删, "all"=全删
    var deleteTargetIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // SAF 文件夹选择器
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val folderName = uri.lastPathSegment?.substringAfterLast(':') ?: "Selected Folder"
            viewModel.scanFolder(it, folderName)
        }
    }

    // 每次进入页面时刷新漫画列表
    LaunchedEffect(Unit) {
        viewModel.loadComics()
    }

    // 退出选择模式
    if (selectedIds.isNotEmpty()) {
        BackHandler {
            viewModel.clearSelection()
        }
    }

    // 删除确认对话框
    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = null
                deleteTargetIds = emptyList()
            },
            title = { Text("确认删除") },
            text = {
                Text(
                    if (deleteTargetIds.size > 1) "确定删除选中的 ${deleteTargetIds.size} 部漫画的全部本地缓存吗？"
                    else "确定删除该漫画的全部本地缓存吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteComics(deleteTargetIds)
                    showDeleteConfirmDialog = null
                    deleteTargetIds = emptyList()
                    contextMenuComicId = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = null
                    deleteTargetIds = emptyList()
                }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectedIds.isNotEmpty()) {
                // 多选模式顶部栏
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 项", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            deleteTargetIds = selectedIds.toList()
                            showDeleteConfirmDialog = "batch"
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } else {
                // 正常模式顶部栏
                TopAppBar(
                    title = { Text("漫画阅读器", fontWeight = FontWeight.Bold) },
                    actions = {
                        // 排序按钮
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                offset = DpOffset(0.dp, 0.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (sortMode == ComicSortMode.TITLE) "按文件名 ✓" else "按文件名") },
                                    onClick = { viewModel.setSortMode(ComicSortMode.TITLE); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (sortMode == ComicSortMode.LAST_READ) "按阅读时间 ✓" else "按阅读时间") },
                                    onClick = { viewModel.setSortMode(ComicSortMode.LAST_READ); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (sortMode == ComicSortMode.FILE_SIZE) "按文件大小 ✓" else "按文件大小") },
                                    onClick = { viewModel.setSortMode(ComicSortMode.FILE_SIZE); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (sortMode == ComicSortMode.PAGE_COUNT) "按页数 ✓" else "按页数") },
                                    onClick = { viewModel.setSortMode(ComicSortMode.PAGE_COUNT); showSortMenu = false }
                                )
                            }
                        }
                        // 视图切换
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                if (isGridView) Icons.Default.ViewDay else Icons.Default.ViewModule,
                                contentDescription = if (isGridView) "切换列表视图" else "切换网格视图"
                            )
                        }
                        IconButton(onClick = onWebDavClick) {
                            Icon(Icons.Default.Cloud, contentDescription = "WebDAV")
                        }
                        IconButton(onClick = { folderLauncher.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "选择文件夹")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索漫画标题...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // 已选文件夹提示
            if (selectedFolder != null) {
                Text(
                    text = "已选择: $selectedFolder",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 加载状态
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (comics.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "点击右上角选择漫画文件夹或连接 WebDAV",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(comics, key = { it.id }) { comic ->
                            ComicGridCard(
                                comic = comic,
                                viewModel = viewModel,
                                isSelected = selectedIds.contains(comic.id),
                                isSelectionMode = selectedIds.isNotEmpty(),
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        viewModel.toggleSelection(comic.id)
                                    } else {
                                        onComicClick(comic.id, comic.title)
                                    }
                                },
                                onLongClick = {
                                    contextMenuComicId = comic.id
                                },
                                showMenu = contextMenuComicId == comic.id,
                                onDismissMenu = { contextMenuComicId = null },
                                onSelect = {
                                    viewModel.toggleSelection(comic.id)
                                    contextMenuComicId = null
                                },
                                onDelete = {
                                    deleteTargetIds = listOf(comic.id)
                                    showDeleteConfirmDialog = "single"
                                    contextMenuComicId = null
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(comics, key = { it.id }) { comic ->
                            ComicListCard(
                                comic = comic,
                                viewModel = viewModel,
                                isSelected = selectedIds.contains(comic.id),
                                isSelectionMode = selectedIds.isNotEmpty(),
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        viewModel.toggleSelection(comic.id)
                                    } else {
                                        onComicClick(comic.id, comic.title)
                                    }
                                },
                                onLongClick = {
                                    contextMenuComicId = comic.id
                                },
                                showMenu = contextMenuComicId == comic.id,
                                onDismissMenu = { contextMenuComicId = null },
                                onSelect = {
                                    viewModel.toggleSelection(comic.id)
                                    contextMenuComicId = null
                                },
                                onDelete = {
                                    deleteTargetIds = listOf(comic.id)
                                    showDeleteConfirmDialog = "single"
                                    contextMenuComicId = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 网格漫画卡片（带封面 + 信息底部）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicGridCard(
    comic: Comic,
    viewModel: ComicListViewModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var coverFile by remember { mutableStateOf<File?>(null) }
    val progressText = remember(comic.id) { viewModel.getReadingProgressText(comic.id) }

    LaunchedEffect(comic.id) {
        withContext(Dispatchers.IO) {
            coverFile = viewModel.getCoverFile(comic.id)
        }
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = if (isSelected) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) else CardDefaults.cardColors()
        ) {
            Column {
                // 封面区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverFile != null && coverFile!!.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = comic.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            comic.title.take(1),
                            fontSize = 32.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // 选中覆盖层
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                    }
                }
                // 标题
                Text(
                    text = comic.title,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 信息行：大小 + 页数
                Text(
                    text = "${formatFileSize(comic.fileSize)} | ${comic.pageCount}页",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                // 阅读进度
                if (progressText != null) {
                    Text(
                        text = progressText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp).padding(bottom = 4.dp),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        // 选中标记（左上角）
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "已选" else "未选",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
            )
        }

        // 长按菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu,
            offset = DpOffset(40.dp, 0.dp)
        ) {
            DropdownMenuItem(
                text = { Text("选择") },
                onClick = onSelect
            )
            DropdownMenuItem(
                text = { Text("删除缓存", color = MaterialTheme.colorScheme.error) },
                onClick = onDelete
            )
        }
    }
}

/**
 * 列表漫画卡片（带封面 + 详细信息）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicListCard(
    comic: Comic,
    viewModel: ComicListViewModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var coverFile by remember { mutableStateOf<File?>(null) }
    val progressText = remember(comic.id) { viewModel.getReadingProgressText(comic.id) }

    LaunchedEffect(comic.id) {
        withContext(Dispatchers.IO) {
            coverFile = viewModel.getCoverFile(comic.id)
        }
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = if (isSelected) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) else CardDefaults.cardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                // 封面
                Box(
                    modifier = Modifier
                        .width(75.dp)
                        .fillMaxHeight()
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverFile != null && coverFile!!.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = comic.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            comic.title.take(1),
                            fontSize = 24.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // 选中覆盖层
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                    }
                }
                // 信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = comic.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 大小 + 页数
                    Text(
                        text = "${formatFileSize(comic.fileSize)} · ${comic.pageCount}页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 阅读进度或上次阅读时间
                    if (progressText != null) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 选中标记（左侧）
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "已选" else "未选",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
            )
        }

        // 长按菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("选择") },
                onClick = onSelect
            )
            DropdownMenuItem(
                text = { Text("删除缓存", color = MaterialTheme.colorScheme.error) },
                onClick = onDelete
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
