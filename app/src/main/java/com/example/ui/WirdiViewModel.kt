package com.example.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.CompassSensorReading
import com.example.data.WirdiPersistedState
import com.example.domain.model.AsmaItem
import com.example.domain.model.AsrMadhab
import com.example.domain.model.CityLocation
import com.example.domain.model.DarkModeConfig
import com.example.domain.model.DhikrCategory
import com.example.domain.model.DhikrItem
import com.example.domain.model.DuasAndFridayBundle
import com.example.domain.model.HadithItem
import com.example.domain.model.HighLatitudeRuleSetting
import com.example.domain.model.HomeSectionId
import com.example.domain.model.PrayerCalcMethod
import com.example.domain.model.PrayerNotificationConfig
import com.example.domain.model.PrayerType
import com.example.domain.model.ReadingFontFamily
import com.example.domain.model.UserSettings
import com.example.domain.model.WirdiThemePalette
import com.example.domain.util.DailyPrayerSchedule
import com.example.domain.util.FormattedHijriDate
import com.example.domain.util.HijriCalendarCalculator
import com.example.domain.util.IslamicEventItem
import com.example.domain.util.OfficialComparisonRow
import com.example.domain.util.PrayerTimeEngine
import com.example.domain.util.SunnahTimesInfo
import com.example.ui.widget.WirdiAppWidget
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WirdiTab {
    HOME,
    ADHKAR,
    DHIKR_GATES,
    LIBRARY,
    MORE
}

enum class LibrarySection(val titleAr: String) {
    NAWAWI("الأربعون النووية (٤٢ حديثاً)"),
    ASMA("أسماء الله الحسنى (٩٩ اسماً)"),
    DUAS_QURAN("أدعية قرآنية (الرسم العثماني)"),
    DUAS_SUNNAH("أدعية نبوية صحيحة"),
    FRIDAY_SUNAN("سنن يوم الجمعة")
}

enum class MoreSubScreen {
    MENU,
    TASBIH,
    QIBLA,
    IMPORTANT_DAYS,
    MONTHLY_TIMETABLE,
    SETTINGS,
    ABOUT
}

data class CategoryCompletionStatus(
    val completedItems: Int,
    val totalItems: Int
) {
    val isComplete: Boolean get() = totalItems > 0 && completedItems >= totalItems
    val progressFraction: Float get() = if (totalItems > 0) completedItems.toFloat() / totalItems else 0f
}

data class WirdiUiState(
    val isLoading: Boolean = true,
    val persisted: WirdiPersistedState = WirdiPersistedState(),
    val allAdhkar: List<DhikrItem> = emptyList(),
    val nawawiHadiths: List<HadithItem> = emptyList(),
    val asmaUlHusna: List<AsmaItem> = emptyList(),
    val duasBundle: DuasAndFridayBundle = DuasAndFridayBundle(emptyList(), emptyList(), emptyList()),
    val cities: List<CityLocation> = emptyList(),
    val dailyHighlightDhikr: DhikrItem? = null,
    val todayPrayerSchedule: DailyPrayerSchedule? = null,
    val todaySunnahTimes: SunnahTimesInfo? = null,
    val hijriToday: FormattedHijriDate? = null,
    val upcomingEvents: List<IslamicEventItem> = emptyList(),
    val morningStatus: CategoryCompletionStatus = CategoryCompletionStatus(0, 0),
    val eveningStatus: CategoryCompletionStatus = CategoryCompletionStatus(0, 0),
    val afterPrayerStatus: CategoryCompletionStatus = CategoryCompletionStatus(0, 0),
    val sleepStatus: CategoryCompletionStatus = CategoryCompletionStatus(0, 0),
    val recommendedWirdCategory: DhikrCategory = DhikrCategory.MORNING,
    val recommendedWirdStatus: CategoryCompletionStatus = CategoryCompletionStatus(0, 0),
    val selectedTab: WirdiTab = WirdiTab.HOME,
    val selectedDhikrCategory: DhikrCategory = DhikrCategory.MORNING,
    val selectedLibrarySection: LibrarySection = LibrarySection.NAWAWI,
    val selectedMoreSubScreen: MoreSubScreen = MoreSubScreen.MENU,
    val isDhikrSessionActive: Boolean = false,
    val sessionCategory: DhikrCategory = DhikrCategory.MORNING,
    val sessionInitialIndex: Int = 0,
    val searchQuery: String = "",
    val statusBannerMessage: String? = null,
    val officialComparisonResults: List<OfficialComparisonRow> = emptyList()
)

private data class StaticLoadedData(
    val adhkar: List<DhikrItem> = emptyList(),
    val nawawi: List<HadithItem> = emptyList(),
    val asma: List<AsmaItem> = emptyList(),
    val duasBundle: DuasAndFridayBundle = DuasAndFridayBundle(emptyList(), emptyList(), emptyList()),
    val cities: List<CityLocation> = emptyList(),
    val dailyHighlight: DhikrItem? = null,
    val isLoaded: Boolean = false
)

private data class NavigationUiSelections(
    val tab: WirdiTab = WirdiTab.HOME,
    val dhikrCategory: DhikrCategory = DhikrCategory.MORNING,
    val librarySection: LibrarySection = LibrarySection.NAWAWI,
    val moreSubScreen: MoreSubScreen = MoreSubScreen.MENU,
    val isSessionActive: Boolean = false,
    val sessionCategory: DhikrCategory = DhikrCategory.MORNING,
    val sessionInitialIndex: Int = 0,
    val searchQuery: String = "",
    val bannerMessage: String? = null,
    val comparisonRows: List<OfficialComparisonRow> = emptyList(),
    val referenceNow: ZonedDateTime = ZonedDateTime.now()
)

class WirdiViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val staticDataFlow = MutableStateFlow(StaticLoadedData())
    private val navSelectionsFlow = MutableStateFlow(NavigationUiSelections())
    private var lastCounterTapUptimeMs: Long = 0L

    val compassFlow: Flow<CompassSensorReading> = container.compassAndLocationHelper.observeCompass()

    val uiState: StateFlow<WirdiUiState> = combine(
        container.preferencesRepository.stateFlow,
        staticDataFlow,
        navSelectionsFlow
    ) { persisted, staticData, nav ->
        val now = nav.referenceNow
        val today = now.toLocalDate()
        val settings = persisted.settings
        val schedule = PrayerTimeEngine.calculateForSettings(today, settings)
        val sunnahTimes = PrayerTimeEngine.calculateSunnahTimes(today, settings)
        val hijri = HijriCalendarCalculator.formatHijriDate(
            today,
            settings.hijriOffsetDays,
            settings.useArabicIndicNumerals
        )
        val events = HijriCalendarCalculator.getUpcomingEvents(today, settings.hijriOffsetDays)

        fun computeStatus(catName: String): CategoryCompletionStatus {
            val items = staticData.adhkar.filter { it.category == catName }
            if (items.isEmpty()) return CategoryCompletionStatus(0, 0)
            val done = items.count { (persisted.todayDhikrCounts[it.id] ?: 0) >= it.repeat }
            return CategoryCompletionStatus(done, items.size)
        }

        val morningSt = computeStatus("MORNING")
        val eveningSt = computeStatus("EVENING")
        val afterPrayerSt = computeStatus("AFTER_PRAYER")
        val sleepSt = computeStatus("SLEEP")

        val recommendedCat = determineRecommendedWirdCategory(
            now = now,
            schedule = schedule,
            morningStatus = morningSt,
            eveningStatus = eveningSt,
            afterPrayerStatus = afterPrayerSt
        )
        val recommendedSt = when (recommendedCat) {
            DhikrCategory.MORNING -> morningSt
            DhikrCategory.EVENING -> eveningSt
            DhikrCategory.AFTER_PRAYER -> afterPrayerSt
            DhikrCategory.SLEEP -> sleepSt
            else -> computeStatus(recommendedCat.name)
        }

        WirdiUiState(
            isLoading = !staticData.isLoaded,
            persisted = persisted,
            allAdhkar = staticData.adhkar,
            nawawiHadiths = staticData.nawawi,
            asmaUlHusna = staticData.asma,
            duasBundle = staticData.duasBundle,
            cities = staticData.cities,
            dailyHighlightDhikr = staticData.dailyHighlight,
            todayPrayerSchedule = schedule,
            todaySunnahTimes = sunnahTimes,
            hijriToday = hijri,
            upcomingEvents = events,
            morningStatus = morningSt,
            eveningStatus = eveningSt,
            afterPrayerStatus = afterPrayerSt,
            sleepStatus = sleepSt,
            recommendedWirdCategory = recommendedCat,
            recommendedWirdStatus = recommendedSt,
            selectedTab = nav.tab,
            selectedDhikrCategory = nav.dhikrCategory,
            selectedLibrarySection = nav.librarySection,
            selectedMoreSubScreen = nav.moreSubScreen,
            isDhikrSessionActive = nav.isSessionActive,
            sessionCategory = nav.sessionCategory,
            sessionInitialIndex = nav.sessionInitialIndex,
            searchQuery = nav.searchQuery,
            statusBannerMessage = nav.bannerMessage,
            officialComparisonResults = nav.comparisonRows
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WirdiUiState()
    )

    init {
        viewModelScope.launch {
            val adhkar = container.assetsRepository.getAdhkar()
            val nawawi = container.assetsRepository.getNawawiHadiths()
            val asma = container.assetsRepository.getAsmaUlHusna()
            val duas = container.assetsRepository.getDuasAndFriday()
            val cities = container.assetsRepository.getCities()
            val highlight = container.assetsRepository.getDailyHighlightDhikr(LocalDate.now())

            staticDataFlow.value = StaticLoadedData(
                adhkar = adhkar,
                nawawi = nawawi,
                asma = asma,
                duasBundle = duas,
                cities = cities,
                dailyHighlight = highlight,
                isLoaded = true
            )

            container.preferencesRepository.ensureDailyReset(LocalDate.now().toString())
        }
    }

    /**
     * Refreshes time-dependent state (next prayer transition, recommended Wird, midnight daily reset)
     * when a prayer time or midnight boundary is crossed while the app is open.
     */
    fun onTimeBoundaryReached(now: ZonedDateTime = ZonedDateTime.now()) {
        val prevDate = navSelectionsFlow.value.referenceNow.toLocalDate()
        val newDate = now.toLocalDate()
        navSelectionsFlow.value = navSelectionsFlow.value.copy(referenceNow = now)
        if (newDate != prevDate) {
            viewModelScope.launch {
                container.preferencesRepository.ensureDailyReset(newDate.toString())
                val highlight = container.assetsRepository.getDailyHighlightDhikr(newDate)
                staticDataFlow.value = staticDataFlow.value.copy(dailyHighlight = highlight)
            }
        }
    }

    private fun determineRecommendedWirdCategory(
        now: ZonedDateTime,
        schedule: DailyPrayerSchedule,
        morningStatus: CategoryCompletionStatus,
        eveningStatus: CategoryCompletionStatus,
        afterPrayerStatus: CategoryCompletionStatus
    ): DhikrCategory {
        // Check if within 30 minutes after an obligatory prayer and After-Prayer adhkar are not complete
        val obligatoryTimes = listOf(
            schedule.fajr,
            schedule.dhuhr,
            schedule.asr,
            schedule.maghrib,
            schedule.isha
        )
        val recentlyPrayed = obligatoryTimes.any { prayerTime ->
            val minsAfter = Duration.between(prayerTime, now).toMinutes()
            minsAfter in 0..30
        }
        if (recentlyPrayed && !afterPrayerStatus.isComplete) {
            return DhikrCategory.AFTER_PRAYER
        }

        val hour = now.hour
        return when {
            hour in 4..11 -> if (!morningStatus.isComplete) DhikrCategory.MORNING else DhikrCategory.AFTER_PRAYER
            hour in 12..14 -> if (!morningStatus.isComplete) DhikrCategory.MORNING else DhikrCategory.AFTER_PRAYER
            hour in 15..20 -> if (!eveningStatus.isComplete) DhikrCategory.EVENING else DhikrCategory.AFTER_PRAYER
            else -> DhikrCategory.SLEEP
        }
    }

    fun selectTab(tab: WirdiTab) {
        val currentCat = navSelectionsFlow.value.dhikrCategory
        val nextCat = when (tab) {
            WirdiTab.DHIKR_GATES -> if (currentCat.isStandaloneGate) currentCat else DhikrCategory.GATE_ISTIGHFAR
            WirdiTab.ADHKAR -> if (!currentCat.isStandaloneGate) currentCat else DhikrCategory.MORNING
            else -> currentCat
        }
        navSelectionsFlow.value = navSelectionsFlow.value.copy(
            tab = tab,
            dhikrCategory = nextCat,
            searchQuery = "",
            moreSubScreen = if (tab == WirdiTab.MORE) MoreSubScreen.MENU else navSelectionsFlow.value.moreSubScreen
        )
    }

    fun selectDhikrCategory(category: DhikrCategory) {
        val targetTab = if (category.isStandaloneGate) WirdiTab.DHIKR_GATES else WirdiTab.ADHKAR
        navSelectionsFlow.value = navSelectionsFlow.value.copy(
            tab = targetTab,
            dhikrCategory = category,
            searchQuery = ""
        )
    }

    /**
     * Opens the current recommended Wird either in the Adhkar reader or directly in the focused Dhikr Session,
     * ensuring the exact resume index is persisted before opening.
     */
    fun openCurrentRecommendedWird(asFocusSession: Boolean = false) {
        val cat = uiState.value.recommendedWirdCategory
        val savedIdx = resolveResumeIndexForCategory(cat)
        saveLastReadDhikrPosition(cat, savedIdx)
        if (asFocusSession) {
            openDhikrSession(cat, savedIdx)
        } else {
            selectDhikrCategory(cat)
        }
    }

    /**
     * Routes incoming notification taps to the exact screen or Adhkar category.
     */
    fun handleNotificationDeepLink(alarmType: String) {
        when {
            alarmType == "ADHKAR_MORNING" -> selectDhikrCategory(DhikrCategory.MORNING)
            alarmType == "ADHKAR_EVENING" -> selectDhikrCategory(DhikrCategory.EVENING)
            alarmType == "ADHKAR_SLEEP" -> selectDhikrCategory(DhikrCategory.SLEEP)
            alarmType == "FRIDAY_SUNAN" -> selectLibrarySection(LibrarySection.FRIDAY_SUNAN)
            alarmType.startsWith("PRAYER_") -> selectTab(WirdiTab.HOME)
        }
    }

    fun resolveResumeIndexForCategory(category: DhikrCategory): Int {
        val state = uiState.value
        val items = when (category) {
            DhikrCategory.FAVORITES -> (state.allAdhkar + state.persisted.customAdhkar).filter { it.id in state.persisted.favoriteIds }
            DhikrCategory.CUSTOM -> state.persisted.customAdhkar
            else -> state.allAdhkar.filter { it.category == category.name }
        }
        if (items.isEmpty()) return 0
        val savedIndex = state.persisted.lastReadDhikrIndexByCategory[category.name]
        if (savedIndex != null && savedIndex in items.indices) {
            val itemAtSaved = items[savedIndex]
            val countAtSaved = state.persisted.todayDhikrCounts[itemAtSaved.id] ?: 0
            if (countAtSaved < itemAtSaved.repeat) {
                return savedIndex
            }
        }
        val firstIncomplete = items.indexOfFirst { (state.persisted.todayDhikrCounts[it.id] ?: 0) < it.repeat }
        return if (firstIncomplete >= 0) firstIncomplete else 0
    }

    fun openDhikrSession(category: DhikrCategory, startIndex: Int? = null) {
        val idx = startIndex ?: resolveResumeIndexForCategory(category)
        navSelectionsFlow.value = navSelectionsFlow.value.copy(
            dhikrCategory = category,
            isSessionActive = true,
            sessionCategory = category,
            sessionInitialIndex = idx.coerceAtLeast(0)
        )
    }

    fun closeDhikrSession() {
        navSelectionsFlow.value = navSelectionsFlow.value.copy(isSessionActive = false)
    }

    fun saveLastReadDhikrPosition(category: DhikrCategory, index: Int) {
        viewModelScope.launch {
            container.preferencesRepository.saveLastReadDhikrPosition(category.name, index)
        }
    }

    fun saveLastReadLibraryItem(section: LibrarySection, index: Int) {
        viewModelScope.launch {
            container.preferencesRepository.saveLastReadLibraryItem(section.name, index.toString())
        }
    }

    fun toggleDarkModeQuick(currentlyDark: Boolean) {
        viewModelScope.launch {
            val nextMode = if (currentlyDark) DarkModeConfig.LIGHT else DarkModeConfig.DARK
            container.preferencesRepository.updateSettings { it.copy(darkModeConfig = nextMode) }
        }
    }

    fun selectLibrarySection(section: LibrarySection) {
        navSelectionsFlow.value = navSelectionsFlow.value.copy(
            tab = WirdiTab.LIBRARY,
            librarySection = section,
            searchQuery = ""
        )
    }

    fun openMoreSubScreen(subScreen: MoreSubScreen) {
        navSelectionsFlow.value = navSelectionsFlow.value.copy(
            tab = WirdiTab.MORE,
            moreSubScreen = subScreen
        )
    }

    fun updateSearchQuery(query: String) {
        navSelectionsFlow.value = navSelectionsFlow.value.copy(searchQuery = query)
    }

    fun clearBannerMessage() {
        navSelectionsFlow.value = navSelectionsFlow.value.copy(bannerMessage = null)
    }

    fun postBannerMessage(msg: String) {
        navSelectionsFlow.value = navSelectionsFlow.value.copy(bannerMessage = msg)
    }

    fun onDhikrCardTap(
        dhikr: DhikrItem,
        onCompletedItem: (Boolean) -> Unit = {}
    ) {
        val nowUptime = SystemClock.uptimeMillis()
        val delta = nowUptime - lastCounterTapUptimeMs
        if (lastCounterTapUptimeMs > 0L && delta in 1..120L) {
            // Ignore accidental ultra-rapid double tap within 120ms
            return
        }
        lastCounterTapUptimeMs = nowUptime

        viewModelScope.launch {
            val currentSettings = uiState.value.persisted.settings
            val prevCount = uiState.value.persisted.todayDhikrCounts[dhikr.id] ?: 0
            if (prevCount >= dhikr.repeat) {
                onCompletedItem(false)
                return@launch
            }
            val newCount = container.preferencesRepository.incrementDhikrCount(dhikr.id, dhikr.repeat)
            if (currentSettings.hapticFeedbackEnabled) {
                triggerGentleHaptic(isCompletion = newCount >= dhikr.repeat)
            }
            onCompletedItem(newCount >= dhikr.repeat && currentSettings.autoAdvanceDhikr)
        }
    }

    fun resetCurrentCategoryCounts(items: List<DhikrItem>) {
        viewModelScope.launch {
            container.preferencesRepository.resetCategoryCounts(items.map { it.id })
        }
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            container.preferencesRepository.toggleFavorite(itemId)
        }
    }

    fun saveCustomDhikr(id: String?, text: String, repeat: Int, note: String?) {
        viewModelScope.launch {
            val item = DhikrItem(
                id = id ?: "custom_${UUID.randomUUID()}",
                category = "CUSTOM",
                text = text.trim(),
                repeat = repeat.coerceIn(1, 10000),
                virtue = note?.trim()?.ifBlank { null },
                source = "ورد شخصي (مضاف بواسطة المستخدم)",
                grade = null,
                note = note?.trim()?.ifBlank { null },
                isCustom = true
            )
            container.preferencesRepository.addOrUpdateCustomDhikr(item)
            postBannerMessage("تم حفظ الذكر في «أذكاري»")
        }
    }

    fun deleteCustomDhikr(id: String) {
        viewModelScope.launch {
            container.preferencesRepository.deleteCustomDhikr(id)
            postBannerMessage("تم حذف الذكر")
        }
    }

    fun onTasbihTap() {
        val nowUptime = SystemClock.uptimeMillis()
        val delta = nowUptime - lastCounterTapUptimeMs
        if (lastCounterTapUptimeMs > 0L && delta in 1..110L) {
            return
        }
        lastCounterTapUptimeMs = nowUptime

        viewModelScope.launch {
            val settings = uiState.value.persisted.settings
            val nextCount = uiState.value.persisted.tasbihCurrentCount + 1
            container.preferencesRepository.incrementTasbih()
            if (settings.hapticFeedbackEnabled) {
                val targetHit = settings.tasbihTarget > 0 && (nextCount % settings.tasbihTarget == 0)
                triggerGentleHaptic(isCompletion = targetHit)
            }
        }
    }

    fun resetTasbihCurrent() {
        viewModelScope.launch {
            container.preferencesRepository.resetCurrentTasbih()
        }
    }

    fun setTasbihTarget(target: Int) {
        updateUserSettings { it.copy(tasbihTarget = target.coerceAtLeast(0)) }
    }

    fun setTasbihKeepScreenOn(enabled: Boolean) {
        updateUserSettings { it.copy(tasbihKeepScreenOn = enabled) }
    }

    // Home Customization & Onboarding & Session Options
    fun setHomeCompactMode(enabled: Boolean) {
        updateUserSettings { it.copy(homeCompactMode = enabled) }
    }

    fun toggleHomeSectionVisibility(section: HomeSectionId) {
        updateUserSettings { current ->
            val currentSet = current.homeVisibleSections.toMutableSet()
            if (section.name in currentSet) {
                if (currentSet.size > 1) currentSet.remove(section.name)
            } else {
                currentSet.add(section.name)
            }
            current.copy(homeVisibleSections = currentSet)
        }
    }

    fun moveHomeSectionOrder(section: HomeSectionId, moveUp: Boolean) {
        updateUserSettings { current ->
            val list = current.normalizedHomeSectionsOrder().toMutableList()
            val idx = list.indexOf(section)
            if (idx >= 0) {
                val targetIdx = if (moveUp) idx - 1 else idx + 1
                if (targetIdx in list.indices) {
                    val temp = list[idx]
                    list[idx] = list[targetIdx]
                    list[targetIdx] = temp
                }
            }
            current.copy(homeSectionsOrder = list.map { it.name })
        }
    }

    fun togglePinnedGateCategory(category: DhikrCategory) {
        if (!category.isStandaloneGate) return
        updateUserSettings { current ->
            val list = current.pinnedGateCategories.toMutableList()
            if (category.name in list) {
                if (list.size > 1) list.remove(category.name)
            } else {
                list.add(category.name)
            }
            current.copy(pinnedGateCategories = list)
        }
    }

    fun completeOnboarding() {
        updateUserSettings { it.copy(hasCompletedOnboarding = true) }
    }

    fun updatePersistentPrayerNotification(enabled: Boolean) {
        updateUserSettings(rescheduleNotifications = true) {
            it.copy(persistentPrayerNotificationEnabled = enabled)
        }
    }

    fun updateSessionKeepScreenOn(enabled: Boolean) {
        updateUserSettings { it.copy(sessionKeepScreenOn = enabled) }
    }

    fun updateSessionDimUi(enabled: Boolean) {
        updateUserSettings { it.copy(sessionDimUi = enabled) }
    }

    fun updateSessionCompactCounter(enabled: Boolean) {
        updateUserSettings { it.copy(sessionCompactCounter = enabled) }
    }

    fun selectCity(city: CityLocation) {
        val defaultMethod = runCatching { PrayerCalcMethod.valueOf(city.defaultMethod) }
            .getOrDefault(PrayerCalcMethod.MUSLIM_WORLD_LEAGUE)
        updateUserSettings(rescheduleNotifications = true) {
            it.copy(
                cityNameAr = city.nameAr,
                countryAr = city.countryAr,
                latitude = city.latitude,
                longitude = city.longitude,
                timeZoneId = city.timeZoneId,
                calcMethod = defaultMethod
            )
        }
        postBannerMessage("تم اختيار مدينة ${city.nameAr} وتحديث المواقيت والودجت")
    }

    fun setManualCoordinates(nameAr: String, lat: Double, lon: Double, timeZoneId: String) {
        val safeLat = lat.coerceIn(-89.9, 89.9)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        updateUserSettings(rescheduleNotifications = true) {
            it.copy(
                cityNameAr = nameAr.ifBlank { "موقع يدوي" },
                countryAr = "إحداثيات يدوية",
                latitude = safeLat,
                longitude = safeLon,
                timeZoneId = timeZoneId.ifBlank { java.util.TimeZone.getDefault().id }
            )
        }
        postBannerMessage("تم تحديث الإحداثيات اليدوية")
    }

    fun updateLocationFromDeviceApproximate() {
        val coords = container.compassAndLocationHelper.getApproximateDeviceLocation()
        if (coords != null) {
            val (lat, lon) = coords
            val tz = java.util.TimeZone.getDefault().id
            updateUserSettings(rescheduleNotifications = true) {
                it.copy(
                    cityNameAr = "موقعي الحالي (تقريبي)",
                    countryAr = "محلياً على الجهاز",
                    latitude = lat,
                    longitude = lon,
                    timeZoneId = tz
                )
            }
            postBannerMessage("تم تحديث الموقع محلياً بنجاح دون إنترنت")
        } else {
            postBannerMessage("تعذر قراءة الموقع التقريبي؛ يمكنك اختيار مدينتك من القائمة المضمّنة")
        }
    }

    fun updateCalcMethod(method: PrayerCalcMethod) {
        updateUserSettings(rescheduleNotifications = true) { it.copy(calcMethod = method) }
    }

    fun updateAsrMadhab(madhab: AsrMadhab) {
        updateUserSettings(rescheduleNotifications = true) { it.copy(asrMadhab = madhab) }
    }

    fun updateHighLatitudeRule(rule: HighLatitudeRuleSetting) {
        updateUserSettings(rescheduleNotifications = true) { it.copy(highLatitudeRule = rule) }
    }

    fun updatePrayerOffset(prayer: PrayerType, offsetMinutes: Int) {
        updateUserSettings(rescheduleNotifications = true) {
            it.copy(manualOffsets = it.manualOffsets.withOffset(prayer, offsetMinutes))
        }
    }

    fun updateHijriOffset(days: Int) {
        updateUserSettings(rescheduleNotifications = true) {
            it.copy(hijriOffsetDays = days.coerceIn(-2, 2))
        }
    }

    fun updateThemePalette(palette: WirdiThemePalette) {
        val targetDarkConfig = if (palette.prefersLightByDefault) {
            DarkModeConfig.LIGHT
        } else {
            DarkModeConfig.DARK
        }
        updateUserSettings {
            it.copy(
                themePalette = palette,
                darkModeConfig = targetDarkConfig,
                useDynamicColor = false
            )
        }
    }

    fun updateDarkModeConfig(config: DarkModeConfig) {
        updateUserSettings { it.copy(darkModeConfig = config) }
    }

    fun updateDynamicColor(enabled: Boolean) {
        updateUserSettings { it.copy(useDynamicColor = enabled) }
    }

    fun updateReadingFont(font: ReadingFontFamily) {
        updateUserSettings { it.copy(readingFont = font) }
    }

    fun updateReadingFontScale(scale: Float) {
        updateUserSettings { it.copy(readingFontScale = scale.coerceIn(0.85f, 1.6f)) }
    }

    fun updateNumeralsFormat(useArabicIndic: Boolean) {
        updateUserSettings { it.copy(useArabicIndicNumerals = useArabicIndic) }
    }

    fun updateHapticFeedback(enabled: Boolean) {
        updateUserSettings { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun updateAutoAdvance(enabled: Boolean) {
        updateUserSettings { it.copy(autoAdvanceDhikr = enabled) }
    }

    fun updatePrayerNotification(prayer: PrayerType, enabled: Boolean, minutesBefore: Int) {
        updateUserSettings(rescheduleNotifications = true) { current ->
            val updatedMap = current.prayerNotifications.toMutableMap().apply {
                put(prayer, PrayerNotificationConfig(enabled, minutesBefore.coerceIn(0, 60)))
            }
            current.copy(prayerNotifications = updatedMap)
        }
    }

    fun updateAdhkarReminder(
        category: String,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        updateUserSettings(rescheduleNotifications = true) { current ->
            when (category) {
                "MORNING" -> current.copy(
                    morningReminderEnabled = enabled,
                    morningReminderHour = hour.coerceIn(0, 23),
                    morningReminderMinute = minute.coerceIn(0, 59)
                )
                "EVENING" -> current.copy(
                    eveningReminderEnabled = enabled,
                    eveningReminderHour = hour.coerceIn(0, 23),
                    eveningReminderMinute = minute.coerceIn(0, 59)
                )
                "SLEEP" -> current.copy(
                    sleepReminderEnabled = enabled,
                    sleepReminderHour = hour.coerceIn(0, 23),
                    sleepReminderMinute = minute.coerceIn(0, 59)
                )
                "FRIDAY" -> current.copy(
                    fridayReminderEnabled = enabled,
                    fridayReminderHour = hour.coerceIn(0, 23),
                    fridayReminderMinute = minute.coerceIn(0, 59)
                )
                else -> current
            }
        }
    }

    fun updateSnoozeMinutes(minutes: Int) {
        updateUserSettings(rescheduleNotifications = true) {
            it.copy(snoozeMinutes = minutes.coerceIn(5, 60))
        }
    }

    fun runOfficialTableComparison(rawCsvText: String) {
        val settings = uiState.value.persisted.settings
        val rows = PrayerTimeEngine.compareWithOfficialTable(rawCsvText, settings)
        navSelectionsFlow.value = navSelectionsFlow.value.copy(comparisonRows = rows)
    }

    suspend fun exportBackupJsonString(): String {
        return container.preferencesRepository.exportBackupJson()
    }

    fun importBackupJsonString(rawJson: String) {
        viewModelScope.launch {
            val result = container.preferencesRepository.importBackupJson(rawJson)
            if (result.isSuccess) {
                val newState = uiState.value.persisted
                container.notificationScheduler.scheduleAllAlarms(newState.settings, ZonedDateTime.now())
                WirdiAppWidget.updateAllWidgets(appContext)
                postBannerMessage("تم استيراد النسخة الاحتياطية واستعادة الحالة كاملة بنجاح")
            } else {
                postBannerMessage("تعذر استيراد الملف: تأكد من أنه ملف JSON صالح من تطبيق وِرْدِي")
            }
        }
    }

    fun triggerGentleHaptic(isCompletion: Boolean = false) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                val effect = if (isCompletion) {
                    VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
                vibrator.vibrate(effect)
            }
        }
    }

    private fun updateUserSettings(
        rescheduleNotifications: Boolean = false,
        transform: (UserSettings) -> UserSettings
    ) {
        viewModelScope.launch {
            val updated = container.preferencesRepository.updateSettings(transform)
            if (rescheduleNotifications) {
                container.notificationScheduler.scheduleAllAlarms(updated, ZonedDateTime.now())
                WirdiAppWidget.updateAllWidgets(appContext)
            }
        }
    }

    companion object {
        fun provideFactory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WirdiViewModel(container, context.applicationContext) as T
                }
            }
    }
}
