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

@Immutable
data class MagiskLogScreenUiState(
    val loading: Boolean = true,
    val visibleLogs: List<MagiskLogUiItem> = emptyList()
)

@Immutable
data class LogStats(
    val total: Int,
    val issues: Int,
    val sources: Int
) {
    companion object {
        fun from(items: List<MagiskLogUiItem>): LogStats {
            return LogStats(
                total = items.size,
                issues = items.count { it.isIssue },
                sources = items.map { it.sourceLabel }.distinct().size
            )
        }
    }
}

enum class LogDisplayFilter(@param:StringRes val labelRes: Int) {
    ALL(CoreR.string.log_filter_all),
    MAGISK(CoreR.string.log_filter_magisk),
    SU(CoreR.string.log_filter_su),
    ISSUES(CoreR.string.log_filter_issues)
}

enum class MagiskLogLevel(val code: Char, val shortLabel: String) {
    VERBOSE('V', "V"),
    DEBUG('D', "D"),
    INFO('I', "I"),
    WARN('W', "W"),
    ERROR('E', "E"),
    FATAL('F', "F"),
    UNKNOWN('?', "?");

    @Composable
    fun color(): Color = when (this) {
        WARN -> MaterialTheme.colorScheme.tertiary
        ERROR, FATAL -> MaterialTheme.colorScheme.error
        DEBUG -> MaterialTheme.colorScheme.primary
        INFO -> MaterialTheme.colorScheme.secondary
        VERBOSE -> MaterialTheme.colorScheme.outline
        UNKNOWN -> MaterialTheme.colorScheme.outline
    }

    fun icon(): ImageVector = when (this) {
        WARN -> Icons.Rounded.Warning
        ERROR, FATAL -> Icons.Rounded.Dangerous
        DEBUG -> Icons.Rounded.BugReport
        else -> Icons.Rounded.Info
    }

    companion object {
        fun from(code: Char): MagiskLogLevel {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}

@Immutable
data class MagiskLogUiItem(
    val id: Int,
    val timestamp: String,
    val tag: String,
    val level: MagiskLogLevel,
    val message: String,
    val raw: String,
    val pid: Int = 0,
    val tid: Int = 0
) {
    val isIssue: Boolean
        get() = level == MagiskLogLevel.WARN ||
                level == MagiskLogLevel.ERROR ||
                level == MagiskLogLevel.FATAL

    val isMagisk: Boolean
        get() = tag.contains("magisk", ignoreCase = true) ||
                message.contains("magisk", ignoreCase = true)

    val isSu: Boolean
        get() = message.contains("su:", ignoreCase = true) ||
                raw.contains("su:", ignoreCase = true) ||
                tag.equals("su", ignoreCase = true)

    val sourceLabel: String
        get() = when {
            isMagisk -> AppContext.getString(CoreR.string.log_source_magisk)
            isSu -> AppContext.getString(CoreR.string.log_source_su)
            tag.isNotBlank() -> tag
            else -> AppContext.getString(CoreR.string.log_source_system)
        }

    fun contains(query: String): Boolean {
        return tag.contains(query, ignoreCase = true) ||
                message.contains(query, ignoreCase = true) ||
                raw.contains(query, ignoreCase = true) ||
                timestamp.contains(query, ignoreCase = true)
    }
}

class MagiskLogViewModel(private val repo: LogRepository) : ViewModel() {
    private val _state = MutableStateFlow(MagiskLogScreenUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val raw = withContext(Dispatchers.IO) { repo.fetchMagiskLogs() }
            val items = withContext(Dispatchers.Default) {
                MagiskLogParser.parse(raw).mapIndexed { index, entry ->
                    MagiskLogUiItem(
                        id = index,
                        timestamp = entry.timestamp,
                        tag = entry.tag,
                        level = MagiskLogLevel.from(entry.level),
                        message = entry.message,
                        raw = entry.message,
                        pid = entry.pid,
                        tid = entry.tid
                    )
                }
            }
            _state.update { it.copy(loading = false, visibleLogs = items) }
        }
    }

    fun clearMagiskLogs() {
        repo.clearMagiskLogs {
            _messages.tryEmit(AppContext.getString(CoreR.string.logs_cleared))
            refresh()
        }
    }

    fun saveMagiskLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val filename = "magisk_log_%s.log".format(
                    System.currentTimeMillis().toTime(timeFormatStandard)
                )
                val logFile = MediaStoreUtils.getFile(filename)
                val raw = repo.fetchMagiskLogs()
                logFile.uri.outputStream().bufferedWriter().use {
                    it.write("---Magisk Logs---\n${Info.env.versionString}\n\n$raw")
                }
                logFile.toString()
            }
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { _messages.emit(AppContext.getString(CoreR.string.saved_to_path, it)) }
                    .onFailure { _messages.emit(AppContext.getString(CoreR.string.failure)) }
            }
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MagiskLogViewModel(ServiceLocator.logRepo) as T
            }
        }
    }
}

