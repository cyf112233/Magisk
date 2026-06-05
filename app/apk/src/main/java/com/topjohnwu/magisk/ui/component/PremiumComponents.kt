package com.topjohnwu.magisk.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.topjohnwu.magisk.ui.animation.ExpandableCardMotion
import com.topjohnwu.magisk.ui.animation.MagiskMotion
import com.topjohnwu.magisk.ui.animation.rememberExpandableCardMotion
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun PremiumIconContainer(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    border: BorderStroke? = null,
    backgroundBrush: Brush? = null,
    backgroundColor: Color = Color.Transparent,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .size(size)
            .run {
                if (backgroundBrush != null) {
                    background(backgroundBrush, shape)
                } else {
                    this
                }
            },
        shape = shape,
        color = if (backgroundBrush != null) Color.Transparent else backgroundColor,
        border = border
    ) {
        Box(contentAlignment = contentAlignment, content = content)
    }
}

@Composable
fun MagiskListCard(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MagiskUiDefaults.ExtraLargeShape,
    backgroundBrush: Brush? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    expandedElevation: Dp = MagiskUiDefaults.ExpandedCardElevation,
    collapsedElevation: Dp = MagiskUiDefaults.CardElevation,
    expandedScale: Float = 1f,
    collapsedScale: Float = 1f,
    showWatermark: Boolean = false,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.(ExpandableCardMotion) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = rememberExpandableCardMotion(
        expanded = expanded,
        expandedElevation = expandedElevation,
        collapsedElevation = collapsedElevation,
        expandedScale = expandedScale,
        collapsedScale = collapsedScale
    )

    val solidColor = backgroundColor.copy(alpha = 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(motion.scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = solidColor,
        shadowElevation = motion.elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = MagiskMotion.cardContentSpring())
                .clip(shape)
                .run {
                    if (backgroundBrush != null) {
                        background(backgroundBrush)
                    } else if (backgroundColor.alpha < 1f) {
                        background(backgroundColor)
                    } else {
                        this
                    }
                }
        ) {
            if (showWatermark) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_magisk_outline),
                    contentDescription = null,
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-30).dp)
                        .alpha(0.04f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            backgroundContent()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
            ) {
                content(motion)
            }
        }
    }
}

@Composable
fun MagiskListCardChevron(
    motion: ExpandableCardMotion,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
        contentDescription = null,
        modifier = modifier.rotate(motion.rotation),
        tint = MaterialTheme.colorScheme.outline
    )
}
