package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.StudioLanguage
import com.example.ui.theme.LocalStudioAppearance

@Composable
fun StudioAmbientBackdrop(
    modifier: Modifier = Modifier
) {
    val appearance = LocalStudioAppearance.current
    val bgColor = MaterialTheme.colorScheme.background
    val cyan = MaterialTheme.colorScheme.primary
    val violet = MaterialTheme.colorScheme.secondary
    val emerald = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (appearance.liquidGlassEnabled) {
            val blurDp = appearance.glassBlurRadiusDp.coerceIn(0f, 28f).dp
            val orbAlpha = if (appearance.amoledDark) 0.16f else 0.24f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cyan.copy(alpha = orbAlpha), Color.Transparent),
                        center = Offset(size.width * 0.14f, size.height * 0.12f),
                        radius = size.minDimension * 0.68f
                    ),
                    radius = size.minDimension * 0.68f,
                    center = Offset(size.width * 0.14f, size.height * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(violet.copy(alpha = orbAlpha * 0.92f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.36f),
                        radius = size.minDimension * 0.64f
                    ),
                    radius = size.minDimension * 0.64f,
                    center = Offset(size.width * 0.88f, size.height * 0.36f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(emerald.copy(alpha = orbAlpha * 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.30f, size.height * 0.86f),
                        radius = size.minDimension * 0.56f
                    ),
                    radius = size.minDimension * 0.56f,
                    center = Offset(size.width * 0.30f, size.height * 0.86f)
                )
            }
        }
    }
}

@Composable
fun StudioGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    accentBorder: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val appearance = LocalStudioAppearance.current
    val baseColor = MaterialTheme.colorScheme.surfaceContainer
    val containerColor = when {
        appearance.liquidGlassEnabled && appearance.amoledDark -> baseColor.copy(alpha = 0.72f)
        appearance.liquidGlassEnabled -> baseColor.copy(alpha = 0.76f)
        else -> baseColor
    }
    val borderBrush = if (appearance.liquidGlassEnabled) {
        val rimHighlight = accentBorder ?: Color.White
        Brush.linearGradient(
            colors = listOf(
                rimHighlight.copy(alpha = if (accentBorder != null) 0.55f else 0.18f),
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                (accentBorder ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.20f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                accentBorder ?: MaterialTheme.colorScheme.outlineVariant,
                accentBorder ?: MaterialTheme.colorScheme.outlineVariant
            )
        )
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(if (accentBorder != null) 1.25.dp else 1.dp, borderBrush)
    ) {
        Column(content = content)
    }
}

@Composable
fun LanguagePickerDialog(
    title: String,
    languages: List<StudioLanguage>,
    selectedCode: String,
    onSelect: (StudioLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(languages, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) languages
        else languages.filter {
            it.name.lowercase().contains(q) ||
                it.nativeName.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .testTag("language_picker_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close language picker",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_search_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Search Hindi, Bengali, English, Spanish…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search language",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.code }) { lang ->
                        val isSelected = lang.code == selectedCode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelect(lang)
                                    onDismiss()
                                }
                                .testTag("language_option_${lang.code}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang.flag,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lang.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = lang.nativeName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
