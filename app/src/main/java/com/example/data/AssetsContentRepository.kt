package com.example.data

import android.content.Context
import com.example.domain.model.AsmaItem
import com.example.domain.model.CityLocation
import com.example.domain.model.DhikrItem
import com.example.domain.model.DuasAndFridayBundle
import com.example.domain.model.HadithItem
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AssetsContentRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mutex = Mutex()
    private var cachedAdhkar: List<DhikrItem>? = null
    private var cachedNawawi: List<HadithItem>? = null
    private var cachedAsma: List<AsmaItem>? = null
    private var cachedDuasBundle: DuasAndFridayBundle? = null
    private var cachedCities: List<CityLocation>? = null

    suspend fun getAdhkar(): List<DhikrItem> = withContext(ioDispatcher) {
        mutex.withLock {
            cachedAdhkar?.let { return@withContext it }
            val raw = readAssetFile("adhkar.json")
            val parsed = json.decodeFromString<List<DhikrItem>>(raw)
                // Enforce Governing Rule 5: No item displayed without source or repeat <= 0
                .filter { !it.source.isNullOrBlank() && it.repeat > 0 }
            cachedAdhkar = parsed
            parsed
        }
    }

    suspend fun getNawawiHadiths(): List<HadithItem> = withContext(ioDispatcher) {
        mutex.withLock {
            cachedNawawi?.let { return@withContext it }
            val raw = readAssetFile("nawawi_42.json")
            val parsed = json.decodeFromString<List<HadithItem>>(raw)
                .filter { !it.source.isNullOrBlank() }
            cachedNawawi = parsed
            parsed
        }
    }

    suspend fun getAsmaUlHusna(): List<AsmaItem> = withContext(ioDispatcher) {
        mutex.withLock {
            cachedAsma?.let { return@withContext it }
            val raw = readAssetFile("asma_ul_husna.json")
            val parsed = json.decodeFromString<List<AsmaItem>>(raw)
                .filter { it.listReference.isNotBlank() }
            cachedAsma = parsed
            parsed
        }
    }

    suspend fun getDuasAndFriday(): DuasAndFridayBundle = withContext(ioDispatcher) {
        mutex.withLock {
            cachedDuasBundle?.let { return@withContext it }
            val raw = readAssetFile("duas_and_friday.json")
            val parsed = json.decodeFromString<DuasAndFridayBundle>(raw)
            val filtered = DuasAndFridayBundle(
                quranicDuas = parsed.quranicDuas.filter { !it.source.isNullOrBlank() },
                propheticDuas = parsed.propheticDuas.filter { !it.source.isNullOrBlank() },
                fridaySunan = parsed.fridaySunan.filter { !it.source.isNullOrBlank() }
            )
            cachedDuasBundle = filtered
            filtered
        }
    }

    suspend fun getCities(): List<CityLocation> = withContext(ioDispatcher) {
        mutex.withLock {
            cachedCities?.let { return@withContext it }
            val raw = readAssetFile("cities.json")
            val parsed = json.decodeFromString<List<CityLocation>>(raw)
            cachedCities = parsed
            parsed
        }
    }

    /**
     * Selects a deterministic "Dhikr of the Day" based on the day of year so it stays consistent
     * throughout the day across the Home screen and Glance AppWidget.
     */
    suspend fun getDailyHighlightDhikr(date: LocalDate = LocalDate.now()): DhikrItem {
        val list = getAdhkar()
        if (list.isEmpty()) {
            return DhikrItem(
                id = "fallback",
                category = "MORNING",
                text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ.",
                repeat = 1,
                source = "صحيح البخاري (6406) وصحيح مسلم (2694)",
                grade = "متفق عليه"
            )
        }
        val index = ((date.year * 366 + date.dayOfYear).coerceAtLeast(0)) % list.size
        return list[index]
    }

    private fun readAssetFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
