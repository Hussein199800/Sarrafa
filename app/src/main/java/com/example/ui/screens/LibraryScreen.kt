package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AsmaItem
import com.example.domain.model.DuaItem
import com.example.domain.model.FridaySunnahItem
import com.example.domain.model.HadithItem
import com.example.domain.model.ReadingFontFamily
import com.example.domain.util.ArabicTextNormalizer
import com.example.domain.util.NumeralFormatter
import com.example.ui.LibrarySection
import com.example.ui.WirdiUiState
import com.example.ui.components.WirdiIcons
import com.example.ui.components.WirdiLargeTitleHeader
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.shareText
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.WirdiDesignTokens
import com.example.ui.theme.buildArabicReadingTextStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    uiState: WirdiUiState,
    isExpandedScreen: Boolean,
    onSelectSection: (LibrarySection) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSaveLastReadItem: (LibrarySection, Int) -> Unit = { _, _ -> },
    onCopiedToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSection = uiState.selectedLibrarySection
    val query = uiState.searchQuery

    if (isExpandedScreen) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        WirdiLargeTitleHeader(
                            title = stringResource(R.string.nav_library),
                            subtitle = "الأحاديث والأسماء والأدعية"
                        )
                    }
                    items(LibrarySection.entries) { sec ->
                        val isSelected = sec == selectedSection
                        Surface(
                            shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(WirdiDesignTokens.smallCornerRadius))
                                .clickable { onSelectSection(sec) }
                                .testTag("lib_sec_master_${sec.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sec.titleAr,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = WirdiIcons.ChevronStart,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LibrarySectionDetailContent(
                    uiState = uiState,
                    selectedSection = selectedSection,
                    query = query,
                    onSearchQueryChange = onSearchQueryChange,
                    onToggleFavorite = onToggleFavorite,
                    onSaveLastReadItem = onSaveLastReadItem,
                    onCopiedToast = onCopiedToast
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding)
        ) {
            WirdiLargeTitleHeader(
                title = stringResource(R.string.nav_library)
            )

            LibrarySegmentedControl(
                selectedSection = selectedSection,
                onSelectSection = onSelectSection
            )

            Spacer(modifier = Modifier.height(6.dp))

            LibrarySectionDetailContent(
                uiState = uiState,
                selectedSection = selectedSection,
                query = query,
                onSearchQueryChange = onSearchQueryChange,
                onToggleFavorite = onToggleFavorite,
                onSaveLastReadItem = onSaveLastReadItem,
                onCopiedToast = onCopiedToast
            )
        }
    }
}

@Composable
private fun LibrarySegmentedControl(
    selectedSection: LibrarySection,
    onSelectSection: (LibrarySection) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val shortTitleMap = remember {
        mapOf(
            LibrarySection.NAWAWI to "الأربعون",
            LibrarySection.ASMA to "الأسماء",
            LibrarySection.DUAS_QURAN to "أدعية القرآن",
            LibrarySection.DUAS_SUNNAH to "أدعية السنة",
            LibrarySection.FRIDAY_SUNAN to "الجمعة"
        )
    }

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
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LibrarySection.entries.forEach { sec ->
                val isSelected = sec == selectedSection
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    animationSpec = if (reduceMotion) snap() else tween(180, easing = FastOutSlowInEasing),
                    label = "libSegmentBg"
                )
                Surface(
                    onClick = { onSelectSection(sec) },
                    shape = RoundedCornerShape(50),
                    color = bgColor,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lib_sec_chip_${sec.name}")
                ) {
                    Text(
                        text = shortTitleMap[sec] ?: sec.titleAr,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionDetailContent(
    uiState: WirdiUiState,
    selectedSection: LibrarySection,
    query: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSaveLastReadItem: (LibrarySection, Int) -> Unit,
    onCopiedToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val favIds = uiState.persisted.favoriteIds
    val savedIndex = uiState.persisted.lastReadLibraryItemBySection[selectedSection.name]?.toIntOrNull() ?: 0
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var favoritesOnly by remember(selectedSection) { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(listState, selectedSection, query, favoritesOnly) {
        if (query.isBlank() && !favoritesOnly) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    if (index > 0) {
                        onSaveLastReadItem(selectedSection, index)
                    }
                }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sticky search bar + Favorites filter chip + optional Resume Last Read chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("library_search_input")
            )

            Surface(
                onClick = { favoritesOnly = !favoritesOnly },
                shape = RoundedCornerShape(50),
                color = if (favoritesOnly) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    WirdiDesignTokens.hairlineBorderWidth,
                    if (favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.testTag("library_favorites_filter_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "المفضلة",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (favoritesOnly) FontWeight.Bold else FontWeight.Medium,
                        color = if (favoritesOnly) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (savedIndex > 0 && query.isBlank() && !favoritesOnly) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(savedIndex)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("library_resume_last_read_btn")
                ) {
                    Text(
                        text = "${stringResource(R.string.last_read_resume_banner)} (${NumeralFormatter.format(savedIndex + 1, useArabicIndic)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        if (selectedSection == LibrarySection.ASMA) {
            Surface(
                shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = WirdiIcons.InfoCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.asma_source_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (selectedSection) {
            LibrarySection.NAWAWI -> {
                val filtered = remember(uiState.nawawiHadiths, query, favoritesOnly, favIds) {
                    uiState.nawawiHadiths.filter {
                        (!favoritesOnly || favIds.contains(it.id)) &&
                            ArabicTextNormalizer.matchesQuery("${it.text} ${it.narrator} ${it.source}", query)
                    }
                }
                if (filtered.isEmpty()) {
                    LibraryEmptyStateCard(
                        isFavoritesFilter = favoritesOnly && query.isBlank(),
                        onClearFilters = {
                            onSearchQueryChange("")
                            favoritesOnly = false
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp)
                    ) {
                        items(filtered, key = { it.id }) { hadith ->
                            HadithLibraryCard(
                                hadith = hadith,
                                isFavorite = favIds.contains(hadith.id),
                                readingFont = settings.readingFont,
                                readingFontScale = settings.readingFontScale,
                                useArabicIndic = useArabicIndic,
                                onToggleFavorite = { onToggleFavorite(hadith.id) },
                                onCopiedToast = onCopiedToast
                            )
                        }
                    }
                }
            }

            LibrarySection.ASMA -> {
                val filtered = remember(uiState.asmaUlHusna, query, favoritesOnly, favIds) {
                    uiState.asmaUlHusna.filter {
                        (!favoritesOnly || favIds.contains("asma_${it.id}")) &&
                            ArabicTextNormalizer.matchesQuery("${it.name} ${it.meaning}", query)
                    }
                }
                if (filtered.isEmpty()) {
                    LibraryEmptyStateCard(
                        isFavoritesFilter = favoritesOnly && query.isBlank(),
                        onClearFilters = {
                            onSearchQueryChange("")
                            favoritesOnly = false
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp)
                    ) {
                        items(filtered, key = { it.id }) { asma ->
                            AsmaLibraryCard(
                                asma = asma,
                                isFavorite = favIds.contains("asma_${asma.id}"),
                                readingFont = settings.readingFont,
                                readingFontScale = settings.readingFontScale,
                                useArabicIndic = useArabicIndic,
                                onToggleFavorite = { onToggleFavorite("asma_${asma.id}") },
                                onCopiedToast = onCopiedToast
                            )
                        }
                    }
                }
            }

            LibrarySection.DUAS_QURAN -> {
                val filtered = remember(uiState.duasBundle.quranicDuas, query, favoritesOnly, favIds) {
                    uiState.duasBundle.quranicDuas.filter {
                        (!favoritesOnly || favIds.contains(it.id)) &&
                            ArabicTextNormalizer.matchesQuery("${it.text} ${it.source}", query)
                    }
                }
                if (filtered.isEmpty()) {
                    LibraryEmptyStateCard(
                        isFavoritesFilter = favoritesOnly && query.isBlank(),
                        onClearFilters = {
                            onSearchQueryChange("")
                            favoritesOnly = false
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp)
                    ) {
                        items(filtered, key = { it.id }) { dua ->
                            DuaLibraryCard(
                                dua = dua,
                                isFavorite = favIds.contains(dua.id),
                                readingFont = settings.readingFont,
                                readingFontScale = settings.readingFontScale,
                                useArabicIndic = useArabicIndic,
                                onToggleFavorite = { onToggleFavorite(dua.id) },
                                onCopiedToast = onCopiedToast
                            )
                        }
                    }
                }
            }

            LibrarySection.DUAS_SUNNAH -> {
                val filtered = remember(uiState.duasBundle.propheticDuas, query, favoritesOnly, favIds) {
                    uiState.duasBundle.propheticDuas.filter {
                        (!favoritesOnly || favIds.contains(it.id)) &&
                            ArabicTextNormalizer.matchesQuery("${it.text} ${it.source}", query)
                    }
                }
                if (filtered.isEmpty()) {
                    LibraryEmptyStateCard(
                        isFavoritesFilter = favoritesOnly && query.isBlank(),
                        onClearFilters = {
                            onSearchQueryChange("")
                            favoritesOnly = false
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp)
                    ) {
                        items(filtered, key = { it.id }) { dua ->
                            DuaLibraryCard(
                                dua = dua,
                                isFavorite = favIds.contains(dua.id),
                                readingFont = settings.readingFont,
                                readingFontScale = settings.readingFontScale,
                                useArabicIndic = useArabicIndic,
                                onToggleFavorite = { onToggleFavorite(dua.id) },
                                onCopiedToast = onCopiedToast
                            )
                        }
                    }
                }
            }

            LibrarySection.FRIDAY_SUNAN -> {
                val filtered = remember(uiState.duasBundle.fridaySunan, query, favoritesOnly, favIds) {
                    uiState.duasBundle.fridaySunan.filter {
                        (!favoritesOnly || favIds.contains(it.id)) &&
                            ArabicTextNormalizer.matchesQuery("${it.title} ${it.evidence} ${it.source}", query)
                    }
                }
                if (filtered.isEmpty()) {
                    LibraryEmptyStateCard(
                        isFavoritesFilter = favoritesOnly && query.isBlank(),
                        onClearFilters = {
                            onSearchQueryChange("")
                            favoritesOnly = false
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp)
                    ) {
                        items(filtered, key = { it.id }) { sunnah ->
                            FridaySunnahCard(
                                sunnah = sunnah,
                                isFavorite = favIds.contains(sunnah.id),
                                readingFont = settings.readingFont,
                                readingFontScale = settings.readingFontScale,
                                useArabicIndic = useArabicIndic,
                                onToggleFavorite = { onToggleFavorite(sunnah.id) },
                                onCopiedToast = onCopiedToast
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyStateCard(
    isFavoritesFilter: Boolean,
    onClearFilters: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("library_empty_state"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isFavoritesFilter) Icons.Default.FavoriteBorder else Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isFavoritesFilter) {
                    "لا توجد عناصر مفضلة في هذا القسم بعد"
                } else {
                    "لا توجد نتائج مطابقة لبحثك"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isFavoritesFilter) {
                    "اضغط على أيقونة القلب بجانب أي حديث أو اسم أو دعاء لحفظه في المفضلة والرجوع إليه سريعاً."
                } else {
                    "جرّب البحث بكلمات أقصر أو بدون علامات ترقيم، فالبحث يدعم تجاهل التشكيل والهمزات تلقائياً."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Surface(
                onClick = onClearFilters,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("library_clear_filters_btn")
            ) {
                Text(
                    text = "عرض جميع العناصر",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun HadithLibraryCard(
    hadith: HadithItem,
    isFavorite: Boolean,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    onToggleFavorite: () -> Unit,
    onCopiedToast: () -> Unit
) {
    val context = LocalContext.current
    val numFormatted = NumeralFormatter.format(hadith.number, useArabicIndic)
    val fullText = "الحديث ($numFormatted)\n${hadith.text}\nالراوي: ${hadith.narrator}\nالمصدر: ${hadith.source.orEmpty()}\nالدرجة: ${hadith.grade.orEmpty()}"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hadith_card_${hadith.number}"),
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
                        text = "الحديث $numFormatted",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفضيل الحديث",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { copyTextToClipboard(context, "حديث", fullText, onCopiedToast) }) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ الحديث",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareText(context, fullText) }) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة الحديث",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = hadith.text,
                style = buildArabicReadingTextStyle(readingFont, readingFontScale, 20f),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${stringResource(R.string.narrator_label)} ${hadith.narrator}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            if (!hadith.source.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(hadith.source, useArabicIndic)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!hadith.grade.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.grade_label)} ${NumeralFormatter.format(hadith.grade, useArabicIndic)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AsmaLibraryCard(
    asma: AsmaItem,
    isFavorite: Boolean,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    onToggleFavorite: () -> Unit,
    onCopiedToast: () -> Unit
) {
    val context = LocalContext.current
    val numStr = NumeralFormatter.format(asma.id, useArabicIndic)
    val shareContent = "${asma.name}: ${asma.meaning}\nالمرجع: ${asma.listReference}"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("asma_card_${asma.id}"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = numStr,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = asma.name,
                        style = buildArabicReadingTextStyle(
                            readingFont = readingFont,
                            scale = readingFontScale,
                            baseFontSizeSp = 24f,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (asma.needsHumanReview) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.needs_human_review_badge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفضيل الاسم",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { copyTextToClipboard(context, asma.name, shareContent, onCopiedToast) }) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ الاسم",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareText(context, shareContent) }) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة الاسم",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = asma.meaning,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "المرجع: ${NumeralFormatter.format(asma.listReference, useArabicIndic)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DuaLibraryCard(
    dua: DuaItem,
    isFavorite: Boolean,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    onToggleFavorite: () -> Unit,
    onCopiedToast: () -> Unit
) {
    val context = LocalContext.current
    val shareContent = buildString {
        append(dua.text)
        if (!dua.source.isNullOrBlank()) append("\nالمصدر: ${dua.source}")
        if (!dua.grade.isNullOrBlank()) append(" (${dua.grade})")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                        text = if (dua.category == "QURANIC") "دعاء قرآني" else "دعاء نبوي",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفضيل الدعاء",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { copyTextToClipboard(context, "دعاء", shareContent, onCopiedToast) }) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ الدعاء",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareText(context, shareContent) }) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة الدعاء",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = dua.text,
                style = buildArabicReadingTextStyle(readingFont, readingFontScale, 21f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (!dua.source.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(dua.source, useArabicIndic)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!dua.grade.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.grade_label)} ${NumeralFormatter.format(dua.grade, useArabicIndic)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FridaySunnahCard(
    sunnah: FridaySunnahItem,
    isFavorite: Boolean,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    onToggleFavorite: () -> Unit,
    onCopiedToast: () -> Unit
) {
    val context = LocalContext.current
    val shareContent = "${sunnah.title}\n${sunnah.evidence}\nالمصدر: ${sunnah.source.orEmpty()} (${sunnah.grade.orEmpty()})"

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = sunnah.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفضيل السنة",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { copyTextToClipboard(context, sunnah.title, shareContent, onCopiedToast) }) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ السنة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareText(context, shareContent) }) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = "مشاركة السنة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = sunnah.evidence,
                style = buildArabicReadingTextStyle(readingFont, readingFontScale, 20f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (!sunnah.source.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(sunnah.source, useArabicIndic)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!sunnah.grade.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.grade_label)} ${NumeralFormatter.format(sunnah.grade, useArabicIndic)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
