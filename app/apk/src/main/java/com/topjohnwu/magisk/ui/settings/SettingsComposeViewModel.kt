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
import com.topjohnwu.magisk.ui.theme.Theme
import com.topjohnwu.magisk.view.Shortcuts
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.magisk.core.R as CoreR

data class SettingsUiState(
    val darkThemeMode: Int = Config.darkTheme,
    val themeOrdinal: Int = Config.themeOrdinal,
    val selectedThemeIndex: Int = Theme.values().indexOf(Theme.selected).coerceAtLeast(0),
    val themeName: String = Theme.selected.themeName,
    val useLocaleManager: Boolean = LocaleSetting.useLocaleManager,
    val languageSystemName: String = LocaleSetting.instance.appLocale?.let { it.getDisplayName(it) }
        ?: AppContext.getString(CoreR.string.system_default),
    val languageIndex: Int = LocaleSetting.available.tags.indexOf(Config.locale)
        .let { if (it < 0) 0 else it },
    val languageName: String = LocaleSetting.available.names.getOrElse(
        LocaleSetting.available.tags.indexOf(
            Config.locale
        ).let { if (it < 0) 0 else it }) { AppContext.getString(CoreR.string.system_default) },
    val canAddShortcut: Boolean = isRunningAsStub && ShortcutManagerCompat.isRequestPinShortcutSupported(
        AppContext
    ),
    val canMigrateApp: Boolean = Info.env.isActive && Const.USER_ID == 0,
    val isHiddenApp: Boolean = AppContext.packageName != BuildConfig.APP_PACKAGE_NAME,
    val checkUpdate: Boolean = Config.checkUpdate,
    val updateChannel: Int = Config.updateChannel,
    val isCustomChannel: Boolean = Config.updateChannel == Config.Value.CUSTOM_CHANNEL,
    val updateChannelName: String = AppContext.resources.getStringArray(CoreR.array.update_channel)
        .getOrElse(Config.updateChannel) { "-" },
    val customChannelUrl: String = Config.customChannelUrl,
    val doh: Boolean = Config.doh,
    val downloadDir: String = Config.downloadDir,
    val downloadDirPath: String = MediaStoreUtils.fullPath(Config.downloadDir),
    val randName: Boolean = Config.randName,
    val zygisk: Boolean = Config.zygisk,
    val zygiskMismatch: Boolean = Config.zygisk != Info.isZygiskEnabled,
    val denyList: Boolean = Config.denyList,
    val showMagisk: Boolean = Info.env.isActive,
    val showMagiskAdvanced: Boolean = Info.env.isActive && Const.Version.atLeast_24_0(),
    val showDenyListConfig: Boolean = Const.Version.atLeast_24_0(),
    val showSuperuser: Boolean = Info.showSuperUser,
    val deviceSecure: Boolean = Info.isDeviceSecure,
    val suTapjack: Boolean = Config.suTapjack,
    val suAuth: Boolean = Config.suAuth,
    val hideTapjackOnSPlus: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val rootMode: Int = Config.rootMode,
    val accessModeName: String = AppContext.resources.getStringArray(CoreR.array.su_access)
        .getOrElse(Config.rootMode) { "-" },
    val suMultiuserMode: Int = Config.suMultiuserMode,
    val multiuserModeName: String = AppContext.resources.getStringArray(CoreR.array.multiuser_mode)
        .getOrElse(Config.suMultiuserMode) { "-" },
    val multiuserModeEnabled: Boolean = Const.USER_ID == 0,
    val multiuserSummary: String = AppContext.resources.getStringArray(CoreR.array.multiuser_summary)
        .getOrElse(Config.suMultiuserMode) { "-" },
    val suMntNamespaceMode: Int = Config.suMntNamespaceMode,
    val mountNamespaceName: String = AppContext.resources.getStringArray(CoreR.array.namespace)
        .getOrElse(Config.suMntNamespaceMode) { "-" },
    val mountNamespaceSummary: String = AppContext.resources.getStringArray(CoreR.array.namespace_summary)
        .getOrElse(Config.suMntNamespaceMode) { "-" },
    val suAutoResponse: Int = Config.suAutoResponse,
    val autoResponseName: String = AppContext.resources.getStringArray(CoreR.array.auto_response)
        .getOrElse(Config.suAutoResponse) { "-" },
    val suTimeoutIndex: Int = SU_TIMEOUT_VALUES.indexOf(Config.suDefaultTimeout)
        .let { if (it < 0) 0 else it },
    val requestTimeoutName: String = AppContext.resources.getStringArray(CoreR.array.request_timeout)
        .getOrElse(
            SU_TIMEOUT_VALUES.indexOf(Config.suDefaultTimeout)
                .let { if (it < 0) 0 else it }) { "-" },
    val suNotification: Int = Config.suNotification,
    val suNotificationName: String = AppContext.resources.getStringArray(CoreR.array.su_notification)
        .getOrElse(Config.suNotification) { "-" },
    val suReAuth: Boolean = Config.suReAuth,
    val showReauthenticate: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O,
    val suRestrict: Boolean = Config.suRestrict,
    val showRestrict: Boolean = Const.Version.atLeast_30_1()
)

class SettingsComposeViewModel : ViewModel() {
    var state by mutableStateOf(snapshotState())
        private set
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var refreshJob: Job? = null

    fun refreshState() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { state = snapshotState() }
    }

    fun setDarkMode(mode: Int) {
        Config.darkTheme = mode; state = snapshotState()
    }

    fun setThemeOrdinal(index: Int) {
        val theme = Theme.values().getOrNull(index) ?: Theme.Default; Config.themeOrdinal =
            if (theme == Theme.Default) -1 else theme.ordinal; state = snapshotState()
    }

    fun setLanguageByIndex(index: Int) {
        if (state.useLocaleManager) return;
        val tags = LocaleSetting.available.tags; if (tags.isEmpty()) return;
        val safe = index.coerceIn(0, tags.lastIndex); Config.locale = tags[safe]; state =
            snapshotState()
    }

    fun addShortcut() {
        runCatching {
            Shortcuts.addHomeIcon(AppContext); state = snapshotState()
        }.onFailure { _messages.tryEmit(AppContext.getString(CoreR.string.failure)) }
    }

    fun hideApp(activity: UIActivity<*>?, label: String) {
        val safeLabel = label.trim()
        if (activity == null || safeLabel.isBlank() || safeLabel.length > AppMigration.MAX_LABEL_LENGTH) {
            _messages.tryEmit(AppContext.getString(CoreR.string.failure))
            return
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                AppMigration.patchAndHide(activity, safeLabel)
            }
            if (!success) {
                _messages.emit(AppContext.getString(CoreR.string.failure))
            }
            state = snapshotState()
        }
    }

    fun restoreApp(activity: UIActivity<*>?) {
        if (activity == null) {
            _messages.tryEmit(AppContext.getString(CoreR.string.failure))
            return
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                AppMigration.restoreApp(activity)
            }
            if (!success) {
                _messages.emit(AppContext.getString(CoreR.string.failure))
            }
            state = snapshotState()
        }
    }

    fun setCheckUpdate(v: Boolean) {
        Config.checkUpdate = v; state = snapshotState()
    }

    fun setUpdateChannel(c: Int) {
        Config.updateChannel = c; Info.resetUpdate(); state = snapshotState()
    }

    fun setCustomChannelUrl(u: String) {
        Config.customChannelUrl = u; Info.resetUpdate(); state = snapshotState()
    }

    fun setDoH(v: Boolean) {
        Config.doh = v; state = snapshotState()
    }

    fun setDownloadDir(v: String) {
        Config.downloadDir = v; state = snapshotState()
    }

    fun setRandName(v: Boolean) {
        Config.randName = v; state = snapshotState()
    }

    fun createSystemlessHosts() {
        viewModelScope.launch {
            val ok = RootUtils.addSystemlessHosts(); _messages.tryEmit(
            AppContext.getString(if (ok) CoreR.string.settings_hosts_toast else CoreR.string.failure)
        )
        }
    }

    fun setZygisk(v: Boolean) {
        Config.zygisk = v; state =
            snapshotState(); if (v != Info.isZygiskEnabled) _messages.tryEmit(
            AppContext.getString(
                CoreR.string.reboot_apply_change
            )
        )
    }

    fun setDenyList(v: Boolean) {
        viewModelScope.launch {
            val cmd = if (v) "enable" else "disable";
            val ok = withContext(Dispatchers.IO) {
                Shell.cmd("magisk --denylist $cmd").exec().isSuccess
            }; state = if (ok) {
            Config.denyList = v; snapshotState()
        } else {
            _messages.emit(AppContext.getString(CoreR.string.failure)); snapshotState()
        }
        }
    }

    fun setRootMode(v: Int) {
        Config.rootMode = v; state = snapshotState()
    }

    fun setSuMultiuserMode(v: Int) {
        Config.suMultiuserMode = v; state = snapshotState()
    }

    fun setSuMntNamespaceMode(v: Int) {
        Config.suMntNamespaceMode = v; state = snapshotState()
    }

    fun setSuAuth(v: Boolean) {
        Config.suAuth = v; state = snapshotState()
    }

    fun setSuAutoResponse(v: Int) {
        Config.suAutoResponse = v; state = snapshotState()
    }

    fun setSuTimeoutIndex(index: Int) {
        val safe = index.coerceIn(0, SU_TIMEOUT_VALUES.lastIndex); Config.suDefaultTimeout =
            SU_TIMEOUT_VALUES[safe]; state = snapshotState()
    }

    fun setSuNotification(v: Int) {
        Config.suNotification = v; state = snapshotState()
    }

    fun setSuReAuth(v: Boolean) {
        Config.suReAuth = v; state = snapshotState()
    }

    fun setSuTapjack(v: Boolean) {
        Config.suTapjack = v; state = snapshotState()
    }

    fun setSuRestrict(v: Boolean) {
        Config.suRestrict = v; state = snapshotState()
    }

    fun setMessageRes(res: Int) {
        _messages.tryEmit(AppContext.getString(res))
    }

    private fun snapshotState(): SettingsUiState {
        return SettingsUiState()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return SettingsComposeViewModel() as T
            }
        }
    }
}

val SU_TIMEOUT_VALUES = listOf(10, 15, 20, 30, 45, 60)

