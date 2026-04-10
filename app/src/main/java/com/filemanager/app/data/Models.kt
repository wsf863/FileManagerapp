package com.filemanager.app.data

import java.io.Serializable

enum class Protocol { WEBDAV, SMB3 }

data class ServerConnection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val protocol: Protocol,
    val address: String,      // WebDAV: URL, SMB3: IP/hostname
    val username: String,
    val password: String,
    val shareName: String = "", // SMB3 only
    val port: Int = 0           // SMB3: 0=默认445, 其他=指定端口
) : Serializable

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String = ""
)
