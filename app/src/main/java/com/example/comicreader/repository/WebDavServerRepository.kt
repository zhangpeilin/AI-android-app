package com.example.comicreader.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.comicreader.model.WebDavServerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebDAV 服务器配置存储
 * 使用 SharedPreferences + JSON 序列化
 */
class WebDavServerRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "WebDavServerRepo"
        private const val PREFS_NAME = "webdav_servers"
        private const val KEY_SERVERS = "servers"

        @Volatile
        private var instance: WebDavServerRepository? = null

        fun getInstance(context: Context): WebDavServerRepository {
            return instance ?: synchronized(this) {
                instance ?: WebDavServerRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取所有已保存的服务器配置
     */
    fun getServers(): List<WebDavServerConfig> {
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        Log.d(TAG, "getServers: found saved servers")
        return parseServers(json)
    }

    /**
     * 添加服务器配置
     */
    fun addServer(config: WebDavServerConfig) {
        Log.d(TAG, "addServer: id=${config.id}, name=${config.name}, url=${config.url}")
        val servers = getServers().toMutableList()
        servers.add(config)
        saveServers(servers)
    }

    /**
     * 更新服务器配置
     */
    fun updateServer(config: WebDavServerConfig) {
        Log.d(TAG, "updateServer: id=${config.id}, name=${config.name}")
        val servers = getServers().toMutableList()
        val index = servers.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            servers[index] = config
            saveServers(servers)
        }
    }

    /**
     * 删除服务器配置
     */
    fun removeServer(serverId: String) {
        Log.d(TAG, "removeServer: id=$serverId")
        val servers = getServers().toMutableList()
        servers.removeAll { it.id == serverId }
        saveServers(servers)
    }

    /**
     * 根据 ID 获取服务器配置
     */
    fun getServer(serverId: String): WebDavServerConfig? {
        return getServers().find { it.id == serverId }
    }

    private fun saveServers(servers: List<WebDavServerConfig>) {
        val jsonArray = JSONArray()
        servers.forEach { config ->
            jsonArray.put(JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("url", config.url)
                put("username", config.username)
                put("password", config.password)
            })
        }
        prefs.edit().putString(KEY_SERVERS, jsonArray.toString()).apply()
        Log.d(TAG, "saveServers: saved ${servers.size} servers")
    }

    private fun parseServers(json: String): List<WebDavServerConfig> {
        return try {
            val jsonArray = JSONArray(json)
            val servers = mutableListOf<WebDavServerConfig>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                servers.add(WebDavServerConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.getString("url"),
                    username = obj.getString("username"),
                    password = obj.getString("password")
                ))
            }
            Log.d(TAG, "parseServers: parsed ${servers.size} servers")
            servers
        } catch (e: Exception) {
            Log.e(TAG, "parseServers: 解析失败", e)
            emptyList()
        }
    }
}
