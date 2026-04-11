package com.filemanager.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filemanager.app.data.RemoteFile
import com.filemanager.app.ui.viewmodel.FileViewModel
import com.filemanager.app.util.FileTypeIcons
import com.filemanager.app.util.formatDate
import com.filemanager.app.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: FileViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.fileListState.collectAsState()
    val context = LocalContext.current
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showUsageDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<RemoteFile?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<RemoteFile?>(null) }

    // File picker launcher for upload
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                // 使用 contentResolver 获取更可靠的文件信息
                val contentResolver = context.contentResolver
                
                // 尝试从 Cursor 获取显示名称（更可靠）
                var fileName: String? = null
                contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                
                // 如果没拿到，使用 lastPathSegment 作为后备
                if (fileName == null) {
                    fileName = selectedUri.lastPathSegment?.substringAfterLast('/')
                        ?.substringAfterLast(':')  // 处理一些特殊 URI 格式
                }
                
                // 确保有文件名
                val finalFileName = fileName ?: "upload_file_${System.currentTimeMillis()}"
                
                android.util.Log.d("FileListScreen", "Uploading file: $finalFileName from URI: $selectedUri")
                
                // 复制到临时文件
                val tempFile = java.io.File(context.cacheDir, finalFileName)
                contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw Exception("无法打开选择的文件")
                
                android.util.Log.d("FileListScreen", "Temp file created: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                
                viewModel.uploadFile(tempFile, context)
            } catch (e: Exception) {
                android.util.Log.e("FileListScreen", "File pick error", e)
                android.widget.Toast.makeText(context, "选择文件失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { uploadLauncher.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Upload, contentDescription = "上传文件")
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.connection?.name ?: "文件管理",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showUsageDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "使用说明")
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                    }
                    IconButton(onClick = { state.connection?.let { viewModel.connectTo(it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Path navigation bar
            PathNavigationBar(
                segments = state.pathSegments,
                onNavigate = { path -> viewModel.listDirectory(path) }
            )

            // Error message
            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }

            // Loading indicator
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // File list
            if (state.files.isEmpty() && !state.isLoading) {
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("空文件夹", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(state.files) { file ->
                        FileListItem(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    val newPath = if (state.currentPath.isEmpty()) file.name
                                    else "${state.currentPath}/${file.name}"
                                    viewModel.listDirectory(newPath)
                                }
                            },
                            onLongClick = { selectedItem = file }
                        )
                    }
                }
            }
        }
    }

    // Context menu
    selectedItem?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(file.name) },
            text = {
                Column {
                    if (!file.isDirectory) {
                        TextButton(
                            onClick = {
                                val f = selectedItem
                                selectedItem = null
                                if (f != null) viewModel.downloadAndOpenFile(f, viewModel.getApplication())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("打开/下载")
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            pendingFile = selectedItem
                            selectedItem = null
                            showRenameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("重命名")
                        }
                    }
                    TextButton(
                        onClick = {
                            pendingFile = selectedItem
                            selectedItem = null
                            showDeleteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedItem = null }) { Text("取消") }
            }
        )
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName.trim())
                            showCreateFolderDialog = false
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("取消") }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        val file = pendingFile
        var newName by remember { mutableStateOf(file?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && file != null) {
                            viewModel.renameItem(file, newName.trim())
                            showRenameDialog = false
                            pendingFile = null
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; pendingFile = null }) { Text("取消") }
            }
        )
    }

    // Delete confirm dialog
    if (showDeleteDialog) {
        val file = pendingFile
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${file?.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        file?.let { viewModel.deleteItem(it) }
                        showDeleteDialog = false
                        pendingFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; pendingFile = null }) { Text("取消") }
            }
        )
    }

    // Usage dialog
    if (showUsageDialog) {
        AlertDialog(
            onDismissRequest = { showUsageDialog = false },
            title = { Text("使用说明") },
            text = {
                Text(
                    "• 点击文件夹进入子目录\n" +
                    "• 点击路径栏中的节点快速跳转\n" +
                    "• 长按文件/文件夹打开操作菜单\n" +
                    "• 点击右上角文件夹图标新建文件夹\n" +
                    "• 点击刷新按钮重新加载当前目录"
                )
            },
            confirmButton = {
                TextButton(onClick = { showUsageDialog = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
fun PathNavigationBar(
    segments: List<Pair<String, String>>,
    onNavigate: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (name, path) ->
            if (index > 0) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            TextButton(
                onClick = { onNavigate(path) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == segments.size - 1) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: RemoteFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = FileTypeIcons.getIcon(file.name, file.isDirectory),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    if (!file.isDirectory) {
                        Text(
                            formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    val dateStr = formatDate(file.lastModified)
                    if (dateStr.isNotEmpty()) {
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            if (file.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
