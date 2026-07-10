package com.example.comicreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comicreader.model.WebDavServerConfig
import com.example.comicreader.network.WebDavClient
import com.example.comicreader.repository.WebDavServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ServerSettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ServerSettingsVM"
    }

    private val serverRepo = WebDavServerRepository.getInstance(application)

    private val _servers = MutableStateFlow<List<WebDavServerConfig>>(emptyList())
    val servers: StateFlow<List<WebDavServerConfig>> = _servers.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        Log.d(TAG, "loadServers")
        _servers.value = serverRepo.getServers()
        Log.d(TAG, "loadServers: ${_servers.value.size} 个服务器")
    }

    fun addServer(name: String, url: String, username: String, password: String) {
        // 自动补全 http:// 前缀
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "http://$url"
        }
        Log.d(TAG, "addServer: name=$name, url=$normalizedUrl")
        val config = WebDavServerConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            url = normalizedUrl.trimEnd('/') + "/",
            username = username,
            password = password
        )
        serverRepo.addServer(config)
        loadServers()
    }

    fun updateServer(serverId: String, name: String, url: String, username: String, password: String) {
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "http://$url"
        }
        Log.d(TAG, "updateServer: id=$serverId, name=$name, url=$normalizedUrl")
        val config = WebDavServerConfig(
            id = serverId,
            name = name,
            url = normalizedUrl.trimEnd('/') + "/",
            username = username,
            password = password
        )
        serverRepo.updateServer(config)
        loadServers()
    }

    fun removeServer(serverId: String) {
        Log.d(TAG, "removeServer: id=$serverId")
        serverRepo.removeServer(serverId)
        loadServers()
    }

    fun testConnection(config: WebDavServerConfig) {
        Log.d(TAG, "testConnection: url=${config.url}")
        viewModelScope.launch(Dispatchers.IO) {
            _isTesting.value = true
            _testResult.value = null
            try {
                val client = WebDavClient()
                val success = client.testConnection(config)
                _testResult.value = if (success) "连接成功" else "连接失败"
                Log.d(TAG, "testConnection: 结果=${_testResult.value}")
            } catch (e: Exception) {
                _testResult.value = "连接失败: ${e.message}"
                Log.e(TAG, "testConnection: 异常", e)
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
