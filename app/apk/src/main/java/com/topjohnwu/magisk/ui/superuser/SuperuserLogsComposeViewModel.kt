package com.topjohnwu.magisk.ui.superuser

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.arch.UIActivity
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.ktx.timeDateFormat
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.model.su.SuLog
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.core.repository.LogRepository
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.magisk.ui.RefreshOnResume
import com.topjohnwu.magisk.ui.component.MagiskSnackbarHost
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import com.topjohnwu.magisk.core.R as CoreR

data class SuLogUiItem(
    val id: Int,
    val appName: String,
    val icon: Bitmap,
    val allowed: Boolean,
    val infoLines: List<String>,
    val command: String
)

data class SuperuserLogsUiState(
    val loading: Boolean = true,
    val items: List<SuLogUiItem> = emptyList()
)

class SuperuserLogsComposeViewModel(private val repo: LogRepository) : ViewModel() {
    private val _state = MutableStateFlow(SuperuserLogsUiState())
    val state: StateFlow<SuperuserLogsUiState> = _state
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var refreshJob: Job? = null
    private val pm = AppContext.packageManager
    private val iconCache = mutableMapOf<String, Bitmap>()

    fun refresh() {
        refreshJob?.cancel()
        val hadItems = _state.value.items.isNotEmpty()
        refreshJob = viewModelScope.launch {
            if (!hadItems) {
                _state.update { it.copy(loading = true) }
            }
            val items = withContext(Dispatchers.IO) {
                repo.fetchSuLogs().map { it.toUiItem() }
            }
            _state.update { it.copy(loading = false, items = items) }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearLogs() }
            _messages.emit(AppContext.getString(CoreR.string.logs_cleared))
            refresh()
        }
    }

    fun saveLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val name = "superuser_log_%s.log".format(
                    System.currentTimeMillis().toTime(timeFormatStandard)
                )
                val logFile = MediaStoreUtils.getFile(name)
                logFile.uri.outputStream().bufferedWriter().use { writer ->
                    state.value.items.forEach { item ->
                        writer.write("${item.appName}\n")
                        item.infoLines.forEach { line -> writer.write("$line\n") }
                        if (item.command.isNotBlank()) {
                            writer.write("${item.command}\n")
                        }
                        writer.write("\n")
                    }
                }
                logFile.uri.toString()
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { path ->
                    _messages.emit(
                        AppContext.getString(
                            CoreR.string.saved_to_path,
                            path
                        )
                    )
                }
                    .onFailure { _messages.emit(AppContext.getString(CoreR.string.failure)) }
            }
        }
    }

    fun postExternalRwDenied() {
        _messages.tryEmit(AppContext.getString(CoreR.string.external_rw_permission_denied))
    }

    private fun SuLog.toUiItem(): SuLogUiItem {
        val res = AppContext.resources
        val infoLines = mutableListOf<String>()
        infoLines += time.toTime(timeDateFormat)
        val primaryLine = buildString {
            append(res.getString(CoreR.string.target_uid, toUid))
            append("  ")
            append(res.getString(CoreR.string.pid, fromPid))
            if (target != -1) {
                val pid = if (target == 0) "magiskd" else target.toString()
                append("  ")
                append(res.getString(CoreR.string.target_pid, pid))
            }
        }
        infoLines += primaryLine
        if (context.isNotEmpty()) {
            infoLines += res.getString(CoreR.string.selinux_context, context)
        }
        if (gids.isNotEmpty()) {
            infoLines += res.getString(CoreR.string.supp_group, gids)
        }
        val icon = iconCache.getOrPut(packageName) {
            runCatching { pm.getApplicationIcon(packageName) }
                .getOrDefault(pm.defaultActivityIcon)
                .toBitmap()
        }

        return SuLogUiItem(
            id = id,
            appName = appName,
            icon = icon,
            allowed = action >= SuPolicy.ALLOW,
            infoLines = infoLines,
            command = command
        )
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return SuperuserLogsComposeViewModel(ServiceLocator.logRepo) as T
            }
        }
    }
}

