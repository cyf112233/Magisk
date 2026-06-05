package com.topjohnwu.magisk.ui.log

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.component.PremiumCard
import com.topjohnwu.magisk.ui.component.MagiskUiDefaults
import com.topjohnwu.magisk.ui.terminal.ansiLogText
import com.topjohnwu.magisk.core.R as CoreR

@Composable
internal fun LogFilterSection(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilter: LogDisplayFilter,
    onFilterChange: (LogDisplayFilter) -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(id = CoreR.string.log_search_hint), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                shape = MagiskUiDefaults.MediumShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(40.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(22.dp))
                }
            }
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogDisplayFilter.entries.forEach {
                FilterChip(
                    selected = it == activeFilter,
                    onClick = { onFilterChange(it) },
                    label = { Text(stringResource(id = it.labelRes), fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
internal fun LogEventCard(item: MagiskLogUiItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val accent = item.level.color()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        contentModifier = Modifier.animateContentSize(MagiskMotion.cardContentSpring()),
        shape = MagiskUiDefaults.ExtraLargeShape,
        backgroundColor = if (item.isIssue) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        },
        onClick = { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Level Badge Container (Circle shape, matching the premium visual style of app icon containers)
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                contentColor = accent,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Top)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.level.shortLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }

            // Main Content Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.tag.ifBlank { item.sourceLabel },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (item.isIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (!expanded) {
                    Text(
                        text = item.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "PID: ${item.pid} | TID: ${item.tid}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MagiskUiDefaults.MediumShape, // Symmetrical 20.dp inner container shape
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = ansiLogText(item.message, MaterialTheme.colorScheme),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(item.message))
                                    context.toast(CoreR.string.copied_to_clipboard, Toast.LENGTH_SHORT)
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("COPIA", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
