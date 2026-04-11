package com.filemanager.app.network

import com.filemanager.app.data.RemoteFile
import jcifs.CIFSContext
import jcifs.context.BaseContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class SMB3Client(
    private val serverIP: String,
    private val shareName: String,
    private val username: String,
    private val password: String,
    private val port: Int = 445
) {
    private var authContext: CIFSContext? = null

    private fun getContext(): CIFSContext {
        if (authContext == null) {
            val base = SingletonContext.getInstance()
            // 支持 DOMAIN\username 或直接 username 格式
            val domain: String?
            val user: String
            if (username.contains("\\")) {
                val parts = username.split("\\", limit = 2)
                domain = parts[0]
                user = parts[1]
            } else {
                domain = null
                user = username
            }
            val auth = NtlmPasswordAuthenticator(domain, user, password)
            authContext = base.withCredentials(auth)
        }
        return authContext!!
    }

    private fun buildUrl(path: String): String {
        val cleanPath = path.trim('/')
        // 去掉地址中的协议前缀（如 tcp://, smb:// 等）
        val cleanServer = serverIP
            .removePrefix("tcp://")
            .removePrefix("smb://")
            .removePrefix(" SMB://".lowercase())
            .trim()
        val portSuffix = if (port != 445) ":$port" else ""
        return if (cleanPath.isEmpty()) {
            "smb://$cleanServer$portSuffix/$shareName/"
        } else {
            "smb://$cleanServer$portSuffix/$shareName/$cleanPath/"
        }
    }

    /** 构建文件 URL（无尾斜杠） */
    private fun buildFileUrl(path: String): String {
        val cleanPath = path.trim('/')
        // 去掉地址中的协议前缀（如 tcp://, smb:// 等）
        val cleanServer = serverIP
            .removePrefix("tcp://")
            .removePrefix("smb://")
            .removePrefix(" SMB://".lowercase())
            .trim()
        val portSuffix = if (port != 445) ":$port" else ""
        return "smb://$cleanServer$portSuffix/$shareName/$cleanPath"
    }

    /** 构建删除 URL（无尾斜杠） */
    private fun buildDeleteUrl(path: String): String = buildFileUrl(path)

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 验证 IP 地址格式
            val ip = serverIP.trim()
            if (ip.isEmpty()) {
                return@withContext Result.failure(Exception("IP 地址不能为空"))
            }
            val url = buildUrl("")
            val domainStr: String?
            val userStr: String
            if (username.contains("\\")) {
                val parts = username.split("\\", limit = 2)
                domainStr = parts[0]
                userStr = parts[1]
            } else {
                domainStr = null
                userStr = username
            }
            android.util.Log.d("SMB3Client", "Testing: $url")
            android.util.Log.d("SMB3Client", "Auth: domain=$domainStr user=$userStr")
            val smbFile = SmbFile(url, getContext())
            val exists = smbFile.exists()
            android.util.Log.d("SMB3Client", "Share exists: $exists")
            smbFile.close()
            if (exists) Result.success(Unit)
            else Result.failure(Exception("共享 '$shareName' 不存在"))
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("SMB3Client", "DNS failed", e)
            Result.failure(Exception("无法解析服务器地址 '$serverIP'"))
        } catch (e: jcifs.smb.SmbAuthException) {
            android.util.Log.e("SMB3Client", "Auth failed: ${e.ntStatus}", e)
            Result.failure(Exception("认证失败，请检查用户名和密码 (NT: ${e.ntStatus})"))
        } catch (e: Exception) {
            android.util.Log.e("SMB3Client", "Connection error", e)
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    suspend fun listDirectory(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val smbFile = SmbFile(url, getContext())
            if (!smbFile.exists()) {
                smbFile.close()
                return@withContext Result.failure(Exception("目录不存在"))
            }
            val children = smbFile.listFiles()
            val files = children.mapNotNull { child ->
                val name = child.name.trimEnd('/')
                // 过滤 . 和 .. 目录条目，以及空名称
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                // 提取相对路径：去掉 smb://服务器/共享名/ 前缀
                val childUrl = child.url.toString()
                val cleanServer = serverIP
                    .removePrefix("tcp://")
                    .removePrefix("smb://")
                    .trim()
                val basePrefix = "smb://$cleanServer/$shareName"
                val relativePath = childUrl.substringAfter(basePrefix, childUrl).trimEnd('/')
                RemoteFile(
                    name = name,
                    path = relativePath,
                    isDirectory = child.isDirectory,
                    size = if (child.isFile) child.length() else 0L,
                    lastModified = child.lastModified
                )
            }.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
            smbFile.close()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val smbFile = SmbFile(url, getContext())
            smbFile.mkdir()
            smbFile.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = buildFileUrl(path)
            val smbFile = SmbFile(url, getContext())
            if (smbFile.isDirectory) {
                smbFile.delete()
            } else {
                smbFile.delete()
            }
            smbFile.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val oldUrl = buildFileUrl(oldPath)
            val parentPath = oldPath.substringBeforeLast('/')
            val newUrl = buildFileUrl("$parentPath/$newName")
            val oldFile = SmbFile(oldUrl, getContext())
            val newFile = SmbFile(newUrl, getContext())
            oldFile.renameTo(newFile)
            oldFile.close()
            newFile.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(remotePath: String, localFile: java.io.File): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // remotePath 是文件名（不含 currentPath），因为 buildFileUrl 已经拼接了 currentPath
            val remoteUrl = buildFileUrl(remotePath)
            android.util.Log.d("SMB3Client", "Download: $remoteUrl -> ${localFile.absolutePath}")
            
            val smbFile = SmbFile(remoteUrl, getContext())
            if (!smbFile.exists()) {
                smbFile.close()
                return@withContext Result.failure(Exception("文件不存在"))
            }
            
            val expectedSize = smbFile.length()
            android.util.Log.d("SMB3Client", "Remote file size: $expectedSize bytes")
            
            // 使用 buffered stream 提高大文件传输效率 (64KB buffer)
            localFile.outputStream().use { out ->
                smbFile.inputStream.use { input ->
                    val buffer = ByteArray(65536)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesCopied += read
                    }
                    out.flush()
                    android.util.Log.d("SMB3Client", "Downloaded $bytesCopied bytes")
                }
            }
            smbFile.close()
            
            val downloadedSize = localFile.length()
            android.util.Log.d("SMB3Client", "Download complete: $downloadedSize bytes")
            
            // 验证文件大小
            if (expectedSize > 0 && downloadedSize != expectedSize) {
                android.util.Log.w("SMB3Client", "Size mismatch: expected=$expectedSize, downloaded=$downloadedSize")
            }
            
            Result.success(downloadedSize)
        } catch (e: Exception) {
            android.util.Log.e("SMB3Client", "Download error", e)
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    /** 下载文件到指定的 OutputStream（用于写入到 content:// URI） */
    suspend fun downloadFileToStream(remotePath: String, outputStream: java.io.OutputStream): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // remotePath 是文件名（不含 currentPath），因为 buildFileUrl 已经拼接了 currentPath
            val remoteUrl = buildFileUrl(remotePath)
            android.util.Log.d("SMB3Client", "Download to stream: $remoteUrl")
            
            val smbFile = SmbFile(remoteUrl, getContext())
            if (!smbFile.exists()) {
                smbFile.close()
                return@withContext Result.failure(Exception("文件不存在"))
            }
            
            val expectedSize = smbFile.length()
            android.util.Log.d("SMB3Client", "Remote file size: $expectedSize bytes")
            
            outputStream.use { out ->
                smbFile.inputStream.use { input ->
                    val buffer = ByteArray(65536)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesCopied += read
                    }
                    out.flush()
                    android.util.Log.d("SMB3Client", "Downloaded $bytesCopied bytes")
                }
            }
            smbFile.close()
            
            Result.success(expectedSize)
        } catch (e: Exception) {
            android.util.Log.e("SMB3Client", "Download error", e)
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    suspend fun uploadFile(localFile: java.io.File, remotePath: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 标准化路径：移除首尾斜杠，避免双斜杠
            val cleanRemotePath = remotePath.trimStart('/').trimEnd('/')
            val remoteUrl = buildFileUrl(cleanRemotePath)
            android.util.Log.d("SMB3Client", "Upload: ${localFile.absolutePath} -> $remoteUrl (file size: ${localFile.length()} bytes)")
            
            val smbFile = SmbFile(remoteUrl, getContext())
            
            // 先检查文件是否存在，如果存在则先删除再创建（避免写入问题）
            if (smbFile.exists()) {
                android.util.Log.d("SMB3Client", "File exists, deleting first")
                smbFile.delete()
            }
            
            // 使用 buffered stream 提高大文件传输效率 (64KB buffer)
            localFile.inputStream().use { input ->
                smbFile.outputStream.use { out ->
                    val buffer = ByteArray(65536)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesCopied += read
                    }
                    out.flush()
                    android.util.Log.d("SMB3Client", "Wrote $bytesCopied bytes")
                }
            }
            
            // 验证上传是否成功
            smbFile.close()
            val uploadedFile = SmbFile(remoteUrl, getContext())
            if (!uploadedFile.exists()) {
                return@withContext Result.failure(Exception("上传后文件未找到"))
            }
            val size = uploadedFile.length()
            uploadedFile.close()
            
            android.util.Log.d("SMB3Client", "Upload complete: $size bytes (local: ${localFile.length()})")
            
            // 验证文件大小匹配
            if (size != localFile.length()) {
                android.util.Log.w("SMB3Client", "Size mismatch: uploaded=$size, local=${localFile.length()}")
            }
            
            Result.success(size)
        } catch (e: Exception) {
            android.util.Log.e("SMB3Client", "Upload error", e)
            Result.failure(Exception("上传失败: ${e.message}"))
        }
    }
}
