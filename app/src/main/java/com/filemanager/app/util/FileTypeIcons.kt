package com.filemanager.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.ui.graphics.vector.ImageVector

object FileTypeIcons {
    fun getIcon(fileName: String, isDirectory: Boolean): ImageVector {
        if (isDirectory) return Icons.Default.Folder

        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            // Images
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico" -> Icons.Default.Image
            // Video
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> Icons.Default.Movie
            // Audio
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" -> Icons.Default.AudioFile
            // Documents
            "pdf" -> Icons.Default.PictureAsPdf
            "doc", "docx" -> Icons.Default.Description
            "xls", "xlsx" -> Icons.Default.TableChart
            "ppt", "pptx" -> Icons.Default.Slideshow
            "txt", "md", "log" -> Icons.AutoMirrored.Filled.Article
            // Code
            "kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "c", "cpp", "h" -> Icons.Default.Code
            // Archive
            "zip", "rar", "7z", "tar", "gz", "bz2" -> Icons.Default.FolderZip
            // APK
            "apk" -> Icons.Default.Android
            // Executable
            "exe", "msi", "bat", "sh" -> Icons.Default.Terminal
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }

    fun getFileTypeName(fileName: String, isDirectory: Boolean): String {
        if (isDirectory) return "文件夹"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "图片"
            "mp4", "avi", "mkv", "mov" -> "视频"
            "mp3", "wav", "flac", "aac" -> "音频"
            "pdf" -> "PDF文档"
            "doc", "docx" -> "Word文档"
            "xls", "xlsx" -> "Excel表格"
            "ppt", "pptx" -> "演示文稿"
            "txt" -> "文本文件"
            "zip", "rar", "7z" -> "压缩包"
            "apk" -> "APK安装包"
            else -> "文件"
        }
    }
}
