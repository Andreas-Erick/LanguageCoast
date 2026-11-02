package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.theme.ChipContainer
import com.andreaserick.languagecoast.ui.theme.OceanGround
import com.andreaserick.languagecoast.ui.theme.SurfaceCard
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** The side margin of every screen's content, which lines it up with the title. */
val ScreenMargin = 20.dp

/** The height of main actions, so they land the same on every screen. */
val ButtonHeight = 54.dp

/** The main action of a screen or section. Keep to one per view: it's what the accent colour is for. */
@Composable
fun CoastButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}

/** An action that sits next to or below the main one, without competing with it. */
@Composable
fun CoastSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    CoastButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceSelected, contentColor = TextPrimary),
        content = content
    )
}

/**
 * The app's text field: a soft filled box with no outline until it has focus. Translucent, so it reads
 * as a step lighter on both the background and a card.
 */
@Composable
fun CoastTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.medium
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(
            if (focused) Modifier.border(BorderStroke(1.5.dp, Sunlit), shape) else Modifier
        ),
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        interactionSource = source,
        shape = shape,
        colors = coastTextFieldColors()
    )
}

/** The colors of [CoastTextField]; also for Material's own fields, such as an exposed dropdown's. */
@Composable
fun coastTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = ChipContainer,
    unfocusedContainerColor = ChipContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = Sunlit,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    focusedLeadingIconColor = TextSecondary,
    unfocusedLeadingIconColor = TextSecondary,
    focusedTrailingIconColor = TextSecondary,
    unfocusedTrailingIconColor = TextSecondary,
    cursorColor = Sunlit
)

/** Filter chip colors: the accent shows which one of a group is picked. */
@Composable
fun coastChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = ChipContainer,
    labelColor = TextPrimary,
    selectedContainerColor = Sunlit,
    selectedLabelColor = OceanGround
)

/** A chip border that only exists to satisfy the API: chips are told apart by their fill. */
@Composable
fun coastChipBorder(enabled: Boolean, selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = enabled,
    selected = selected,
    borderColor = Color.Transparent,
    selectedBorderColor = Color.Transparent
)

/**
 * A group of content on a solid surface. Tappable when [onClick] is set, with the ripple kept inside its corners.
 *
 * @param padding The space between the card's edge and [content].
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = SurfaceCard, contentColor = TextPrimary)
    val shape = MaterialTheme.shapes.large
    val body: @Composable ColumnScope.() -> Unit = { Column(modifier = Modifier.padding(padding), content = content) }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, content = body)
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, content = body)
    }
}

/** A small rounded label, such as "7 due". The accent by default; pass colors for anything that isn't a call to action. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Sunlit,
    content: Color = OceanGround
) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** A rounded square holding an icon, an emoji or a language code at the start of a row or card. */
@Composable
fun IconTile(modifier: Modifier = Modifier, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(SurfaceRaised, MaterialTheme.shapes.small),
        content = { content() }
    )
}

/**
 * A compact switch between two or three modes of the same view. Selection is a lighter tile and a brighter
 * label, without the accent: choosing a mode isn't the main action of a screen.
 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(SurfaceCard, MaterialTheme.shapes.small)
            .padding(3.dp)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) SurfaceSelected else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    })
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/** Shown in place of a screen's content when it has none yet: an icon, what's going on, and optionally what to do. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(ScreenMargin)
    ) {
        IconTile(size = 64.dp) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), content = action)
        }
    }
}
