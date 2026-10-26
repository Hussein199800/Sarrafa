package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.domain.model.DhikrCategory
import com.example.domain.model.DhikrItem
import com.example.domain.model.UserSettings
import com.example.domain.model.WirdiBackupPayload
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private object WirdiDataStoreProvider {
    private val instances = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun get(context: Context): DataStore<Preferences> {
        val appContext = context.applicationContext ?: context
        val key = appContext.filesDir.absolutePath
        return instances.computeIfAbsent(key) {
            PreferenceDataStoreFactory.create(
                produceFile = { File(appContext.filesDir, "datastore/wirdi_preferences.preferences_pb") }
            )
        }
    }
}

private val Context.wirdiDataStore: DataStore<Preferences>
    get() = WirdiDataStoreProvider.get(this)

data class WirdiPersistedState(
    val settings: UserSettings = UserSettings(),
    val favoriteIds: Set<String> = emptySet(),
    val customAdhkar: List<DhikrItem> = emptyList(),
    val todayDateIso: String = LocalDate.now().toString(),
    val todayDhikrCounts: Map<String, Int> = emptyMap(),
    val tasbihCurrentCount: Int = 0,
    val tasbihTodayTotal: Int = 0,
    val lastReadDhikrCategory: String = DhikrCategory.MORNING.name,
    val lastReadDhikrIndexByCategory: Map<String, Int> = emptyMap(),
    val lastReadLibraryItemBySection: Map<String, String> = emptyMap()
)

class WirdiPreferencesRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
    }

    private object Keys {
        val USER_SETTINGS_JSON = stringPreferencesKey("user_settings_json")
        val FAVORITE_IDS_JSON = stringPreferencesKey("favorite_ids_json")
        val CUSTOM_ADHKAR_JSON = stringPreferencesKey("custom_adhkar_json")
        val TODAY_DATE_ISO = stringPreferencesKey("today_date_iso")
        val TODAY_DHIKR_COUNTS_JSON = stringPreferencesKey("today_dhikr_counts_json")
        val TASBIH_CURRENT_COUNT = intPreferencesKey("tasbih_current_count")
        val TASBIH_TODAY_TOTAL = intPreferencesKey("tasbih_today_total")
        val LAST_READ_DHIKR_CATEGORY = stringPreferencesKey("last_read_dhikr_category")
        val LAST_READ_DHIKR_INDEX_JSON = stringPreferencesKey("last_read_dhikr_index_json")
        val LAST_READ_LIBRARY_ITEM_JSON = stringPreferencesKey("last_read_library_item_json")
    }

    fun decodeSettingsSafely(rawJson: String?): UserSettings {
        if (rawJson.isNullOrBlank()) return UserSettings()
        return runCatching {
            json.decodeFromString<UserSettings>(rawJson).sanitized()
        }.getOrDefault(UserSettings())
    }

    val stateFlow: Flow<WirdiPersistedState> = context.wirdiDataStore.data.map { prefs ->
        val storedDate = prefs[Keys.TODAY_DATE_ISO] ?: LocalDate.now().toString()

        val settings = decodeSettingsSafely(prefs[Keys.USER_SETTINGS_JSON])

        val favorites = prefs[Keys.FAVORITE_IDS_JSON]?.let {
            runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull()
        } ?: emptySet()

        val customList = prefs[Keys.CUSTOM_ADHKAR_JSON]?.let {
            runCatching { json.decodeFromString<List<DhikrItem>>(it) }.getOrNull()
        } ?: emptyList()

        val counts = prefs[Keys.TODAY_DHIKR_COUNTS_JSON]?.let {
            runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull()
        } ?: emptyMap()

        val tasbihCurrent = (prefs[Keys.TASBIH_CURRENT_COUNT] ?: 0).coerceAtLeast(0)
        val tasbihTotal = (prefs[Keys.TASBIH_TODAY_TOTAL] ?: 0).coerceAtLeast(0)

        val lastCat = prefs[Keys.LAST_READ_DHIKR_CATEGORY] ?: DhikrCategory.MORNING.name
        val lastDhikrIndices = prefs[Keys.LAST_READ_DHIKR_INDEX_JSON]?.let {
            runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull()
        } ?: emptyMap()

        val lastLibItems = prefs[Keys.LAST_READ_LIBRARY_ITEM_JSON]?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        } ?: emptyMap()

        WirdiPersistedState(
            settings = settings,
            favoriteIds = favorites,
            customAdhkar = customList,
            todayDateIso = storedDate,
            todayDhikrCounts = counts,
            tasbihCurrentCount = tasbihCurrent,
            tasbihTodayTotal = tasbihTotal,
            lastReadDhikrCategory = lastCat,
            lastReadDhikrIndexByCategory = lastDhikrIndices,
            lastReadLibraryItemBySection = lastLibItems
        )
    }

    suspend fun ensureDailyReset(currentDateIso: String = LocalDate.now().toString()) {
        context.wirdiDataStore.edit { prefs ->
            val storedDate = prefs[Keys.TODAY_DATE_ISO]
            if (storedDate != currentDateIso) {
                prefs[Keys.TODAY_DATE_ISO] = currentDateIso
                prefs[Keys.TODAY_DHIKR_COUNTS_JSON] = json.encodeToString(emptyMap<String, Int>())
                prefs[Keys.TASBIH_TODAY_TOTAL] = 0
            }
        }
    }

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings {
        var result = UserSettings()
        context.wirdiDataStore.edit { prefs ->
            val current = decodeSettingsSafely(prefs[Keys.USER_SETTINGS_JSON])
            val updated = transform(current).sanitized()
            result = updated
            prefs[Keys.USER_SETTINGS_JSON] = json.encodeToString(updated)
        }
        return result
    }

    suspend fun saveLastReadDhikrPosition(categoryName: String, pageIndex: Int) {
        context.wirdiDataStore.edit { prefs ->
            prefs[Keys.LAST_READ_DHIKR_CATEGORY] = categoryName
            val currentMap = prefs[Keys.LAST_READ_DHIKR_INDEX_JSON]?.let {
                runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull()
            } ?: emptyMap()
            val updated = currentMap.toMutableMap().apply {
                put(categoryName, pageIndex.coerceAtLeast(0))
            }
            prefs[Keys.LAST_READ_DHIKR_INDEX_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun saveLastReadLibraryItem(sectionName: String, itemId: String) {
        context.wirdiDataStore.edit { prefs ->
            val currentMap = prefs[Keys.LAST_READ_LIBRARY_ITEM_JSON]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            } ?: emptyMap()
            val updated = currentMap.toMutableMap().apply {
                put(sectionName, itemId)
            }
            prefs[Keys.LAST_READ_LIBRARY_ITEM_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun toggleFavorite(itemId: String) {
        context.wirdiDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS_JSON]?.let {
                runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull()
            } ?: emptySet()
            val updated = if (current.contains(itemId)) current - itemId else current + itemId
            prefs[Keys.FAVORITE_IDS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun incrementDhikrCount(dhikrId: String, targetRepeat: Int, todayIso: String = LocalDate.now().toString()): Int {
        var newCountResult = 0
        context.wirdiDataStore.edit { prefs ->
            val storedDate = prefs[Keys.TODAY_DATE_ISO]
            val currentMap = if (storedDate == todayIso) {
                prefs[Keys.TODAY_DHIKR_COUNTS_JSON]?.let {
                    runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull()
                } ?: emptyMap()
            } else {
                prefs[Keys.TODAY_DATE_ISO] = todayIso
                prefs[Keys.TASBIH_TODAY_TOTAL] = 0
                emptyMap()
            }
            val currentVal = currentMap[dhikrId] ?: 0
            val nextVal = (currentVal + 1).coerceAtMost(targetRepeat.coerceAtLeast(1))
            newCountResult = nextVal
            val updatedMap = currentMap.toMutableMap().apply { put(dhikrId, nextVal) }
            prefs[Keys.TODAY_DHIKR_COUNTS_JSON] = json.encodeToString(updatedMap)
        }
        return newCountResult
    }

    suspend fun resetCategoryCounts(dhikrIds: List<String>, todayIso: String = LocalDate.now().toString()) {
        context.wirdiDataStore.edit { prefs ->
            val storedDate = prefs[Keys.TODAY_DATE_ISO]
            if (storedDate != todayIso) {
                prefs[Keys.TODAY_DATE_ISO] = todayIso
                prefs[Keys.TODAY_DHIKR_COUNTS_JSON] = json.encodeToString(emptyMap<String, Int>())
                return@edit
            }
            val currentMap = prefs[Keys.TODAY_DHIKR_COUNTS_JSON]?.let {
                runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull()
            } ?: emptyMap()
            val updatedMap = currentMap.toMutableMap().apply {
                dhikrIds.forEach { remove(it) }
            }
            prefs[Keys.TODAY_DHIKR_COUNTS_JSON] = json.encodeToString(updatedMap)
        }
    }

    suspend fun addOrUpdateCustomDhikr(item: DhikrItem) {
        if (item.text.isBlank() || item.repeat <= 0) return
        context.wirdiDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_ADHKAR_JSON]?.let {
                runCatching { json.decodeFromString<List<DhikrItem>>(it) }.getOrNull()
            } ?: emptyList()
            val mutable = current.toMutableList()
            val idx = mutable.indexOfFirst { it.id == item.id }
            val sanitized = item.copy(
                category = "CUSTOM",
                source = item.source?.ifBlank { "ورد شخصي (مضاف بواسطة المستخدم)" } ?: "ورد شخصي (مضاف بواسطة المستخدم)",
                isCustom = true
            )
            if (idx >= 0) {
                mutable[idx] = sanitized
            } else {
                mutable.add(sanitized)
            }
            prefs[Keys.CUSTOM_ADHKAR_JSON] = json.encodeToString(mutable)
        }
    }

    suspend fun deleteCustomDhikr(id: String) {
        context.wirdiDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_ADHKAR_JSON]?.let {
                runCatching { json.decodeFromString<List<DhikrItem>>(it) }.getOrNull()
            } ?: emptyList()
            val filtered = current.filterNot { it.id == id }
            prefs[Keys.CUSTOM_ADHKAR_JSON] = json.encodeToString(filtered)
        }
    }

    suspend fun incrementTasbih(todayIso: String = LocalDate.now().toString()) {
        context.wirdiDataStore.edit { prefs ->
            val storedDate = prefs[Keys.TODAY_DATE_ISO]
            val currentCount = prefs[Keys.TASBIH_CURRENT_COUNT] ?: 0
            val todayTotal = if (storedDate == todayIso) {
                prefs[Keys.TASBIH_TODAY_TOTAL] ?: 0
            } else {
                prefs[Keys.TODAY_DATE_ISO] = todayIso
                prefs[Keys.TODAY_DHIKR_COUNTS_JSON] = json.encodeToString(emptyMap<String, Int>())
                0
            }
            prefs[Keys.TASBIH_CURRENT_COUNT] = currentCount + 1
            prefs[Keys.TASBIH_TODAY_TOTAL] = todayTotal + 1
        }
    }

    suspend fun resetCurrentTasbih() {
        context.wirdiDataStore.edit { prefs ->
            prefs[Keys.TASBIH_CURRENT_COUNT] = 0
        }
    }

    suspend fun exportBackupJson(): String {
        val current = stateFlow.first()
        val payload = WirdiBackupPayload(
            version = 2,
            exportedAtEpochMillis = System.currentTimeMillis(),
            settings = current.settings,
            favoriteIds = current.favoriteIds,
            customAdhkar = current.customAdhkar,
            todayDateIso = current.todayDateIso,
            todayDhikrCounts = current.todayDhikrCounts,
            tasbihCurrentCount = current.tasbihCurrentCount,
            tasbihTodayTotal = current.tasbihTodayTotal,
            lastReadDhikrCategory = current.lastReadDhikrCategory,
            lastReadDhikrIndexByCategory = current.lastReadDhikrIndexByCategory,
            lastReadLibraryItemBySection = current.lastReadLibraryItemBySection
        )
        return json.encodeToString(payload)
    }

    suspend fun importBackupJson(rawJson: String): Result<Unit> {
        return runCatching {
            require(rawJson.length <= 2_000_000) {
                "Backup file exceeds maximum safe size (2MB)"
            }
            val trimmed = rawJson.trim()
            require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
                "Invalid JSON backup object"
            }
            val payload = json.decodeFromString<WirdiBackupPayload>(trimmed)
            require(payload.version in 1..10) {
                "Unsupported backup version: ${payload.version}"
            }
            val safeSettings = payload.settings.sanitized()
            val safeCustom = payload.customAdhkar
                .filter { it.text.isNotBlank() && it.id.isNotBlank() }
                .distinctBy { it.id }
                .take(500)
                .map {
                    it.copy(
                        id = it.id.trim().take(120),
                        category = "CUSTOM",
                        text = it.text.trim().take(4000),
                        repeat = it.repeat.coerceIn(1, 10000),
                        isCustom = true,
                        source = it.source?.ifBlank { "ورد شخصي (مضاف بواسطة المستخدم)" }
                            ?: "ورد شخصي (مضاف بواسطة المستخدم)"
                    )
                }
            val safeCounts = payload.todayDhikrCounts
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, v) -> v.coerceIn(0, 10000) }
            val validDateIso = payload.todayDateIso.takeIf { iso ->
                iso.isNotBlank() && runCatching { LocalDate.parse(iso) }.isSuccess
            }
            val validCategoryNames = com.example.domain.model.DhikrCategory.entries.map { it.name }.toSet()
            val validLastCat = payload.lastReadDhikrCategory.takeIf { it in validCategoryNames }

            context.wirdiDataStore.edit { prefs ->
                prefs[Keys.USER_SETTINGS_JSON] = json.encodeToString(safeSettings)
                prefs[Keys.FAVORITE_IDS_JSON] = json.encodeToString(
                    payload.favoriteIds.filter { it.isNotBlank() }.map { it.trim() }.toSet()
                )
                prefs[Keys.CUSTOM_ADHKAR_JSON] = json.encodeToString(safeCustom)
                if (validDateIso != null) {
                    prefs[Keys.TODAY_DATE_ISO] = validDateIso
                }
                prefs[Keys.TODAY_DHIKR_COUNTS_JSON] = json.encodeToString(safeCounts)
                prefs[Keys.TASBIH_CURRENT_COUNT] = payload.tasbihCurrentCount.coerceIn(0, 1_000_000)
                prefs[Keys.TASBIH_TODAY_TOTAL] = payload.tasbihTodayTotal.coerceIn(0, 1_000_000)
                if (validLastCat != null) {
                    prefs[Keys.LAST_READ_DHIKR_CATEGORY] = validLastCat
                }
                if (payload.lastReadDhikrIndexByCategory.isNotEmpty()) {
                    val safeIndices = payload.lastReadDhikrIndexByCategory
                        .filterKeys { it in validCategoryNames }
                        .mapValues { (_, v) -> v.coerceIn(0, 1000) }
                    prefs[Keys.LAST_READ_DHIKR_INDEX_JSON] = json.encodeToString(safeIndices)
                }
                if (payload.lastReadLibraryItemBySection.isNotEmpty()) {
                    val safeLibraryMap = payload.lastReadLibraryItemBySection
                        .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
                    prefs[Keys.LAST_READ_LIBRARY_ITEM_JSON] = json.encodeToString(safeLibraryMap)
                }
            }
        }
    }
}
