package com.topjohnwu.magisk.ui.superuser

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.core.model.su.SuLog
import com.topjohnwu.magisk.core.repository.LogRepository
import com.topjohnwu.magisk.core.su.SuEvents
import com.topjohnwu.magisk.ui.MATCH_UNINSTALLED_PACKAGES_COMPAT
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.component.MagiskDialogConfirmButton
import com.topjohnwu.magisk.ui.component.MagiskDialogDismissButton
import com.topjohnwu.magisk.ui.component.MagiskEmptyState
import com.topjohnwu.magisk.ui.component.MagiskListCard
import com.topjohnwu.magisk.ui.component.MagiskListCardChevron
import com.topjohnwu.magisk.ui.component.MagiskSnackbarHost
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.component.PremiumIconContainer
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun SuperuserScreen(
    onOpenLogs: () -> Unit = {},
    viewModel: SuperuserComposeViewModel = viewModel(factory = SuperuserComposeViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? UIActivity<*>
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRevoke by remember { mutableStateOf<PolicyUiItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeCap = StrokeCap.Round)
                }
            }

            !Info.showSuperUser -> {
                DisabledState()
            }

            else -> {
                PolicyList(
                    items = state.items,
                    onOpenLogs = onOpenLogs,
                    onToggleExpanded = viewModel::toggleExpanded,
                    onToggleEnabled = { item, enabled ->
                        ensureAuthThen(activity, scope) {
                            viewModel.setPolicy(
                                item.uid,
                                if (enabled) SuPolicy.ALLOW else SuPolicy.DENY
                            )
                        }
                    },
                    onSliderFinished = { item, value ->
                        ensureAuthThen(activity, scope) {
                            viewModel.setPolicy(item.uid, sliderValueToPolicy(value))
                        }
                    },
                    onToggleNotify = { item ->
                        ensureAuthThen(activity, scope) { viewModel.toggleNotify(item) }
                    },
                    onToggleLog = { item ->
                        ensureAuthThen(activity, scope) { viewModel.toggleLog(item) }
                    },
                    onRevoke = { item ->
                        if (Config.suAuth) {
                            ensureAuthThen(activity, scope) { viewModel.revoke(item) }
                        } else {
                            pendingRevoke = item
                        }
                    }
                )
            }
        }

        MagiskSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter),
            hasBottomBar = true
        )
    }

    pendingRevoke?.let { item ->
        MagiskDialog(
            onDismissRequest = { pendingRevoke = null },
            title = stringResource(id = CoreR.string.su_revoke_title),
            icon = Icons.Rounded.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            text = {
                Text(
                    text = stringResource(id = CoreR.string.su_revoke_msg, item.title),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                MagiskDialogConfirmButton(
                    onClick = {
                        pendingRevoke = null
                        scope.launch { viewModel.revoke(item) }
                    },
                    destructive = true
                )
            },
            dismissButton = {
                MagiskDialogDismissButton(onClick = { pendingRevoke = null })
            }
        )
    }
}

private fun ensureAuthThen(
    activity: UIActivity<*>?,
    scope: CoroutineScope,
    block: suspend () -> Unit
) {
    if (!Config.suAuth) {
        scope.launch { block() }
        return
    }
    activity?.withAuthentication { ok ->
        if (ok) scope.launch { block() }
    }
}

@Composable
private fun EmptyStateContent() {
    MagiskEmptyState(
        icon = Icons.Rounded.Security,
        title = stringResource(id = CoreR.string.superuser_policy_none)
    )
}

@Composable
private fun DisabledState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp), contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(id = CoreR.string.unsupport_nonroot_stub_msg),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PolicyList(
    items: List<PolicyUiItem>,
    onOpenLogs: () -> Unit,
    onToggleExpanded: (Int, String) -> Unit,
    onToggleEnabled: (PolicyUiItem, Boolean) -> Unit,
    onSliderFinished: (PolicyUiItem, Float) -> Unit,
    onToggleNotify: (PolicyUiItem) -> Unit,
    onToggleLog: (PolicyUiItem) -> Unit,
    onRevoke: (PolicyUiItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = MagiskUiDefaults.screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.ListItemSpacing)
    ) {
        item {
            SuperuserLogsButton(onClick = onOpenLogs)
        }

        if (items.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillParentMaxWidth()
                        .fillParentMaxHeight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateContent()
                }
            }
        } else {
            items(
                items = items,
                key = { "${it.uid}:${it.packageName}" },
                contentType = { "policy_item" }
            ) { item ->
                StylishMagiskPolicyCard(
                    item = item,
                    onToggleExpanded = { onToggleExpanded(item.uid, item.packageName) },
                    onToggleEnabled = { onToggleEnabled(item, it) },
                    onSliderFinished = { onSliderFinished(item, it) },
                    onToggleNotify = { onToggleNotify(item) },
                    onToggleLog = { onToggleLog(item) },
                    onRevoke = { onRevoke(item) }
                )
            }
        }
    }
}

@Composable
private fun SuperuserLogsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MagiskUiDefaults.PrimaryActionHeight),
        shape = MagiskUiDefaults.MediumShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.HistoryEdu,
                null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(id = CoreR.string.superuser_logs).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StylishMagiskPolicyCard(
    item: PolicyUiItem,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSliderFinished: (Float) -> Unit,
    onToggleNotify: () -> Unit,
    onToggleLog: () -> Unit,
    onRevoke: () -> Unit
) {
    val isAllowed = item.policy >= SuPolicy.ALLOW
    val iconPainter = remember(item.uid, item.packageName, item.icon) {
        BitmapPainter(item.icon.asImageBitmap())
    }
    val statusColor = when {
        isAllowed -> MaterialTheme.colorScheme.primary
        item.policy == SuPolicy.RESTRICT -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val gradientColors = if (isAllowed) {
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    } else if (item.policy == SuPolicy.RESTRICT) {
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        )
    }

    MagiskListCard(
        expanded = item.expanded,
        onClick = onToggleExpanded,
        shape = MagiskUiDefaults.OrganicShapeReversed,
        backgroundBrush = Brush.linearGradient(colors = gradientColors)
    ) { cardMotion ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                PremiumIconContainer(
                    size = 56.dp,
                    shape = CircleShape,
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            isAllowed -> AppContext.getString(CoreR.string.grant)
                            item.policy == SuPolicy.RESTRICT -> AppContext.getString(CoreR.string.restrict)
                            else -> AppContext.getString(CoreR.string.deny)
                        }.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (item.showSlider) {
                var sliderValue by remember(item.uid, item.policy) {
                    mutableStateOf(policyToSliderValue(item.policy))
                }
                Slider(
                    modifier = Modifier.width(80.dp),
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..3f,
                    steps = 1,
                    onValueChangeFinished = { onSliderFinished(sliderValue) }
                )
            } else {
                Switch(
                    checked = isAllowed,
                    onCheckedChange = onToggleEnabled,
                    thumbContent = if (isAllowed) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }

            MagiskListCardChevron(
                motion = cardMotion,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        if (item.expanded) {
            Column {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp,
                        alignment = Alignment.CenterHorizontally
                    )
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FilledTonalIconButton(
                            onClick = onToggleNotify,
                            modifier = Modifier.size(MagiskUiDefaults.IconActionSize),
                            shape = MagiskUiDefaults.SmallShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (item.notification) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                contentColor = if (item.notification) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Notifications,
                                contentDescription = stringResource(id = CoreR.string.superuser_toggle_notification),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = CoreR.string.superuser_toggle_notification),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FilledTonalIconButton(
                            onClick = onToggleLog,
                            modifier = Modifier.size(MagiskUiDefaults.IconActionSize),
                            shape = MagiskUiDefaults.SmallShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (item.logging) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                contentColor = if (item.logging) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        ) {
                            Icon(
                                Icons.Rounded.BugReport,
                                contentDescription = stringResource(id = CoreR.string.logs),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = CoreR.string.logs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            onClick = onRevoke,
                            modifier = Modifier.size(MagiskUiDefaults.IconActionSize),
                            shape = MagiskUiDefaults.SmallShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteForever,
                                    contentDescription = stringResource(id = CoreR.string.superuser_toggle_revoke),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = CoreR.string.superuser_toggle_revoke),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Logic components remain identical
