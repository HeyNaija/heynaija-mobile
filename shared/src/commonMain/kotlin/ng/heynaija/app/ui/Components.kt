package ng.heynaija.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

/**
 * A screen's bar: back (when there is somewhere to go back to), a title, and
 * actions on the right, over a hairline.
 */
@Composable
fun TopBar(
    title: String?,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.Back,
    actions: @Composable RowScope.() -> Unit = {},
    content: (@Composable () -> Unit)? = null,
) {
    val colors = HeyNaija.colors
    Column(Modifier.background(colors.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(backIcon, "Back", Modifier.size(24.dp), tint = colors.ink) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                if (content != null) content()
                else if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        HorizontalDivider(color = colors.line)
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled && !busy,
        shape = Radii.control,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HeyNaija.colors.accentInk)
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = HeyNaija.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        enabled = enabled,
        shape = Radii.control,
        border = BorderStroke(1.dp, if (danger) colors.danger.copy(alpha = 0.35f) else colors.lineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) colors.danger else colors.ink),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** A text field in the app's style: hairline edge, green when focused. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = HeyNaija.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it, color = colors.faint) } },
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            shape = Radii.control,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.lineStrong,
                errorBorderColor = colors.danger,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                cursorColor = colors.accent,
            ),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    PostListSkeleton(modifier)
}

/** Something that would not load, with a way to try again. Scrollable, so pulling down retries too. */
@Composable
fun Problem(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).fillMaxWidth().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.ink, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", onRetry)
            }
        }
    }
}

/** A quiet line where a list has nothing in it. */
@Composable
fun Empty(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = HeyNaija.colors.muted,
        textAlign = TextAlign.Center,
    )
}

/** A row in a settings-like list: an icon, a label, and what pressing it does. */
@Composable
fun MenuRow(
    label: String,
    icon: ImageVector? = null,
    detail: String? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    val colors = HeyNaija.colors
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (danger) colors.danger else colors.muted)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) colors.danger else colors.ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
        trailing?.invoke()
    }
}

/** A small round count, as on the Notifications tab. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    Box(
        modifier.defaultMinSize(minWidth = 16.dp, minHeight = 16.dp).clip(CircleShape).background(colors.accent).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize * 0.85f), color = colors.accentInk)
    }
}

/** A section heading inside a list. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = HeyNaija.colors.muted,
    )
}
