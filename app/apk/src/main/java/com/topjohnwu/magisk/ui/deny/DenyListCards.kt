package com.topjohnwu.magisk.ui.deny

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.component.MagiskListCard
import com.topjohnwu.magisk.ui.component.MagiskListCardChevron
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.component.PremiumIconContainer

@Composable
internal fun DenyListCard(
    item: DenyListAppUi,
    onToggleExpanded: () -> Unit,
    onToggleApp: () -> Unit,
    onToggleProcess: (DenyListProcessUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnyChecked = item.checkedCount > 0

    val gradientColors = when {
        item.expanded -> listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
        )
        isAnyChecked -> listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        )
        else -> listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
        )
    }

    val cardShape = MagiskUiDefaults.ExtraLargeShape

    MagiskListCard(
        expanded = item.expanded,
        onClick = onToggleExpanded,
        modifier = modifier,
        shape = cardShape,
        backgroundBrush = Brush.linearGradient(colors = gradientColors)
    ) { cardMotion ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                PremiumIconContainer(
                    size = 56.dp,
                    shape = CircleShape,
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    val iconPainter = remember(item.packageName, item.icon) {
                        BitmapPainter(item.icon.toBitmap().asImageBitmap())
                    }
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                if (isAnyChecked) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
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
                    text = item.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isAnyChecked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${item.checkedCount}/${item.processes.size} ATTIVI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = item.expanded,
                enter = MagiskMotion.expandedControlEnter(),
                exit = MagiskMotion.expandedControlExit()
            ) {
                TriStateCheckbox(
                    state = item.selectionState,
                    onClick = onToggleApp,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            }

            MagiskListCardChevron(
                motion = cardMotion,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        AnimatedVisibility(
            visible = item.expanded,
            enter = MagiskMotion.expandablePanelEnter(),
            exit = MagiskMotion.expandablePanelExit()
        ) {
            Column {
                Spacer(Modifier.height(24.dp))
                item.processes.forEach { process ->
                    val processShape = RoundedCornerShape(16.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(processShape)
                            .clickable { onToggleProcess(process) },
                        color = if (process.enabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)
                        },
                        shape = processShape
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = process.enabled,
                                onCheckedChange = { onToggleProcess(process) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = process.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (process.enabled) {
                                        FontWeight.Black
                                    } else {
                                        FontWeight.Medium
                                    },
                                    color = if (process.enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (process.packageName != item.packageName) {
                                    Text(
                                        text = process.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
