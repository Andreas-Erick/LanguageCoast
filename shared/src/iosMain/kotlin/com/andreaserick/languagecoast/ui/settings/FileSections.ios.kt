package com.andreaserick.languagecoast.ui.settings

import androidx.compose.runtime.Composable

// Picking files isn't built for iPhone yet.

@Composable
actual fun CardTransferSection() {
    Hint("Importing and exporting cards isn't available on iPhone yet.")
}

@Composable
actual fun BackupSection() {
    Hint("Backups aren't available on iPhone yet.")
}
