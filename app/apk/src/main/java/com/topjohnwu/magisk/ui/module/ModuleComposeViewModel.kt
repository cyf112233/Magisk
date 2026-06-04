package com.topjohnwu.magisk.ui.module

import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.ExtensionOff
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.download.DownloadEngine
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.ui.MainActivity
import com.topjohnwu.magisk.ui.RefreshOnResume
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.component.ConfirmResult
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.component.MagiskDialogDismissButton
import com.topjohnwu.magisk.ui.component.MagiskEmptyState
import com.topjohnwu.magisk.ui.component.MagiskSnackbarHost
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.component.rememberConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import com.topjohnwu.magisk.core.R as CoreR

data class ModuleUiItem(
    val id: String,
    val name: String,
    val versionAuthor: String,
    val description: String,
    val enabled: Boolean,
    val removed: Boolean,
    val updated: Boolean,
    val showAction: Boolean,
    val noticeText: String?,
    val showUpdate: Boolean,
    val updateReady: Boolean,
    val update: OnlineModule?,
    val badges: List<String>,
    val searchKey: String,
    val expanded: Boolean = false
)

data class ModuleUiState(val loading: Boolean = true, val modules: List<ModuleUiItem> = emptyList())

class ModuleComposeViewModel(private val moduleProvider: suspend () -> List<LocalModule>) :
    ViewModel() {
    private val _state = MutableStateFlow(ModuleUiState())
    val state: StateFlow<ModuleUiState> = _state
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var refreshJob: Job? = null
    private var metadataJob: Job? = null
    private val moduleCache = linkedMapOf<String, LocalModule>()
    private val cacheLock = Any()
    private var lastRefreshAt = 0L
    private var lastMetadataRefreshAt = 0L

    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && _state.value.modules.isNotEmpty() && now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) {
            return
        }
        lastRefreshAt = now
        refreshJob?.cancel()
        metadataJob?.cancel()
        val hadModules = _state.value.modules.isNotEmpty()
        refreshJob = viewModelScope.launch {
            if (!hadModules) {
                _state.update { it.copy(loading = true) }
            }
            val list =
                if (Info.env.isActive && isModuleRepoLoaded()) readInstalledModules() else emptyList()
            synchronized(cacheLock) {
                moduleCache.clear()
                list.forEach { moduleCache[it.id] = it }
            }
            val currentExpanded = _state.value.modules.filter { it.expanded }.map { it.id }.toSet()
            _state.update {
                it.copy(
                    loading = false,
                    modules = list.map { it.toUiItem(currentExpanded.contains(it.id)) })
            }
            if (list.isNotEmpty() && now - lastMetadataRefreshAt >= MIN_METADATA_REFRESH_INTERVAL_MS) {
                lastMetadataRefreshAt = now
                metadataJob = launch(Dispatchers.IO) {
                    list.forEach { runCatching { it.fetch() } }
                    val currentExpandedMetadata =
                        _state.value.modules.filter { it.expanded }.map { it.id }.toSet()
                    val updatedUi =
                        list.map { it.toUiItem(currentExpandedMetadata.contains(it.id)) }
                    withContext(Dispatchers.Main) {
                        _state.update { st -> st.copy(modules = updatedUi) }
                    }
                }
            }
        }
    }

    fun toggleExpanded(id: String) {
        _state.update { st ->
            st.copy(modules = st.modules.map {
                if (it.id == id) it.copy(expanded = !it.expanded) else it
            })
        }
    }

    fun toggleEnabled(id: String) = updateModule(id) { it.enable = !it.enable }
    fun toggleRemove(id: String) = updateModule(id) { it.remove = !it.remove }
    fun postMessageRes(@androidx.annotation.StringRes res: Int) {
        _messages.tryEmit(AppContext.getString(res))
    }

    private fun updateModule(id: String, block: (LocalModule) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val module = synchronized(cacheLock) { moduleCache[id] } ?: run {
                val list = readInstalledModules()
                synchronized(cacheLock) {
                    moduleCache.clear()
                    list.forEach { moduleCache[it.id] = it }
                }
                val currentExp = _state.value.modules.filter { it.expanded }.map { it.id }.toSet()
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(modules = list.map { m ->
                            m.toUiItem(
                                currentExp.contains(
                                    m.id
                                )
                            )
                        })
                    }
                }
                synchronized(cacheLock) { moduleCache[id] } ?: return@launch
            }
            val ok = runCatching { block(module) }.isSuccess
            if (!ok) {
                withContext(Dispatchers.Main) {
                    _messages.emit(AppContext.getString(CoreR.string.failure))
                }
                return@launch
            }
            val currentExpanded = _state.value.modules.find { it.id == id }?.expanded ?: false
            val updatedUi = module.toUiItem(currentExpanded)
            withContext(Dispatchers.Main) {
                _state.update { st ->
                    val index = st.modules.indexOfFirst { it.id == id }
                    if (index < 0) st
                    else {
                        val copy = st.modules.toMutableList()
                        copy[index] = updatedUi
                        st.copy(modules = copy)
                    }
                }
            }
        }
    }

    private suspend fun isModuleRepoLoaded(): Boolean =
        withTimeoutOrNull(3000) { withContext(Dispatchers.IO) { LocalModule.loaded() } } ?: false

    private suspend fun readInstalledModules(): List<LocalModule> =
        withTimeoutOrNull(5000) { withContext(Dispatchers.IO) { moduleProvider() } } ?: emptyList()

    object Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return ModuleComposeViewModel { LocalModule.installed() } as T
        }
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 1200L
        private const val MIN_METADATA_REFRESH_INTERVAL_MS = 30_000L
    }
}

private fun LocalModule.toUiItem(expanded: Boolean = false): ModuleUiItem {
    val zygiskLabel = AppContext.getString(CoreR.string.zygisk)
    val safeName = name.ifBlank { id }
    val safeDescription = description
    val noticeText: String? = when {
        zygiskUnloaded -> AppContext.getString(CoreR.string.zygisk_module_unloaded)
        Info.isZygiskEnabled && isRiru -> AppContext.getString(
            CoreR.string.suspend_text_riru,
            zygiskLabel
        )

        !Info.isZygiskEnabled && isZygisk -> AppContext.getString(
            CoreR.string.suspend_text_zygisk,
            zygiskLabel
        )

        else -> null
    }
    return ModuleUiItem(
        id,
        safeName,
        AppContext.getString(CoreR.string.module_version_author, version, author),
        safeDescription,
        enable,
        remove,
        updated,
        hasAction && noticeText == null,
        noticeText,
        updateInfo != null,
        outdated && !remove && enable,
        updateInfo,
        buildList {
            if (outdated) add(AppContext.getString(CoreR.string.module_badge_update))
            if (updated) add(AppContext.getString(CoreR.string.module_badge_updated))
            if (remove) add(AppContext.getString(CoreR.string.module_badge_removing))
            if (!enable) add(AppContext.getString(CoreR.string.module_badge_disabled))
        },
        buildString {
            append(safeName.lowercase(Locale.ROOT))
            append('\n')
            append(id.lowercase(Locale.ROOT))
            append('\n')
            append(safeDescription.lowercase(Locale.ROOT))
        },
        expanded
    )
}

