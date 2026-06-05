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
import androidx.compose.foundation.shape.CircleShape
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
import com.topjohnwu.magisk.ui.component.PremiumCard
import com.topjohnwu.magisk.ui.component.PremiumIconContainer
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

@Composable
fun SuperuserLogsScreen(
    viewModel: SuperuserLogsComposeViewModel = viewModel(factory = SuperuserLogsComposeViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalContext.current as? UIActivity<*>

    RefreshOnResume { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MagiskUiDefaults.ScreenHorizontalPadding, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Timeline Content
                when {
                    state.loading && state.items.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeCap = StrokeCap.Round)
                        }
                    }

                    state.items.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    modifier = Modifier.size(120.dp),
                                    shape = RoundedCornerShape(40.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.History,
                                            null,
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    stringResource(id = CoreR.string.log_data_none),
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = state.items,
                                key = { _, item -> item.id },
                                contentType = { _, _ -> "su_log_item" }
                            ) { index, item ->
                                TimelineLogItem(index, state.items.size, item)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SuperuserLogActionButtons(
                onClear = { viewModel.clearLogs() },
                onSave = {
                    activity?.withPermission(WRITE_EXTERNAL_STORAGE) { granted ->
                        if (granted) viewModel.saveLogs() else viewModel.postExternalRwDenied()
                    } ?: viewModel.saveLogs()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            )
        }

        MagiskSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SuperuserLogActionButtons(
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onClear,
            modifier = Modifier
                .height(MagiskUiDefaults.ActionHeight)
                .weight(0.3f),
            shape = MagiskUiDefaults.SmallShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(CoreR.string.menuClearLog),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(MagiskUiDefaults.ActionHeight),
            shape = MagiskUiDefaults.SmallShape
        ) {
            Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(CoreR.string.menuSaveLog).uppercase(),
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun TimelineLogItem(index: Int, total: Int, item: SuLogUiItem) {
    val decisionColor = if (item.allowed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline aesthetics
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(
                        if (index == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = 0.4f
                        )
                    )
            )

            HexagonNode(color = decisionColor)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(
                        if (index == total - 1) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = 0.4f
                        )
                    )
            )
        }

        val cardShape = MagiskUiDefaults.ExtraLargeShape
        // Log Content Card
        PremiumCard(
            shape = cardShape,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .weight(1f)
        ) {
            Box {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val appIconPainter =
                            remember(item.icon) { BitmapPainter(item.icon.asImageBitmap()) }
                        PremiumIconContainer(
                            size = 34.dp,
                            shape = CircleShape,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Icon(
                                painter = appIconPainter,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp),
                                tint = Color.Unspecified
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = decisionColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = (if (item.allowed) AppContext.getString(CoreR.string.grant) else AppContext.getString(
                                    CoreR.string.deny
                                )).uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = decisionColor,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // FORMATTED INFO SECTION
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = MagiskUiDefaults.MediumShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            item.infoLines.forEachIndexed { index, line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                if (index != item.infoLines.lastIndex) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }

                            if (item.command.isNotBlank()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                                Text(
                                    text = item.command,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexagonNode(color: Color) {
    Canvas(
        modifier = Modifier
            .size(28.dp)
            .padding(4.dp)
    ) {
        val path = Path().apply {
            val radius = size.minDimension / 2
            val centerX = size.width / 2
            val centerY = size.height / 2
            for (i in 0..5) {
                val angle = Math.toRadians((i * 60 - 30).toDouble())
                val x = centerX + radius * cos(angle).toFloat()
                val y = centerY + radius * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color)
        drawPath(path, Color.White.copy(alpha = 0.25f), style = Fill)
    }
}
