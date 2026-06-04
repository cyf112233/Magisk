package com.topjohnwu.magisk.ui.theme

import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
fun enableMaterial3ExpressiveFlags() {
    ComposeMaterial3Flags.isCheckboxStylingFixEnabled = true
    ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
    ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled = true
    ComposeMaterial3Flags.isAnchoredDraggableComponentsStrictOffsetCheckEnabled = true
    ComposeMaterial3Flags.isAnchoredDraggableComponentsInvalidationFixEnabled = true
    ComposeMaterial3Flags.isBottomSheetPartiallyExpandedDeterministicEnabled = true
}
