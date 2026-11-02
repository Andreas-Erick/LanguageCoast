package com.andreaserick.languagecoast.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import org.koin.compose.viewmodel.koinViewModel

/** The month so far in numbers, with a button that shares it as a picture. */
@Composable
fun RecapScreen(onNavigateBack: () -> Unit, viewModel: RecapViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val share = rememberRecapSharer()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "This month", subtitle = uiState.title.takeIf { !uiState.isLoading }, onBack = onNavigateBack)
        if (uiState.isLoading) return@Column

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin, vertical = 12.dp)
        ) {
            Text(
                text = recapHeadline(uiState),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatTile("${uiState.daysStudied}", "of ${uiState.dayOfMonth} days", Modifier.weight(1f).fillMaxHeight())
                StatTile("${uiState.cardsReviewed}", "cards reviewed", Modifier.weight(1f).fillMaxHeight())
                StatTile("${uiState.streakCount}", "day streak", Modifier.weight(1f).fillMaxHeight())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatTile("${uiState.cardsSettled}", "cards that stuck", Modifier.weight(1f).fillMaxHeight())
                StatTile("${uiState.newIslands}", "new islands", Modifier.weight(1f).fillMaxHeight())
                StatTile("${uiState.leakyCount}", "leaky words", Modifier.weight(1f).fillMaxHeight())
            }

            if (uiState.coasts.isNotEmpty()) {
                ContentCard {
                    Text("By coast", style = MaterialTheme.typography.titleMedium)
                    uiState.coasts.forEachIndexed { index, coast ->
                        if (index > 0) HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 10.dp)) else Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(coast.flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(coast.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(plural(coast.cardsReviewed, "card"), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }

            Text(
                text = "Cards that stuck are due again more than a month from now. Leaky words are the ones you keep forgetting.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (share != null) {
                CoastButton(
                    onClick = {
                        share(
                            RecapShare(
                                title = uiState.title,
                                headline = recapHeadline(uiState),
                                stats = listOf(
                                    "${uiState.daysStudied}" to "of ${uiState.dayOfMonth} days studied",
                                    "${uiState.cardsReviewed}" to "cards reviewed",
                                    "${uiState.cardsSettled}" to "cards that stuck",
                                    "${uiState.streakCount}" to "day streak"
                                ),
                                coasts = uiState.coasts.map { "${it.flag} ${it.name} · ${plural(it.cardsReviewed, "card")}" }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight).padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Share as a picture")
                }
            }
        }
    }
}

/** One line that sums the month up. */
internal fun recapHeadline(state: RecapUiState): String = when {
    state.cardsReviewed == 0 -> "Nothing reviewed yet this month. The coast is waiting."
    state.daysStudied >= state.dayOfMonth -> "Every single day so far. Remarkable."
    state.daysStudied >= state.dayOfMonth * 2 / 3 -> "Most days on the coast. The words are sticking."
    state.daysStudied >= 3 -> "${plural(state.daysStudied, "study day")} and ${plural(state.cardsReviewed, "card")} so far."
    else -> "A quiet month so far. One island a day turns the tide."
}

/** A large [value] with a short [label] below it. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    ContentCard(modifier = modifier, padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** What the shared picture of a month shows. */
data class RecapShare(val title: String, val headline: String, val stats: List<Pair<String, String>>, val coasts: List<String>)

/** A way to draw a [RecapShare] as a picture and hand it to another app, or null where that isn't possible. */
@Composable
expect fun rememberRecapSharer(): ((RecapShare) -> Unit)?
