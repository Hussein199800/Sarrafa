package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.domain.model.CityLocation
import com.example.domain.model.DarkModeConfig
import com.example.domain.model.DhikrCategory
import com.example.domain.model.HomeSectionId
import com.example.domain.model.PrayerCalcMethod
import com.example.domain.model.PrayerType
import com.example.domain.model.WirdiThemePalette
import com.example.domain.util.NumeralFormatter
import com.example.domain.util.PrayerTimeEngine
import com.example.ui.CategoryCompletionStatus
import com.example.ui.MoreSubScreen
import com.example.ui.WirdiUiState
import com.example.ui.components.InsetGroupedDivider
import com.example.ui.components.InsetGroupedSection
import com.example.ui.components.WirdiCircularProgressRing
import com.example.ui.components.WirdiIcons
import com.example.ui.components.WirdiLargeTitleHeader
import com.example.ui.components.WirdiRefinedDedicationNote
import com.example.ui.components.WirdiSwitch
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.shareText
import com.example.ui.theme.WirdiDesignTokens
import com.example.ui.theme.buildArabicReadingTextStyle
import com.example.ui.theme.getDhikrGateVisualSpec
import com.example.ui.theme.getPrayerHeroGradient
import com.example.ui.theme.resolveIsDarkTheme
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: WirdiUiState,
    isExpandedScreen: Boolean,
    onOpenCategory: (DhikrCategory) -> Unit,
    onOpenCurrentWird: (Boolean) -> Unit = { _ -> },
    onOpenFridaySunan: () -> Unit = {},
    onOpenMoreSubScreen: (MoreSubScreen) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onSetCompactMode: (Boolean) -> Unit = {},
    onToggleSectionVisibility: (HomeSectionId) -> Unit = {},
    onMoveSectionOrder: (HomeSectionId, Boolean) -> Unit = { _, _ -> },
    onTogglePinnedGate: (DhikrCategory) -> Unit = {},
    onUpdatePrayerOffset: (PrayerType, Int) -> Unit = { _, _ -> },
    onUpdatePrayerNotification: (PrayerType, Boolean, Int) -> Unit = { _, _, _ -> },
    onUpdateThemePalette: (WirdiThemePalette) -> Unit = {},
    onSelectCity: (CityLocation) -> Unit = {},
    onUpdateCalcMethod: (PrayerCalcMethod) -> Unit = {},
    onCompleteOnboarding: () -> Unit = {},
    onTimeBoundaryReached: (ZonedDateTime) -> Unit = {},
    onCopiedMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val lifecycleOwner = LocalLifecycleOwner.current

    val systemDark = isSystemInDarkTheme()
    val isDark = resolveIsDarkTheme(
        palette = settings.themePalette,
        darkModeConfig = settings.darkModeConfig,
        systemDark = systemDark
    )

    var canScheduleExact by remember {
        mutableStateOf(checkCanScheduleExactAlarms(context))
    }
    var exactBannerDismissed by remember { mutableStateOf(false) }
    var selectedPrayerForDetails by remember { mutableStateOf<PrayerType?>(null) }
    var showHomeCustomizationSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExact = checkCanScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Countdown ticks ONLY while RESUMED
    var currentNow by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(lifecycleOwner, settings) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                currentNow = ZonedDateTime.now()
                delay(1000L)
            }
        }
    }

    val nextPrayerInfo = remember(currentNow, settings) {
        PrayerTimeEngine.getNextPrayer(currentNow, settings)
    }

    // Notify ViewModel when date changes (midnight rollover) or next prayer transitions
    LaunchedEffect(currentNow.toLocalDate(), nextPrayerInfo.prayerType) {
        onTimeBoundaryReached(currentNow)
    }

    val isFriday = remember(currentNow) {
        currentNow.dayOfWeek == DayOfWeek.FRIDAY
    }

    // Determine previous and following prayer relative to nextPrayerInfo.prayerType
    val orderedPrayers = remember { PrayerType.entries }
    val (previousPrayerType, followingPrayerType) = remember(nextPrayerInfo.prayerType) {
        val idx = orderedPrayers.indexOf(nextPrayerInfo.prayerType)
        val prev = orderedPrayers[(idx - 1 + orderedPrayers.size) % orderedPrayers.size]
        val following = orderedPrayers[(idx + 1) % orderedPrayers.size]
        prev to following
    }

    val greetingRes = remember(currentNow) {
        when (currentNow.hour) {
            in 4..11 -> R.string.greeting_morning
            in 12..15 -> R.string.greeting_afternoon
            in 16..19 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    }

    val gregorianStr = remember(currentNow, useArabicIndic) {
        val raw = currentNow.toLocalDate().format(
            DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar"))
        )
        NumeralFormatter.format(raw, useArabicIndic)
    }

    val hijriSubtitle = remember(uiState.hijriToday, gregorianStr, settings.cityNameAr) {
        val hijri = uiState.hijriToday?.fullFormattedAr ?: ""
        if (hijri.isNotBlank()) "$hijri • $gregorianStr" else gregorianStr
    }

    val listState = rememberLazyListState()
    val isCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 }
    }

    // Ordered visible optional sections when NOT in compact mode
    val activeOptionalSections = remember(
        settings.homeCompactMode,
        settings.homeSectionsOrder,
        settings.homeVisibleSections
    ) {
        if (settings.homeCompactMode) {
            emptyList()
        } else {
            val baseOrder = settings.normalizedHomeSectionsOrder()
            baseOrder.filter { it.name in settings.homeVisibleSections }
        }
    }

    if (isExpandedScreen) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1.05f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item {
                    HomeCompactTopBar(
                        title = stringResource(greetingRes),
                        subtitle = hijriSubtitle,
                        isCollapsed = isCollapsed,
                        isDark = isDark,
                        onToggleDarkMode = { onToggleDarkMode(isDark) },
                        onOpenCustomization = { showHomeCustomizationSheet = true }
                    )
                }
                item {
                    HeroNextPrayerCard(
                        prayerType = nextPrayerInfo.prayerType,
                        previousPrayerType = previousPrayerType,
                        followingPrayerType = followingPrayerType,
                        isDarkTheme = isDark,
                        cityName = "${settings.cityNameAr} • ${settings.countryAr}",
                        nextPrayerName = nextPrayerInfo.prayerType.nameAr,
                        nextPrayerTimeFormatted = PrayerTimeEngine.formatTime(nextPrayerInfo.prayerTime, useArabicIndic),
                        countdownFormatted = PrayerTimeEngine.formatCountdown(nextPrayerInfo.remainingSeconds, useArabicIndic),
                        remainingSeconds = nextPrayerInfo.remainingSeconds,
                        onOpenPrayerDetails = { selectedPrayerForDetails = nextPrayerInfo.prayerType },
                        onOpenSettings = { onOpenMoreSubScreen(MoreSubScreen.SETTINGS) }
                    )
                }
                item {
                    YourWirdNowCard(
                        category = uiState.recommendedWirdCategory,
                        status = uiState.recommendedWirdStatus,
                        isFriday = isFriday,
                        useArabicIndic = useArabicIndic,
                        onContinueReading = { onOpenCurrentWird(false) },
                        onStartFocusSession = { onOpenCurrentWird(true) },
                        onOpenFridaySunan = onOpenFridaySunan
                    )
                }
                if (!settings.hasCompletedOnboarding) {
                    item {
                        FirstRunOnboardingCard(
                            uiState = uiState,
                            onUpdateThemePalette = onUpdateThemePalette,
                            onSelectCity = onSelectCity,
                            onUpdateCalcMethod = onUpdateCalcMethod,
                            onCompleteOnboarding = onCompleteOnboarding
                        )
                    }
                }
                if (!canScheduleExact && !exactBannerDismissed) {
                    item {
                        ExactAlarmGuidanceCard(
                            onOpenSettings = { openExactAlarmSystemSettings(context) },
                            onDismiss = { exactBannerDismissed = true }
                        )
                    }
                }
                item {
                    TodayPrayersInsetGroupedList(
                        uiState = uiState,
                        nextPrayerType = nextPrayerInfo.prayerType,
                        useArabicIndic = useArabicIndic,
                        onSelectPrayerForDetails = { selectedPrayerForDetails = it },
                        onOpenMonthlyTable = { onOpenMoreSubScreen(MoreSubScreen.MONTHLY_TIMETABLE) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(0.95f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
            ) {
                items(activeOptionalSections, key = { it.name }) { sectionId ->
                    HomeOptionalSectionItem(
                        sectionId = sectionId,
                        uiState = uiState,
                        isDark = isDark,
                        useArabicIndic = useArabicIndic,
                        onOpenCategory = onOpenCategory,
                        onOpenMoreSubScreen = onOpenMoreSubScreen,
                        onCopiedMessage = onCopiedMessage
                    )
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(WirdiDesignTokens.sectionVerticalSpacing),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)
        ) {
            item {
                HomeCompactTopBar(
                    title = stringResource(greetingRes),
                    subtitle = hijriSubtitle,
                    isCollapsed = isCollapsed,
                    isDark = isDark,
                    onToggleDarkMode = { onToggleDarkMode(isDark) },
                    onOpenCustomization = { showHomeCustomizationSheet = true }
                )
            }
            item {
                HeroNextPrayerCard(
                    prayerType = nextPrayerInfo.prayerType,
                    previousPrayerType = previousPrayerType,
                    followingPrayerType = followingPrayerType,
                    isDarkTheme = isDark,
                    cityName = "${settings.cityNameAr} • ${settings.countryAr}",
                    nextPrayerName = nextPrayerInfo.prayerType.nameAr,
                    nextPrayerTimeFormatted = PrayerTimeEngine.formatTime(nextPrayerInfo.prayerTime, useArabicIndic),
                    countdownFormatted = PrayerTimeEngine.formatCountdown(nextPrayerInfo.remainingSeconds, useArabicIndic),
                    remainingSeconds = nextPrayerInfo.remainingSeconds,
                    onOpenPrayerDetails = { selectedPrayerForDetails = nextPrayerInfo.prayerType },
                    onOpenSettings = { onOpenMoreSubScreen(MoreSubScreen.SETTINGS) }
                )
            }
            item {
                YourWirdNowCard(
                    category = uiState.recommendedWirdCategory,
                    status = uiState.recommendedWirdStatus,
                    isFriday = isFriday,
                    useArabicIndic = useArabicIndic,
                    onContinueReading = { onOpenCurrentWird(false) },
                    onStartFocusSession = { onOpenCurrentWird(true) },
                    onOpenFridaySunan = onOpenFridaySunan
                )
            }
            if (!settings.hasCompletedOnboarding) {
                item {
                    FirstRunOnboardingCard(
                        uiState = uiState,
                        onUpdateThemePalette = onUpdateThemePalette,
                        onSelectCity = onSelectCity,
                        onUpdateCalcMethod = onUpdateCalcMethod,
                        onCompleteOnboarding = onCompleteOnboarding
                    )
                }
            }
            if (!canScheduleExact && !exactBannerDismissed) {
                item {
                    ExactAlarmGuidanceCard(
                        onOpenSettings = { openExactAlarmSystemSettings(context) },
                        onDismiss = { exactBannerDismissed = true }
                    )
                }
            }
            item {
                TodayPrayersInsetGroupedList(
                    uiState = uiState,
                    nextPrayerType = nextPrayerInfo.prayerType,
                    useArabicIndic = useArabicIndic,
                    onSelectPrayerForDetails = { selectedPrayerForDetails = it },
                    onOpenMonthlyTable = { onOpenMoreSubScreen(MoreSubScreen.MONTHLY_TIMETABLE) }
                )
            }
            items(activeOptionalSections, key = { it.name }) { sectionId ->
                HomeOptionalSectionItem(
                    sectionId = sectionId,
                    uiState = uiState,
                    isDark = isDark,
                    useArabicIndic = useArabicIndic,
                    onOpenCategory = onOpenCategory,
                    onOpenMoreSubScreen = onOpenMoreSubScreen,
                    onCopiedMessage = onCopiedMessage
                )
            }
        }
    }

    // Prayer Details BottomSheet
    val detailPrayer = selectedPrayerForDetails
    if (detailPrayer != null && uiState.todayPrayerSchedule != null) {
        PrayerDetailsBottomSheet(
            prayerType = detailPrayer,
            uiState = uiState,
            currentNow = currentNow,
            onDismiss = { selectedPrayerForDetails = null },
            onUpdateOffset = { newOffset -> onUpdatePrayerOffset(detailPrayer, newOffset) },
            onToggleNotification = { enabled ->
                val mins = settings.prayerNotifications[detailPrayer]?.minutesBefore ?: 0
                onUpdatePrayerNotification(detailPrayer, enabled, mins)
            },
            onOpenMonthlyTimetable = {
                selectedPrayerForDetails = null
                onOpenMoreSubScreen(MoreSubScreen.MONTHLY_TIMETABLE)
            }
        )
    }

    // Home Customization BottomSheet
    if (showHomeCustomizationSheet) {
        HomeCustomizationBottomSheet(
            uiState = uiState,
            onDismiss = { showHomeCustomizationSheet = false },
            onSetCompactMode = onSetCompactMode,
            onToggleSectionVisibility = onToggleSectionVisibility,
            onMoveSectionOrder = onMoveSectionOrder,
            onTogglePinnedGate = onTogglePinnedGate,
            onOpenSettings = {
                showHomeCustomizationSheet = false
                onOpenMoreSubScreen(MoreSubScreen.SETTINGS)
            }
        )
    }
}

@Composable
private fun HomeOptionalSectionItem(
    sectionId: HomeSectionId,
    uiState: WirdiUiState,
    isDark: Boolean,
    useArabicIndic: Boolean,
    onOpenCategory: (DhikrCategory) -> Unit,
    onOpenMoreSubScreen: (MoreSubScreen) -> Unit,
    onCopiedMessage: () -> Unit
) {
    val pinnedGateNames = uiState.persisted.settings.pinnedGateCategories
    when (sectionId) {
        HomeSectionId.PINNED_GATES -> {
            HomeStandaloneGatesSection(
                isDarkTheme = isDark,
                pinnedGateNames = pinnedGateNames,
                onOpenGate = onOpenCategory
            )
        }
        HomeSectionId.SHORTCUTS -> {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QuickPillShortcutsRow(
                    onOpenQibla = { onOpenMoreSubScreen(MoreSubScreen.QIBLA) },
                    onOpenTasbih = { onOpenMoreSubScreen(MoreSubScreen.TASBIH) }
                )
                DailyAdhkarStatusCard(
                    morningStatus = uiState.morningStatus,
                    eveningStatus = uiState.eveningStatus,
                    sleepStatus = uiState.sleepStatus,
                    useArabicIndic = useArabicIndic,
                    onOpenCategory = onOpenCategory
                )
            }
        }
        HomeSectionId.DAILY_HIGHLIGHT -> {
            DailyHighlightDhikrCard(
                uiState = uiState,
                onCopiedMessage = onCopiedMessage
            )
        }
        HomeSectionId.DEDICATION -> {
            HomeDedicationCornerCard()
        }
    }
}

@Composable
private fun HomeCompactTopBar(
    title: String,
    subtitle: String,
    isCollapsed: Boolean,
    isDark: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenCustomization: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            WirdiLargeTitleHeader(
                title = title,
                subtitle = subtitle,
                isCollapsed = isCollapsed
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onOpenCustomization,
                shape = RoundedCornerShape(WirdiDesignTokens.pillCornerPercent),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.testTag("home_customize_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = WirdiIcons.SettingsSliders,
                        contentDescription = stringResource(R.string.home_customize_btn),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "تخصيص",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                onClick = onToggleDarkMode,
                shape = RoundedCornerShape(WirdiDesignTokens.pillCornerPercent),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.testTag("home_dark_mode_toggle_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = WirdiIcons.PaletteSpark,
                        contentDescription = stringResource(R.string.toggle_dark_mode_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDark) "نهاري" else "ليلي",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Skippable 3-step First-Run Onboarding Card with live theme selection, city & method confirmation,
 * and 100% offline privacy note. Does NOT request runtime permissions prematurely.
 */
@Composable
private fun FirstRunOnboardingCard(
    uiState: WirdiUiState,
    onUpdateThemePalette: (WirdiThemePalette) -> Unit,
    onSelectCity: (CityLocation) -> Unit,
    onUpdateCalcMethod: (PrayerCalcMethod) -> Unit,
    onCompleteOnboarding: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val settings = uiState.persisted.settings

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("first_run_onboarding_card"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "إعداد سريع (${currentStep + 1} / 3)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                TextButton(
                    onClick = onCompleteOnboarding,
                    modifier = Modifier.testTag("onboarding_skip_btn")
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            when (currentStep) {
                0 -> {
                    Text(
                        text = stringResource(R.string.onboarding_step1_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.onboarding_step1_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WirdiThemePalette.entries) { palette ->
                            FilterChip(
                                selected = settings.themePalette == palette,
                                onClick = { onUpdateThemePalette(palette) },
                                label = { Text(palette.titleAr.substringBefore(" —")) },
                                modifier = Modifier.testTag("onboarding_theme_${palette.name}")
                            )
                        }
                    }
                }
                1 -> {
                    Text(
                        text = stringResource(R.string.onboarding_step2_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.onboarding_step2_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.cities.take(8)) { city ->
                            FilterChip(
                                selected = settings.cityNameAr == city.nameAr,
                                onClick = { onSelectCity(city) },
                                label = { Text(city.nameAr) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PrayerCalcMethod.SYRIA_MAWAQIT,
                            PrayerCalcMethod.UMM_AL_QURA,
                            PrayerCalcMethod.MUSLIM_WORLD_LEAGUE
                        ).forEach { method ->
                            FilterChip(
                                selected = settings.calcMethod == method,
                                onClick = { onUpdateCalcMethod(method) },
                                label = { Text(method.titleAr.substringBefore(" (")) }
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = stringResource(R.string.onboarding_step3_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.onboarding_step3_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        if (currentStep < 2) {
                            currentStep += 1
                        } else {
                            onCompleteOnboarding()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("onboarding_next_btn")
                ) {
                    Text(
                        text = if (currentStep < 2) {
                            stringResource(R.string.onboarding_next)
                        } else {
                            stringResource(R.string.onboarding_finish)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Dynamic «وِردك الآن» (Your Wird Now) card showing the active recommended Wird based on time/state,
 * real completion progress, «متابعة» button, and «جلسة ذكر» button.
 */
@Composable
private fun YourWirdNowCard(
    category: DhikrCategory,
    status: CategoryCompletionStatus,
    isFriday: Boolean = false,
    useArabicIndic: Boolean,
    onContinueReading: () -> Unit,
    onStartFocusSession: () -> Unit,
    onOpenFridaySunan: () -> Unit = {}
) {
    val doneStr = NumeralFormatter.format(status.completedItems, useArabicIndic)
    val totalStr = NumeralFormatter.format(status.totalItems, useArabicIndic)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("current_wird_card"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = WirdiIcons.AdhkarLeaf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.current_wird_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = category.titleAr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$doneStr / $totalStr",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { status.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (status.isComplete) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = WirdiIcons.CheckMark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.current_wird_completed_calm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onContinueReading,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("current_wird_continue_btn")
                ) {
                    Text(stringResource(R.string.current_wird_continue))
                }
                FilledTonalButton(
                    onClick = onStartFocusSession,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("current_wird_session_btn")
                ) {
                    Icon(
                        imageVector = WirdiIcons.FocusSession,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.current_wird_start_session))
                }
            }

            if (isFriday) {
                Surface(
                    onClick = onOpenFridaySunan,
                    shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friday_sunan_calm_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = WirdiIcons.SparkleStar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "تذكير هادئ بسنن يوم الجمعة وآدابه (تصفح بلا عداد إلزامي)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = WirdiIcons.ChevronStart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStandaloneGatesSection(
    isDarkTheme: Boolean,
    pinnedGateNames: List<String>,
    onOpenGate: (DhikrCategory) -> Unit
) {
    val gates = remember(pinnedGateNames) {
        val filtered = DhikrCategory.standaloneGateCategories.filter { it.name in pinnedGateNames }
        filtered.ifEmpty { DhikrCategory.standaloneGateCategories }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_standalone_gates_section")
    ) {
        Text(
            text = stringResource(R.string.home_gates_section_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(gates) { gate ->
                val visual = getDhikrGateVisualSpec(gate, isDarkTheme)
                Surface(
                    onClick = { onOpenGate(gate) },
                    shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                    color = Color.Transparent,
                    border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, visual.accentColor.copy(alpha = 0.35f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier.testTag("home_gate_card_${gate.name}")
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(visual.containerStart, visual.containerEnd)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = visual.badgeBgColor,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = WirdiIcons.gateIconFor(gate),
                                        contentDescription = null,
                                        tint = visual.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = gate.titleAr,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = visual.titleColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = gate.subtitleAr,
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.subtitleColor
                        )
                    }
                }
            }
        }
    }
}

private fun checkCanScheduleExactAlarms(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        alarmManager?.canScheduleExactAlarms() ?: true
    } else {
        true
    }
}

private fun openExactAlarmSystemSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}

@Composable
private fun ExactAlarmGuidanceCard(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exact_alarm_guidance_banner"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = WirdiIcons.Clock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.exact_alarm_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "إخفاء",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.exact_alarm_banner_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("exact_alarm_settings_btn")
            ) {
                Text(
                    text = stringResource(R.string.exact_alarm_banner_btn),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Mature Next Prayer Hero Card with calm low-contrast gradient, previous/following prayer context,
 * circular countdown ring, and «تفاصيل الصلاة» button.
 */
@Composable
private fun HeroNextPrayerCard(
    prayerType: PrayerType,
    previousPrayerType: PrayerType,
    followingPrayerType: PrayerType,
    isDarkTheme: Boolean,
    cityName: String,
    nextPrayerName: String,
    nextPrayerTimeFormatted: String,
    countdownFormatted: String,
    remainingSeconds: Long,
    onOpenPrayerDetails: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val heroPalette = remember(prayerType, isDarkTheme) {
        getPrayerHeroGradient(prayerType, isDarkTheme)
    }
    val gradient = remember(heroPalette) {
        Brush.linearGradient(colors = listOf(heroPalette.startColor, heroPalette.endColor))
    }

    val ringProgress = remember(remainingSeconds) {
        val windowSeconds = 6 * 3600f
        (1f - (remainingSeconds.toFloat() / windowSeconds)).coerceIn(0.08f, 0.96f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_next_prayer_card"),
        shape = RoundedCornerShape(WirdiDesignTokens.heroCornerRadius),
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, heroPalette.accentRingColor.copy(alpha = 0.28f)),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(50),
                        color = heroPalette.contentColor.copy(alpha = 0.08f),
                        modifier = Modifier.testTag("hero_city_settings_pill")
                    ) {
                        Text(
                            text = cityName,
                            style = MaterialTheme.typography.labelMedium,
                            color = heroPalette.contentColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = onOpenPrayerDetails,
                        shape = RoundedCornerShape(50),
                        color = heroPalette.contentColor.copy(alpha = 0.1f),
                        modifier = Modifier.testTag("prayer_details_open_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = WirdiIcons.InfoCircle,
                                contentDescription = null,
                                tint = heroPalette.accentRingColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.prayer_details_btn),
                                style = MaterialTheme.typography.bodySmall,
                                color = heroPalette.contentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.next_prayer),
                            style = MaterialTheme.typography.bodySmall,
                            color = heroPalette.secondaryContentColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextPrayerName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = heroPalette.contentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextPrayerTimeFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            color = heroPalette.accentRingColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "السابقة: ${previousPrayerType.nameAr} • التالية: ${followingPrayerType.nameAr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = heroPalette.secondaryContentColor
                        )
                    }

                    WirdiCircularProgressRing(
                        progress = ringProgress,
                        size = 108.dp,
                        strokeWidth = 5.dp,
                        progressColor = heroPalette.accentRingColor,
                        trackColor = heroPalette.contentColor.copy(alpha = 0.12f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "المتبقي",
                                style = MaterialTheme.typography.bodySmall,
                                color = heroPalette.secondaryContentColor
                            )
                            Text(
                                text = countdownFormatted,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = heroPalette.contentColor,
                                modifier = Modifier.testTag("countdown_timer_text")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPillShortcutsRow(
    onOpenQibla: () -> Unit,
    onOpenTasbih: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PillShortcutButton(
            title = stringResource(R.string.shortcut_qibla),
            icon = WirdiIcons.Compass,
            testTag = "shortcut_qibla_btn",
            onClick = onOpenQibla,
            modifier = Modifier.weight(1f)
        )
        PillShortcutButton(
            title = stringResource(R.string.shortcut_tasbih),
            icon = WirdiIcons.TasbihBeads,
            testTag = "shortcut_tasbih_btn",
            onClick = onOpenTasbih,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PillShortcutButton(
    title: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(WirdiDesignTokens.pillCornerPercent),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Integrated scannable Today's Prayer Times list.
 * Distinguishes the next prayer with a dot + text badge + bold weight (never color alone).
 * Tapping any prayer row opens `PrayerDetailsBottomSheet`.
 */
@Composable
private fun TodayPrayersInsetGroupedList(
    uiState: WirdiUiState,
    nextPrayerType: PrayerType,
    useArabicIndic: Boolean,
    onSelectPrayerForDetails: (PrayerType) -> Unit,
    onOpenMonthlyTable: () -> Unit
) {
    val schedule = uiState.todayPrayerSchedule ?: return
    val ordered = schedule.allOrdered()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_prayers_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مواقيت اليوم (${uiState.persisted.settings.calcMethod.titleAr.substringBefore(" ")})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.shortcut_monthly_table),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenMonthlyTable)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("open_monthly_table_btn")
            )
        }

        InsetGroupedSection {
            ordered.forEachIndexed { index, (pType, pTime) ->
                val isNext = pType == nextPrayerType
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prayer_row_${pType.name}")
                        .clickable { onSelectPrayerForDetails(pType) }
                        .background(
                            if (isNext) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isNext) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isNext) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                        )
                        Text(
                            text = pType.nameAr,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                            color = if (isNext) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isNext) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "القادمة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val iqamahOffset = PrayerTimeEngine.getTypicalIqamahOffsetMinutes(pType)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (iqamahOffset > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "الإقامة +${NumeralFormatter.format(iqamahOffset, useArabicIndic)} د",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = PrayerTimeEngine.formatTime(pTime, useArabicIndic),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                            color = if (isNext) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                if (index < ordered.lastIndex) {
                    InsetGroupedDivider(startIndent = 42.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerDetailsBottomSheet(
    prayerType: PrayerType,
    uiState: WirdiUiState,
    currentNow: ZonedDateTime,
    onDismiss: () -> Unit,
    onUpdateOffset: (Int) -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onOpenMonthlyTimetable: () -> Unit
) {
    val schedule = uiState.todayPrayerSchedule ?: return
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val prayerTime = schedule.timeFor(prayerType)
    val athanFormatted = PrayerTimeEngine.formatTime(prayerTime, useArabicIndic)
    val iqamahOffset = PrayerTimeEngine.getTypicalIqamahOffsetMinutes(prayerType)
    val iqamahFormatted = if (iqamahOffset > 0) {
        val iqamahTime = prayerTime.plusMinutes(iqamahOffset.toLong())
        "${PrayerTimeEngine.formatTime(iqamahTime, useArabicIndic)} (+${NumeralFormatter.format(iqamahOffset, useArabicIndic)} د)"
    } else {
        "لا إقامة لصلاة الشروق"
    }
    val appliedOffset = settings.manualOffsets.getOffset(prayerType)
    val notifConfig = settings.prayerNotifications[prayerType]
    val notifEnabled = notifConfig?.enabled ?: true

    val diffSeconds = Duration.between(currentNow, prayerTime).seconds
    val remainingLabel = if (diffSeconds > 0) {
        PrayerTimeEngine.formatCountdown(diffSeconds, useArabicIndic)
    } else {
        "مضى وقتها اليوم"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("prayer_details_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "${stringResource(R.string.prayer_details_sheet_title)} — ${prayerType.nameAr}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            InsetGroupedSection {
                DetailKeyValueRow(
                    label = stringResource(R.string.prayer_athan_time_label),
                    value = athanFormatted
                )
                InsetGroupedDivider(startIndent = 16.dp)
                DetailKeyValueRow(
                    label = stringResource(R.string.prayer_iqamah_time_label),
                    value = iqamahFormatted
                )
                InsetGroupedDivider(startIndent = 16.dp)
                DetailKeyValueRow(
                    label = stringResource(R.string.remaining_time),
                    value = remainingLabel
                )
                InsetGroupedDivider(startIndent = 16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.prayer_manual_offset_applied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onUpdateOffset(appliedOffset - 1) }) { Text("-1") }
                        val prefix = if (appliedOffset > 0) "+" else ""
                        Text(
                            text = "$prefix${NumeralFormatter.format(appliedOffset, useArabicIndic)} د",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { onUpdateOffset(appliedOffset + 1) }) { Text("+1") }
                    }
                }
                InsetGroupedDivider(startIndent = 16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.prayer_notification_status_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    WirdiSwitch(
                        checked = notifEnabled,
                        onCheckedChange = onToggleNotification
                    )
                }
            }

            val sunnahTimes = uiState.todaySunnahTimes
            if (sunnahTimes != null) {
                InsetGroupedSection {
                    DetailKeyValueRow(
                        label = "وقت صلاة الضحى",
                        value = "${PrayerTimeEngine.formatTime(sunnahTimes.duhaStart, useArabicIndic)} – ${PrayerTimeEngine.formatTime(sunnahTimes.duhaEnd, useArabicIndic)}"
                    )
                    InsetGroupedDivider(startIndent = 16.dp)
                    DetailKeyValueRow(
                        label = "منتصف الليل الشرعي",
                        value = PrayerTimeEngine.formatTime(sunnahTimes.islamicMidnight, useArabicIndic)
                    )
                    InsetGroupedDivider(startIndent = 16.dp)
                    DetailKeyValueRow(
                        label = "بداية الثلث الأخير من الليل",
                        value = PrayerTimeEngine.formatTime(sunnahTimes.lastThirdOfNightStart, useArabicIndic)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.prayer_method_explanation_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.prayer_method_explanation_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenMonthlyTimetable,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.shortcut_monthly_table))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCustomizationBottomSheet(
    uiState: WirdiUiState,
    onDismiss: () -> Unit,
    onSetCompactMode: (Boolean) -> Unit,
    onToggleSectionVisibility: (HomeSectionId) -> Unit,
    onMoveSectionOrder: (HomeSectionId, Boolean) -> Unit,
    onTogglePinnedGate: (DhikrCategory) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val settings = uiState.persisted.settings
    val orderedSections = settings.normalizedHomeSectionsOrder()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("home_customization_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_customize_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_customization_open_settings_btn")
                ) {
                    Text(stringResource(R.string.more_settings))
                }
            }

            InsetGroupedSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_compact_mode_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.home_compact_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    WirdiSwitch(
                        checked = settings.homeCompactMode,
                        onCheckedChange = onSetCompactMode,
                        modifier = Modifier.testTag("home_compact_mode_switch")
                    )
                }
            }

            if (!settings.homeCompactMode) {
                Text(
                    text = stringResource(R.string.home_sections_order_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                InsetGroupedSection {
                    orderedSections.forEachIndexed { idx, sec ->
                        val isVisible = sec.name in settings.homeVisibleSections
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sec.titleAr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onMoveSectionOrder(sec, true) },
                                    enabled = idx > 0,
                                    modifier = Modifier.testTag("move_up_${sec.name}")
                                ) { Text("↑") }
                                TextButton(
                                    onClick = { onMoveSectionOrder(sec, false) },
                                    enabled = idx < orderedSections.lastIndex,
                                    modifier = Modifier.testTag("move_down_${sec.name}")
                                ) { Text("↓") }
                                WirdiSwitch(
                                    checked = isVisible,
                                    onCheckedChange = { onToggleSectionVisibility(sec) },
                                    modifier = Modifier.testTag("toggle_sec_${sec.name}")
                                )
                            }
                        }
                        if (idx < orderedSections.lastIndex) {
                            InsetGroupedDivider(startIndent = 16.dp)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.home_pinned_gates_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DhikrCategory.standaloneGateCategories) { gate ->
                        val pinned = gate.name in settings.pinnedGateCategories
                        FilterChip(
                            selected = pinned,
                            onClick = { onTogglePinnedGate(gate) },
                            label = { Text(gate.titleAr) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DailyAdhkarStatusCard(
    morningStatus: CategoryCompletionStatus,
    eveningStatus: CategoryCompletionStatus,
    sleepStatus: CategoryCompletionStatus,
    useArabicIndic: Boolean,
    onOpenCategory: (DhikrCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_adhkar_status_card")
    ) {
        Text(
            text = stringResource(R.string.daily_adhkar_status_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        InsetGroupedSection {
            AdhkarStatusInsetRow(
                label = stringResource(R.string.cat_morning),
                status = morningStatus,
                useArabicIndic = useArabicIndic,
                onClick = { onOpenCategory(DhikrCategory.MORNING) }
            )
            InsetGroupedDivider(startIndent = 20.dp)
            AdhkarStatusInsetRow(
                label = stringResource(R.string.cat_evening),
                status = eveningStatus,
                useArabicIndic = useArabicIndic,
                onClick = { onOpenCategory(DhikrCategory.EVENING) }
            )
            InsetGroupedDivider(startIndent = 20.dp)
            AdhkarStatusInsetRow(
                label = stringResource(R.string.cat_sleep),
                status = sleepStatus,
                useArabicIndic = useArabicIndic,
                onClick = { onOpenCategory(DhikrCategory.SLEEP) }
            )
        }
    }
}

@Composable
private fun AdhkarStatusInsetRow(
    label: String,
    status: CategoryCompletionStatus,
    useArabicIndic: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WirdiCircularProgressRing(
                progress = status.progressFraction,
                size = 34.dp,
                strokeWidth = 3.dp,
                progressColor = MaterialTheme.colorScheme.primary
            ) {
                if (status.isComplete) {
                    Icon(
                        imageVector = WirdiIcons.CheckMark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val doneStr = NumeralFormatter.format(status.completedItems, useArabicIndic)
            val totalStr = NumeralFormatter.format(status.totalItems, useArabicIndic)
            Text(
                text = "$doneStr / $totalStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = WirdiIcons.ChevronStart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DailyHighlightDhikrCard(
    uiState: WirdiUiState,
    onCopiedMessage: () -> Unit
) {
    val dhikr = uiState.dailyHighlightDhikr ?: return
    val settings = uiState.persisted.settings
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_highlight_dhikr_card"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.daily_dhikr_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            copyTextToClipboard(
                                context,
                                "ذكر اليوم",
                                "${dhikr.text}\nالمصدر: ${dhikr.source.orEmpty()}",
                                onCopiedMessage
                            )
                        }
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ ذكر اليوم",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            shareText(
                                context,
                                "${dhikr.text}\nالمصدر: ${dhikr.source.orEmpty()}"
                            )
                        }
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة ذكر اليوم",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = dhikr.text,
                style = buildArabicReadingTextStyle(
                    readingFont = settings.readingFont,
                    scale = settings.readingFontScale,
                    baseFontSizeSp = 20f
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!dhikr.source.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(dhikr.source, settings.useArabicIndicNumerals)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeDedicationCornerCard() {
    Box(modifier = Modifier.testTag("home_dedication_corner")) {
        WirdiRefinedDedicationNote(compact = true)
    }
}
