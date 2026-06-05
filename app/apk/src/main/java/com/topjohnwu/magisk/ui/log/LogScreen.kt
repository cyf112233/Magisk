package com.topjohnwu.magisk.ui.log

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.arch.UIActivity
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.repository.LogRepository
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.magisk.ui.RefreshOnResume
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.component.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun LogsScreen(
    viewModel: MagiskLogViewModel = viewModel(factory = MagiskLogViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalContext.current as? UIActivity<*>
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clearDialog = rememberConfirmDialog()

    var filter by remember { mutableStateOf(LogDisplayFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(state.visibleLogs, filter, searchQuery) {
        val base = when (filter) {
            LogDisplayFilter.ALL -> state.visibleLogs
            LogDisplayFilter.ISSUES -> state.visibleLogs.filter { it.isIssue }
            LogDisplayFilter.MAGISK -> state.visibleLogs.filter { it.isMagisk }
            LogDisplayFilter.SU -> state.visibleLogs.filter { it.isSu }
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filter { it.contains(searchQuery) }
        }
    }

    val stats = remember(state.visibleLogs) { LogStats.from(state.visibleLogs) }

    RefreshOnResume { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding)
                    .padding(top = MagiskUiDefaults.ScreenTopPadding)
            ) {
                LogFilterSection(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    activeFilter = filter,
                    onFilterChange = { filter = it },
                    loading = state.loading,
                    onRefresh = viewModel::refresh,
                    onSave = {
                        activity?.withPermission(WRITE_EXTERNAL_STORAGE) { granted ->
                            if (granted) viewModel.saveMagiskLog()
                        }
                    },
                    onClear = {
                        scope.launch {
                            val result = clearDialog.awaitConfirm(
                                title = AppContext.getString(CoreR.string.log_clear_confirm_title),
                                content = AppContext.getString(CoreR.string.log_clear_confirm_msg),
                                confirm = AppContext.getString(CoreR.string.menuClearLog)
                            )
                            if (result == ConfirmResult.Confirmed) {
                                viewModel.clearMagiskLogs()
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MagiskUiDefaults.ScreenHorizontalPadding,
                    end = MagiskUiDefaults.ScreenHorizontalPadding,
                    bottom = MagiskUiDefaults.ScreenBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.ListItemSpacing)
            ) {
                if (state.loading && state.visibleLogs.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeCap = StrokeCap.Round)
                        }
                    }
                } else if (filteredLogs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.5f),
                            contentAlignment = Alignment.Center
                        ) {
                            MagiskEmptyState(
                                icon = Icons.Rounded.Terminal,
                                title = stringResource(id = CoreR.string.log_data_magisk_none)
                            )
                        }
                    }
                } else {
                    items(
                        items = filteredLogs,
                        key = { it.id },
                        contentType = { "log_item" }
                    ) { item ->
                        LogEventCard(item = item)
                    }
                }
            }
        }

        MagiskSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter),
            hasBottomBar = true
        )

        val showFab = (listState.canScrollForward || listState.canScrollBackward) && filteredLogs.isNotEmpty()
        val isAtBottom = !listState.canScrollForward

        AnimatedVisibility(
            visible = showFab,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = MagiskUiDefaults.ScreenHorizontalPadding,
                    bottom = MagiskUiDefaults.FloatingActionBottomPaddingWithBar
                ),
            enter = MagiskMotion.fabEnter(),
            exit = MagiskMotion.fabExit()
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (isAtBottom) {
                            MagiskMotion.scrollToItem(listState, 0)
                        } else {
                            MagiskMotion.scrollToItem(listState, filteredLogs.size)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MagiskUiDefaults.PillShape
            ) {
                Icon(
                    imageVector = if (isAtBottom) Icons.Rounded.KeyboardDoubleArrowUp else Icons.Rounded.KeyboardDoubleArrowDown,
                    contentDescription = null
                )
            }
        }
    }
}
