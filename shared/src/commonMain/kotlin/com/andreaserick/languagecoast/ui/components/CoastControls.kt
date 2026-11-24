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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.andreaserick.languagecoast.ui.theme.tabular
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.ui.theme.AccentControl
import com.andreaserick.languagecoast.ui.theme.CardEdge
import com.andreaserick.languagecoast.ui.theme.ChipContainer
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.SurfaceCard
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.palette

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
 * A button label in two parts: what the tap does, then a quieter detail about it ("Start", "about 5 minutes";
 * "Download", "1.2 GB"). The action never shrinks; when space runs out the detail gives way first.
 * Use it inside a button's content instead of joining the two with " · ".
 */
@Composable
fun RowScope.ButtonLabel(action: String, detail: String?) {
    // Only at very large text sizes is the action wider than the button; then it ends in an ellipsis, not mid-letter.
    Text(action, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    if (!detail.isNullOrEmpty()) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = detail,
            style = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
            color = LocalContentColor.current.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * A button label with a count: what the tap does, then the count in a small badge, the way a mail app
 * shows unread mail ("Review the coast [6]"). One weight of type, so the label and the number don't look
 * like two fonts; the badge says what the number is without a second word.
 *
 * The action gives way (with an ellipsis) before the badge does, so the count still shows at large text sizes.
 *
 * @param countDescription The count in words for a screen reader ("6 due"), read after [action].
 * @param badgeTextColor The number's colour: the button's container colour, so it reads as cut out of the
 *     badge. [Sunlit] suits a [CoastButton]; pass the container of any other button.
 */
@Composable
fun RowScope.ButtonCountLabel(action: String, count: Int, countDescription: String, badgeTextColor: Color = Sunlit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Not a merge of its own: the button merges this description into its one focusable node.
        modifier = Modifier
            .weight(1f, fill = false)
            .clearAndSetSemantics { contentDescription = "$action, $countDescription" }
    ) {
        Text(
            text = action,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                .background(LocalContentColor.current, CircleShape)
                .padding(horizontal = 7.dp)
        ) {
            Text(
                text = if (count > 999) "999+" else "$count",
                style = MaterialTheme.typography.labelMedium.tabular,
                color = badgeTextColor,
                maxLines = 1
            )
        }
    }
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
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    interactionSource: MutableInteractionSource? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.medium
    val colors = coastTextFieldColors()
    // Material's TextField, built from its parts so that the focus outline goes on the container only: on
    // the whole field it would also box in the supporting text below. Everything else is as TextField does it,
    // supporting text included, which stays part of the field for screen readers.
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight),
            readOnly = readOnly,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = colors.focusedTextColor)),
            cursorBrush = SolidColor(colors.cursorColor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = source,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = source,
                    label = label,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    supportingText = supportingText,
                    shape = shape,
                    colors = colors,
                    container = {
                        TextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = source,
                            modifier = if (focused) Modifier.border(BorderStroke(1.5.dp, AccentControl), shape) else Modifier,
                            colors = colors,
                            shape = shape
                        )
                    }
                )
            }
        )
    }
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
    focusedLabelColor = SunlitText,
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
    selectedLabelColor = OnSunlit
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
 * @param onClickLabel What a tap does, for screen readers ("open", "edit card"); they say it after "double-tap to".
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
    val edge = BorderStroke(1.dp, CardEdge)
    val body: @Composable ColumnScope.() -> Unit = { Column(modifier = Modifier.padding(padding), content = content) }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, border = edge, content = body)
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().tappableCardSemantics(onClickLabel),
            colors = colors,
            shape = shape,
            border = edge,
            content = body
        )
    }
}

/**
 * Tells screen readers that a tappable card is a button and, with [label], what a tap does. The card's own
 * click stays in place: an action without a function only adds its name to it.
 */
private fun Modifier.tappableCardSemantics(label: String?): Modifier = semantics {
    role = Role.Button
    if (label != null) onClick(label = label, action = null)
}

/** A small rounded label, such as "7 due". The accent by default; pass colors for anything that isn't a call to action. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Sunlit,
    content: Color = OnSunlit
) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content, modifier = modifier) {
        Text(
            text = text,
            // Tabular, so a count that changes ("2/5" to "3/5") doesn't change the pill's width.
            style = MaterialTheme.typography.labelSmall.tabular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
 * An emoji or flag inside an [IconTile]. Sized in dp, not sp: it is an icon, so it keeps its size in the tile
 * at any font scale instead of growing out of it. Half the tile by default, the same in every tile.
 */
@Composable
fun TileEmoji(emoji: String, tileSize: Dp = 44.dp) {
    val size = with(LocalDensity.current) { (tileSize * 0.5f).toSp() }
    Text(emoji, fontSize = size, lineHeight = size, maxLines = 1)
}

/**
 * A compact switch between two or three modes of the same view. Selection is a lighter tile and a brighter
 * label, without the accent: choosing a mode isn't the main action of a screen.
 *
 * A label that doesn't fit its tile wraps to a second line and then shrinks a little, before it is cut;
 * every tile is as tall as the tallest, so a wrapped label doesn't leave the selected tile short.
 *
 * @param container The strip behind the tiles. The card tone suits the screen background; on a surface of
 *     that tone already (a bottom sheet) pass the field tone, or the strip disappears and the selected
 *     tile floats on its own.
 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = SurfaceCard
) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(3.dp)
            .height(IntrinsicSize.Min)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) SurfaceSelected else Color.Transparent)
                    // The lighter fill alone is under 3:1 in the light palettes; a hairline edge makes the pick plain
                    // there. The dark palettes' fill stands out on its own, and an outline would only add noise.
                    .then(if (selected && !palette.isDark) Modifier.border(1.dp, TextSecondary, RoundedCornerShape(9.dp)) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = {
                        if (!selected && hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    })
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val labelStyle = MaterialTheme.typography.labelMedium
                Text(
                    text = option,
                    style = labelStyle,
                    color = if (selected) TextPrimary else TextSecondary,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = labelStyle.fontSize),
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Shown in place of a screen's content when it has none yet: an icon, what's going on, and optionally what to do.
 * Centred while it fits; in a short window (landscape, split screen, large text) it scrolls instead of being cut.
 */
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
            .verticalScroll(rememberScrollState())
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
