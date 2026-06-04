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

data class ModuleActionUiState(
    val running: Boolean = false,
    val success: Boolean = false
)

class ModuleActionComposeViewModel : ViewModel() {

    private val _state = MutableStateFlow(ModuleActionUiState())
    val state: StateFlow<ModuleActionUiState> = _state
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val terminal = TerminalLogBuffer()
    val lines = terminal.lines
    private val logs = terminal.logs
    private val outItems = terminal.console
    private var started = false

    init {
        terminal.bind(viewModelScope)
    }

    fun start(actionId: String, actionName: String) {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(running = true, success = false) }
            val success = runCatching {
                Shell.cmd("run_action ${shellQuote(actionId)}")
                    .to(outItems, logs)
                    .exec()
                    .isSuccess
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        running = false,
                        success = success
                    )
                }
                if (success) {
                    _messages.emit(AppContext.getString(CoreR.string.done_action, actionName))
                }
            }
        }
    }

    fun saveLog(actionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val safeName = actionName.ifBlank { "module" }
                val name = "%s_action_log_%s.log".format(
                    safeName,
                    System.currentTimeMillis().toTime(timeFormatStandard)
                )
                val file = MediaStoreUtils.getFile(name)
                file.uri.outputStream().bufferedWriter().use(terminal::writeTo)
                file.toString()
            }.onSuccess { path ->
                _messages.emit(path)
            }.onFailure {
                _messages.emit(AppContext.getString(CoreR.string.failure))
            }
        }
    }

    override fun onCleared() {
        terminal.close()
        super.onCleared()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ModuleActionComposeViewModel() as T
            }
        }
    }
}

private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

