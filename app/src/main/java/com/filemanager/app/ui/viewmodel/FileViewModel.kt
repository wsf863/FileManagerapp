package com.filemanager.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.ConnectionStorage
import com.filemanager.app.data.Protocol
import com.filemanager.app.data.RemoteFile
import com.filemanager.app.data.ServerConnection
import com.filemanager.app.network.SMB3Client
import com.filemanager.app.network.WebDAVClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val connections: List<ServerConnection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class FileListUiState(
    val files: List<RemoteFile> = emptyList(),
    val currentPath: String = "",
    val pathSegments: List<Pair<String, String>> = emptyList(), // (name, fullPath)
    val isLoading: Boolean = false,
    val error: String? = null,
    val connection: ServerConnection? = null
)

class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = ConnectionStorage(application)

    private val _connectionState = MutableStateFlow(ConnectionUiState())
    val connectionState: StateFlow<ConnectionUiState> = _connectionState

    private val _fileListState = MutableStateFlow(FileListUiState())
    val fileListState: StateFlow<FileListUiState> = _fileListState

    private var webdavClient: WebDAVClient? = null
    private var smbClient: SMB3Client? = null

    init {
        loadConnections()
    }

    fun loadConnections() {
        val connections = storage.loadConnections()
        _connectionState.value = _connectionState.value.copy(connections = connections)
    }

    fun addConnection(connection: ServerConnection) {
        val current = _connectionState.value.connections.toMutableList()
        current.add(connection)
        storage.saveConnections(current)
        _connectionState.value = _connectionState.value.copy(connections = current)
    }

    fun updateConnection(connection: ServerConnection) {
        val current = _connectionState.value.connections.toMutableList()
        val index = current.indexOfFirst { it.id == connection.id }
        if (index >= 0) {
            current[index] = connection
            storage.saveConnections(current)
            _connectionState.value = _connectionState.value.copy(connections = current)
        }
    }

    fun deleteConnection(connection: ServerConnection) {
        val current = _connectionState.value.connections.toMutableList()
        current.removeAll { it.id == connection.id }
        storage.saveConnections(current)
        _connectionState.value = _connectionState.value.copy(connections = current)
    }

    fun connectTo(connection: ServerConnection) {
        _connectionState.value = _connectionState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val result = when (connection.protocol) {
                    Protocol.WEBDAV -> {
                        // 验证 URL 格式
                        val addr = connection.address.trim()
                        if (!addr.startsWith("http://") && !addr.startsWith("https://")) {
                            Result.failure(Exception("地址必须以 http:// 或 https:// 开头"))
                        } else {
                            webdavClient = WebDAVClient(addr, connection.username, connection.password)
                            smbClient = null
                            webdavClient!!.testConnection()
                        }
                    }
                    Protocol.SMB3 -> {
                        smbClient = SMB3Client(connection.address, connection.shareName, connection.username, connection.password, connection.port)
                        webdavClient = null
                        smbClient!!.testConnection()
                    }
                }
                result.fold(
                    onSuccess = {
                        _connectionState.value = _connectionState.value.copy(
                            isLoading = false,
                            successMessage = "连接成功"
                        )
                        // 只在真正成功时才设置 connection，触发导航
                        _fileListState.value = FileListUiState(connection = connection)
                        listDirectory("")
                    },
                    onFailure = { e ->
                        webdavClient = null
                        smbClient = null
                        _connectionState.value = _connectionState.value.copy(
                            isLoading = false,
                            error = "连接失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                webdavClient = null
                smbClient = null
                _connectionState.value = _connectionState.value.copy(
                    isLoading = false,
                    error = "连接失败: ${e.message}"
                )
            }
        }
    }

    fun disconnect() {
        webdavClient = null
        smbClient = null
        _fileListState.value = FileListUiState()
        _connectionState.value = _connectionState.value.copy(
            isLoading = false,
            error = null,
            successMessage = null
        )
    }

    fun listDirectory(path: String) {
        val conn = _fileListState.value.connection ?: return
        _fileListState.value = _fileListState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = when (conn.protocol) {
                Protocol.WEBDAV -> webdavClient?.listDirectory(path) ?: Result.failure(Exception("未连接"))
                Protocol.SMB3 -> smbClient?.listDirectory(path) ?: Result.failure(Exception("未连接"))
            }
            result.fold(
                onSuccess = { files ->
                    val segments = buildPathSegments(path)
                    _fileListState.value = _fileListState.value.copy(
                        files = files,
                        currentPath = path,
                        pathSegments = segments,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _fileListState.value = _fileListState.value.copy(
                        isLoading = false,
                        error = "加载失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun createFolder(folderName: String) {
        val conn = _fileListState.value.connection ?: return
        val currentPath = _fileListState.value.currentPath
        val fullPath = if (currentPath.isEmpty()) folderName else "$currentPath/$folderName"
        _fileListState.value = _fileListState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = when (conn.protocol) {
                Protocol.WEBDAV -> webdavClient?.createDirectory(fullPath) ?: Result.failure(Exception("未连接"))
                Protocol.SMB3 -> smbClient?.createDirectory(fullPath) ?: Result.failure(Exception("未连接"))
            }
            result.fold(
                onSuccess = { listDirectory(currentPath) },
                onFailure = { e ->
                    _fileListState.value = _fileListState.value.copy(
                        isLoading = false,
                        error = "创建失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun deleteItem(file: RemoteFile) {
        val conn = _fileListState.value.connection ?: return
        val currentPath = _fileListState.value.currentPath
        _fileListState.value = _fileListState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = when (conn.protocol) {
                Protocol.WEBDAV -> webdavClient?.delete(file.path) ?: Result.failure(Exception("未连接"))
                Protocol.SMB3 -> smbClient?.delete(file.path) ?: Result.failure(Exception("未连接"))
            }
            result.fold(
                onSuccess = { listDirectory(currentPath) },
                onFailure = { e ->
                    _fileListState.value = _fileListState.value.copy(
                        isLoading = false,
                        error = "删除失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun renameItem(file: RemoteFile, newName: String) {
        val conn = _fileListState.value.connection ?: return
        val currentPath = _fileListState.value.currentPath
        _fileListState.value = _fileListState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = when (conn.protocol) {
                Protocol.WEBDAV -> {
                    val parentPath = file.path.substringBeforeLast('/')
                    val newPath = "$parentPath/$newName"
                    webdavClient?.move(file.path, newPath)
                }
                Protocol.SMB3 -> {
                    smbClient?.rename(file.path, newName)
                }
            } ?: Result.failure(Exception("未连接"))
            result.fold(
                onSuccess = { listDirectory(currentPath) },
                onFailure = { e ->
                    _fileListState.value = _fileListState.value.copy(
                        isLoading = false,
                        error = "重命名失败: ${e.message}"
                    )
                }
            )
        }
    }

    private fun buildPathSegments(path: String): List<Pair<String, String>> {
        val segments = mutableListOf<Pair<String, String>>()
        segments.add("根目录" to "")
        if (path.isNotEmpty()) {
            val parts = path.trim('/').split('/')
            var currentPath = ""
            for (part in parts) {
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                segments.add(part to currentPath)
            }
        }
        return segments
    }

    fun clearError() {
        _connectionState.value = _connectionState.value.copy(error = null)
        _fileListState.value = _fileListState.value.copy(error = null)
    }

    fun clearSuccess() {
        _connectionState.value = _connectionState.value.copy(successMessage = null)
    }


    fun downloadAndOpenFile(file: RemoteFile, context: android.content.Context) {
        val conn = _fileListState.value.connection ?: return
        _fileListState.value = _fileListState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                // 使用更短的文件名避免路径问题，同时保证扩展名正确
                val ext = file.name.substringAfterLast('.', "")
                val baseName = if (ext.isNotEmpty() && ext != file.name) {
                    file.name.substringBeforeLast('.')
                } else {
                    file.name
                }
                val safeFileName = "${baseName}_${System.currentTimeMillis()}.$ext".trimEnd('.')
                val localFile = java.io.File(context.cacheDir, safeFileName)
                
                android.util.Log.d("FileViewModel", "downloadAndOpenFile: downloading ${file.name} to ${localFile.absolutePath}")
                
                val result = when (conn.protocol) {
                    Protocol.WEBDAV -> webdavClient?.downloadFile(file.path, localFile)
                    Protocol.SMB3 -> smbClient?.downloadFile(file.path, localFile)
                } ?: Result.failure(Exception("未连接"))
                result.fold(
                    onSuccess = { size ->
                        android.util.Log.d("FileViewModel", "Downloaded: ${localFile.length()} bytes, exists: ${localFile.exists()}")
                        
                        if (!localFile.exists() || localFile.length() == 0L) {
                            _fileListState.value = _fileListState.value.copy(
                                isLoading = false,
                                error = "下载后文件无效"
                            )
                            return@fold
                        }
                        
                        _fileListState.value = _fileListState.value.copy(isLoading = false)
                        
                        // 用 FileProvider 获取 content:// URI
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            localFile
                        )
                        android.util.Log.d("FileViewModel", "FileProvider URI: $uri")
                        
                        // 获取 MIME 类型
                        val mimeType = getMimeTypeFromExtension(ext)
                        android.util.Log.d("FileViewModel", "MIME type: $mimeType")
                        
                        // 构建打开文件的 Intent
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setData(uri)
                            if (mimeType != "*/*") {
                                setType(mimeType)
                            }
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        // 使用 createChooser 让用户选择打开应用
                        val chooserIntent = android.content.Intent.createChooser(intent, "选择打开方式")
                        chooserIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        
                        try {
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(chooserIntent)
                            } else {
                                android.widget.Toast.makeText(context, "没有找到可以打开此文件的应用", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: android.content.ActivityNotFoundException) {
                            android.util.Log.e("FileViewModel", "Activity not found", e)
                            android.widget.Toast.makeText(context, "没有找到可以打开此文件的应用", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("FileViewModel", "Error opening file", e)
                            android.widget.Toast.makeText(context, "打开失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { e ->
                        android.util.Log.e("FileViewModel", "Download failed", e)
                        _fileListState.value = _fileListState.value.copy(
                            isLoading = false,
                            error = "下载失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("FileViewModel", "downloadAndOpenFile error", e)
                _fileListState.value = _fileListState.value.copy(
                    isLoading = false,
                    error = "下载失败: ${e.message}"
                )
            }
        }
    }
    
    /** 根据文件扩展名获取 MIME 类型 */
    private fun getMimeTypeFromExtension(ext: String): String {
        if (ext.isEmpty()) return "*/*"
        val mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext.lowercase())
        return mimeType ?: "*/*"
    }

    /**
     * 下载文件到用户通过 CreateDocument 选择的位置（content:// URI）
     * 这是独立的下载按钮功能，可选择保存位置
     */
    fun downloadFileToUri(file: RemoteFile, uri: android.net.Uri, context: android.content.Context) {
        val conn = _fileListState.value.connection ?: return
        _fileListState.value = _fileListState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                // 获取 content resolver 的 OutputStream
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _fileListState.value = _fileListState.value.copy(
                        isLoading = false,
                        error = "无法打开选择的文件位置"
                    )
                    return@launch
                }
                
                val result = when (conn.protocol) {
                    Protocol.WEBDAV -> webdavClient?.downloadFileToStream(file.path, outputStream)
                    Protocol.SMB3 -> smbClient?.downloadFileToStream(file.path, outputStream)
                } ?: Result.failure(Exception("未连接"))
                
                result.fold(
                    onSuccess = { size ->
                        _fileListState.value = _fileListState.value.copy(isLoading = false)
                        android.widget.Toast.makeText(
                            context,
                            "下载成功: ${file.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { e ->
                        _fileListState.value = _fileListState.value.copy(
                            isLoading = false,
                            error = "下载失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _fileListState.value = _fileListState.value.copy(
                    isLoading = false,
                    error = "下载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 打开已下载的文件（通过 content:// URI）
     * 用于打开用户通过下载功能保存到本地的文件
     */
    fun openDownloadedFile(uri: android.net.Uri, mimeType: String, context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "没有找到可以打开此文件的应用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun uploadFile(localFile: java.io.File, context: android.content.Context) {
        val conn = _fileListState.value.connection ?: return
        val currentPath = _fileListState.value.currentPath
        _fileListState.value = _fileListState.value.copy(isLoading = true)
        viewModelScope.launch {
            val remotePath = if (currentPath.isEmpty()) localFile.name else "$currentPath/${localFile.name}"
            val result = when (conn.protocol) {
                Protocol.WEBDAV -> webdavClient?.uploadFile(localFile, remotePath)
                Protocol.SMB3 -> smbClient?.uploadFile(localFile, remotePath)
            } ?: Result.failure(Exception("未连接"))
            result.fold(
                onSuccess = {
                    _fileListState.value = _fileListState.value.copy(isLoading = false)
                    listDirectory(currentPath)
                },
                onFailure = { e ->
                    _fileListState.value = _fileListState.value.copy(
                        isLoading = false,
                        error = "上传失败: ${e.message}"
                    )
                }
            )
        }
    }
}
