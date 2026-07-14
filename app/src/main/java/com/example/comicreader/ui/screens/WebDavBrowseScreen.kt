package com.example.comicreader.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.comicreader.model.WebDavEntry
import com.example.comicreader.viewmodel.SortMode
import com.example.comicreader.viewmodel.WebDavBrowseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavBrowseScreen(
    serverId: String,
    viewModel: WebDavBrowseViewModel,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onComicClick: (String, String) -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val error by viewModel.error.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    val listState = rememberLazyListState()
    // 排序菜单状态
    var showSortMenu by remember { mutableStateOf(false) }

    // 日志 Tag
    val tag = "WebDavBrowse"

    LaunchedEffect(serverId) {
        viewModel.init(serverId)
    }

    // 加载完成后恢复滚动位置（监听 isLoading 确保 LazyColumn 已就绪）
    LaunchedEffect(isLoading) {
        if (!isLoading && entries.isNotEmpty()) {
            val path = currentPath
            val savedPos = viewModel.savedScrollPositions[path]
            Log.d(tag, "加载完成: path=$path, savedPos=$savedPos, entries=${entries.size}, firstVisible=${listState.firstVisibleItemIndex}")
            if (savedPos != null && savedPos > 0) {
                val target = savedPos.coerceAtMost(entries.size - 1)
                Log.d(tag, "滚动到: pos=$target")
                listState.scrollToItem(target)
            }
        }
    }

    // 返回上级目录处理
    BackHandler {
        viewModel.savedScrollPositions[currentPath] = listState.firstVisibleItemIndex
        viewModel.highlightDir = viewModel.lastSubDir
        Log.d(tag, "返回上级: save=${listState.firstVisibleItemIndex}, highlight=${viewModel.highlightDir}")
        if (!viewModel.goBack()) {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "WebDAV 浏览",
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize
                            )
                            Text(
                                currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.savedScrollPositions[currentPath] = listState.firstVisibleItemIndex
                            viewModel.highlightDir = viewModel.lastSubDir
                            Log.d(tag, "返回上级(按钮): save=${listState.firstVisibleItemIndex}, highlight=${viewModel.highlightDir}")
                            if (!viewModel.goBack()) {
                                onBackClick()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 排序按钮
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("名称 A-Z") },
                                    onClick = {
                                        viewModel.setSortMode(SortMode.NAME_ASC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortMode == SortMode.NAME_ASC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("名称 Z-A") },
                                    onClick = {
                                        viewModel.setSortMode(SortMode.NAME_DESC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortMode == SortMode.NAME_DESC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("大小 ↑") },
                                    onClick = {
                                        viewModel.setSortMode(SortMode.SIZE_ASC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortMode == SortMode.SIZE_ASC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("大小 ↓") },
                                    onClick = {
                                        viewModel.setSortMode(SortMode.SIZE_DESC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortMode == SortMode.SIZE_DESC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                        IconButton(onClick = onHomeClick) {
                            Icon(Icons.Default.Home, contentDescription = "返回首页")
                        }
                    }
                )
                // 下载进度条（固定在顶部栏下方，不随内容滚动）
                if (downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.savedScrollPositions.clear()
                            viewModel.loadDirectory(currentPath)
                        }) {
                            Text("重试")
                        }
                    }
                }
                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "空目录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(entries) { entry ->
                            WebDavEntryItem(
                                entry = entry,
                                isHighlighted = entry.name == viewModel.highlightDir,
                                onClick = {
                                    if (entry.isDirectory) {
                                        viewModel.savedScrollPositions[currentPath] = listState.firstVisibleItemIndex
                                        viewModel.lastSubDir = entry.name
                                        viewModel.highlightDir = null  // 进入新目录，清除高亮
                                        Log.d(tag, "进入目录: path=$currentPath, savePos=${listState.firstVisibleItemIndex}, dir=${entry.name}")
                                        viewModel.enterDirectory(entry)
                                    } else if (entry.name.lowercase().endsWith(".zip")) {
                                        viewModel.openComic(entry) { comic ->
                                            onComicClick(comic.id, comic.title)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebDavEntryItem(
    entry: WebDavEntry,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                entry.name,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (!entry.isDirectory && entry.size > 0) {
                Text(
                    formatFileSize(entry.size),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = {
            if (entry.isDirectory) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        colors = if (isHighlighted) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
