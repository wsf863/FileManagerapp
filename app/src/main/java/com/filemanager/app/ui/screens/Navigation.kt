package com.filemanager.app.ui.screens

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filemanager.app.ui.viewmodel.FileViewModel

@Composable
fun AppNavigation(viewModel: FileViewModel = viewModel()) {
    // 由 ViewModel 的状态驱动导航，而不是手动切换
    val fileListState by viewModel.fileListState.collectAsState()
    val isConnected = fileListState.connection != null

    if (isConnected) {
        FileListScreen(
            viewModel = viewModel,
            onBack = { viewModel.disconnect() }
        )
    } else {
        ConnectionListScreen(
            viewModel = viewModel,
            onConnect = { connection ->
                viewModel.connectTo(connection)
            }
        )
    }
}
