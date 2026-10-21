package com.andreaserick.languagecoast.ui.settings

import androidx.compose.runtime.Composable

// Picking files isn't built for iPhone yet, so Settings hides these sections (see fileTransferAvailable).

@Composable
actual fun CardTransferSection() = Unit

@Composable
actual fun BackupSection() = Unit
