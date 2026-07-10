package com.example.comicreader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.comicreader.model.Comic
import com.example.comicreader.viewmodel.ComicListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 视图模式：true = 网格，false = 列表
    var isGridView by remember { mutableStateOf(true) }

    // SAF 文件夹选择器
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val folderName = uri.lastPathSegment?.substringAfterLast(':') ?: "Selected Folder"
            viewModel.scanFolder(it, folderName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("漫画阅读器", fontWeight = FontWeight.Bold) },
                actions = {
                    // 视图切换按钮
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
                    // 网格视图
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
                                onClick = { onComicClick(comic.id, comic.title) }
                            )
                        }
                    }
                } else {
                    // 列表视图
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(comics, key = { it.id }) { comic ->
                            ComicListCard(
                                comic = comic,
                                viewModel = viewModel,
                                onClick = { onComicClick(comic.id, comic.title) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 网格漫画卡片（带封面）
 */
@Composable
fun ComicGridCard(
    comic: Comic,
    viewModel: ComicListViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var coverFile by remember { mutableStateOf<File?>(null) }

    // 加载封面
    LaunchedEffect(comic.id) {
        withContext(Dispatchers.IO) {
            coverFile = viewModel.getCoverFile(comic.id)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            }
            // 标题
            Text(
                text = comic.title,
                modifier = Modifier.padding(6.dp),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 列表漫画卡片（带封面，横向布局）
 */
@Composable
fun ComicListCard(
    comic: Comic,
    viewModel: ComicListViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var coverFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(comic.id) {
        withContext(Dispatchers.IO) {
            coverFile = viewModel.getCoverFile(comic.id)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "大小: ${formatFileSize(comic.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
