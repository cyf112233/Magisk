package com.topjohnwu.magisk.ui.flash

import android.net.Uri
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
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.ktx.reboot
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.ktx.writeTo
import com.topjohnwu.magisk.core.tasks.MagiskInstaller
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.displayName
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.inputStream
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.magisk.ui.RouteProcessTopBarState
import com.topjohnwu.magisk.ui.component.TerminalCloseButton
import com.topjohnwu.magisk.ui.component.TerminalRebootButton
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
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun FlashScreen(
    action: String,
    uriArg: String?,
    onTitleStateChange: (String?, String?, RouteProcessTopBarState) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val viewModel: FlashComposeViewModel = viewModel(factory = FlashComposeViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lines = viewModel.lines
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val hasLogs by remember { derivedStateOf { lines.isNotEmpty() } }
    val parsedUri = uriArg
        ?.takeIf { it.isNotBlank() }
        ?.let(::parseNavigationUriArg)
    var hasStarted by remember(action, uriArg) { mutableStateOf(false) }

    BackHandler(enabled = state.running) { }

    LaunchedEffect(action, parsedUri) {
        viewModel.start(action, parsedUri)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.running, state.success, lines.size) {
        if (state.running || hasLogs) {
            hasStarted = true
        }
        val title = AppContext.getString(CoreR.string.flash_screen_title)
        val subtitle = when {
            state.running -> AppContext.getString(CoreR.string.flashing)
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
        emptyText = AppContext.getString(CoreR.string.waiting_for_logs)
    ) {
        TerminalSaveLogButton(
            hasLogs = hasLogs,
            onClick = viewModel::saveLog,
            modifier = Modifier.weight(1f)
        )
        TerminalRunningActionSlot(
            isRunning = state.running,
            modifier = Modifier.weight(1f)
        ) {
            if (state.showReboot) {
                TerminalRebootButton(onClick = viewModel::rebootNow)
            } else {
                TerminalCloseButton(onClick = onBack)
            }
        }
    }
}
private fun parseNavigationUriArg(rawArg: String): Uri? {
    val rawParsed = runCatching { rawArg.toUri() }.getOrNull()
    if (rawParsed?.scheme == "content" || rawParsed?.scheme == "file") {
        return rawParsed
    }
    val decoded = runCatching { Uri.decode(rawArg) }.getOrNull() ?: return rawParsed
    val decodedParsed = runCatching { decoded.toUri() }.getOrNull()
    return when (decodedParsed?.scheme) {
        "content", "file" -> decodedParsed
        else -> rawParsed
    }
}
