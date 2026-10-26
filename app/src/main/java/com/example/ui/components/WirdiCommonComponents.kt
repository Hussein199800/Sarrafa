package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.DhikrItem
import com.example.domain.model.ReadingFontFamily
import com.example.domain.util.NumeralFormatter
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.WirdiDesignTokens
import com.example.ui.theme.buildArabicReadingTextStyle

/**
 * Balanced Contemporary Serenity Header (24sp calm title shrinking gently to 20sp on scroll).
 * Respects system reduce-motion setting.
 */
@Composable
fun WirdiLargeTitleHeader(
    title: String,
    subtitle: String? = null,
    isCollapsed: Boolean = false,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val titleFontSize by animateFloatAsState(
        targetValue = if (isCollapsed) 19f else 24f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "calmHeaderTitleSize"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (isCollapsed) 6.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = titleFontSize.sp,
                        lineHeight = (titleFontSize * 1.4f).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank() && !isCollapsed) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}

/**
 * Contemporary Serenity Section Card (22dp corner radius, flat surface integrated into background,
 * 0.5dp subtle border, zero heavy elevation).
 */
@Composable
fun InsetGroupedSection(
    modifier: Modifier = Modifier,
    headerTitle: String? = null,
    footerNote: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!headerTitle.isNullOrBlank()) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
        if (!footerNote.isNullOrBlank()) {
            Text(
                text = footerNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun InsetGroupedDivider(startIndent: Dp = 56.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = WirdiDesignTokens.hairlineBorderWidth,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Inset Grouped Row with a 14dp rounded soft-tinted icon badge, title, optional subtitle/value, and RTL chevron.
 */
@Composable
fun InsetGroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    trailingText: String? = null,
    valueText: String? = trailingText,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .let {
            if (onClick != null) it.clickable(onClick = onClick) else it
        }
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(WirdiDesignTokens.smallCornerRadius))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            if (trailingContent != null) {
                trailingContent()
            }
            if (showChevron) {
                Icon(
                    imageVector = WirdiIcons.ChevronStart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Custom rounded capsule toggle switch with calm 180ms animation and >= 48dp touch target.
 */
@Composable
fun WirdiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "switchThumb"
    )
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .semantics { role = Role.Switch }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(trackColor)
                .border(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

/**
 * Segmented Control for switching between sections cleanly.
 */
@Composable
fun <T> WirdiSegmentedControl(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    testTagProvider: ((T) -> String)? = null,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = 0.dp,
                    border = if (isSelected) BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant) else null,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(CircleShape)
                        .let { m ->
                            val tag = testTagProvider?.invoke(item)
                            if (tag != null) m.testTag(tag) else m
                        }
                        .clickable { onSelect(item) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = labelProvider(item),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thin smooth circular progress ring used in Hero Countdown, Adhkar Category cards, and Tasbih.
 */
@Composable
fun WirdiCircularProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    centerContent: @Composable () -> Unit = {}
) {
    val reduceMotion = LocalReduceMotion.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "ringProgress"
    )

    Box(
        modifier = if (size != null) modifier.size(size) else modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        centerContent()
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String, onCopied: () -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    onCopied()
}

fun shareText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$text\n\n— عبر تطبيق وِرْدِي | Wirdi")
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_via))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

/**
 * Refined, quiet dedication banner/card honoring Hussein Mohammed Ali Al-Taweel and his parents
 * with contemporary serenity and visual dignity.
 */
@Composable
fun WirdiRefinedDedicationNote(
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dedication_hussein_al_taweel")
    ) {
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = WirdiIcons.HeartDua,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.dedication_footer_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = WirdiIcons.HeartDua,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.dedication_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.dedication_prayer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DhikrInteractiveCard(
    dhikr: DhikrItem,
    currentCount: Int,
    isFavorite: Boolean,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    onTapCount: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopiedToast: () -> Unit,
    onEditCustom: (() -> Unit)? = null,
    onDeleteCustom: (() -> Unit)? = null,
    onOpenFocusSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCompleted = currentCount >= dhikr.repeat
    val progress = (currentCount.toFloat() / dhikr.repeat.coerceAtLeast(1)).coerceIn(0f, 1f)

    val fullShareText = buildString {
        append(dhikr.text)
        if (!dhikr.virtue.isNullOrBlank()) {
            append("\n\nالفضل: ${dhikr.virtue}")
        }
        if (!dhikr.source.isNullOrBlank()) {
            append("\nالمصدر: ${dhikr.source}")
        }
        if (!dhikr.grade.isNullOrBlank()) {
            append(" (${dhikr.grade})")
        }
    }

    Surface(
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isCompleted) 1.dp else WirdiDesignTokens.hairlineBorderWidth,
            color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dhikr_card_${dhikr.id}")
            .semantics {
                contentDescription = "بطاقة ذكر، التكرار الحالي $currentCount من ${dhikr.repeat}"
            }
            .clip(RoundedCornerShape(WirdiDesignTokens.cardCornerRadius))
            .combinedClickable(
                onClick = onTapCount,
                onLongClick = {
                    copyTextToClipboard(context, "ذكر", fullShareText, onCopiedToast)
                }
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WirdiCircularProgressRing(
                        progress = progress,
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        val countStr = NumeralFormatter.format(currentCount, useArabicIndic)
                        Text(
                            text = countStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val targetStr = NumeralFormatter.format(dhikr.repeat, useArabicIndic)
                    Text(
                        text = "من $targetStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenFocusSession != null) {
                        IconButton(
                            onClick = onOpenFocusSession,
                            modifier = Modifier.testTag("session_btn_${dhikr.id}")
                        ) {
                            Icon(
                                imageVector = WirdiIcons.FocusSession,
                                contentDescription = stringResource(R.string.session_start_button),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("fav_btn_${dhikr.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "إضافة أو إزالة من المفضلة",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { copyTextToClipboard(context, "ذكر", fullShareText, onCopiedToast) },
                        modifier = Modifier.testTag("copy_btn_${dhikr.id}")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ الذكر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { shareText(context, fullShareText) },
                        modifier = Modifier.testTag("share_btn_${dhikr.id}")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة الذكر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (dhikr.isCustom && onEditCustom != null && onDeleteCustom != null) {
                        IconButton(onClick = onEditCustom) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_custom_dhikr),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDeleteCustom) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_custom_dhikr),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = dhikr.text,
                style = buildArabicReadingTextStyle(
                    readingFont = readingFont,
                    scale = readingFontScale,
                    baseFontSizeSp = 22f
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!dhikr.virtue.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${stringResource(R.string.virtue_label)} ${dhikr.virtue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!dhikr.source.isNullOrBlank()) {
                    Text(
                        text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(dhikr.source, useArabicIndic)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!dhikr.grade.isNullOrBlank()) {
                    Text(
                        text = "${stringResource(R.string.grade_label)} ${NumeralFormatter.format(dhikr.grade, useArabicIndic)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
