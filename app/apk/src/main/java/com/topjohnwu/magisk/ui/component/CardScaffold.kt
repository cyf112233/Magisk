package com.topjohnwu.magisk.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.topjohnwu.magisk.ui.animation.ExpandableCardMotion
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.animation.rememberExpandableCardMotion

@Composable
fun MagiskCard(
    modifier: Modifier = Modifier,
    shape: Shape = MagiskUiDefaults.ExtraLargeShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = MagiskUiDefaults.CardElevation,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.elevatedCardColors(
        containerColor = containerColor,
        contentColor = contentColor
    )
    val cardElevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)

    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = cardElevation,
            content = content
        )
    } else {
        ElevatedCard(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = cardElevation,
            content = content
        )
    }
}

@Composable
fun MagiskExpandableCard(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MagiskUiDefaults.ExtraLargeShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    expandedElevation: Dp = MagiskUiDefaults.ExpandedCardElevation,
    collapsedElevation: Dp = MagiskUiDefaults.CardElevation,
    expandedScale: Float = 1f,
    collapsedScale: Float = 0.992f,
    content: @Composable ColumnScope.(ExpandableCardMotion) -> Unit
) {
    val motion = rememberExpandableCardMotion(
        expanded = expanded,
        expandedElevation = expandedElevation,
        collapsedElevation = collapsedElevation,
        expandedScale = expandedScale,
        collapsedScale = collapsedScale
    )

    MagiskCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MagiskMotion.cardContentSpring())
            .scale(motion.scale),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = motion.elevation,
        onClick = onClick
    ) {
        content(motion)
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    shape: Shape = MagiskUiDefaults.ExtraLargeShape,
    border: BorderStroke? = null,
    backgroundBrush: Brush? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = MagiskUiDefaults.CardElevation,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val cardModifier = if (border != null) modifier.border(border, shape) else modifier

    MagiskCard(
        modifier = cardModifier,
        shape = shape,
        containerColor = if (backgroundBrush != null) Color.Transparent else backgroundColor,
        contentColor = contentColor,
        elevation = elevation,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .run {
                    if (backgroundBrush != null) {
                        background(backgroundBrush)
                    } else {
                        this
                    }
                },
            content = content
        )
    }
}

@Composable
fun PremiumExpandableCard(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MagiskUiDefaults.ExtraLargeShape,
    border: BorderStroke? = null,
    backgroundBrush: Brush? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    expandedElevation: Dp = MagiskUiDefaults.ExpandedCardElevation,
    collapsedElevation: Dp = MagiskUiDefaults.CardElevation,
    expandedScale: Float = 1f,
    collapsedScale: Float = 0.992f,
    content: @Composable BoxScope.(ExpandableCardMotion) -> Unit
) {
    val motion = rememberExpandableCardMotion(
        expanded = expanded,
        expandedElevation = expandedElevation,
        collapsedElevation = collapsedElevation,
        expandedScale = expandedScale,
        collapsedScale = collapsedScale
    )
    val cardModifier = if (border != null) modifier.border(border, shape) else modifier

    MagiskCard(
        modifier = cardModifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MagiskMotion.cardContentSpring())
            .scale(motion.scale),
        shape = shape,
        containerColor = if (backgroundBrush != null) Color.Transparent else backgroundColor,
        contentColor = contentColor,
        elevation = motion.elevation,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .run {
                    if (backgroundBrush != null) {
                        background(backgroundBrush)
                    } else {
                        this
                    }
                }
        ) {
            content(motion)
        }
    }
}
