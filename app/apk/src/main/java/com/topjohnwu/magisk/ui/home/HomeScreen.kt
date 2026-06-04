package com.topjohnwu.magisk.ui.home

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.download.DownloadEngine
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.core.ktx.await
import com.topjohnwu.magisk.core.ktx.reboot
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.repository.NetworkService
import com.topjohnwu.magisk.core.tasks.AppMigration
import com.topjohnwu.magisk.core.tasks.MagiskInstaller
import com.topjohnwu.magisk.ui.MainActivity
import com.topjohnwu.magisk.ui.RefreshOnResume
import com.topjohnwu.magisk.ui.component.MagiskBottomSheet
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.component.MagiskDialogConfirmButton
import com.topjohnwu.magisk.ui.component.MagiskDialogDismissButton
import com.topjohnwu.magisk.ui.component.MagiskDialogOption
import com.topjohnwu.magisk.ui.component.MagiskSnackbarHost
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.component.SectionHeader
import com.topjohnwu.magisk.ui.component.rememberLoadingDialog
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.util.Locale
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun HomeScreen(
    rebootRequestToken: Int = 0,
    onRebootTokenConsumed: () -> Unit = {},
    onOpenInstall: () -> Unit = {},
    onOpenUninstall: () -> Unit = {},
    viewModel: HomeComposeViewModel = viewModel(factory = HomeComposeViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val isHiddenApp = remember(context.packageName) { context.packageName != BuildConfig.APP_PACKAGE_NAME }
    val snackbarHostState = remember { SnackbarHostState() }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var showManagerInstallSheet by remember { mutableStateOf(false) }
    var showEnvFixDialog by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var envFixCode by remember { mutableStateOf(0) }

    RefreshOnResume { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(rebootRequestToken) {
        if (rebootRequestToken > 0) {
            onRebootTokenConsumed()
            showRebootDialog = true
        }
    }
    LaunchedEffect(state.envFixCode) {
        if (state.envFixCode != 0) {
            envFixCode = state.envFixCode
            showEnvFixDialog = true
            viewModel.onEnvFixConsumed()
        }
    }
    LaunchedEffect(state.showHideRestore) {
        if (state.showHideRestore) {
            if (isHiddenApp) showRestoreDialog = true else showHideDialog = true
            viewModel.onHideRestoreConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = MagiskUiDefaults.verticalContentPadding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.ListItemSpacing)
        ) {
            item {
                HomeStatusHeroCard(envActive = state.envActive)
            }

            if (state.noticeVisible) {
                item {
                    Box(modifier = Modifier.padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding)) {
                        HomeNotice(onHide = viewModel::hideNotice)
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.SectionSpacing)
                ) {
                    SectionHeader(
                        title = stringResource(id = CoreR.string.home_section_magisk_core),
                        icon = Icons.Rounded.VerifiedUser,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    )
                    HomeMagiskCoreCard(
                        magiskState = state.magiskState,
                        magiskInstalledVersion = state.magiskInstalledVersion,
                        onAction = onOpenInstall
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.SectionSpacing)
                ) {
                    SectionHeader(
                        title = stringResource(id = CoreR.string.home_section_application),
                        icon = Icons.Rounded.AppShortcut,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    )
                    HomeAppCard(
                        appState = state.appState,
                        managerInstalledVersion = state.managerInstalledVersion,
                        managerRemoteVersion = state.managerRemoteVersion,
                        updateChannelName = state.updateChannelName,
                        packageName = state.packageName,
                        isHidden = isHiddenApp,
                        onAction = {
                            viewModel.onManagerPressed {
                                showManagerInstallSheet = true
                            }
                        },
                        onHideRestore = viewModel::onHideRestorePressed
                    )
                }
            }

            if (state.envActive) {
                item {
                    Box(modifier = Modifier.padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding)) {
                        HomeUninstallCard(onClick = { showUninstallDialog = true })
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.SectionSpacing)
                ) {
                    SectionHeader(
                        title = stringResource(id = CoreR.string.home_section_contributors),
                        icon = Icons.Rounded.Groups,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    )
                    HomeContributorsList(
                        state.contributors,
                        state.contributorsLoading,
                        onOpen = { viewModel.openLink(context, it) }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.SectionSpacing)
                ) {
                    SectionHeader(
                        title = stringResource(id = CoreR.string.home_support_title),
                        icon = Icons.Rounded.Favorite,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    )
                    HomeSupportCard(
                        onPatreon = { viewModel.openLink(context, Const.Url.PATREON_URL) },
                        onPaypal = { viewModel.openLink(context, Const.Url.PAYPAL_URL) }
                    )
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

    if (showRebootDialog) {
        RebootExpressiveDialog(
            onDismiss = { showRebootDialog = false },
            onReboot = { type ->
                showRebootDialog = false
                reboot(type)
            }
        )
    }

    if (showUninstallDialog) {
        UninstallExpressiveDialog(
            onDismiss = { showUninstallDialog = false },
            onRestoreImages = {
                showUninstallDialog = false
                viewModel.restoreImages()
            },
            onCompleteUninstall = {
                showUninstallDialog = false
                onOpenUninstall()
            }
        )
    }

    if (showEnvFixDialog) {
        EnvFixExpressiveDialog(
            code = envFixCode,
            onDismiss = { showEnvFixDialog = false },
            onFix = {
                showEnvFixDialog = false
                val needsFullFix = envFixCode == 2 ||
                        Info.env.versionCode != BuildConfig.APP_VERSION_CODE ||
                        Info.env.versionString != BuildConfig.APP_VERSION_NAME
                if (needsFullFix) {
                    onOpenInstall()
                } else {
                    scope.launch {
                        val success = loadingDialog.withLoading {
                            MagiskInstaller.FixEnv().exec()
                        }
                        activity.toast(
                            if (success) CoreR.string.reboot_delay_toast else CoreR.string.setup_fail,
                            Toast.LENGTH_LONG
                        )
                        if (success) {
                            Handler(Looper.getMainLooper()).postDelayed({ reboot() }, 5000)
                        }
                    }
                }
            }
        )
    }

    if (showHideDialog) {
        HideAppExpressiveDialog(
            onDismiss = { showHideDialog = false },
            onConfirm = { appName ->
                showHideDialog = false
                scope.launch {
                    loadingDialog.withLoading {
                        AppMigration.patchAndHide(context, appName)
                    }
                }
            }
        )
    }

    if (showRestoreDialog) {
        RestoreAppExpressiveDialog(
            onDismiss = { showRestoreDialog = false },
            onConfirm = {
                showRestoreDialog = false
                scope.launch {
                    loadingDialog.withLoading {
                        AppMigration.restoreApp(context)
                    }
                }
            }
        )
    }

    if (showManagerInstallSheet) {
        ManagerInstallSheet(
            notes = state.managerReleaseNotes,
            onDismiss = { showManagerInstallSheet = false },
            onInstall = {
                showManagerInstallSheet = false
                DownloadEngine.startWithActivity(activity, activity.extension, Subject.App())
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerInstallSheet(
    notes: String,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    MagiskBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(MagiskUiDefaults.SectionSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = CoreR.string.install),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(id = CoreR.string.release_notes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 400.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    HomeMarkdownText(
                        markdown = notes.ifBlank { AppContext.getString(CoreR.string.not_available) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(MagiskUiDefaults.ActionHeight),
                    shape = MagiskUiDefaults.PillShape
                ) {
                    Text(stringResource(id = android.R.string.cancel), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .weight(1f)
                        .height(MagiskUiDefaults.ActionHeight),
                    shape = MagiskUiDefaults.PillShape
                ) {
                    Icon(Icons.Rounded.DownloadDone, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(id = CoreR.string.install), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                textSize = 14f
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            if (textView.tag != markdown) {
                ServiceLocator.markwon.setMarkdown(textView, markdown)
                textView.tag = markdown
            }
        }
    )
}

@Composable
private fun RebootExpressiveDialog(onDismiss: () -> Unit, onReboot: (String) -> Unit) {
    MagiskDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = CoreR.string.reboot),
        icon = Icons.Rounded.RestartAlt,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MagiskDialogOption(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = stringResource(id = CoreR.string.reboot),
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onReboot("") }
                )
                MagiskDialogOption(
                    icon = Icons.Rounded.History,
                    title = stringResource(id = CoreR.string.reboot_recovery),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = { onReboot("recovery") }
                )
                MagiskDialogOption(
                    icon = Icons.Rounded.Terminal,
                    title = stringResource(id = CoreR.string.reboot_bootloader),
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { onReboot("bootloader") }
                )
                MagiskDialogOption(
                    icon = Icons.Rounded.Bolt,
                    title = stringResource(id = CoreR.string.reboot_userspace),
                    accentColor = MaterialTheme.colorScheme.outline,
                    onClick = { onReboot("userspace") }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            MagiskDialogDismissButton(
                onClick = onDismiss,
                text = stringResource(id = CoreR.string.close)
            )
        }
    )
}

@Composable
private fun UninstallExpressiveDialog(onDismiss: () -> Unit, onRestoreImages: () -> Unit, onCompleteUninstall: () -> Unit) {
    MagiskDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = CoreR.string.uninstall_magisk_title),
        icon = Icons.Rounded.DeleteSweep,
        iconTint = MaterialTheme.colorScheme.error,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(id = CoreR.string.uninstall_magisk_msg))
                MagiskDialogOption(
                    icon = Icons.Rounded.SettingsBackupRestore,
                    title = stringResource(id = CoreR.string.restore_img),
                    subtitle = stringResource(id = CoreR.string.uninstall_restore_images_subtitle),
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onRestoreImages
                )
                MagiskDialogOption(
                    icon = Icons.Rounded.DeleteForever,
                    title = stringResource(id = CoreR.string.complete_uninstall),
                    subtitle = stringResource(id = CoreR.string.uninstall_complete_subtitle),
                    accentColor = MaterialTheme.colorScheme.error,
                    onClick = onCompleteUninstall
                )
            }
        },
        dismissButton = {
            MagiskDialogDismissButton(
                onClick = onDismiss,
                text = stringResource(id = CoreR.string.close)
            )
        }
    )
}

@Composable
private fun EnvFixExpressiveDialog(code: Int, onDismiss: () -> Unit, onFix: () -> Unit) {
    val needsFullFix = code == 2 || Info.env.versionCode != BuildConfig.APP_VERSION_CODE || Info.env.versionString != BuildConfig.APP_VERSION_NAME
    MagiskDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = CoreR.string.env_fix_title),
        icon = Icons.Rounded.BuildCircle,
        text = { Text(stringResource(id = if (needsFullFix) CoreR.string.env_full_fix_msg else CoreR.string.env_fix_msg)) },
        confirmButton = {
            MagiskDialogConfirmButton(onClick = onFix)
        },
        dismissButton = {
            MagiskDialogDismissButton(onClick = onDismiss)
        }
    )
}

@Composable
private fun HideAppExpressiveDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val defaultAppName = stringResource(id = CoreR.string.settings)
    var appName by remember(defaultAppName) { mutableStateOf(defaultAppName) }
    val isError = appName.length > AppMigration.MAX_LABEL_LENGTH || appName.isBlank()
    MagiskDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = CoreR.string.settings_hide_app_title),
        icon = Icons.Rounded.Masks,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(id = CoreR.string.settings_hide_app_summary))
                OutlinedTextField(
                    value = appName, onValueChange = { appName = it },
                    label = { Text(stringResource(id = CoreR.string.settings_app_name_hint)) },
                    singleLine = true, isError = isError, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            MagiskDialogConfirmButton(
                onClick = { onConfirm(appName) },
                enabled = !isError
            )
        },
        dismissButton = {
            MagiskDialogDismissButton(onClick = onDismiss)
        }
    )
}

@Composable
private fun RestoreAppExpressiveDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MagiskDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = CoreR.string.settings_restore_app_title),
        icon = Icons.Rounded.RestorePage,
        text = { Text(stringResource(id = CoreR.string.restore_app_confirmation)) },
        confirmButton = {
            MagiskDialogConfirmButton(onClick = onConfirm)
        },
        dismissButton = {
            MagiskDialogDismissButton(onClick = onDismiss)
        }
    )
}
