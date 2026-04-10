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
        val portSuffix = if (port != 445) ":$port" else ""
        return if (cleanPath.isEmpty()) {
            "smb://$serverIP$portSuffix/$shareName/"
        } else {
            "smb://$serverIP$portSuffix/$shareName/$cleanPath/"
        }
    }

    private fun buildFileUrl(path: String): String {
        val cleanPath = path.trim('/')
        val portSuffix = if (port != 445) ":$port" else ""
        return "smb://$serverIP$portSuffix/$shareName/$cleanPath"
    }

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
                RemoteFile(
                    name = name,
                    path = child.url.toString().replace("smb://$serverIP/$shareName", "").trimEnd('/'),
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
            val remoteUrl = buildFileUrl(remotePath)
            android.util.Log.d("SMB3Client", "Download: $remoteUrl -> ${localFile.absolutePath}")
            val smbFile = SmbFile(remoteUrl, getContext())
            if (!smbFile.exists()) {
                smbFile.close()
                return@withContext Result.failure(Exception("文件不存在"))
            }
            val size = smbFile.length()
            localFile.outputStream().use { out ->
                smbFile.inputStream.use { input ->
                    input.copyTo(out)
                }
            }
            smbFile.close()
            android.util.Log.d("SMB3Client", "Download complete: ${localFile.length()} bytes")
            Result.success(localFile.length())
        } catch (e: Exception) {
            android.util.Log.e("SMB3Client", "Download error", e)
            Result.failure(e)
        }
    }

    suspend fun uploadFile(localFile: java.io.File, remotePath: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val remoteUrl = buildFileUrl(remotePath)
            android.util.Log.d("SMB3Client", "Upload: ${localFile.absolutePath} -> $remoteUrl")
            val smbFile = SmbFile(remoteUrl, getContext())
            localFile.inputStream().use { input ->
                smbFile.outputStream.use { out ->
                    input.copyTo(out)
                }
            }
            val size = smbFile.length()
            smbFile.close()
            android.util.Log.d("SMB3Client", "Upload complete: $size bytes")
            Result.success(size)
        } catch (e: Exception) {
            android.util.Log.e("SMB3Client", "Upload error", e)
            Result.failure(e)
        }
    }
}
