package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.AsrMadhab
import com.example.domain.model.CityLocation
import com.example.domain.model.DarkModeConfig
import com.example.domain.model.HighLatitudeRuleSetting
import com.example.domain.model.PrayerCalcMethod
import com.example.domain.model.PrayerType
import com.example.domain.model.ReadingFontFamily
import com.example.domain.model.WirdiThemePalette
import com.example.domain.util.NumeralFormatter
import com.example.ui.components.copyTextToClipboard
import com.example.notifications.WirdiNotificationScheduler
import com.example.ui.MoreSubScreen
import com.example.ui.WirdiUiState
import com.example.ui.WirdiViewModel
import com.example.ui.components.InsetGroupedDivider
import com.example.ui.components.InsetGroupedRow
import com.example.ui.components.InsetGroupedSection
import com.example.ui.components.WirdiIcons
import com.example.ui.components.WirdiLargeTitleHeader
import com.example.ui.components.WirdiSwitch
import com.example.ui.components.shareText
import com.example.ui.theme.buildArabicReadingTextStyle
import kotlinx.coroutines.launch

private data class MoreMenuEntry(
    val subScreen: MoreSubScreen,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun MoreScreen(
    uiState: WirdiUiState,
    viewModel: WirdiViewModel,
    notificationScheduler: WirdiNotificationScheduler,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val currentSubScreen = uiState.selectedMoreSubScreen

    if (!isExpandedScreen && currentSubScreen != MoreSubScreen.MENU) {
        BackHandler {
            viewModel.openMoreSubScreen(MoreSubScreen.MENU)
        }
    }

    val menuItems = remember {
        listOf(
            MoreMenuEntry(
                MoreSubScreen.TASBIH,
                "المسبحة الإلكترونية",
                "حلقة عدّ هادئة مع أهداف يومية",
                WirdiIcons.TasbihBeads
            ),
            MoreMenuEntry(
                MoreSubScreen.QIBLA,
                "اتجاه القبلة",
                "مؤشر اتجاه مجرّد بالدائرة العظمى",
                WirdiIcons.Compass
            ),
            MoreMenuEntry(
                MoreSubScreen.IMPORTANT_DAYS,
                "الأيام والمناسبات المهمة",
                "تقويم أم القرى والمواسم المباركة",
                WirdiIcons.Calendar
            ),
            MoreMenuEntry(
                MoreSubScreen.MONTHLY_TIMETABLE,
                "الجدول الشهري والمقارنة",
                "مواقيت الشهر كاملًا ومطابقة الجداول الرسمية",
                WirdiIcons.Clock
            ),
            MoreMenuEntry(
                MoreSubScreen.SETTINGS,
                "الإعدادات",
                "المواقيت، التنبيهات، السمات، والخطوط",
                WirdiIcons.SettingsSliders
            ),
            MoreMenuEntry(
                MoreSubScreen.ABOUT,
                "حول التطبيق والتراخيص",
                "المصادر، الخصوصية، وركن الدعاء",
                WirdiIcons.InfoCircle
            )
        )
    }

    if (isExpandedScreen) {
        val activeTabletSub = if (currentSubScreen == MoreSubScreen.MENU) MoreSubScreen.TASBIH else currentSubScreen
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        WirdiLargeTitleHeader(
                            title = stringResource(R.string.nav_more),
                            subtitle = "الأدوات والإعدادات"
                        )
                    }
                    items(menuItems) { entry ->
                        val isSelected = entry.subScreen == activeTabletSub
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.openMoreSubScreen(entry.subScreen) }
                                .testTag("more_menu_${entry.subScreen.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = entry.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        DedicationSupplicationCard(
                            onCopiedToast = { viewModel.postBannerMessage("تم نسخ الدعاء، تقبّل الله منكم") }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                MoreDetailRouter(
                    subScreen = activeTabletSub,
                    uiState = uiState,
                    viewModel = viewModel,
                    notificationScheduler = notificationScheduler,
                    showBackButton = false
                )
            }
        }
    } else {
        if (currentSubScreen == MoreSubScreen.MENU) {
            // Compact More Hub: Inset Grouped List of rows (NO circular icon grid!)
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    WirdiLargeTitleHeader(
                        title = stringResource(R.string.nav_more),
                        subtitle = "الأدوات المساندة والإعدادات"
                    )
                }

                // Tools Inset Grouped Section
                item {
                    Column {
                        Text(
                            text = "الأدوات اليومية",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                        InsetGroupedSection {
                            val tools = menuItems.take(4)
                            tools.forEachIndexed { idx, entry ->
                                InsetGroupedRow(
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    icon = entry.icon,
                                    onClick = { viewModel.openMoreSubScreen(entry.subScreen) },
                                    modifier = Modifier.testTag("more_menu_${entry.subScreen.name}")
                                )
                                if (idx < tools.lastIndex) {
                                    InsetGroupedDivider(startIndent = 70.dp)
                                }
                            }
                        }
                    }
                }

                // Settings & About Inset Grouped Section
                item {
                    Column {
                        Text(
                            text = "التفضيلات ومعلومات التطبيق",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                        InsetGroupedSection {
                            val prefs = menuItems.drop(4)
                            prefs.forEachIndexed { idx, entry ->
                                InsetGroupedRow(
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    icon = entry.icon,
                                    onClick = { viewModel.openMoreSubScreen(entry.subScreen) },
                                    modifier = Modifier.testTag("more_menu_${entry.subScreen.name}")
                                )
                                if (idx < prefs.lastIndex) {
                                    InsetGroupedDivider(startIndent = 70.dp)
                                }
                            }
                        }
                    }
                }

                // Dedicated Corner for حسين محمد علي الطويل and his parents
                item {
                    DedicationSupplicationCard(
                        onCopiedToast = { viewModel.postBannerMessage("تم نسخ الدعاء، تقبّل الله منكم") }
                    )
                }
            }
        } else {
            MoreDetailRouter(
                subScreen = currentSubScreen,
                uiState = uiState,
                viewModel = viewModel,
                notificationScheduler = notificationScheduler,
                showBackButton = true
            )
        }
    }
}

/**
 * Heartfelt dedication and supplication card for حسين محمد علي الطويل and his parents.
 */
@Composable
fun DedicationSupplicationCard(
    onCopiedToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = stringResource(R.string.dedication_title)
    val name = stringResource(R.string.dedication_name)
    val prayer = stringResource(R.string.dedication_prayer)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dedication_supplication_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            copyTextToClipboard(context, title, prayer, onCopiedToast)
                        }
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ الدعاء",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { shareText(context, prayer) }
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة الدعاء",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = prayer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun MoreDetailRouter(
    subScreen: MoreSubScreen,
    uiState: WirdiUiState,
    viewModel: WirdiViewModel,
    notificationScheduler: WirdiNotificationScheduler,
    showBackButton: Boolean
) {
    val onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) }
    when (subScreen) {
        MoreSubScreen.TASBIH, MoreSubScreen.MENU -> TasbihSubScreen(
            uiState = uiState,
            showBackButton = showBackButton,
            onBack = onBack,
            onTapTasbih = viewModel::onTasbihTap,
            onResetCurrent = viewModel::resetTasbihCurrent,
            onSetTarget = viewModel::setTasbihTarget,
            onSetKeepScreenOn = viewModel::setTasbihKeepScreenOn
        )

        MoreSubScreen.QIBLA -> QiblaSubScreen(
            uiState = uiState,
            compassFlow = viewModel.compassFlow,
            showBackButton = showBackButton,
            onBack = onBack,
            onTriggerAlignedHaptic = { viewModel.triggerGentleHaptic(isCompletion = true) }
        )

        MoreSubScreen.IMPORTANT_DAYS -> ImportantDaysSubScreen(
            uiState = uiState,
            showBackButton = showBackButton,
            onBack = onBack
        )

        MoreSubScreen.MONTHLY_TIMETABLE -> MonthlyTimetableAndComparisonSubScreen(
            uiState = uiState,
            showBackButton = showBackButton,
            onBack = onBack,
            onRunComparison = viewModel::runOfficialTableComparison
        )

        MoreSubScreen.SETTINGS -> SettingsSubScreen(
            uiState = uiState,
            viewModel = viewModel,
            notificationScheduler = notificationScheduler,
            showBackButton = showBackButton,
            onBack = onBack
        )

        MoreSubScreen.ABOUT -> AboutSubScreen(
            showBackButton = showBackButton,
            onBack = onBack,
            onCopiedToast = { viewModel.postBannerMessage("تم نسخ الدعاء، تقبّل الله منكم") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubScreen(
    uiState: WirdiUiState,
    viewModel: WirdiViewModel,
    notificationScheduler: WirdiNotificationScheduler,
    showBackButton: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val coroutineScope = rememberCoroutineScope()

    var showCityBottomSheet by remember { mutableStateOf(false) }
    var showCalcMethodBottomSheet by remember { mutableStateOf(false) }
    var showThemeBottomSheet by remember { mutableStateOf(false) }
    var showFontBottomSheet by remember { mutableStateOf(false) }
    var showManualCoordsDialog by remember { mutableStateOf(false) }
    var showOemGuideDialog by remember { mutableStateOf(false) }
    var showPasteBackupDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateLocationFromDeviceApproximate()
        } else {
            viewModel.postBannerMessage("تم رفض صلاحية الموقع؛ يمكنك اختيار مدينتك من القائمة المضمّنة")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationScheduler.scheduleAllAlarms(settings)
            viewModel.postBannerMessage("تم تفعيل إشعارات الأذان والأذكار")
        } else {
            viewModel.postBannerMessage("لن تظهر الإشعارات لحين السماح بصلاحية الإشعارات")
        }
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val jsonString = viewModel.exportBackupJsonString()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.postBannerMessage("تم تصدير النسخة الاحتياطية بنجاح")
                }.onFailure {
                    viewModel.postBannerMessage("حدث خطأ أثناء حفظ ملف النسخة الاحتياطية")
                }
            }
        }
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val rawJson = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                    if (!rawJson.isNullOrBlank()) {
                        viewModel.importBackupJsonString(rawJson)
                    }
                }.onFailure {
                    viewModel.postBannerMessage("تعذر قراءة الملف المختار")
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SubScreenTopBar(
                title = stringResource(R.string.more_settings),
                showBackButton = showBackButton,
                onBack = onBack
            )
        }

        // 1. Prayer Times & Location (iOS-style Inset Grouped List)
        item {
            Column {
                SettingsGroupHeader(stringResource(R.string.settings_prayer_section))
                InsetGroupedSection {
                    InsetGroupedRow(
                        title = stringResource(R.string.location_select_city),
                        subtitle = "${settings.cityNameAr} (${settings.countryAr})",
                        icon = WirdiIcons.Compass,
                        onClick = { showCityBottomSheet = true },
                        modifier = Modifier.testTag("select_city_btn")
                    )
                    InsetGroupedDivider(startIndent = 20.dp)
                    InsetGroupedRow(
                        title = stringResource(R.string.location_update_gps),
                        subtitle = "تحديد الموقع التقريبي محلياً دون إنترنت",
                        onClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)
                    InsetGroupedRow(
                        title = stringResource(R.string.location_manual_coords),
                        subtitle = "${settings.latitude}, ${settings.longitude}",
                        onClick = { showManualCoordsDialog = true }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)
                    InsetGroupedRow(
                        title = stringResource(R.string.settings_calc_method),
                        valueText = settings.calcMethod.titleAr.substringBefore(" ("),
                        onClick = { showCalcMethodBottomSheet = true },
                        modifier = Modifier.testTag("select_calc_method_btn")
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    // Asr Madhab selector
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_madhab),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsrMadhab.entries.forEach { madhab ->
                                FilterChip(
                                    selected = settings.asrMadhab == madhab,
                                    onClick = { viewModel.updateAsrMadhab(madhab) },
                                    label = { Text(madhab.titleAr) }
                                )
                            }
                        }
                    }

                    InsetGroupedDivider(startIndent = 20.dp)

                    // High Latitude Rule selector (Copenhagen / Stockholm / Oslo / London summer support)
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = "قاعدة العروض العليا (عند غياب الشفق صيفاً)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = settings.highLatitudeRule.descAr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(HighLatitudeRuleSetting.entries) { rule ->
                                FilterChip(
                                    selected = settings.highLatitudeRule == rule,
                                    onClick = { viewModel.updateHighLatitudeRule(rule) },
                                    label = { Text(rule.titleAr) },
                                    modifier = Modifier.testTag("high_lat_rule_chip_${rule.name}")
                                )
                            }
                        }
                    }

                    InsetGroupedDivider(startIndent = 20.dp)

                    // Hijri Offset (-2..+2)
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = "${stringResource(R.string.settings_hijri_offset)}: ${NumeralFormatter.format(settings.hijriOffsetDays, useArabicIndic)} يوم",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (offset in -2..2) {
                                FilterChip(
                                    selected = settings.hijriOffsetDays == offset,
                                    onClick = { viewModel.updateHijriOffset(offset) },
                                    label = {
                                        val prefix = if (offset > 0) "+" else ""
                                        Text("$prefix${NumeralFormatter.format(offset, useArabicIndic)}")
                                    },
                                    modifier = Modifier.testTag("hijri_offset_chip_$offset")
                                )
                            }
                        }
                    }

                    InsetGroupedDivider(startIndent = 20.dp)

                    // Manual Prayer Offsets (±30 min)
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_manual_offsets),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PrayerType.entries.forEach { pType ->
                            val currentOffset = settings.manualOffsets.getOffset(pType)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = pType.nameAr, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { viewModel.updatePrayerOffset(pType, currentOffset - 1) }) {
                                        Text("-1")
                                    }
                                    Text(
                                        text = "${NumeralFormatter.format(currentOffset, useArabicIndic)} د",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { viewModel.updatePrayerOffset(pType, currentOffset + 1) }) {
                                        Text("+1")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Appearance & Themes Section (ModalBottomSheet + Quick Palette Chips + WirdiSwitch)
        item {
            Column {
                SettingsGroupHeader(stringResource(R.string.settings_appearance_section))
                InsetGroupedSection {
                    InsetGroupedRow(
                        title = stringResource(R.string.settings_theme_palette),
                        valueText = settings.themePalette.titleAr.substringBefore(" ("),
                        onClick = { showThemeBottomSheet = true },
                        modifier = Modifier.testTag("select_theme_palette_btn")
                    )

                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(WirdiThemePalette.entries) { palette ->
                                FilterChip(
                                    selected = settings.themePalette == palette && !settings.useDynamicColor,
                                    onClick = { viewModel.updateThemePalette(palette) },
                                    label = { Text(palette.titleAr.substringBefore(" (")) },
                                    modifier = Modifier.testTag("theme_chip_${palette.name}")
                                )
                            }
                        }
                    }

                    InsetGroupedDivider(startIndent = 20.dp)

                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_dark_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DarkModeConfig.entries.forEach { cfg ->
                                FilterChip(
                                    selected = settings.darkModeConfig == cfg,
                                    onClick = { viewModel.updateDarkModeConfig(cfg) },
                                    label = { Text(cfg.titleAr) }
                                )
                            }
                        }
                    }

                    InsetGroupedDivider(startIndent = 20.dp)

                    InsetGroupedRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.useDynamicColor,
                                onCheckedChange = viewModel::updateDynamicColor
                            )
                        }
                    )
                }
            }
        }

        // 3. Typography & Numerals Section (ModalBottomSheet for Font + Live Preview)
        item {
            Column {
                SettingsGroupHeader(stringResource(R.string.settings_typography_section))
                InsetGroupedSection {
                    InsetGroupedRow(
                        title = stringResource(R.string.settings_reading_font),
                        valueText = settings.readingFont.titleAr,
                        onClick = { showFontBottomSheet = true },
                        modifier = Modifier.testTag("select_reading_font_btn")
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = "${stringResource(R.string.settings_font_scale)}: ${NumeralFormatter.format((settings.readingFontScale * 100).toInt(), useArabicIndic)}%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = settings.readingFontScale,
                            onValueChange = viewModel::updateReadingFontScale,
                            valueRange = 0.85f..1.6f
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.useArabicIndicNumerals,
                                onClick = { viewModel.updateNumeralsFormat(true) },
                                label = { Text(stringResource(R.string.numerals_arabic_indic)) }
                            )
                            FilterChip(
                                selected = !settings.useArabicIndicNumerals,
                                onClick = { viewModel.updateNumeralsFormat(false) },
                                label = { Text(stringResource(R.string.numerals_western)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = NumeralFormatter.format(
                                    stringResource(R.string.font_preview_sample),
                                    settings.useArabicIndicNumerals
                                ),
                                style = buildArabicReadingTextStyle(
                                    readingFont = settings.readingFont,
                                    scale = settings.readingFontScale,
                                    baseFontSizeSp = 20f
                                ),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Notifications & Exact Alarms Policy Section
        item {
            Column {
                SettingsGroupHeader(stringResource(R.string.settings_notifications_section))
                InsetGroupedSection {
                    InsetGroupedRow(
                        title = stringResource(R.string.settings_persistent_prayer_notif),
                        subtitle = stringResource(R.string.settings_persistent_prayer_notif_desc),
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.persistentPrayerNotificationEnabled,
                                onCheckedChange = viewModel::updatePersistentPrayerNotification
                            )
                        },
                        modifier = Modifier.testTag("toggle_persistent_notification")
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    val exactGranted = notificationScheduler.canScheduleExactAlarms()
                    InsetGroupedRow(
                        title = stringResource(R.string.settings_exact_alarm_policy),
                        subtitle = if (exactGranted) {
                            "مفعّلة (تنبيهات دقيقة)"
                        } else {
                            "غير مفعّلة — اضغط لفتح إعدادات النظام (يعمل البديل التلقائي setWindow حالياً)"
                        },
                        onClick = if (!exactGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        } else null,
                        showChevron = !exactGranted
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationScheduler.hasNotificationPermission()) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("السماح بإرسال إشعارات الأذان والأذكار")
                            }
                        }
                        InsetGroupedDivider(startIndent = 20.dp)
                    }

                    InsetGroupedRow(
                        title = stringResource(R.string.settings_battery_opt),
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    InsetGroupedRow(
                        title = stringResource(R.string.settings_oem_guide),
                        onClick = { showOemGuideDialog = true }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    PrayerType.entries.forEach { pType ->
                        val cfg = settings.prayerNotifications[pType]
                        val isEnabled = cfg?.enabled ?: true
                        InsetGroupedRow(
                            title = "تنبيه ${pType.nameAr}",
                            showChevron = false,
                            trailingContent = {
                                WirdiSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.updatePrayerNotification(pType, enabled, cfg?.minutesBefore ?: 0)
                                    }
                                )
                            }
                        )
                        InsetGroupedDivider(startIndent = 20.dp)
                    }

                    InsetGroupedRow(
                        title = "${stringResource(R.string.settings_morning_reminder)} (${settings.morningReminderHour}:00)",
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.morningReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateAdhkarReminder("MORNING", it, settings.morningReminderHour, settings.morningReminderMinute)
                                }
                            )
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    InsetGroupedRow(
                        title = "${stringResource(R.string.settings_evening_reminder)} (${settings.eveningReminderHour}:${settings.eveningReminderMinute})",
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.eveningReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateAdhkarReminder("EVENING", it, settings.eveningReminderHour, settings.eveningReminderMinute)
                                }
                            )
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    InsetGroupedRow(
                        title = "${stringResource(R.string.settings_sleep_reminder)} (${settings.sleepReminderHour}:00)",
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.sleepReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateAdhkarReminder("SLEEP", it, settings.sleepReminderHour, settings.sleepReminderMinute)
                                }
                            )
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    InsetGroupedRow(
                        title = stringResource(R.string.settings_friday_reminder),
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.fridayReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateAdhkarReminder("FRIDAY", it, settings.fridayReminderHour, settings.fridayReminderMinute)
                                }
                            )
                        }
                    )
                }
            }
        }

        // 5. Adhkar Behavior & Backup Section
        item {
            Column {
                SettingsGroupHeader(stringResource(R.string.settings_adhkar_behavior_section))
                InsetGroupedSection {
                    InsetGroupedRow(
                        title = stringResource(R.string.settings_haptic_feedback),
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.hapticFeedbackEnabled,
                                onCheckedChange = viewModel::updateHapticFeedback
                            )
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    InsetGroupedRow(
                        title = stringResource(R.string.settings_auto_advance),
                        showChevron = false,
                        trailingContent = {
                            WirdiSwitch(
                                checked = settings.autoAdvanceDhikr,
                                onCheckedChange = viewModel::updateAutoAdvance
                            )
                        }
                    )
                    InsetGroupedDivider(startIndent = 20.dp)

                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_backup_section),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { exportSafLauncher.launch("wirdi_backup.json") },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).testTag("backup_export_btn")
                            ) {
                                Text("تصدير JSON")
                            }
                            OutlinedButton(
                                onClick = { importSafLauncher.launch(arrayOf("application/json", "*/*")) },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).testTag("backup_import_btn")
                            ) {
                                Text("استيراد JSON")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val jsonString = viewModel.exportBackupJsonString()
                                        copyTextToClipboard(
                                            context,
                                            "wirdi_backup",
                                            jsonString
                                        ) {
                                            viewModel.postBannerMessage("تم نسخ النسخة الاحتياطية (JSON) إلى الحافظة")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).testTag("backup_copy_clipboard_btn")
                            ) {
                                Text("نسخ للحافظة")
                            }
                            OutlinedButton(
                                onClick = { showPasteBackupDialog = true },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).testTag("backup_paste_restore_btn")
                            ) {
                                Text("لصق واستعادة")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasteBackupDialog) {
        var pastedJson by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteBackupDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("استعادة نسخة احتياطية نصية", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "الصق نص النسخة الاحتياطية (JSON) المنسوخ من جهازك الآخر لاستعادة الإعدادات والمفضلة والأذكار الشخصية:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pastedJson,
                        onValueChange = { pastedJson = it },
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text("{\"version\": 2, ...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paste_backup_json_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackupJsonString(pastedJson)
                        showPasteBackupDialog = false
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("confirm_paste_backup_btn")
                ) {
                    Text("استعادة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteBackupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ModalBottomSheets for selections (City, CalcMethod, Theme Palette, Reading Font)
    if (showCityBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCityBottomSheet = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.location_select_city),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.cities, key = { it.id }) { city ->
                        val isSelected = city.nameAr == settings.cityNameAr
                        Surface(
                            onClick = {
                                viewModel.selectCity(city)
                                showCityBottomSheet = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = city.nameAr, fontWeight = FontWeight.Bold)
                                Text(text = city.countryAr, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showCalcMethodBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCalcMethodBottomSheet = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_calc_method),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                PrayerCalcMethod.entries.forEach { method ->
                    val isSelected = settings.calcMethod == method
                    Surface(
                        onClick = {
                            viewModel.updateCalcMethod(method)
                            showCalcMethodBottomSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = method.titleAr,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = WirdiIcons.CheckMark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showThemeBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeBottomSheet = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_palette),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                WirdiThemePalette.entries.forEach { palette ->
                    val isSelected = settings.themePalette == palette && !settings.useDynamicColor
                    Surface(
                        onClick = {
                            viewModel.updateThemePalette(palette)
                            showThemeBottomSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = palette.titleAr,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = WirdiIcons.CheckMark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showFontBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFontBottomSheet = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_reading_font),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                ReadingFontFamily.entries.forEach { font ->
                    val isSelected = settings.readingFont == font
                    Surface(
                        onClick = {
                            viewModel.updateReadingFont(font)
                            showFontBottomSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = font.titleAr,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = WirdiIcons.CheckMark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showManualCoordsDialog) {
        var nameInput by remember { mutableStateOf(settings.cityNameAr) }
        var latInput by remember { mutableStateOf(settings.latitude.toString()) }
        var lonInput by remember { mutableStateOf(settings.longitude.toString()) }

        AlertDialog(
            onDismissRequest = { showManualCoordsDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.location_manual_coords), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("اسم الموقع") }
                    )
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text("خط العرض (Latitude)") }
                    )
                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it },
                        label = { Text("خط الطول (Longitude)") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = NumeralFormatter.toWestern(latInput).toDoubleOrNull() ?: settings.latitude
                        val lon = NumeralFormatter.toWestern(lonInput).toDoubleOrNull() ?: settings.longitude
                        viewModel.setManualCoordinates(nameInput, lat, lon, settings.timeZoneId)
                        showManualCoordsDialog = false
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualCoordsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showOemGuideDialog) {
        AlertDialog(
            onDismissRequest = { showOemGuideDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.settings_oem_guide), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "بعض الشركات (Samsung، Xiaomi، Huawei، Oppo) قد توقف تنبيهات الخلفية لتوفير البطارية. لضمان دقة الأذان:\n" +
                        "١. فعّل خيار «التشغيل التلقائي / Auto-start» لتطبيق وِرْدِي.\n" +
                        "٢. اختر «بلا قيود / Unrestricted» في إعدادات بطارية التطبيق.\n" +
                        "٣. اسمح بصلاحية «المنبهات والتذكيرات الدقيقة»."
                )
            },
            confirmButton = {
                Button(onClick = { showOemGuideDialog = false }, shape = RoundedCornerShape(50)) {
                    Text("حسناً")
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
fun AboutSubScreen(
    showBackButton: Boolean,
    onBack: () -> Unit,
    onCopiedToast: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(
                title = stringResource(R.string.more_about),
                showBackButton = showBackButton,
                onBack = onBack
            )
        }

        // Prominent Dedication & Supplication Card for حسين محمد علي الطويل and his parents
        item {
            DedicationSupplicationCard(onCopiedToast = onCopiedToast)
        }

        item {
            InsetGroupedSection {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "الإصدار: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.about_privacy_note),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mandatory_disclaimer_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "تنبيه وإخلاء مسؤولية",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.mandatory_disclaimer),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            InsetGroupedSection {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "مصادر المحتوى والتراخيص المفتوحة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• الآيات القرآنية: مضبوطة بالرسم العثماني وفق مصحف المدينة النبوية ومشروع Tanzil.net (انظر ملف SOURCES_TO_VERIFY.md لقائمة الآيات الكاملة).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• الأحاديث والأذكار: من صحيحي البخاري ومسلم والسنن الأربع، ومتن الأربعين النووية للإمام النووي (٤٢ حديثاً)، وأسماء الله الحسنى برواية جامع الترمذي (3507).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• حساب مواقيت الصلاة والقبلة: مكتبة Adhan Java من Batoul Apps (com.batoulapps.adhan:adhan v1.2.1) المرخصة بموجب رخصة MIT المفتوحة.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• الخطوط: يعتمد التطبيق خط النظام العربي لضمان كمال الحروف والتشكيل، مع تضمين ملفي Amiri وNoto Naskh Arabic بموجب رخصة SIL Open Font License (OFL 1.1).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
