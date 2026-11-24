package com.andreaserick.languagecoast.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * The state for a fully expanded bottom sheet that holds work which a stray swipe, scrim tap or back press
 * shouldn't throw away. While [guarded] is true the sheet refuses to hide and calls [onBlocked] instead,
 * which should ask the user (a "Discard changes?" dialog); the sheet is then closed by leaving composition.
 *
 * Asking from onDismissRequest is too late: the sheet has already slid away by then, so picking
 * "Keep editing" would leave an invisible sheet that blocks the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberGuardedSheetState(guarded: Boolean, onBlocked: () -> Unit): SheetState {
    val guardedNow by rememberUpdatedState(guarded)
    val onBlockedNow by rememberUpdatedState(onBlocked)
    return rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && guardedNow) {
                onBlockedNow()
                false
            } else true
        }
    )
}
