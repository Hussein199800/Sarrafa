package com.example.ui.screens

import android.app.Activity
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CompassSensorReading
import com.example.domain.util.NumeralFormatter
import com.example.domain.util.PrayerTimeEngine
import com.example.domain.util.QiblaCalculator
import com.example.ui.WirdiUiState
import com.example.ui.components.InsetGroupedDivider
import com.example.ui.components.InsetGroupedSection
import com.example.ui.components.WirdiCircularProgressRing
import com.example.ui.components.WirdiIcons
import com.example.ui.components.WirdiSwitch
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.WirdiDesignTokens
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

@Composable
fun SubScreenTopBar(
    title: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBackButton) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("subscreen_back_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun TasbihSubScreen(
    uiState: WirdiUiState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onTapTasbih: () -> Unit,
    onResetCurrent: () -> Unit,
    onSetTarget: (Int) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val currentCount = uiState.persisted.tasbihCurrentCount
    val todayTotal = uiState.persisted.tasbihTodayTotal
    val target = settings.tasbihTarget

    var showCustomTargetDialog by remember { mutableStateOf(false) }
    var customTargetInput by remember { mutableStateOf("100") }
    var isFullScreenMode by rememberSaveable { mutableStateOf(false) }
    var isControlsLocked by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(settings.tasbihKeepScreenOn, context) {
        val activity = context as? Activity
        if (settings.tasbihKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val ringProgress = remember(currentCount, target) {
        if (target <= 0) {
            ((currentCount % 33).toFloat() / 33f).coerceIn(0f, 1f)
        } else {
            (currentCount.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubScreenTopBar(
            title = stringResource(R.string.more_tasbih),
            showBackButton = showBackButton,
            onBack = onBack,
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { isControlsLocked = !isControlsLocked },
                        modifier = Modifier.testTag("tasbih_lock_controls_btn")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.LockShield,
                            contentDescription = stringResource(R.string.tasbih_lock_controls),
                            tint = if (isControlsLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { isFullScreenMode = !isFullScreenMode },
                        modifier = Modifier.testTag("tasbih_fullscreen_toggle_btn")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.FocusSession,
                            contentDescription = stringResource(R.string.tasbih_fullscreen_mode),
                            tint = if (isFullScreenMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

        if (!isFullScreenMode) {
            Spacer(modifier = Modifier.height(6.dp))

            // Segmented target selector (disabled when controls are locked)
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = listOf(
                        Triple(33, stringResource(R.string.tasbih_target_33), "tasbih_target_33_chip"),
                        Triple(99, stringResource(R.string.tasbih_target_99), "tasbih_target_99_chip"),
                        Triple(0, stringResource(R.string.tasbih_target_open), "tasbih_target_open_chip")
                    )
                    options.forEach { (valTarget, label, tag) ->
                        val isSelected = target == valTarget
                        Surface(
                            onClick = { if (!isControlsLocked) onSetTarget(valTarget) },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    val isCustom = target != 33 && target != 99 && target != 0
                    val customLabel = if (isCustom) {
                        "مخصص (${NumeralFormatter.format(target, useArabicIndic)})"
                    } else {
                        stringResource(R.string.tasbih_target_custom)
                    }
                    Surface(
                        onClick = { if (!isControlsLocked) showCustomTargetDialog = true },
                        shape = RoundedCornerShape(50),
                        color = if (isCustom) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tasbih_target_custom_chip")
                    ) {
                        Text(
                            text = customLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCustom) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // One-handed / Full-screen tap area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(WirdiDesignTokens.heroCornerRadius))
                .clickable { onTapTasbih() }
                .semantics {
                    contentDescription = "منطقة التسبيح. العدد الحالي $currentCount"
                }
                .testTag("tasbih_tap_circle"),
            contentAlignment = if (isFullScreenMode) Alignment.Center else Alignment.BottomCenter
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .padding(bottom = if (isFullScreenMode) 0.dp else 12.dp)
                    .size(if (isFullScreenMode) 280.dp else 252.dp)
            ) {
                WirdiCircularProgressRing(
                    progress = ringProgress,
                    size = if (isFullScreenMode) 280.dp else 252.dp,
                    strokeWidth = 6.dp,
                    progressColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = NumeralFormatter.format(currentCount, useArabicIndic),
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("tasbih_current_count_text")
                        )
                        if (target > 0) {
                            Text(
                                text = "/ ${NumeralFormatter.format(target, useArabicIndic)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isControlsLocked) "الأزرار مقفلة • اضغط للعدّ" else stringResource(R.string.tasbih_tap_area),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        if (!isFullScreenMode) {
            InsetGroupedSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.tasbih_daily_total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = NumeralFormatter.format(todayTotal, useArabicIndic),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("tasbih_today_total_text")
                        )
                    }

                    OutlinedButton(
                        onClick = { if (!isControlsLocked) onResetCurrent() },
                        enabled = !isControlsLocked,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("tasbih_reset_btn")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.RefreshArc,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.tasbih_reset_current))
                    }
                }

                InsetGroupedDivider(startIndent = 20.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tasbih_keep_screen_on),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    WirdiSwitch(
                        checked = settings.tasbihKeepScreenOn,
                        onCheckedChange = onSetKeepScreenOn
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showCustomTargetDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTargetDialog = false },
            shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
            title = { Text("تحديد هدف مخصص للمسبحة", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customTargetInput,
                    onValueChange = { customTargetInput = it },
                    label = { Text("العدد المستهدف") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = NumeralFormatter.toWestern(customTargetInput).toIntOrNull()?.coerceAtLeast(1) ?: 100
                        onSetTarget(parsed)
                        showCustomTargetDialog = false
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTargetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Qibla screen with clear sensor accuracy badge and explicit textual angular alignment readout
 * so alignment does not depend on motion or color alone.
 */
@Composable
fun QiblaSubScreen(
    uiState: WirdiUiState,
    compassFlow: Flow<CompassSensorReading>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onTriggerAlignedHaptic: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val reading by compassFlow.collectAsState(
        initial = CompassSensorReading(0f, SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, true)
    )

    val qiblaBearing = remember(settings.latitude, settings.longitude) {
        QiblaCalculator.calculateQiblaBearing(settings.latitude, settings.longitude).toFloat()
    }

    val declination = remember(settings.latitude, settings.longitude) {
        QiblaCalculator.computeMagneticDeclination(settings.latitude, settings.longitude)
    }

    val trueHeading = QiblaCalculator.magneticToTrueNorth(reading.magneticAzimuthDegrees, declination)
    val diffAngle = QiblaCalculator.shortestAngleDiff(trueHeading, qiblaBearing)
    val isAligned = reading.isSensorAvailable && abs(diffAngle) <= 5f

    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(isAligned) {
        if (isAligned && !wasAligned && settings.hapticFeedbackEnabled) {
            onTriggerAlignedHaptic()
        }
        wasAligned = isAligned
    }

    val animatedRingColor by animateColorAsState(
        targetValue = if (isAligned) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = if (reduceMotion) snap() else tween(200, easing = FastOutSlowInEasing),
        label = "qiblaRingColor"
    )

    val animatedPointerRotation by animateFloatAsState(
        targetValue = qiblaBearing - trueHeading,
        animationSpec = if (reduceMotion) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "qiblaPointerRotation"
    )

    val accuracyLabel = when {
        !reading.isSensorAvailable -> "الحساس غير متوفر — اعتمد على الزاوية الرقمية"
        reading.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "دقة البوصلة: عالية"
        reading.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "دقة البوصلة: متوسطة"
        else -> "دقة البوصلة: منخفضة (تحتاج معايرة 8)"
    }

    val angularGuidanceText = remember(diffAngle, isAligned, reading.isSensorAvailable, useArabicIndic) {
        if (!reading.isSensorAvailable) {
            "وجّه أعلى الهاتف نحو زاوية القبلة من الشمال الحقيقي"
        } else if (isAligned) {
            "مطابق لاتجاه القبلة تماماً (٠° — ضمن هامش ±٥°)"
        } else {
            val deg = NumeralFormatter.format(abs(diffAngle).roundToInt(), useArabicIndic)
            val dir = if (diffAngle > 0) "إلى اليمين" else "إلى اليسار"
            "أدِر الهاتف بمقدار $deg° $dir للوصول إلى القبلة"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding),
        contentPadding = PaddingValues(bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(
                title = stringResource(R.string.qibla_title),
                showBackButton = showBackButton,
                onBack = onBack
            )
        }

        item {
            InsetGroupedSection {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${settings.cityNameAr} • ${settings.countryAr}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = accuracyLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("qibla_accuracy_badge")
                            )
                        }
                    }

                    val bearingFormatted = NumeralFormatter.format(
                        String.format(Locale.US, "%.1f°", qiblaBearing),
                        useArabicIndic
                    )
                    Text(
                        text = "${stringResource(R.string.qibla_bearing_from_north)}: $bearingFormatted",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("qibla_bearing_text")
                    )
                    Text(
                        text = angularGuidanceText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("qibla_angular_guidance_text")
                    )
                    Text(
                        text = stringResource(R.string.qibla_magnetic_declination),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!reading.isSensorAvailable) {
            item {
                Surface(
                    shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.qibla_no_sensor_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        } else if (reading.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            reading.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = WirdiIcons.InfoCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.qibla_low_accuracy_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .size(276.dp)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f

                        drawCircle(
                            color = animatedRingColor,
                            radius = radius,
                            style = Stroke(width = if (isAligned) 5.dp.toPx() else 2.dp.toPx())
                        )

                        drawLine(
                            color = primaryColor,
                            start = Offset(center.x, center.y - radius - 4.dp.toPx()),
                            end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        rotate(degrees = animatedPointerRotation) {
                            drawLine(
                                color = if (isAligned) primaryColor else secondaryTextColor,
                                start = Offset(center.x, center.y + radius * 0.45f),
                                end = Offset(center.x, center.y - radius * 0.72f),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            val arrowHead = Path().apply {
                                moveTo(center.x, center.y - radius * 0.78f)
                                lineTo(center.x - 12.dp.toPx(), center.y - radius * 0.56f)
                                lineTo(center.x, center.y - radius * 0.62f)
                                lineTo(center.x + 12.dp.toPx(), center.y - radius * 0.56f)
                                close()
                            }
                            drawPath(
                                path = arrowHead,
                                color = if (isAligned) primaryColor else secondaryTextColor
                            )
                        }

                        drawCircle(
                            color = if (isAligned) primaryColor else secondaryTextColor,
                            radius = 4.dp.toPx()
                        )
                    }
                }
            }
        }

        if (isAligned) {
            item {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = WirdiIcons.CheckMark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qibla_aligned_msg),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImportantDaysSubScreen(
    uiState: WirdiUiState,
    showBackButton: Boolean,
    onBack: () -> Unit
) {
    val useArabicIndic = uiState.persisted.settings.useArabicIndicNumerals
    val events = uiState.upcomingEvents

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SubScreenTopBar(
                title = stringResource(R.string.more_events),
                showBackButton = showBackButton,
                onBack = onBack
            )
            Text(
                text = stringResource(R.string.events_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(events, key = { it.id }) { event ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_card_${event.id}"),
                shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = WirdiIcons.Calendar,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = event.titleAr,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = NumeralFormatter.format(event.hijriTargetLabel, useArabicIndic),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = NumeralFormatter.format(event.subtitleAr, useArabicIndic),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (event.daysRemaining == 0L) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        val badgeText = if (event.daysRemaining == 0L) {
                            stringResource(R.string.event_today)
                        } else {
                            stringResource(
                                R.string.days_remaining,
                                NumeralFormatter.format(event.daysRemaining, useArabicIndic)
                            )
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (event.daysRemaining == 0L) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyTimetableAndComparisonSubScreen(
    uiState: WirdiUiState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onRunComparison: (String) -> Unit
) {
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val today = LocalDate.now()
    var selectedFirstOfMonth by remember { mutableStateOf(today.withDayOfMonth(1)) }

    val monthlyList = remember(selectedFirstOfMonth.year, selectedFirstOfMonth.monthValue, settings) {
        PrayerTimeEngine.getMonthlySchedule(selectedFirstOfMonth.year, selectedFirstOfMonth.monthValue, settings)
    }

    var officialInput by remember {
        mutableStateOf("${today},05:20,12:19,15:22,17:49,19:12")
    }
    val horizontalTableScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SubScreenTopBar(
                title = stringResource(R.string.shortcut_monthly_table),
                showBackButton = showBackButton,
                onBack = onBack
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "أداة مقارنة الجدول الرسمي للمدينة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الصق أسطر الجدول الرسمي بصيغة (YYYY-MM-DD,Fajr,Dhuhr,Asr,Maghrib,Isha) لمقارنتها مع حساب التطبيق ومعرفة الفارق بالدقائق:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = officialInput,
                        onValueChange = { officialInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("official_comparison_input"),
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onRunComparison(officialInput) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("run_comparison_btn")
                    ) {
                        Text("مقارنة مع الجدول الرسمي")
                    }

                    if (uiState.officialComparisonResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        uiState.officialComparisonResults.forEach { row ->
                            val diffStr = NumeralFormatter.format(row.differenceMinutes, useArabicIndic)
                            Text(
                                text = "• ${row.date} (${row.prayerType.nameAr}): الرسمي ${row.officialTime} | المحسوب ${row.calculatedTime} | الفارق: $diffStr دقيقة",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الجدول الشهري لمدينة ${settings.cityNameAr} (${NumeralFormatter.format("${selectedFirstOfMonth.monthValue}/${selectedFirstOfMonth.year}", useArabicIndic)})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("monthly_timetable_title")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { selectedFirstOfMonth = selectedFirstOfMonth.minusMonths(1) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.testTag("prev_month_btn")
                    ) {
                        Text(
                            text = "السابق",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    if (selectedFirstOfMonth != today.withDayOfMonth(1)) {
                        Surface(
                            onClick = { selectedFirstOfMonth = today.withDayOfMonth(1) },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("reset_month_btn")
                        ) {
                            Text(
                                text = "الحالي",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = { selectedFirstOfMonth = selectedFirstOfMonth.plusMonths(1) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.testTag("next_month_btn")
                    ) {
                        Text(
                            text = "التالي",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Sticky Table Header Row with sticky Date column + horizontally scrollable Prayer columns
        item {
            Surface(
                shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اليوم",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(56.dp)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalTableScroll),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء").forEach { col ->
                            Text(
                                text = col,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(56.dp)
                            )
                        }
                    }
                }
            }
        }

        items(monthlyList, key = { it.date.toString() }) { daySchedule ->
            val isToday = daySchedule.date == today
            Surface(
                shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = NumeralFormatter.format("${daySchedule.date.dayOfMonth}/${daySchedule.date.monthValue}", useArabicIndic),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(56.dp)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalTableScroll),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            daySchedule.fajr,
                            daySchedule.sunrise,
                            daySchedule.dhuhr,
                            daySchedule.asr,
                            daySchedule.maghrib,
                            daySchedule.isha
                        ).forEach { pTime ->
                            Text(
                                text = PrayerTimeEngine.formatTime24(pTime, useArabicIndic),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(56.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
