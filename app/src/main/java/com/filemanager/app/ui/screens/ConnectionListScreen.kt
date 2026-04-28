package com.filemanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filemanager.app.data.Protocol
import com.filemanager.app.data.ServerConnection
import com.filemanager.app.ui.viewmodel.FileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    viewModel: FileViewModel = viewModel(),
    onConnect: (ServerConnection) -> Unit
) {
    val state by viewModel.connectionState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showUsageDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<ServerConnection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程文件管理器") },
                actions = {
                    IconButton(onClick = { showUsageDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "使用说明")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加连接")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (state.connections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "暂无连接，点击右上角 + 添加",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.connections) { connection ->
                    ConnectionCard(
                        connection = connection,
                        isLoading = state.isLoading,
                        onConnect = { onConnect(connection) },
                        onDelete = { viewModel.deleteConnection(connection) },
                        onEdit = {
                            editingConnection = connection
                            showEditDialog = true
                        }
                    )
                }
            }
        }

        // Error toast
        state.error?.let { error ->
            LaunchedEffect(error) {
                android.widget.Toast.makeText(
                    viewModel.getApplication(),
                    error,
                    android.widget.Toast.LENGTH_LONG
                ).show()
                viewModel.clearError()
            }
        }

        // Success toast
        state.successMessage?.let { msg ->
            LaunchedEffect(msg) {
                android.widget.Toast.makeText(
                    viewModel.getApplication(),
                    msg,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.clearSuccess()
            }
        }
    }

    if (showAddDialog) {
        AddConnectionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { connection ->
                viewModel.addConnection(connection)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && editingConnection != null) {
        AddConnectionDialog(
            onDismiss = { showEditDialog = false; editingConnection = null },
            onSave = { connection ->
                viewModel.updateConnection(connection)
                showEditDialog = false
                editingConnection = null
            },
            editConnection = editingConnection
        )
    }

    if (showUsageDialog) {
        UsageDialog(onDismiss = { showUsageDialog = false })
    }
}

@Composable
fun ConnectionCard(
    connection: ServerConnection,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (connection.protocol == Protocol.WEBDAV) Icons.Default.Cloud else Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            connection.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (connection.protocol == Protocol.WEBDAV) "WebDAV" else "SMB3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onConnect, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("连接")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "地址: ${connection.address}" + if (connection.port > 0) ":${connection.port}" else "" + if (connection.shareName.isNotEmpty()) " / 共享: ${connection.shareName}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "用户: ${connection.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除连接「${connection.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionDialog(
    onDismiss: () -> Unit,
    onSave: (ServerConnection) -> Unit,
    editConnection: ServerConnection? = null
) {
    val isEdit = editConnection != null
    var selectedProtocol by remember { mutableStateOf(editConnection?.protocol ?: Protocol.WEBDAV) }
    var name by remember { mutableStateOf(editConnection?.name ?: "") }
    var address by remember { mutableStateOf(editConnection?.address ?: "") }
    var username by remember { mutableStateOf(editConnection?.username ?: "") }
    var password by remember { mutableStateOf(editConnection?.password ?: "") }
    var shareName by remember { mutableStateOf(editConnection?.shareName ?: "") }
    var port by remember { mutableStateOf(if ((editConnection?.port ?: 0) > 0) editConnection?.port.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑连接" else "添加远程连接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Protocol selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedProtocol == Protocol.WEBDAV,
                        onClick = { selectedProtocol = Protocol.WEBDAV },
                        label = { Text("WebDAV") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedProtocol == Protocol.SMB3,
                        onClick = { selectedProtocol = Protocol.SMB3 },
                        label = { Text("SMB3") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("连接名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (selectedProtocol == Protocol.WEBDAV) "服务器地址 (如 http://192.168.1.100)" else "服务器地址 (如 u3ff4d95.natappfree.cc)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedProtocol == Protocol.SMB3) {
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("共享名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("端口 (默认445，如穿透填映射端口)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ServerConnection(
                            id = editConnection?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            protocol = selectedProtocol,
                            address = address,
                            username = username,
                            password = password,
                            shareName = shareName,
                            port = if (port.isNotBlank()) port.toIntOrNull() ?: 0 else 0
                        )
                    )
                },
                enabled = name.isNotBlank() && address.isNotBlank() && username.isNotBlank()
            ) { Text(if (isEdit) "更新" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun UsageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用说明") },
        text = {
            Text(
                "1. 点击右上角 + 按钮添加远程服务器连接\n" +
                "2. 支持 WebDAV 和 SMB3 两种协议\n" +
                "3. 填写服务器地址、用户名、密码等信息\n" +
                "4. 保存后点击「连接」即可浏览远程文件\n" +
                "5. 长按文件/文件夹可进行重命名、删除操作\n" +
                "6. 点击路径栏可快速跳转到上级目录\n\n" +
                "WebDAV 部署：电脑端用 80 端口启动 WebDAV 服务\n" +
                "SMB3 部署：电脑端开启 SMB 共享 (445端口)\n\n" +
                "制作人：王淑凤\n" +
                "贡献者：吴甲龙\n" +
                "贡献者：又又"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}
