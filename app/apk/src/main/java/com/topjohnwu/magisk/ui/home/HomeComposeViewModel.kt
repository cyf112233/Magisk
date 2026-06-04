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

// Logic components - Mantengo questi per compatibilità
data class ContributorLink(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val url: String
)
data class Contributor(val login: String, val avatarUrl: String, val htmlUrl: String, val links: List<ContributorLink> = emptyList())
private val MAINTAINER_LINKS: Map<String, List<ContributorLink>> = mapOf(
    "topjohnwu" to listOf(ContributorLink(CoreR.string.twitter, CoreR.drawable.ic_twitter, "https://x.com/topjohnwu"), ContributorLink(CoreR.string.github, CoreR.drawable.ic_github, "https://github.com/topjohnwu/Magisk")),
    "vvb2060" to listOf(ContributorLink(CoreR.string.twitter, CoreR.drawable.ic_twitter, "https://x.com/vvb2060"), ContributorLink(CoreR.string.github, CoreR.drawable.ic_github, "https://github.com/vvb2060")),
    "yujincheng08" to listOf(ContributorLink(CoreR.string.twitter, CoreR.drawable.ic_twitter, "https://x.com/yujincheng08"), ContributorLink(CoreR.string.github, CoreR.drawable.ic_github, "https://github.com/yujincheng08"), ContributorLink(CoreR.string.github, CoreR.drawable.ic_favorite, "https://github.com/sponsors/yujincheng08")),
    "rikkaw" to listOf(ContributorLink(CoreR.string.twitter, CoreR.drawable.ic_twitter, "https://x.com/rikkaw_"), ContributorLink(CoreR.string.github, CoreR.drawable.ic_github, "https://github.com/RikkaW")),
    "canyie" to listOf(ContributorLink(CoreR.string.twitter, CoreR.drawable.ic_twitter, "https://x.com/canyieq"), ContributorLink(CoreR.string.github, CoreR.drawable.ic_github, "https://github.com/canyie")),
    "anto426" to listOf(ContributorLink(CoreR.string.github, CoreR.drawable.ic_github, "https://github.com/Anto426"))
)
private fun createContributor(login: String, avatarUrl: String, htmlUrl: String): Contributor {
    val normalized = login.lowercase(Locale.US)
    return Contributor(login = login, avatarUrl = avatarUrl, htmlUrl = htmlUrl, links = MAINTAINER_LINKS[normalized].orEmpty())
}
private val FORK_MAINTAINER = createContributor(login = "Anto426", avatarUrl = "https://github.com/Anto426.png", htmlUrl = "https://github.com/Anto426")

interface GitHubService {
    @GET("repos/topjohnwu/Magisk/contributors")
    @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
    suspend fun getContributors(@Query("per_page") perPage: Int = 30): List<Map<String, Any?>>
}

data class HomeUiState(
    val magiskState: HomeViewModel.State = HomeViewModel.State.INVALID,
    val magiskInstalledVersion: String = AppContext.getString(CoreR.string.not_available),
    val appState: HomeViewModel.State = HomeViewModel.State.LOADING,
    val managerRemoteVersion: String = AppContext.getString(CoreR.string.not_available),
    val managerReleaseNotes: String = "",
    val managerInstalledVersion: String = "",
    val updateChannelName: String = AppContext.getString(CoreR.string.settings_update_stable),
    val packageName: String = "",
    val envActive: Boolean = Info.env.isActive,
    val showHideRestore: Boolean = false,
    val envFixCode: Int = 0,
    val contributors: List<Contributor> = emptyList(),
    val contributorsLoading: Boolean = true,
    val noticeVisible: Boolean = Config.safetyNotice
)

class HomeComposeViewModel(private val svc: NetworkService) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var refreshJob: Job? = null
    private var lastRefreshAt = 0L
    private val gitHubService: GitHubService by lazy {
        Retrofit.Builder().baseUrl("https://api.github.com/").addConverterFactory(MoshiConverterFactory.create()).build().create(GitHubService::class.java)
    }

    private fun cachedContributors(): List<Contributor>? {
        val cached = contributorsCache
        val cachedAt = contributorsCacheTimestamp
        return cached.takeIf { cached.isNotEmpty() && System.currentTimeMillis() - cachedAt < CONTRIBUTORS_CACHE_TTL_MS }
    }

    private fun cacheContributors(list: List<Contributor>) {
        contributorsCache = withPinnedContributors(list)
        contributorsCacheTimestamp = System.currentTimeMillis()
    }

    private fun withPinnedContributors(list: List<Contributor>): List<Contributor> {
        return (listOf(FORK_MAINTAINER) + list).distinctBy { it.login.lowercase(Locale.US) }
    }

    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && _state.value.appState != HomeViewModel.State.LOADING && now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) {
            return
        }
        lastRefreshAt = now
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { if (it.contributors.isEmpty()) it.copy(contributorsLoading = true) else it }
            val cached = cachedContributors()
            if (cached != null) {
                _state.update { it.copy(contributors = cached, contributorsLoading = false) }
            } else {
                launch {
                    runCatching { gitHubService.getContributors(perPage = 30) }
                        .onSuccess { raw ->
                            val fetched = raw.mapNotNull { item ->
                                val login = item["login"] as? String ?: return@mapNotNull null
                                createContributor(login = login, avatarUrl = item["avatar_url"] as? String ?: "", htmlUrl = item["html_url"] as? String ?: "")
                            }
                            val priorityOrder = listOf("topjohnwu", "vvb2060", "yujincheng08", "rikkaw", "canyie")
                            val fetchedMap = fetched.associateBy { it.login.lowercase(Locale.US) }
                            val ordered = priorityOrder.mapNotNull { handle -> fetchedMap[handle] }
                            val finalList = withPinnedContributors(ordered.ifEmpty { fetched })
                            cacheContributors(finalList)
                            _state.update { it.copy(contributors = finalList, contributorsLoading = false) }
                        }
                        .onFailure { _state.update { it.copy(contributors = withPinnedContributors(emptyList()), contributorsLoading = false) } }
                }
            }
            val remote = Info.fetchUpdate(svc)
            val appState = when {
                remote == null -> HomeViewModel.State.INVALID
                BuildConfig.APP_VERSION_CODE < remote.versionCode -> HomeViewModel.State.OUTDATED
                else -> HomeViewModel.State.UP_TO_DATE
            }
            val magiskState = when {
                Info.isRooted && Info.env.isUnsupported -> HomeViewModel.State.OUTDATED
                !Info.env.isActive -> HomeViewModel.State.INVALID
                Info.env.versionCode < BuildConfig.APP_VERSION_CODE -> HomeViewModel.State.OUTDATED
                else -> HomeViewModel.State.UP_TO_DATE
            }
            val managerInstalled = "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})" + if (BuildConfig.DEBUG) " (D)" else ""
            _state.update {
                it.copy(
                    magiskState = magiskState,
                    magiskInstalledVersion = Info.env.run { if (isActive) "$versionString ($versionCode)" + if (isDebug) " (D)" else "" else AppContext.getString(CoreR.string.not_available) },
                    appState = appState,
                    managerInstalledVersion = managerInstalled,
                    managerRemoteVersion = remote?.run { val isDebug = Config.updateChannel == Config.Value.DEBUG_CHANNEL; "$version ($versionCode)" + if (isDebug) " (D)" else "" } ?: AppContext.getString(CoreR.string.not_available),
                    managerReleaseNotes = remote?.note.orEmpty(),
                    updateChannelName = AppContext.resources.getStringArray(CoreR.array.update_channel).getOrElse(Config.updateChannel) { AppContext.getString(CoreR.string.settings_update_stable) },
                    packageName = AppContext.packageName,
                    envActive = Info.env.isActive,
                    noticeVisible = Config.safetyNotice
                )
            }
            ensureEnv(magiskState)
        }
    }

    fun hideNotice() { Config.safetyNotice = false; _state.update { it.copy(noticeVisible = false) } }
    fun checkForMagiskUpdates() { refresh() }
    fun onHideRestorePressed() { _state.update { it.copy(showHideRestore = true) } }
    fun onHideRestoreConsumed() { _state.update { it.copy(showHideRestore = false) } }
    fun onEnvFixConsumed() { _state.update { it.copy(envFixCode = 0) } }
    fun onManagerPressed(onShowInstallSheet: () -> Unit) {
        when (_state.value.appState) {
            HomeViewModel.State.LOADING -> _messages.tryEmit(AppContext.getString(CoreR.string.loading))
            HomeViewModel.State.INVALID -> _messages.tryEmit(AppContext.getString(CoreR.string.no_connection))
            else -> onShowInstallSheet()
        }
    }
    fun restoreImages() {
        viewModelScope.launch {
            _messages.tryEmit(AppContext.getString(CoreR.string.restore_img_msg))
            val success = MagiskInstaller.Restore().exec { }
            _messages.emit(AppContext.getString(if (success) CoreR.string.restore_done else CoreR.string.restore_fail))
        }
    }
    fun openLink(c: android.content.Context, l: String) {
        try { c.startActivity(Intent(Intent.ACTION_VIEW, l.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { _messages.tryEmit(AppContext.getString(CoreR.string.open_link_failed_toast)) }
    }
    private suspend fun ensureEnv(magiskState: HomeViewModel.State) {
        if (magiskState == HomeViewModel.State.INVALID || checkedEnv) return
        val cmd = "env_check ${Info.env.versionString} ${Info.env.versionCode}"
        val code = runCatching { Shell.cmd(cmd).await().code }.getOrDefault(0)
        if (code != 0) _state.update { it.copy(envFixCode = code) }
        checkedEnv = true
    }
    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 1200L
        private const val CONTRIBUTORS_CACHE_TTL_MS = 30L * 60_000L
        private var contributorsCache: List<Contributor> = emptyList()
        private var contributorsCacheTimestamp: Long = 0
        private var checkedEnv = false
    }
    object Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return HomeComposeViewModel(ServiceLocator.networkService) as T
        }
    }
}

