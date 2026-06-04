package com.topjohnwu.magisk.ui.settings

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.arch.UIActivity
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.tasks.AppMigration
import com.topjohnwu.magisk.core.utils.LocaleSetting
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.RootUtils
import com.topjohnwu.magisk.ui.POST_NOTIFICATIONS_PERMISSION
import com.topjohnwu.magisk.ui.RefreshOnResume
import com.topjohnwu.magisk.ui.component.ExpressiveSection
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.component.MagiskDialogConfirmButton
import com.topjohnwu.magisk.ui.component.MagiskDialogDismissButton
import com.topjohnwu.magisk.ui.component.MagiskDialogOption
import com.topjohnwu.magisk.ui.component.MagiskSnackbarHost
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.component.PremiumIconContainer
import com.topjohnwu.magisk.ui.theme.Theme
import com.topjohnwu.magisk.view.Shortcuts
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun SettingsScreen(
    onOpenDenyList: () -> Unit = {},
    onOpenTheme: () -> Unit = {},
    viewModel: SettingsComposeViewModel = viewModel(factory = SettingsComposeViewModel.Factory)
) {
    val context = LocalContext.current
    val activity = context as? UIActivity<*>
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }

    var selector by remember { mutableStateOf<SelectorSpec?>(null) }
    var input by remember { mutableStateOf<InputSpec?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    RefreshOnResume { viewModel.refreshState() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = MagiskUiDefaults.screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.ListItemSpacing)
        ) {


            item {
                OrganicSettingsSection(
                    stringResource(id = CoreR.string.settings_customization),
                    Icons.Rounded.Palette
                ) {
                    ExpressiveSettingItem(
                        title = stringResource(id = CoreR.string.section_theme),
                        subtitle = "${darkModeLabel(state.darkThemeMode)} | ${state.themeName}",
                        icon = Icons.Rounded.Palette,
                        onClick = onOpenTheme
                    )

                    ExpressiveSettingItem(
                        title = stringResource(id = CoreR.string.language),
                        subtitle = if (state.useLocaleManager) state.languageSystemName else state.languageName,
                        icon = Icons.Rounded.Language,
                        onClick = {
                            if (state.useLocaleManager) {
                                runCatching { context.startActivity(LocaleSetting.localeSettingsIntent) }
                            } else {
                                selector = SelectorSpec(
                                    title = AppContext.getString(CoreR.string.language),
                                    icon = Icons.Rounded.Language,
                                    options = LocaleSetting.available.names.toList(),
                                    selectedIndex = state.languageIndex,
                                    onSelect = viewModel::setLanguageByIndex
                                )
                            }
                        }
                    )

                    if (state.canAddShortcut) {
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.add_shortcut_title),
                            subtitle = stringResource(id = CoreR.string.setting_add_shortcut_summary),
                            icon = Icons.Rounded.AddLink,
                            onClick = viewModel::addShortcut
                        )
                    }
                }
            }

            item {
                OrganicSettingsSection(
                    stringResource(id = CoreR.string.home_app_title),
                    Icons.Rounded.Settings
                ) {
                    ExpressiveSettingItem(
                        title = stringResource(id = CoreR.string.settings_update_channel_title),
                        subtitle = state.updateChannelName,
                        icon = Icons.Rounded.Update,
                        onClick = {
                            selector = SelectorSpec(
                                title = AppContext.getString(CoreR.string.settings_update_channel_title),
                                icon = Icons.Rounded.Update,
                                options = context.resources.getStringArray(CoreR.array.update_channel)
                                    .toList(),
                                selectedIndex = state.updateChannel.coerceAtLeast(0),
                                onSelect = { index ->
                                    viewModel.setUpdateChannel(index)
                                    if (index == Config.Value.CUSTOM_CHANNEL && viewModel.state.customChannelUrl.isBlank()) {
                                        input = InputSpec(
                                            title = AppContext.getString(CoreR.string.settings_update_custom),
                                            initialValue = "",
                                            onConfirm = viewModel::setCustomChannelUrl
                                        )
                                    }
                                }
                            )
                        }
                    )
                    ExpressiveSettingItem(
                        title = stringResource(id = CoreR.string.settings_update_custom),
                        subtitle = state.customChannelUrl.ifBlank { stringResource(id = CoreR.string.settings_update_custom_msg) },
                        enabled = state.isCustomChannel,
                        icon = Icons.Rounded.Link,
                        onClick = {
                            input = InputSpec(
                                title = AppContext.getString(CoreR.string.settings_update_custom),
                                initialValue = state.customChannelUrl,
                                onConfirm = viewModel::setCustomChannelUrl
                            )
                        }
                    )
                    ExpressiveToggleItem(
                        title = stringResource(id = CoreR.string.settings_doh_title),
                        subtitle = stringResource(id = CoreR.string.settings_doh_description),
                        checked = state.doh,
                        icon = Icons.Rounded.Public,
                        onChecked = viewModel::setDoH
                    )
                    ExpressiveToggleItem(
                        title = stringResource(id = CoreR.string.settings_check_update_title),
                        subtitle = stringResource(id = CoreR.string.settings_check_update_summary),
                        checked = state.checkUpdate,
                        icon = Icons.Rounded.NotificationAdd,
                        onChecked = { checked ->
                            activity?.withPermission(POST_NOTIFICATIONS_PERMISSION) { granted ->
                                if (granted) viewModel.setCheckUpdate(checked)
                                else viewModel.setMessageRes(CoreR.string.post_notifications_denied)
                            } ?: viewModel.setMessageRes(CoreR.string.app_not_found)
                        }
                    )
                    ExpressiveSettingItem(
                        title = stringResource(id = CoreR.string.settings_download_path_title),
                        subtitle = state.downloadDirPath.ifBlank { "-" },
                        icon = Icons.Rounded.FolderOpen,
                        onClick = {
                            activity?.withPermission(WRITE_EXTERNAL_STORAGE) { granted ->
                                if (granted) {
                                    input = InputSpec(
                                        title = AppContext.getString(CoreR.string.settings_download_path_title),
                                        initialValue = state.downloadDir,
                                        onConfirm = viewModel::setDownloadDir
                                    )
                                } else {
                                    viewModel.setMessageRes(CoreR.string.external_rw_permission_denied)
                                }
                            } ?: viewModel.setMessageRes(CoreR.string.app_not_found)
                        }
                    )
                    ExpressiveToggleItem(
                        title = stringResource(id = CoreR.string.settings_random_name_title),
                        subtitle = stringResource(id = CoreR.string.settings_random_name_description),
                        checked = state.randName,
                        icon = Icons.Rounded.Shuffle,
                        onChecked = viewModel::setRandName
                    )
                    if (state.canMigrateApp) {
                        ExpressiveSettingItem(
                            title = stringResource(id = if (state.isHiddenApp) CoreR.string.settings_restore_app_title else CoreR.string.settings_hide_app_title),
                            subtitle = stringResource(id = if (state.isHiddenApp) CoreR.string.settings_restore_app_summary else CoreR.string.settings_hide_app_summary),
                            icon = if (state.isHiddenApp) Icons.Rounded.RestorePage else Icons.Rounded.Masks,
                            onClick = {
                                if (state.isHiddenApp) confirmRestore = true
                                else {
                                    input = InputSpec(
                                        title = AppContext.getString(CoreR.string.settings_hide_app_title),
                                        initialValue = AppContext.getString(CoreR.string.settings),
                                        onConfirm = { label -> viewModel.hideApp(activity, label) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (state.showMagisk) {
                item {
                    OrganicSettingsSection(
                        stringResource(id = CoreR.string.magisk),
                        Icons.Rounded.Security
                    ) {
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.settings_hosts_title),
                            subtitle = stringResource(id = CoreR.string.settings_hosts_summary),
                            icon = Icons.Rounded.Dns,
                            onClick = viewModel::createSystemlessHosts
                        )
                        if (state.showMagiskAdvanced) {
                            ExpressiveToggleItem(
                                title = stringResource(id = CoreR.string.zygisk),
                                subtitle = if (state.zygiskMismatch) stringResource(id = CoreR.string.reboot_apply_change) else stringResource(
                                    id = CoreR.string.settings_zygisk_summary
                                ),
                                checked = state.zygisk,
                                icon = Icons.Rounded.Bolt,
                                onChecked = viewModel::setZygisk
                            )
                            ExpressiveToggleItem(
                                title = stringResource(id = CoreR.string.settings_denylist_title),
                                subtitle = stringResource(id = CoreR.string.settings_denylist_summary),
                                checked = state.denyList,
                                icon = Icons.Rounded.Security,
                                onChecked = viewModel::setDenyList
                            )
                            if (state.showDenyListConfig) {
                                ExpressiveSettingItem(
                                    title = stringResource(id = CoreR.string.settings_denylist_config_title),
                                    subtitle = stringResource(id = CoreR.string.settings_denylist_config_summary),
                                    icon = Icons.Rounded.Block,
                                    onClick = onOpenDenyList
                                )
                            }
                        }
                    }
                }
            }

            if (state.showSuperuser) {
                item {
                    OrganicSettingsSection(
                        stringResource(id = CoreR.string.superuser),
                        Icons.Rounded.Shield
                    ) {
                        if (!state.hideTapjackOnSPlus) {
                            ExpressiveToggleItem(
                                title = stringResource(id = CoreR.string.settings_su_tapjack_title),
                                subtitle = stringResource(id = CoreR.string.settings_su_tapjack_summary),
                                checked = state.suTapjack,
                                icon = Icons.Rounded.TouchApp,
                                onChecked = viewModel::setSuTapjack
                            )
                        }
                        ExpressiveToggleItem(
                            title = stringResource(id = CoreR.string.settings_su_auth_title),
                            subtitle = if (state.deviceSecure) stringResource(id = CoreR.string.settings_su_auth_summary) else stringResource(
                                id = CoreR.string.settings_su_auth_insecure
                            ),
                            checked = state.suAuth,
                            enabled = state.deviceSecure,
                            icon = Icons.Rounded.Fingerprint,
                            onChecked = { checked ->
                                activity?.withAuthentication { ok ->
                                    if (ok) viewModel.setSuAuth(
                                        checked
                                    )
                                }
                                    ?: viewModel.setMessageRes(CoreR.string.app_not_found)
                            }
                        )
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.superuser_access),
                            subtitle = state.accessModeName,
                            icon = Icons.Rounded.Key,
                            onClick = {
                                selector = SelectorSpec(
                                    title = AppContext.getString(CoreR.string.superuser_access),
                                    icon = Icons.Rounded.Key,
                                    options = context.resources.getStringArray(CoreR.array.su_access)
                                        .toList(),
                                    selectedIndex = state.rootMode.coerceAtLeast(0),
                                    onSelect = viewModel::setRootMode
                                )
                            }
                        )
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.multiuser_mode),
                            subtitle = state.multiuserSummary,
                            enabled = state.multiuserModeEnabled,
                            icon = Icons.Rounded.People,
                            onClick = {
                                selector = SelectorSpec(
                                    title = AppContext.getString(CoreR.string.multiuser_mode),
                                    icon = Icons.Rounded.People,
                                    options = context.resources.getStringArray(CoreR.array.multiuser_mode)
                                        .toList(),
                                    selectedIndex = state.suMultiuserMode.coerceAtLeast(0),
                                    onSelect = viewModel::setSuMultiuserMode
                                )
                            }
                        )
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.mount_namespace_mode),
                            subtitle = state.mountNamespaceSummary,
                            icon = Icons.Rounded.Layers,
                            onClick = {
                                selector = SelectorSpec(
                                    title = AppContext.getString(CoreR.string.mount_namespace_mode),
                                    icon = Icons.Rounded.Layers,
                                    options = context.resources.getStringArray(CoreR.array.namespace)
                                        .toList(),
                                    selectedIndex = state.suMntNamespaceMode.coerceAtLeast(0),
                                    onSelect = viewModel::setSuMntNamespaceMode
                                )
                            }
                        )
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.auto_response),
                            subtitle = state.autoResponseName,
                            icon = Icons.AutoMirrored.Rounded.Reply,
                            onClick = {
                                val showSelector = {
                                    selector = SelectorSpec(
                                        title = AppContext.getString(CoreR.string.auto_response),
                                        icon = Icons.AutoMirrored.Rounded.Reply,
                                        options = context.resources.getStringArray(CoreR.array.auto_response)
                                            .toList(),
                                        selectedIndex = state.suAutoResponse.coerceAtLeast(0),
                                        onSelect = viewModel::setSuAutoResponse
                                    )
                                }
                                if (state.suAuth) activity?.withAuthentication { ok -> if (ok) showSelector() }
                                else showSelector()
                            }
                        )
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.request_timeout),
                            subtitle = state.requestTimeoutName,
                            icon = Icons.Rounded.Timer,
                            onClick = {
                                selector = SelectorSpec(
                                    title = AppContext.getString(CoreR.string.request_timeout),
                                    icon = Icons.Rounded.Timer,
                                    options = context.resources.getStringArray(CoreR.array.request_timeout)
                                        .toList(),
                                    selectedIndex = state.suTimeoutIndex.coerceIn(
                                        0,
                                        SU_TIMEOUT_VALUES.lastIndex
                                    ),
                                    onSelect = viewModel::setSuTimeoutIndex
                                )
                            }
                        )
                        ExpressiveSettingItem(
                            title = stringResource(id = CoreR.string.superuser_notification),
                            subtitle = state.suNotificationName,
                            icon = Icons.Rounded.NotificationsActive,
                            onClick = {
                                selector = SelectorSpec(
                                    title = AppContext.getString(CoreR.string.superuser_notification),
                                    icon = Icons.Rounded.NotificationsActive,
                                    options = context.resources.getStringArray(CoreR.array.su_notification)
                                        .toList(),
                                    selectedIndex = state.suNotification.coerceAtLeast(0),
                                    onSelect = viewModel::setSuNotification
                                )
                            }
                        )
                        if (state.showReauthenticate) {
                            ExpressiveToggleItem(
                                title = stringResource(id = CoreR.string.settings_su_reauth_title),
                                subtitle = stringResource(id = CoreR.string.settings_su_reauth_summary),
                                checked = state.suReAuth,
                                icon = Icons.Rounded.VerifiedUser,
                                onChecked = viewModel::setSuReAuth
                            )
                        }
                        if (state.showRestrict) {
                            ExpressiveToggleItem(
                                title = stringResource(id = CoreR.string.settings_su_restrict_title),
                                subtitle = stringResource(id = CoreR.string.settings_su_restrict_summary),
                                checked = state.suRestrict,
                                icon = Icons.Rounded.Lock,
                                onChecked = viewModel::setSuRestrict
                            )
                        }
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

    selector?.let { spec ->
        MagiskDialog(
            onDismissRequest = { selector = null },
            title = spec.title,
            icon = spec.icon,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    spec.options.forEachIndexed { index, label ->
                        MagiskDialogOption(
                            title = label,
                            selected = index == spec.selectedIndex,
                            showRadio = true,
                            onClick = {
                                spec.onSelect(index)
                                selector = null
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    input?.let { spec ->
        var value by remember(spec.initialValue) { mutableStateOf(spec.initialValue) }
        MagiskDialog(
            onDismissRequest = { input = null },
            title = spec.title,
            icon = Icons.Rounded.Edit,
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MagiskUiDefaults.SmallShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            },
            confirmButton = {
                MagiskDialogConfirmButton(
                    onClick = {
                        spec.onConfirm(value.trim())
                        input = null
                    }
                )
            },
            dismissButton = {
                MagiskDialogDismissButton(onClick = { input = null })
            }
        )
    }

    if (confirmRestore) {
        MagiskDialog(
            onDismissRequest = { confirmRestore = false },
            title = stringResource(id = CoreR.string.settings_restore_app_title),
            icon = Icons.Rounded.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            text = { Text(stringResource(id = CoreR.string.restore_app_confirmation)) },
            confirmButton = {
                MagiskDialogConfirmButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restoreApp(activity)
                    },
                    destructive = true
                )
            },
            dismissButton = {
                MagiskDialogDismissButton(onClick = { confirmRestore = false })
            }
        )
    }
}



@Composable
private fun OrganicSettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpressiveSection(
        title = title,
        icon = icon,
        iconContainerSize = 38.dp,
        iconPadding = 9.dp,
        iconShape = CircleShape,
        iconContainerAlpha = 0.85f,
        cardShape = MagiskUiDefaults.ExtraLargeShape,
        content = content
    )
}

@Composable
private fun ExpressiveSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PremiumIconContainer(
                    size = 44.dp,
                    shape = CircleShape,
                    backgroundBrush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                        )
                    )
                ) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.4f
                )
            )
        }
    }
}

@Composable
private fun ExpressiveToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) }) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PremiumIconContainer(
                    size = 44.dp,
                    shape = CircleShape,
                    backgroundBrush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                        )
                    )
                ) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null)
        }
    }
}

private fun darkModeLabel(mode: Int): String = when (mode) {
    AppCompatDelegate.MODE_NIGHT_NO -> AppContext.getString(CoreR.string.settings_dark_mode_light)
    AppCompatDelegate.MODE_NIGHT_YES -> AppContext.getString(CoreR.string.settings_dark_mode_dark)
    Config.Value.DARK_THEME_AMOLED -> AppContext.getString(CoreR.string.settings_dark_mode_amoled)
    else -> AppContext.getString(CoreR.string.settings_dark_mode_system)
}

data class SelectorSpec(
    val title: String,
    val icon: ImageVector,
    val options: List<String>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit
)

data class InputSpec(val title: String, val initialValue: String, val onConfirm: (String) -> Unit)
