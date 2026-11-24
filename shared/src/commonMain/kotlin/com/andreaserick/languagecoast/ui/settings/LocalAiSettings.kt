package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.DeviceHardwareProfile
import com.andreaserick.languagecoast.data.ModelFitLevel
import com.andreaserick.languagecoast.data.ModelInstallStatus
import com.andreaserick.languagecoast.data.ModelRecommendation
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.ButtonLabel
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.SurfBlue
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.tabular
import kotlin.math.roundToInt

// Local AI in Settings: what the phone can run, the models that fit, and downloading them.

/**
 * Local AI: what the phone can run, the models to download for it, and the Hugging Face access code some of them
 * need. What the phone is made of is folded away under "Phone details": the models are already sorted by it,
 * so most people never need to read it.
 *
 * @param onDeleteModel Asks to delete the installed model with that id; the screen confirms first.
 */
@Composable
internal fun LocalAiHubSection(uiState: SettingsUiState, viewModel: SettingsViewModel, onDeleteModel: (String) -> Unit) {
    Hint(
        "An AI model that runs on your phone: free, private, and offline once it is downloaded. Unlike on-device " +
            "translation it also sorts cards onto islands and suggests other ways to say a sentence. " +
            "A small model is slower than an online AI and makes more mistakes."
    )
    Spacer(modifier = Modifier.height(14.dp))

    val profile = uiState.hardwareProfile
    var phoneDetailsOpen by rememberSaveable { mutableStateOf(false) }
    if (profile != null) {
        CollapsibleRow(
            title = "Phone details",
            subtitle = profile.tier.label,
            expanded = phoneDetailsOpen,
            onToggle = { phoneDetailsOpen = !phoneDetailsOpen }
        ) {
            HardwareProfileCard(
                profile = profile,
                isScanning = uiState.isScanningHardware,
                onRescan = viewModel::scanHardware
            )
        }
    } else {
        CoastSecondaryButton(
            onClick = viewModel::scanHardware,
            // Dims while the check runs, so a second tap doesn't start another and the label says it is under way.
            enabled = !uiState.isScanningHardware,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonHeight)
        ) {
            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isScanningHardware) "Checking this phone…" else "Check what this phone can run")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    FieldLabel("Models for this phone")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        uiState.modelRecommendations.forEach { recommendation ->
            val model = recommendation.model
            val installStatus = uiState.modelInstallStates[model.id] ?: ModelInstallStatus.NotInstalled
            val isActive = uiState.localAiModel == model.id
            ModelRecommendationCard(
                recommendation = recommendation,
                installStatus = installStatus,
                isActive = isActive,
                hasToken = uiState.savedHuggingFaceToken.isNotBlank(),
                onInstall = { viewModel.installLocalModel(model.id) },
                onCancel = { viewModel.cancelLocalModelInstall(model.id) },
                onSelect = { viewModel.setLocalAiModel(model.id) },
                onDelete = { onDeleteModel(model.id) }
            )
        }
    }

    if (uiState.modelRecommendations.any { it.model.gated }) {
        Spacer(modifier = Modifier.height(20.dp))
        ApiKeySection(
            title = "Hugging Face access code (only some models need one)",
            hint = "Some models need a free Hugging Face account: accept the model's licence on huggingface.co, then create a read " +
                "access code and paste it here (\"Get an access code\" opens the page). It is stored only on this device and sent " +
                "to Hugging Face only when downloading.",
            placeholder = "hf_...",
            savedKey = uiState.savedHuggingFaceToken,
            justSaved = uiState.huggingFaceTokenSaved,
            onSave = viewModel::saveHuggingFaceToken,
            onEdited = viewModel::onHuggingFaceTokenEdited,
            onRemove = viewModel::removeHuggingFaceToken,
            noun = "access code",
            shortNoun = "access code",
            getUrl = "https://huggingface.co/settings/tokens"
        )
    }
}

/** What the check of the phone found: its processor, memory and free storage, and what that is enough for. */
@Composable
internal fun HardwareProfileCard(
    profile: DeviceHardwareProfile,
    isScanning: Boolean,
    onRescan: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised, contentColor = TextPrimary),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.deviceName,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${profile.socOrProcessor} · ${profile.cpuCores} cores",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Pill(text = profile.tier.label, container = SurfaceSelected, content = TextPrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceSelected)
            Spacer(modifier = Modifier.height(12.dp))

            val ramRatio = if (profile.totalRamMb > 0) {
                ((profile.totalRamMb - profile.availableRamMb).toFloat() / profile.totalRamMb).coerceIn(0.05f, 0.95f)
            } else 0.5f

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Memory in use, ${profile.availableRamGbText} free of ${profile.totalRamGbText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ramRatio },
                color = SurfBlue,
                trackColor = SurfaceSelected,
                gapSize = 0.dp,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Free storage: ${profile.freeStorageGbText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = profile.tier.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRescan,
                    enabled = !isScanning,
                    // Through the button's colours, so it dims while a check runs instead of looking tappable.
                    colors = ButtonDefaults.textButtonColors(contentColor = SunlitText),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScanning) "Checking…" else "Check again", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A card for one local model: how well it fits the phone, and the download, its progress, "use" and delete. */
@Composable
internal fun ModelRecommendationCard(
    recommendation: ModelRecommendation,
    installStatus: ModelInstallStatus,
    isActive: Boolean,
    hasToken: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val model = recommendation.model
    val isBestMatch = recommendation.fitLevel == ModelFitLevel.BEST_MATCH
    val isInstalled = installStatus is ModelInstallStatus.Installed

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised, contentColor = TextPrimary),
        border = if (isActive && isInstalled) BorderStroke(1.5.dp, Seafoam) else null,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FitBadge(fitLevel = recommendation.fitLevel)

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        // What a learner can use: whose model it is, how big the download is, how much memory it needs.
                        text = "${model.family} · ${model.sizeLabel} · ${model.minRamLabel}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isInstalled) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${model.name}",
                            tint = CoralText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = model.summary,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = recommendation.reason,
                color = if (recommendation.fitLevel == ModelFitLevel.NOT_RECOMMENDED) CoralText else TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (installStatus) {
                is ModelInstallStatus.NotInstalled -> {
                    val needsToken = model.gated && !hasToken
                    DownloadButton(
                        label = when {
                            needsToken -> "Needs a Hugging Face access code"
                            !recommendation.hasEnoughStorage -> "Not enough free storage"
                            else -> "Download"
                        },
                        detail = model.sizeLabel.takeIf { !needsToken && recommendation.hasEnoughStorage },
                        enabled = recommendation.hasEnoughStorage && !needsToken,
                        primary = isBestMatch,
                        onClick = onInstall
                    )
                }
                is ModelInstallStatus.Downloading -> {
                    val percent = (installStatus.progress * 100).roundToInt().coerceIn(0, 100)
                    val downloadedMb = installStatus.downloadedMb
                    val totalMb = installStatus.totalMb.coerceAtLeast(1L)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                CircularProgressIndicator(
                                    color = SunlitText,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    // The percentage first: on a narrow screen the megabytes are what gets cut.
                                    text = "$percent%, $downloadedMb of $totalMb MB",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelSmall.tabular,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            var confirmCancel by remember { mutableStateOf(false) }
                            if (confirmCancel) {
                                AlertDialog(
                                    onDismissRequest = { confirmCancel = false },
                                    title = { Text("Stop downloading ${model.name}?") },
                                    text = { Text("What has downloaded so far is thrown away.", color = TextSecondary) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            confirmCancel = false
                                            onCancel()
                                        }) { Text("Stop download", color = CoralText, style = MaterialTheme.typography.labelMedium) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { confirmCancel = false }) {
                                            Text("Keep downloading", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                )
                            }
                            TextButton(
                                onClick = { confirmCancel = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = CoralText, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = CoralText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { installStatus.progress.coerceIn(0f, 1f) },
                            color = Sunlit,
                            trackColor = SurfaceSelected,
                            gapSize = 0.dp,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        // A download that stands still is most often this, and nothing else on the card says so.
                        Text(
                            text = "It waits while there is no Wi-Fi, unless you allowed mobile data.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                is ModelInstallStatus.Installed -> {
                    if (isActive) {
                        // As tall as the "Use this model" button it replaces, so the card doesn't shrink on the tap.
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = SurfaceSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonHeight)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                // The Surface passes its minimum height on, so the row fills it and stays centred.
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Seafoam, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "In use for Local AI",
                                    color = Seafoam,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        CoastSecondaryButton(
                            onClick = onSelect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonHeight)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use this model")
                        }
                    }
                }
                is ModelInstallStatus.Failed -> {
                    Text(
                        text = installStatus.message,
                        color = CoralText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    DownloadButton(label = "Try again", detail = model.sizeLabel, enabled = true, primary = false, onClick = onInstall)
                }
            }
        }
    }
}

/** The button that starts a model download: one line, never clipped, with the accent only for the best match. */
@Composable
internal fun DownloadButton(label: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit, detail: String? = null) {
    CoastButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = SurfaceSelected, contentColor = TextPrimary),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        if (detail == null) Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) else ButtonLabel(label, detail)
    }
}

@Composable
internal fun FitBadge(fitLevel: ModelFitLevel) {
    val (container, content) = when (fitLevel) {
        ModelFitLevel.BEST_MATCH -> Sunlit to OnSunlit
        ModelFitLevel.GREAT_FIT -> SurfaceSelected to Seafoam
        ModelFitLevel.HEAVY_FIT -> SurfaceSelected to TextPrimary
        // Coral is too faint on this fill; the reason under the model's name carries the warning colour.
        ModelFitLevel.NOT_RECOMMENDED -> SurfaceSelected to TextPrimary
    }
    Pill(text = fitLevel.label, container = container, content = content)
}
