package com.topjohnwu.magisk.ui.module

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.magisk.ui.RouteProcessTopBarState
import com.topjohnwu.magisk.ui.component.TerminalCloseButton
import com.topjohnwu.magisk.ui.component.TerminalRunningActionSlot
import com.topjohnwu.magisk.ui.component.TerminalSaveLogButton
import com.topjohnwu.magisk.ui.component.TerminalScreenScaffold
import com.topjohnwu.magisk.ui.terminal.TerminalLogBuffer
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun ModuleActionScreen(
    actionId: String,
    actionName: String,
    onTitleStateChange: (String?, String?, RouteProcessTopBarState) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val viewModel: ModuleActionComposeViewModel =
        viewModel(factory = ModuleActionComposeViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lines = viewModel.lines
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val hasLogs by remember { derivedStateOf { lines.isNotEmpty() } }
    var hasStarted by remember(actionId) { mutableStateOf(false) }

    BackHandler(enabled = state.running) { }

    LaunchedEffect(actionId) {
        viewModel.start(actionId, actionName)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.running, state.success, lines.size, actionName) {
        if (state.running || hasLogs) {
            hasStarted = true
        }
        val title = actionName.takeIf { it.isNotBlank() }
            ?: AppContext.getString(CoreR.string.module_action)
        val subtitle = when {
            state.running -> AppContext.getString(CoreR.string.running)
            !hasStarted -> null
            state.success -> AppContext.getString(CoreR.string.done)
            else -> AppContext.getString(CoreR.string.failure)
        }
        onTitleStateChange(
            title,
            subtitle,
            RouteProcessTopBarState(
                running = state.running,
                success = state.success,
                hasResult = hasStarted && !state.running
            )
        )
    }
    DisposableEffect(Unit) {
        onDispose { onTitleStateChange(null, null, RouteProcessTopBarState()) }
    }

    TerminalScreenScaffold(
        lines = lines,
        listState = listState,
        snackbarHostState = snackbarHostState,
        emptyText = AppContext.getString(CoreR.string.loading)
    ) {
        TerminalSaveLogButton(
            hasLogs = hasLogs,
            onClick = { viewModel.saveLog(actionName) },
            modifier = Modifier.weight(1f)
        )
        TerminalRunningActionSlot(
            isRunning = state.running,
            modifier = Modifier.weight(1f)
        ) {
            TerminalCloseButton(onClick = onBack)
        }
    }
}
