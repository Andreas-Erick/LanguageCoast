package com.andreaserick.languagecoast.ui.settings

import androidx.compose.runtime.Composable

// Both sections read and write files the user picks, which each platform does its own way.

/** Importing cards from a file and exporting them to one. */
@Composable
expect fun CardTransferSection()

/** Backing everything up to a file and restoring it. */
@Composable
expect fun BackupSection()
