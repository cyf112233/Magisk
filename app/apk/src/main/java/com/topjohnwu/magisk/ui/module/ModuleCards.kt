package com.topjohnwu.magisk.ui.module

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.component.MagiskListCard
import com.topjohnwu.magisk.ui.component.MagiskListCardChevron
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.component.PremiumIconContainer
import com.topjohnwu.magisk.core.R as CoreR

@Composable
internal fun ModuleCard(
    module: ModuleUiItem,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleRemove: () -> Unit,
    onUpdate: (OnlineModule?) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = module.enabled && !module.removed
    val gradientColors = when {
        module.removed -> listOf(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
        module.updated -> listOf(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
        module.updateReady -> listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
        !isEnabled -> listOf(
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)
        )
        else -> if (module.expanded) {
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
            )
        }
    }
    val stateDotColor = if (module.updateReady) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    val cardShape = MagiskUiDefaults.ExtraLargeShape
    MagiskListCard(
        expanded = module.expanded,
        onClick = onToggleExpanded,
        modifier = modifier,
        shape = cardShape,
        backgroundBrush = Brush.linearGradient(colors = gradientColors),
        backgroundContent = {
            if (module.removed || module.updated) {
                Icon(
                    imageVector = if (module.removed) {
                        Icons.Rounded.DeleteForever
                    } else {
                        Icons.Rounded.SystemUpdateAlt
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(128.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 24.dp)
                        .alpha(0.08f),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { cardMotion ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                PremiumIconContainer(
                    size = 56.dp,
                    shape = CircleShape,
                    backgroundBrush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                        )
                    )
                ) {
                    Icon(
                        Icons.Rounded.Extension,
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (isEnabled) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(stateDotColor)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (module.removed) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = module.versionAuthor,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        textDecoration = if (module.removed) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        },
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .alpha(if (isEnabled) 1f else 0.7f)
                    )
                }
                if (module.badges.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = module.badges.joinToString(" | "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(
                visible = module.expanded,
                enter = MagiskMotion.expandedControlEnter(),
                exit = MagiskMotion.expandedControlExit()
            ) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    thumbContent = if (isEnabled) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }

            MagiskListCardChevron(
                motion = cardMotion,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        if (module.expanded) {
            Column {
                if (module.description.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        textDecoration = if (module.removed) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        },
                        modifier = Modifier.alpha(if (isEnabled) 1f else 0.8f)
                    )
                }

                if (module.noticeText != null) {
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                module.noticeText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    if (module.showAction) {
                        FilledTonalIconButton(
                            onClick = onAction,
                            modifier = Modifier
                                .size(52.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (module.showUpdate) {
                        Button(
                            onClick = { onUpdate(module.update) },
                            enabled = module.updateReady,
                            shape = MagiskUiDefaults.PillShape,
                            modifier = Modifier
                                .height(52.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SystemUpdateAlt,
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(id = CoreR.string.update),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Surface(
                        onClick = onToggleRemove,
                        enabled = !module.updated,
                        modifier = Modifier
                            .size(52.dp)
                            .alpha(if (module.updated) 0.45f else 1f),
                        shape = CircleShape,
                        color = when {
                            module.updated -> MaterialTheme.colorScheme.surfaceVariant
                            module.removed -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (module.removed) {
                                    Icons.Rounded.SettingsBackupRestore
                                } else {
                                    Icons.Rounded.DeleteForever
                                },
                                contentDescription = null,
                                tint = when {
                                    module.updated -> MaterialTheme.colorScheme.onSurfaceVariant
                                    module.removed -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
