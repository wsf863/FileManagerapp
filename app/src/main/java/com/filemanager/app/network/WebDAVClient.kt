package com.filemanager.app.network

import android.util.Log
import android.util.Xml
import com.filemanager.app.data.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class WebDAVClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    companion object {
        private const val TAG = "WebDAVClient"
        private fun ln(name: String?): String {
            val n = name ?: ""
            val i = n.indexOf(':')
            return if (i >= 0) n.substring(i + 1) else n
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)      // 大文件下载/上传需要更长的超时
        .writeTimeout(300, TimeUnit.SECONDS)    // PUT 请求写入超时
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    private fun authHeader(): String = Credentials.basic(username, password)

    /** 从完整 URL 提取解码后的路径 */
    private fun decodedPath(fullUrl: String): String {
        return try {
            java.net.URI(fullUrl).path.trimEnd('/')
        } catch (_: Exception) {
            val raw = fullUrl.removePrefix("http://").removePrefix("https://")
            val idx = raw.indexOf('/')
            if (idx >= 0) {
                try { URLDecoder.decode(raw.substring(idx), "UTF-8").trimEnd('/') }
                catch (_: Exception) { raw.substring(idx).trimEnd('/') }
            } else ""
        }
    }

    /** 用 URI 构造正确的 URL（自动编码中文等特殊字符），路径末尾不带斜杠 */
    private fun buildUrl(path: String): String {
        val trimmed = path.trimStart('/').trimEnd('/')
        val full = if (trimmed.isEmpty()) baseUrl.trimEnd('/') else "$baseUrl$trimmed"
        return try {
            val uri = java.net.URI(full)
            uri.toASCIIString()
        } catch (_: Exception) { full }
    }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                return@withContext Result.failure(Exception("无效地址: 必须以 http:// 或 https:// 开头"))
            }
            val request = Request.Builder()
                .url(buildUrl(""))
                .method("PROPFIND", propfindBody())
                .header("Authorization", authHeader())
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                val code = response.code
                val davHeader = response.header("DAV")
                val isWebDAV = code == 207 || (code == 200 && davHeader != null)
                if (isWebDAV) {
                    Result.success(Unit)
                } else if (code == 401) {
                    Result.failure(Exception("认证失败，请检查用户名和密码"))
                } else if (code in 200..299) {
                    Result.failure(Exception("服务器不支持 WebDAV 协议 (HTTP $code)"))
                } else {
                    Result.failure(Exception("HTTP $code: ${response.message}"))
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法解析服务器地址"))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("无法连接服务器，请检查地址和端口"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时"))
        } catch (e: Exception) {
            Result.failure(Exception("连接错误: ${e.message}"))
        }
    }

    suspend fun listDirectory(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindBody())
                .header("Authorization", authHeader())
                .header("Depth", "1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val xml = response.body?.string() ?: ""
                // 解码的请求路径，用于过滤自身引用
                val reqPath = decodedPath(url)
                Log.d(TAG, "PROPFIND $url -> path='$reqPath'")
                val files = parseXml(xml, reqPath)
                Result.success(files)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path).trimEnd('/') + "/"
            val request = Request.Builder().url(url).method("MKCOL", null)
                .header("Authorization", authHeader()).build()
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful || r.code == 201) Result.success(Unit)
                else Result.failure(Exception("HTTP ${r.code}"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(buildUrl(path)).delete()
                .header("Authorization", authHeader()).build()
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful || r.code == 204) Result.success(Unit)
                else Result.failure(Exception("HTTP ${r.code}"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun move(sourcePath: String, destPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(buildUrl(sourcePath))
                .method("MOVE", null)
                .header("Authorization", authHeader())
                .header("Destination", buildUrl(destPath))
                .header("Overwrite", "T")
                .build()
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful || r.code in 201..204) Result.success(Unit)
                else Result.failure(Exception("HTTP ${r.code}"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun downloadFile(remotePath: String, localFile: java.io.File): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 标准化路径
            val cleanRemotePath = remotePath.trimStart('/').trimEnd('/')
            val url = buildUrl(cleanRemotePath)
            Log.d(TAG, "Download: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", authHeader())
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("空响应"))
                
                // 分块读取并写入本地文件，大文件不撑爆内存 (256KB buffer)
                java.io.FileOutputStream(localFile).buffered(262144).use { bufferedOut ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(262144)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            bufferedOut.write(buffer, 0, read)
                            bytesCopied += read
                        }
                        bufferedOut.flush()
                        Log.d(TAG, "Downloaded $bytesCopied bytes")
                    }
                }
                Result.success(localFile.length())
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Download timeout", e)
            Result.failure(Exception("下载超时，请检查网络连接或文件是否过大"))
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    /** 下载文件到指定的 OutputStream（用于写入到 content:// URI） */
    suspend fun downloadFileToStream(remotePath: String, outputStream: java.io.OutputStream): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val cleanRemotePath = remotePath.trimStart('/').trimEnd('/')
            val url = buildUrl(cleanRemotePath)
            Log.d(TAG, "Download to stream: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", authHeader())
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("空响应"))
                
                val contentLength = body.contentLength()
                outputStream.buffered(262144).use { bufferedOut ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(262144)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            bufferedOut.write(buffer, 0, read)
                            bytesCopied += read
                        }
                        bufferedOut.flush()
                        Log.d(TAG, "Downloaded $bytesCopied bytes (contentLength=$contentLength)")
                    }
                }
                Result.success(if (contentLength > 0) contentLength else -1)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Download timeout", e)
            Result.failure(Exception("下载超时，请检查网络连接或文件是否过大"))
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    suspend fun uploadFile(localFile: java.io.File, remotePath: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 标准化路径：移除首尾斜杠，避免双斜杠
            val cleanRemotePath = remotePath.trimStart('/').trimEnd('/')
            val url = buildUrl(cleanRemotePath)
            val fileSize = localFile.length()
            Log.d(TAG, "Upload: ${localFile.absolutePath} -> $url (file size: $fileSize bytes)")
            
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(localFile.name.substringAfterLast('.', ""))
                ?: "application/octet-stream"
            
            // 使用自定义 RequestBody 流式上传，避免整个文件加载内存
            // 不设置 contentLength，让 OkHttp 自动分块发送（chunked transfer encoding）
            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                // 不Override contentLength()，让 OkHttp 自动处理
                override fun writeTo(sink: okio.BufferedSink) {
                    // 使用 256KB buffer 提高网络利用率
                    val buffer = ByteArray(262144)
                    java.io.FileInputStream(localFile).use { fis ->
                        var bytesRead: Int
                        var totalWritten = 0L
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            sink.write(buffer, 0, bytesRead)
                            totalWritten += bytesRead
                            // 每写入 1MB 刷新一次，保持连接活跃
                            if (totalWritten % (1024 * 1024) < buffer.size) {
                                sink.flush()
                            }
                        }
                        sink.flush()
                        Log.d(TAG, "Total written to sink: $totalWritten bytes")
                    }
                }
            }
            
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", authHeader())
                .header("Content-Type", "$mimeType; charset=utf-8")
                .build()
            
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 201) {
                    Log.d(TAG, "Upload success: HTTP ${response.code}")
                    Result.success(fileSize)
                } else {
                    Log.e(TAG, "Upload failed: HTTP ${response.code}, body: $responseBody")
                    Result.failure(Exception("上传失败: HTTP ${response.code}"))
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Upload timeout", e)
            Result.failure(Exception("上传超时，请检查网络连接或文件是否过大"))
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            Result.failure(Exception("上传失败: ${e.message}"))
        }
    }

    private fun propfindBody(): RequestBody {
        val xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<D:propfind xmlns:D=\"DAV:\"><D:prop>" +
            "<D:displayname/><D:getcontentlength/><D:getlastmodified/>" +
            "<D:resourcetype/><D:getcontenttype/>" +
            "</D:prop></D:propfind>"
        return xml.toRequestBody("application/xml".toMediaType())
    }

    /**
     * 解析 PROPFIND XML，用解码路径比较过滤自身引用。
     */
    private fun parseXml(xml: String, decodedReqPath: String): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var displayName = ""
            var contentLength = 0L
            var lastModified = 0L
            var isDirectory = false
            var href = ""
            var inResponse = false
            var currentTag = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = ln(parser.name)
                        currentTag = name
                        when (name) {
                            "response" -> {
                                inResponse = true
                                displayName = ""; contentLength = 0L; lastModified = 0L
                                isDirectory = false; href = ""
                            }
                            "collection" -> isDirectory = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inResponse) {
                            val text = parser.text?.trim() ?: ""
                            when (currentTag) {
                                "href" -> if (text.isNotEmpty()) href = text
                                "displayname" -> if (text.isNotEmpty()) displayName = text
                                "getcontentlength" -> contentLength = text.toLongOrNull() ?: 0L
                                "getlastmodified" -> lastModified = parseDate(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (ln(parser.name) == "response" && inResponse) {
                            inResponse = false
                            if (href.isNotEmpty()) {
                                // 解码 href 后与解码的请求路径比较
                                val decodedHref = try {
                                    URLDecoder.decode(href, "UTF-8")
                                } catch (_: Exception) { href }
                                val cleanHref = decodedHref.trimEnd('/')
                                val isEmpty = cleanHref.isEmpty()
                                val isSelfRef = cleanHref == decodedReqPath

                                if (!isEmpty && !isSelfRef) {
                                    val name = if (displayName.isNotEmpty()) displayName
                                        else cleanHref.substringAfterLast('/')
                                    // 过滤 . 和 .. 条目，以及空名称
                                    if (name.isNotEmpty() && name != "." && name != "..") {
                                        files.add(RemoteFile(name, decodedHref, isDirectory, contentLength, lastModified))
                                    }
                                }
                            }
                        }
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "XML parse error", e)
        }
        return files
    }

    private fun parseDate(s: String): Long {
        if (s.isEmpty()) return 0L
        for (fmt in listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )) {
            try { return SimpleDateFormat(fmt, Locale.US).parse(s)?.time ?: 0L } catch (_: Exception) {}
        }
        return 0L
    }
}
