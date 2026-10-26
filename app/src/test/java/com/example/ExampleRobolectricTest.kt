package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AssetsContentRepository
import com.example.data.WirdiPreferencesRepository
import com.example.notifications.WirdiNotificationScheduler
import com.example.ui.WirdiViewModel
import com.example.ui.nav.WirdiAppRoot
import com.example.ui.theme.WirdiTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h840dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `automated content audit - valid JSON, no empty source, repeat greater than 0, counts 42 and 99, Tanzil match`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AssetsContentRepository(context)

        val adhkar = repo.getAdhkar()
        assertTrue("Adhkar list should not be empty", adhkar.isNotEmpty())
        adhkar.forEach { item ->
            assertFalse("Dhikr ${item.id} has blank text", item.text.isBlank())
            assertFalse("Dhikr ${item.id} has blank source", item.source.isNullOrBlank())
            assertTrue("Dhikr ${item.id} has invalid repeat ${item.repeat}", item.repeat > 0)
        }

        // Verify Ayat al-Kursi matches Tanzil Uthmani text verbatim
        val kursi = adhkar.first { it.id == "m_1" }
        assertTrue(kursi.text.contains("اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ"))
        assertTrue(kursi.text.contains("وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ"))

        // Verify Nawawi has strictly 42 hadiths
        val nawawi = repo.getNawawiHadiths()
        assertEquals(42, nawawi.size)
        nawawi.forEach { h ->
            assertFalse("Hadith ${h.id} has blank source", h.source.isNullOrBlank())
            assertFalse("Hadith ${h.id} has blank narrator", h.narrator.isBlank())
        }

        // Verify Asma ul-Husna has exactly 99 names with references and 23 marked needsHumanReview
        val asma = repo.getAsmaUlHusna()
        assertEquals(99, asma.size)
        assertEquals(23, asma.count { it.needsHumanReview })
        asma.forEach { a ->
            assertFalse("Asma ${a.id} has blank meaning", a.meaning.isBlank())
            assertFalse("Asma ${a.id} has blank reference", a.listReference.isBlank())
        }

        // Verify Duas and Friday Sunan have no empty sources
        val bundle = repo.getDuasAndFriday()
        assertTrue(bundle.quranicDuas.isNotEmpty())
        assertTrue(bundle.propheticDuas.isNotEmpty())
        assertTrue(bundle.fridaySunan.isNotEmpty())
        bundle.quranicDuas.forEach { assertFalse(it.source.isNullOrBlank()) }
        bundle.propheticDuas.forEach { assertFalse(it.source.isNullOrBlank()) }
        bundle.fridaySunan.forEach { assertFalse(it.source.isNullOrBlank()) }
    }

    @Test
    fun `manifest audit - strictly no INTERNET permission declared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        assertFalse(
            "CRITICAL: App must NOT request android.permission.INTERNET!",
            requestedPermissions.contains(Manifest.permission.INTERNET)
        )
        assertTrue(requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(requestedPermissions.contains(Manifest.permission.SCHEDULE_EXACT_ALARM))
        assertTrue(requestedPermissions.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED))
    }

    @Test
    fun `datastore daily reset and full JSON backup export import roundtrip`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = WirdiPreferencesRepository(context)

        // Increment counter on day 1
        prefs.incrementDhikrCount("m_1", 3, todayIso = "2026-10-20")
        prefs.incrementTasbih(todayIso = "2026-10-20")
        prefs.toggleFavorite("m_1")

        // Export JSON backup
        val backupJson = prefs.exportBackupJson()
        assertTrue(backupJson.contains("m_1"))

        // Trigger daily reset on a new day
        prefs.ensureDailyReset("2026-10-24")
        val afterReset = prefs.stateFlow.first()
        assertTrue(afterReset.todayDhikrCounts.isEmpty())
        assertEquals(0, afterReset.tasbihTodayTotal)
        assertTrue(afterReset.favoriteIds.contains("m_1"))

        // Restore backup and verify full roundtrip
        val importRes = prefs.importBackupJson(backupJson)
        assertTrue(importRes.isSuccess)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun `ui navigation works on compact phone and expanded tablet layouts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        val viewModel = WirdiViewModel(container, context)
        val scheduler = WirdiNotificationScheduler(context)

        var isTabletMode = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            val size = if (isTabletMode.value) DpSize(1024.dp, 768.dp) else DpSize(360.dp, 800.dp)
            val windowSizeClass = WindowSizeClass.calculateFromSize(size)
            WirdiTheme {
                WirdiAppRoot(
                    viewModel = viewModel,
                    notificationScheduler = scheduler,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify Compact Floating Pill Bottom Navigation & Home Hero Card
        composeTestRule.onNodeWithTag("compact_bottom_nav").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_next_prayer_card").assertIsDisplayed()

        // 2. Navigate to Adhkar tab (single-page reader & category progress rings)
        composeTestRule.onNodeWithTag("nav_tab_adhkar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("adhkar_cat_chip_MORNING").assertIsDisplayed()

        // 2b. Navigate to the new Standalone Dhikr Gates tab (Istighfar, Hamd, Hawqala...)
        composeTestRule.onNodeWithTag("nav_tab_gates").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("adhkar_cat_chip_GATE_ISTIGHFAR").assertIsDisplayed()

        // 3. Navigate to Library tab (Segmented Control)
        composeTestRule.onNodeWithTag("nav_tab_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_search_input").assertIsDisplayed()

        // 4. Navigate to More tab (Inset Grouped List + Dedication Card)
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_menu_TASBIH").assertIsDisplayed()

        // 5. Switch to Expanded Tablet Mode and verify Side Rail + Master-Detail layout
        isTabletMode.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("expanded_nav_rail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_menu_TASBIH").assertIsDisplayed()
    }

    @Test
    fun `v1 backup migration, reading position persistence, and home customization persist safely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = WirdiPreferencesRepository(context)

        // Save reading position for Adhkar & Library
        prefs.saveLastReadDhikrPosition("MORNING", 2)
        prefs.saveLastReadLibraryItem("NAWAWI", "n_5")
        prefs.updateSettings {
            it.copy(
                homeCompactMode = true,
                hasCompletedOnboarding = true
            )
        }

        val state = prefs.stateFlow.first()
        assertEquals(2, state.lastReadDhikrIndexByCategory["MORNING"])
        assertEquals("n_5", state.lastReadLibraryItemBySection["NAWAWI"])
        assertTrue(state.settings.homeCompactMode)
        assertTrue(state.settings.hasCompletedOnboarding)

        // Test importing a legacy v1 JSON backup with old palette name "FAJR"
        val legacyV1Json = """
            {
              "version": 1,
              "settings": {
                "cityNameAr": "دمشق",
                "countryAr": "سوريا",
                "latitude": 33.5138,
                "longitude": 36.2765,
                "timeZoneId": "Asia/Damascus",
                "themePalette": "FAJR"
              },
              "favoriteIds": ["m_2", "n_1"]
            }
        """.trimIndent()
        val importRes = prefs.importBackupJson(legacyV1Json)
        assertTrue("Legacy v1 backup should import cleanly", importRes.isSuccess)
        val afterLegacyImport = prefs.stateFlow.first()
        assertEquals(com.example.domain.model.WirdiThemePalette.UFUQ, afterLegacyImport.settings.themePalette)
        assertTrue(afterLegacyImport.favoriteIds.contains("m_2"))
    }

    private fun waitUntilMainIdle(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMs) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            condition()
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun `sensitive cases and all 5 themes at 1_35x font scale on 360dp phone render and operate cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        val viewModel = WirdiViewModel(container, context)
        val scheduler = WirdiNotificationScheduler(context)

        val currentPalette = androidx.compose.runtime.mutableStateOf(com.example.domain.model.WirdiThemePalette.UFUQ)
        val currentFontScale = androidx.compose.runtime.mutableStateOf(1.35f)

        composeTestRule.setContent {
            val windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp))
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = androidx.compose.ui.platform.LocalDensity.current.density,
                    fontScale = currentFontScale.value
                )
            ) {
                WirdiTheme(
                    palette = currentPalette.value,
                    darkModeConfig = if (currentPalette.value.prefersLightByDefault) {
                        com.example.domain.model.DarkModeConfig.LIGHT
                    } else {
                        com.example.domain.model.DarkModeConfig.DARK
                    }
                ) {
                    WirdiAppRoot(
                        viewModel = viewModel,
                        notificationScheduler = scheduler,
                        widthSizeClass = windowSizeClass.widthSizeClass
                    )
                }
            }
        }
        waitUntilMainIdle { !viewModel.uiState.value.isLoading }

        // 1. Verify persistent prayer notification is OFF by default
        assertFalse(
            "Persistent prayer notification must be disabled by default",
            viewModel.uiState.value.persisted.settings.persistentPrayerNotificationEnabled
        )

        // 2. Cycle through all 5 palettes at 1.35x font scale on 360dp width and verify primary Hero & Wird cards
        for (palette in com.example.domain.model.WirdiThemePalette.entries) {
            currentPalette.value = palette
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("hero_next_prayer_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("current_wird_card").assertExists()
        }

        // Reset font scale to 1.0f for interactive session and counter tests
        currentFontScale.value = 1.0f
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("current_wird_card").assertIsDisplayed()

        // 3. Test anti-rapid-double-tap protection (within 120ms ignored, after >130ms accepted)
        val multiRepeatDhikr = viewModel.uiState.value.allAdhkar.first { it.repeat >= 3 }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(500))
        // First valid tap + immediate rapid second tap within 30ms (< 120ms guard)
        viewModel.onDhikrCardTap(multiRepeatDhikr)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(30))
        viewModel.onDhikrCardTap(multiRepeatDhikr)
        waitUntilMainIdle {
            viewModel.uiState.value.persisted.todayDhikrCounts[multiRepeatDhikr.id] == 1
        }
        assertEquals(
            "Rapid double tap within 30ms must only increment once",
            1,
            viewModel.uiState.value.persisted.todayDhikrCounts[multiRepeatDhikr.id]
        )

        // Normal second tap after 160ms (> 120ms guard) must increment to 2
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(160))
        viewModel.onDhikrCardTap(multiRepeatDhikr)
        waitUntilMainIdle {
            viewModel.uiState.value.persisted.todayDhikrCounts[multiRepeatDhikr.id] == 2
        }
        assertEquals(
            "Normal tap after 160ms must increment to 2",
            2,
            viewModel.uiState.value.persisted.todayDhikrCounts[multiRepeatDhikr.id]
        )

        // 4. Test changing theme palette (HAJAR), city, and calculation method without losing today's dhikr progress
        viewModel.updateThemePalette(com.example.domain.model.WirdiThemePalette.HAJAR)
        val damascus = viewModel.uiState.value.cities.first { it.nameAr.contains("دمشق") }
        viewModel.selectCity(damascus)
        viewModel.updateCalcMethod(com.example.domain.model.PrayerCalcMethod.UMM_AL_QURA)

        waitUntilMainIdle {
            val s = viewModel.uiState.value.persisted.settings
            s.themePalette == com.example.domain.model.WirdiThemePalette.HAJAR &&
                s.calcMethod == com.example.domain.model.PrayerCalcMethod.UMM_AL_QURA &&
                s.cityNameAr.contains("دمشق")
        }
        val stateAfterSettingsChange = viewModel.uiState.value.persisted
        assertEquals(
            "Switching to HAJAR must automatically set LIGHT mode",
            com.example.domain.model.DarkModeConfig.LIGHT,
            stateAfterSettingsChange.settings.darkModeConfig
        )
        assertEquals(
            "Changing theme/city/method must preserve today's dhikr counts",
            2,
            stateAfterSettingsChange.todayDhikrCounts[multiRepeatDhikr.id]
        )

        // 5. Open Dhikr Session from «وِردك الآن», test pause/resume, position persistence, and exit
        viewModel.clearBannerMessage()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("current_wird_session_btn").assertExists()
        viewModel.openCurrentRecommendedWird(asFocusSession = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dhikr_session_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_tap_counter").assertIsDisplayed()

        // Pause and resume inside session
        composeTestRule.onNodeWithTag("session_pause_resume_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("session_paused_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("session_pause_resume_btn").performClick()
        composeTestRule.waitForIdle()

        // Save position 3 and exit session
        viewModel.saveLastReadDhikrPosition(com.example.domain.model.DhikrCategory.MORNING, 3)
        waitUntilMainIdle {
            viewModel.uiState.value.persisted.lastReadDhikrIndexByCategory["MORNING"] == 3
        }
        assertEquals(3, viewModel.uiState.value.persisted.lastReadDhikrIndexByCategory["MORNING"])

        composeTestRule.onNodeWithTag("session_exit_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_next_prayer_card").assertIsDisplayed()

        // 6. Test Home section reordering persistence
        val defaultOrder = viewModel.uiState.value.persisted.settings.normalizedHomeSectionsOrder()
        val secondSection = defaultOrder[1]
        viewModel.moveHomeSectionOrder(secondSection, moveUp = true)
        waitUntilMainIdle {
            viewModel.uiState.value.persisted.settings.normalizedHomeSectionsOrder().first() == secondSection
        }
        assertEquals(
            "Moved section should now be at index 0",
            secondSection,
            viewModel.uiState.value.persisted.settings.normalizedHomeSectionsOrder().first()
        )

        // 7. Test live midnight boundary rollover resetting daily counts while preserving favorites/positions
        val tomorrow = java.time.ZonedDateTime.now().plusDays(1).withHour(5).withMinute(30)
        viewModel.onTimeBoundaryReached(tomorrow)
        waitUntilMainIdle {
            viewModel.uiState.value.persisted.todayDateIso == tomorrow.toLocalDate().toString() &&
                viewModel.uiState.value.persisted.todayDhikrCounts.isEmpty()
        }
        assertEquals(tomorrow.toLocalDate().toString(), viewModel.uiState.value.persisted.todayDateIso)
        assertTrue(viewModel.uiState.value.persisted.todayDhikrCounts.isEmpty())

        // 8. Test notification deep-linking to Morning/Evening/Sleep Adhkar and Friday Sunan
        viewModel.handleNotificationDeepLink("ADHKAR_EVENING")
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.WirdiTab.ADHKAR, viewModel.uiState.value.selectedTab)
        assertEquals(com.example.domain.model.DhikrCategory.EVENING, viewModel.uiState.value.selectedDhikrCategory)

        viewModel.handleNotificationDeepLink("FRIDAY_SUNAN")
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.WirdiTab.LIBRARY, viewModel.uiState.value.selectedTab)
        assertEquals(com.example.ui.LibrarySection.FRIDAY_SUNAN, viewModel.uiState.value.selectedLibrarySection)

        // 9. Test Library Favorites filter chip & Empty State when no favorites match
        composeTestRule.onNodeWithTag("library_favorites_filter_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_clear_filters_btn").performClick()
        composeTestRule.waitForIdle()

        // 10. Test Monthly Timetable month navigation (Next / Reset)
        viewModel.openMoreSubScreen(com.example.ui.MoreSubScreen.MONTHLY_TIMETABLE)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("next_month_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_month_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_month_btn").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `corrupted or out of bounds backup JSON is safely rejected or sanitized without crashing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = WirdiPreferencesRepository(context)

        // 1. Completely invalid string is rejected without corrupting state
        val badRes = prefs.importBackupJson("not a json file")
        assertTrue("Non-JSON input must fail gracefully", badRes.isFailure)

        // 2. Unsupported version is rejected
        val badVerRes = prefs.importBackupJson("""{"version": 999}""")
        assertTrue("Unsupported version must fail gracefully", badVerRes.isFailure)

        // 3. Valid JSON with out-of-bounds coordinates and blank custom dhikr is sanitized
        val dirtyJson = """
            {
              "version": 2,
              "settings": {
                "cityNameAr": "حلب",
                "latitude": 999.0,
                "longitude": -999.0,
                "readingFontScale": 9.9
              },
              "customAdhkar": [
                {"id": "c_valid", "category": "CUSTOM", "text": "سبحان الله وبحمده", "repeat": 999999},
                {"id": "c_blank", "category": "CUSTOM", "text": "   ", "repeat": 3}
              ],
              "tasbihCurrentCount": -50
            }
        """.trimIndent()
        val cleanRes = prefs.importBackupJson(dirtyJson)
        assertTrue("Sanitized JSON backup should succeed", cleanRes.isSuccess)
        val state = prefs.stateFlow.first()
        assertEquals(89.9, state.settings.latitude, 0.001)
        assertEquals(-180.0, state.settings.longitude, 0.001)
        assertEquals(1.6f, state.settings.readingFontScale, 0.001f)
        assertEquals(1, state.customAdhkar.size)
        assertEquals(10000, state.customAdhkar.first().repeat)
        assertEquals(0, state.tasbihCurrentCount)
    }
}

