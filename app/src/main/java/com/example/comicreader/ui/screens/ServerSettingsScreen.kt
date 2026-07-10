package com.example.comicreader.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.comicreader.model.WebDavServerConfig
import com.example.comicreader.viewmodel.ServerSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    viewModel: ServerSettingsViewModel,
    onBackClick: () -> Unit,
    onServerClick: (String) -> Unit
) {
    val servers by viewModel.servers.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<WebDavServerConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebDAV 服务器", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Folder, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加服务器")
            }
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "点击右下角按钮添加 WebDAV 服务器",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(servers) { server ->
                    ServerItem(
                        server = server,
                        onClick = { onServerClick(server.id) },
                        onDelete = { viewModel.removeServer(server.id) },
                        onTest = { viewModel.testConnection(server) },
                        onEdit = { editingServer = server }
                    )
                }
            }
        }

        // 测试连接结果提示
        if (testResult != null) {
            LaunchedEffect(testResult) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearTestResult()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(testResult ?: "")
                }
            }
        }
    }

    // 添加服务器对话框
    if (showAddDialog) {
        ServerDialog(
            title = "添加 WebDAV 服务器",
            confirmText = "添加",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, username, password ->
                viewModel.addServer(name, url, username, password)
                showAddDialog = false
            }
        )
    }

    // 编辑服务器对话框
    editingServer?.let { server ->
        ServerDialog(
            title = "编辑 WebDAV 服务器",
            confirmText = "保存",
            initialName = server.name,
            initialUrl = server.url,
            initialUsername = server.username,
            initialPassword = server.password,
            onDismiss = { editingServer = null },
            onConfirm = { name, url, username, password ->
                viewModel.updateServer(server.id, name, url, username, password)
                editingServer = null
            }
        )
    }
}

@Composable
fun ServerItem(
    server: WebDavServerConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(server.name, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(server.url, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onTest) {
                    Icon(Icons.Default.Check, contentDescription = "测试连接")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun ServerDialog(
    title: String,
    confirmText: String,
    initialName: String = "",
    initialUrl: String = "",
    initialUsername: String = "",
    initialPassword: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, username: String, password: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("如：我的NAS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("如：https://example.com/dav/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(name, url, username, password)
                    }
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
