package com.topjohnwu.magisk.ui.deny

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
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
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.ui.MATCH_UNINSTALLED_PACKAGES_COMPAT
import com.topjohnwu.magisk.ui.RefreshOnResume
import com.topjohnwu.magisk.ui.component.MagiskDropdownMenu
import com.topjohnwu.magisk.ui.component.MagiskDropdownMenuItem
import com.topjohnwu.magisk.ui.component.MagiskEmptyState
import com.topjohnwu.magisk.ui.component.MagiskSnackbarHost
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.superuser.Shell
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
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun DenyListScreen(
    onBack: () -> Unit,
    viewModel: DenyListComposeViewModel = viewModel(factory = DenyListComposeViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    RefreshOnResume { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding),
            contentPadding = MagiskUiDefaults.verticalContentPadding(),
            verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.ListItemSpacing)
        ) {
            item {
                DenyListSearchSection(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    showSystem = state.showSystem,
                    onToggleSystem = { viewModel.setShowSystem(!state.showSystem) },
                    showOs = state.showOs,
                    onToggleOs = { viewModel.setShowOs(!state.showOs) },
                    sortMethod = state.sortMethod,
                    onSortMethodSelected = viewModel::setSortMethod
                )
            }
            when {
                state.loading && state.items.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeCap = StrokeCap.Round)
                        }
                    }
                }

                state.items.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyDenyListState()
                        }
                    }
                }

                else -> {
                    items(
                        items = state.items,
                        key = { it.packageName },
                        contentType = { "deny_list_item" }
                    ) { item ->
                        DenyListCard(
                            item = item,
                            onToggleExpanded = { viewModel.toggleExpanded(item.packageName) },
                            onToggleApp = {
                                viewModel.setAppChecked(
                                    item.packageName,
                                    item.selectionState != ToggleableState.On
                                )
                            },
                            onToggleProcess = { process ->
                                viewModel.toggleProcess(
                                    item.packageName,
                                    process.name,
                                    process.packageName
                                )
                            }
                        )
                    }
                }
            }
        }

        MagiskSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MagiskUiDefaults.SnackbarBottomPadding)
        )
    }
}

@Composable
private fun DenyListSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    showSystem: Boolean,
    onToggleSystem: () -> Unit,
    showOs: Boolean,
    onToggleOs: () -> Unit,
    sortMethod: DenyListSortMethod,
    onSortMethodSelected: (DenyListSortMethod) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = CoreR.string.denylist_search_filters),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )

            Box {
                AssistChip(
                    onClick = { showSortMenu = true },
                    label = {
                        Text(
                            text = stringResource(id = sortMethod.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                MagiskDropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DenyListSortMethod.entries.forEach { method ->
                        MagiskDropdownMenuItem(
                            text = stringResource(id = method.labelRes),
                            selected = method == sortMethod,
                            onClick = {
                                onSortMethodSelected(method)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = AppContext.getString(CoreR.string.hide_filter_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showSystem,
                onClick = onToggleSystem,
                label = {
                    Text(
                        stringResource(id = CoreR.string.show_system_app),
                        fontWeight = FontWeight.Black
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = chipColors
            )
            FilterChip(
                selected = showOs,
                enabled = showSystem,
                onClick = onToggleOs,
                label = {
                    Text(
                        stringResource(id = CoreR.string.show_os_app),
                        fontWeight = FontWeight.Black
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = chipColors
            )
        }
    }
}

@Composable
private fun EmptyDenyListState() {
    MagiskEmptyState(
        icon = Icons.Rounded.SettingsSuggest,
        title = AppContext.getString(CoreR.string.log_data_none)
    )
}

// Logic components remain identical
data class DenyListProcessUi(
    val name: String,
    val packageName: String,
    val isIsolated: Boolean,
    val isAppZygote: Boolean,
    val defaultSelection: Boolean,
    val enabled: Boolean
) {
    val displayName: String
        get() = if (isIsolated) AppContext.getString(
            CoreR.string.isolated_process_label,
            name
        ) else name
}

data class DenyListAppUi(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val isAppUid: Boolean,
    val expanded: Boolean,
    val processes: List<DenyListProcessUi>,
    val checkedCount: Int,
    val selectionState: ToggleableState
) {
    val sortKey: String by lazy(LazyThreadSafetyMode.NONE) { label.lowercase() }
    val searchKey: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            append(label.lowercase())
            append('|')
            append(packageName.lowercase())
            processes.forEach {
                append('|')
                append(it.name.lowercase())
            }
        }
    }

    companion object {
        fun deriveSelectionState(
            processes: List<DenyListProcessUi>,
            expanded: Boolean
        ): ToggleableState {
            val targets = if (expanded) processes else processes.filter { it.defaultSelection }
            if (targets.isEmpty()) return ToggleableState.Off
            val enabled = targets.count { it.enabled }
            return when (enabled) {
                0 -> ToggleableState.Off
                targets.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }

        fun create(
            packageName: String,
            label: String,
            icon: Drawable,
            isSystem: Boolean,
            isAppUid: Boolean,
            expanded: Boolean,
            processes: List<DenyListProcessUi>
        ): DenyListAppUi {
            val checkedCount = processes.count { it.enabled }
            return DenyListAppUi(
                packageName = packageName,
                label = label,
                icon = icon,
                isSystem = isSystem,
                isAppUid = isAppUid,
                expanded = expanded,
                processes = processes,
                checkedCount = checkedCount,
                selectionState = deriveSelectionState(processes, expanded)
            )
        }
    }
}
