package com.topjohnwu.magisk.ui.module

import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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

@Composable
fun ModuleScreen(
    onInstallZip: (Uri) -> Unit = {},
    onRunAction: (String, String) -> Unit = { _, _ -> },
    viewModel: ModuleComposeViewModel = viewModel(factory = ModuleComposeViewModel.Factory)
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val localInstallDialog = rememberConfirmDialog()
    val confirmInstallTitle = stringResource(CoreR.string.confirm_install_title)
    var pendingOnlineModule by remember { mutableStateOf<OnlineModule?>(null) }
    val showOnlineDialog = rememberSaveable { mutableStateOf(false) }

    val defaultZipName = stringResource(id = CoreR.string.documents)
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val displayName =
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                } ?: uri.lastPathSegment ?: defaultZipName
            scope.launch {
                val result = localInstallDialog.awaitConfirm(
                    title = confirmInstallTitle,
                    content = context.getString(CoreR.string.confirm_install, displayName),
                )
                if (result == ConfirmResult.Confirmed) {
                    onInstallZip(uri)
                }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    RefreshOnResume { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(query) {
        delay(120)
        appliedQuery = query
    }

    if (showOnlineDialog.value && pendingOnlineModule != null) {
        OnlineModuleDialog(
            item = pendingOnlineModule!!,
            showDialog = showOnlineDialog,
            onDownload = { install ->
                showOnlineDialog.value = false
                val host = activity
                if (host == null) {
                    viewModel.postMessageRes(CoreR.string.app_not_found)
                } else {
                    DownloadEngine.startWithActivity(
                        host, host.extension,
                        OnlineModuleSubject(pendingOnlineModule!!, install)
                    )
                }
                pendingOnlineModule = null
            },
            onDismiss = {
                showOnlineDialog.value = false
                pendingOnlineModule = null
            }
        )
    }

    val filteredModules = remember(state.modules, appliedQuery) {
        val q = appliedQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) state.modules
        else state.modules.filter { it.searchKey.contains(q) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.loading && state.modules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeCap = StrokeCap.Round)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = MagiskUiDefaults.screenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.ListItemSpacing)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.SectionSpacing)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                onClick = { showSearch = !showSearch },
                                modifier = Modifier
                                    .height(MagiskUiDefaults.PrimaryActionHeight)
                                    .weight(0.3f),
                                shape = MagiskUiDefaults.MediumShape,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Button(
                                onClick = { zipPicker.launch("application/zip") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(MagiskUiDefaults.PrimaryActionHeight),
                                shape = MagiskUiDefaults.MediumShape
                            ) {
                                Icon(
                                    Icons.Rounded.FileUpload,
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(id = CoreR.string.module_action_install_external),
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showSearch,
                            enter = MagiskMotion.simpleExpandEnter(),
                            exit = MagiskMotion.simpleExpandExit()
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MagiskUiDefaults.MediumShape,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                placeholder = { Text(stringResource(id = CoreR.string.modules_search_placeholder)) },
                                singleLine = true
                            )
                        }
                    }
                }

                if (state.modules.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center
                        ) {
                            MagiskEmptyState(
                                icon = Icons.Rounded.ExtensionOff,
                                title = stringResource(id = CoreR.string.module_empty),
                                subtitle = stringResource(id = CoreR.string.module_action_install_external)
                            )
                        }
                    }
                } else if (filteredModules.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(id = CoreR.string.modules_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredModules,
                        key = { _, module -> module.id },
                        contentType = { _, _ -> "module_item" }
                    ) { _, module ->
                        ModuleCard(
                            module = module,
                            onToggleExpanded = { viewModel.toggleExpanded(module.id) },
                            onToggleEnabled = { viewModel.toggleEnabled(module.id) },
                            onToggleRemove = { viewModel.toggleRemove(module.id) },
                            onUpdate = { onlineModule ->
                                if (onlineModule == null) return@ModuleCard
                                if (Info.isConnected.value != true) {
                                    viewModel.postMessageRes(CoreR.string.no_connection)
                                    return@ModuleCard
                                }
                                pendingOnlineModule = onlineModule
                                showOnlineDialog.value = true
                            },
                            onAction = { onRunAction(module.id, module.name) }
                        )
                    }
                }
            }
        }

        MagiskSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MagiskUiDefaults.SnackbarBottomPaddingWithBar)
        )
    }
}

@Composable
private fun OnlineModuleDialog(
    item: OnlineModule,
    showDialog: MutableState<Boolean>,
    onDownload: (install: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val svc = ServiceLocator.networkService
    val title = stringResource(
        CoreR.string.repo_install_title,
        item.name, item.version, item.versionCode
    )
    val changelog by produceState(
        initialValue = AppContext.getString(CoreR.string.loading),
        item.changelog
    ) {
        val text = runCatching {
            withContext(Dispatchers.IO) { svc.fetchString(item.changelog) }
        }.getOrDefault("")
        value = if (text.length > 1000) text.substring(0, 1000) else text
    }

    MagiskDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Rounded.Extension,
        text = {
            Text(
                text = changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onDownload(false) }) {
                    Text(stringResource(CoreR.string.download))
                }
                Button(onClick = { onDownload(true) }) {
                    Text(stringResource(CoreR.string.install))
                }
            }
        },
        dismissButton = {
            MagiskDialogDismissButton(onClick = onDismiss)
        }
    )
}
