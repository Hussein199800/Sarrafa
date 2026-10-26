package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DarkModeConfig
import com.example.domain.model.DhikrCategory
import com.example.domain.model.DhikrItem
import com.example.domain.model.ReadingFontFamily
import com.example.domain.util.ArabicTextNormalizer
import com.example.domain.util.NumeralFormatter
import com.example.ui.WirdiUiState
import com.example.ui.components.WirdiCircularProgressRing
import com.example.ui.components.WirdiIcons
import com.example.ui.components.WirdiLargeTitleHeader
import com.example.ui.components.WirdiSegmentedControl
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.shareText
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.WirdiDesignTokens
import com.example.ui.theme.buildArabicReadingTextStyle
import com.example.ui.theme.getDhikrGateVisualSpec
import com.example.ui.theme.getPaletteColorScheme
import com.example.ui.theme.resolveIsDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun AdhkarScreen(
    uiState: WirdiUiState,
    isExpandedScreen: Boolean,
    isGatesMode: Boolean = false,
    onSwitchGatesMode: (Boolean) -> Unit = {},
    onSelectCategory: (DhikrCategory) -> Unit,
    onTapDhikr: (DhikrItem, (Boolean) -> Unit) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onResetCategory: (List<DhikrItem>) -> Unit,
    onSaveCustomDhikr: (String?, String, Int, String?) -> Unit,
    onDeleteCustomDhikr: (String) -> Unit,
    onOpenFocusSession: (DhikrCategory, Int) -> Unit = { _, _ -> },
    onSaveLastReadPosition: (DhikrCategory, Int) -> Unit = { _, _ -> },
    onUpdateSearchQuery: (String) -> Unit = {},
    onCopiedToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCategory = uiState.selectedDhikrCategory
    val settings = uiState.persisted.settings
    val favoriteIds = uiState.persisted.favoriteIds
    val counts = uiState.persisted.todayDhikrCounts
    val searchQuery = uiState.searchQuery

    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = resolveIsDarkTheme(
        palette = settings.themePalette,
        darkModeConfig = settings.darkModeConfig,
        systemDark = systemDark
    )

    val visibleCategories = remember(isGatesMode) {
        if (isGatesMode) {
            DhikrCategory.standaloneGateCategories
        } else {
            DhikrCategory.dailyAndOccasionCategories
        }
    }

    val displayedItems: List<DhikrItem> = remember(
        selectedCategory,
        uiState.allAdhkar,
        uiState.persisted.customAdhkar,
        favoriteIds,
        searchQuery
    ) {
        val rawList = when (selectedCategory) {
            DhikrCategory.FAVORITES -> {
                (uiState.allAdhkar + uiState.persisted.customAdhkar).filter { favoriteIds.contains(it.id) }
            }
            DhikrCategory.CUSTOM -> uiState.persisted.customAdhkar
            else -> uiState.allAdhkar.filter { it.category == selectedCategory.name }
        }
        if (searchQuery.isBlank()) {
            rawList
        } else {
            rawList.filter {
                ArabicTextNormalizer.matchesQuery("${it.text} ${it.virtue.orEmpty()} ${it.source.orEmpty()}", searchQuery)
            }
        }
    }

    val categoryProgressMap: Map<DhikrCategory, Float> = remember(
        uiState.allAdhkar,
        uiState.persisted.customAdhkar,
        favoriteIds,
        counts
    ) {
        DhikrCategory.entries.associateWith { cat ->
            val catItems = when (cat) {
                DhikrCategory.FAVORITES ->
                    (uiState.allAdhkar + uiState.persisted.customAdhkar).filter { favoriteIds.contains(it.id) }
                DhikrCategory.CUSTOM -> uiState.persisted.customAdhkar
                else -> uiState.allAdhkar.filter { it.category == cat.name }
            }
            if (catItems.isEmpty()) {
                0f
            } else {
                val done = catItems.count { (counts[it.id] ?: 0) >= it.repeat }
                done.toFloat() / catItems.size.toFloat()
            }
        }
    }

    val savedIndexForSelected = uiState.persisted.lastReadDhikrIndexByCategory[selectedCategory.name] ?: 0

    var showCustomDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DhikrItem?>(null) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }

    val segmentLabels = listOf(
        stringResource(R.string.adhkar_tab_daily),
        stringResource(R.string.adhkar_tab_gates)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedCategory == DhikrCategory.CUSTOM) {
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        showCustomDialog = true
                    },
                    shape = RoundedCornerShape(50),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_custom_dhikr_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.add_custom_dhikr))
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isExpandedScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .width(310.dp)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            WirdiLargeTitleHeader(
                                title = if (isGatesMode) {
                                    stringResource(R.string.nav_dhikr_gates)
                                } else {
                                    stringResource(R.string.nav_adhkar)
                                },
                                subtitle = selectedCategory.subtitleAr
                            )
                        }
                        item {
                            WirdiSegmentedControl(
                                items = segmentLabels,
                                selectedItem = if (isGatesMode) segmentLabels[1] else segmentLabels[0],
                                labelProvider = { it },
                                onSelect = { selected -> onSwitchGatesMode(selected == segmentLabels[1]) }
                            )
                        }
                        itemsIndexed(visibleCategories) { _, cat ->
                            val isSelected = cat == selectedCategory
                            val progress = categoryProgressMap[cat] ?: 0f
                            CategoryProgressCard(
                                category = cat,
                                isSelected = isSelected,
                                progressFraction = progress,
                                isDarkTheme = isDarkTheme,
                                useArabicIndic = settings.useArabicIndicNumerals,
                                testTag = "adhkar_cat_master_${cat.name}",
                                onClick = { onSelectCategory(cat) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AdhkarSinglePageReaderContent(
                        selectedCategory = selectedCategory,
                        displayedItems = displayedItems,
                        initialSavedIndex = savedIndexForSelected,
                        counts = counts,
                        favoriteIds = favoriteIds,
                        readingFont = settings.readingFont,
                        readingFontScale = settings.readingFontScale,
                        useArabicIndic = settings.useArabicIndicNumerals,
                        searchQuery = searchQuery,
                        showSearchBar = showSearchBar,
                        onToggleSearchBar = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) onUpdateSearchQuery("")
                        },
                        onSearchQueryChange = onUpdateSearchQuery,
                        onResetCategory = { onResetCategory(displayedItems) },
                        onTapDhikr = onTapDhikr,
                        onToggleFavorite = onToggleFavorite,
                        onOpenFocusSession = { currentPage ->
                            onOpenFocusSession(selectedCategory, currentPage)
                        },
                        onSaveLastReadPosition = { page ->
                            onSaveLastReadPosition(selectedCategory, page)
                        },
                        onCopiedToast = onCopiedToast,
                        onEditCustom = { item ->
                            editingItem = item
                            showCustomDialog = true
                        },
                        onDeleteCustom = onDeleteCustomDhikr
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 84.dp)
            ) {
                Box(modifier = Modifier.padding(horizontal = WirdiDesignTokens.screenHorizontalPadding)) {
                    WirdiLargeTitleHeader(
                        title = if (isGatesMode) {
                            stringResource(R.string.nav_dhikr_gates)
                        } else {
                            stringResource(R.string.nav_adhkar)
                        },
                        subtitle = selectedCategory.subtitleAr,
                        trailingAction = {
                            val lastCatName = uiState.persisted.lastReadDhikrCategory
                            val lastCat = remember(lastCatName) {
                                lastCatName?.let { name -> DhikrCategory.entries.find { it.name == name } }
                            }
                            if (lastCat != null && lastCat != selectedCategory) {
                                Surface(
                                    onClick = { onSelectCategory(lastCat) },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.testTag("adhkar_last_read_chip")
                                ) {
                                    Text(
                                        text = "آخر قراءة: ${lastCat.titleAr}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    )
                }

                Box(modifier = Modifier.padding(horizontal = WirdiDesignTokens.screenHorizontalPadding, vertical = 4.dp)) {
                    WirdiSegmentedControl(
                        items = segmentLabels,
                        selectedItem = if (isGatesMode) segmentLabels[1] else segmentLabels[0],
                        labelProvider = { it },
                        onSelect = { selected -> onSwitchGatesMode(selected == segmentLabels[1]) }
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = WirdiDesignTokens.screenHorizontalPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(visibleCategories) { _, cat ->
                        val isSelected = cat == selectedCategory
                        val progress = categoryProgressMap[cat] ?: 0f
                        CategoryProgressCard(
                            category = cat,
                            isSelected = isSelected,
                            progressFraction = progress,
                            isDarkTheme = isDarkTheme,
                            useArabicIndic = settings.useArabicIndicNumerals,
                            testTag = "adhkar_cat_chip_${cat.name}",
                            onClick = { onSelectCategory(cat) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                AdhkarSinglePageReaderContent(
                    selectedCategory = selectedCategory,
                    displayedItems = displayedItems,
                    initialSavedIndex = savedIndexForSelected,
                    counts = counts,
                    favoriteIds = favoriteIds,
                    readingFont = settings.readingFont,
                    readingFontScale = settings.readingFontScale,
                    useArabicIndic = settings.useArabicIndicNumerals,
                    searchQuery = searchQuery,
                    showSearchBar = showSearchBar,
                    onToggleSearchBar = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) onUpdateSearchQuery("")
                    },
                    onSearchQueryChange = onUpdateSearchQuery,
                    onResetCategory = { onResetCategory(displayedItems) },
                    onTapDhikr = onTapDhikr,
                    onToggleFavorite = onToggleFavorite,
                    onOpenFocusSession = { currentPage ->
                        onOpenFocusSession(selectedCategory, currentPage)
                    },
                    onSaveLastReadPosition = { page ->
                        onSaveLastReadPosition(selectedCategory, page)
                    },
                    onCopiedToast = onCopiedToast,
                    onEditCustom = { item ->
                        editingItem = item
                        showCustomDialog = true
                    },
                    onDeleteCustom = onDeleteCustomDhikr,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = WirdiDesignTokens.screenHorizontalPadding)
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomDhikrDialog(
            initialItem = editingItem,
            onDismiss = { showCustomDialog = false },
            onSave = { id, text, repeat, note ->
                onSaveCustomDhikr(id, text, repeat, note)
                showCustomDialog = false
            }
        )
    }
}

@Composable
private fun CategoryProgressCard(
    category: DhikrCategory,
    isSelected: Boolean,
    progressFraction: Float,
    isDarkTheme: Boolean,
    useArabicIndic: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current

    if (category.isStandaloneGate) {
        val gateVisual = getDhikrGateVisualSpec(category, isDarkTheme)
        val borderColor = if (isSelected) {
            gateVisual.accentColor
        } else {
            gateVisual.accentColor.copy(alpha = 0.28f)
        }
        val borderWidth = if (isSelected) 1.2.dp else WirdiDesignTokens.hairlineBorderWidth

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
            color = Color.Transparent,
            border = BorderStroke(borderWidth, borderColor),
            shadowElevation = 0.dp,
            modifier = modifier.testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(gateVisual.containerStart, gateVisual.containerEnd)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = gateVisual.badgeBgColor,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = WirdiIcons.gateIconFor(category),
                            contentDescription = null,
                            tint = gateVisual.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = category.titleAr,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = gateVisual.titleColor
                    )
                    Text(
                        text = category.subtitleAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = gateVisual.subtitleColor
                    )
                }
            }
        }
    } else {
        val bgColor by animateColorAsState(
            targetValue = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            animationSpec = if (reduceMotion) snap() else tween(180, easing = FastOutSlowInEasing),
            label = "catCardBg"
        )
        val borderColor = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = bgColor,
            border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, borderColor),
            shadowElevation = 0.dp,
            modifier = modifier.testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WirdiCircularProgressRing(
                    progress = progressFraction,
                    size = 26.dp,
                    strokeWidth = 2.5.dp,
                    progressColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ) {
                    if (progressFraction >= 1f) {
                        Icon(
                            imageVector = WirdiIcons.CheckMark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        val pct = NumeralFormatter.format((progressFraction * 100).toInt(), useArabicIndic)
                        Text(
                            text = pct,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = category.titleAr,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdhkarSinglePageReaderContent(
    selectedCategory: DhikrCategory,
    displayedItems: List<DhikrItem>,
    initialSavedIndex: Int,
    counts: Map<String, Int>,
    favoriteIds: Set<String>,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    searchQuery: String,
    showSearchBar: Boolean,
    onToggleSearchBar: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onResetCategory: () -> Unit,
    onTapDhikr: (DhikrItem, (Boolean) -> Unit) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenFocusSession: (Int) -> Unit,
    onSaveLastReadPosition: (Int) -> Unit,
    onCopiedToast: () -> Unit,
    onEditCustom: (DhikrItem) -> Unit,
    onDeleteCustom: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val completedCount = displayedItems.count { (counts[it.id] ?: 0) >= it.repeat }
            val completedStr = NumeralFormatter.format(completedCount, useArabicIndic)
            val totalStr = NumeralFormatter.format(displayedItems.size, useArabicIndic)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = selectedCategory.titleAr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (displayedItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$completedStr / $totalStr",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (displayedItems.isNotEmpty()) {
                    Surface(
                        onClick = { onOpenFocusSession(initialSavedIndex.coerceIn(0, (displayedItems.size - 1).coerceAtLeast(0))) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("open_focus_session_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = WirdiIcons.FocusSession,
                                contentDescription = stringResource(R.string.session_start_button),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.session_start_button),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onToggleSearchBar,
                    modifier = Modifier.testTag("adhkar_toggle_search_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث في الأذكار",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (displayedItems.isNotEmpty()) {
                    IconButton(
                        onClick = onResetCategory,
                        modifier = Modifier.testTag("reset_category_counts_btn")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.RefreshArc,
                            contentDescription = stringResource(R.string.reset_category_progress),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("adhkar_search_input")
            )
        }

        if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyMsg = when {
                    searchQuery.isNotBlank() -> "لا توجد نتائج مطابقة للبحث"
                    selectedCategory == DhikrCategory.FAVORITES -> stringResource(R.string.empty_favorites)
                    else -> stringResource(R.string.empty_custom_adhkar)
                }
                Text(
                    text = emptyMsg,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val safeStartPage = initialSavedIndex.coerceIn(0, displayedItems.lastIndex)
            val pagerState = rememberPagerState(
                initialPage = safeStartPage,
                pageCount = { displayedItems.size }
            )

            LaunchedEffect(selectedCategory) {
                val target = initialSavedIndex.coerceIn(0, (displayedItems.size - 1).coerceAtLeast(0))
                if (pagerState.currentPage != target) {
                    pagerState.scrollToPage(target)
                }
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        onSaveLastReadPosition(page)
                    }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val maxDots = displayedItems.size.coerceAtMost(18)
                for (i in 0 until maxDots) {
                    val item = displayedItems[i]
                    val isDone = (counts[item.id] ?: 0) >= item.repeat
                    val isCurrent = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (isCurrent) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(i) }
                            }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp
            ) { pageIndex ->
                val item = displayedItems[pageIndex]
                val currentCount = counts[item.id] ?: 0
                val isFav = favoriteIds.contains(item.id)

                SinglePageDhikrCard(
                    dhikr = item,
                    pageIndex = pageIndex,
                    totalPages = displayedItems.size,
                    currentCount = currentCount,
                    isFavorite = isFav,
                    readingFont = readingFont,
                    readingFontScale = readingFontScale,
                    useArabicIndic = useArabicIndic,
                    onTapCount = {
                        onTapDhikr(item) { shouldAutoAdvance ->
                            if (shouldAutoAdvance && pageIndex + 1 < displayedItems.size) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pageIndex + 1)
                                }
                            }
                        }
                    },
                    onToggleFavorite = { onToggleFavorite(item.id) },
                    onCopiedToast = onCopiedToast,
                    onEditCustom = if (item.isCustom) ({ onEditCustom(item) }) else null,
                    onDeleteCustom = if (item.isCustom) ({ onDeleteCustom(item.id) }) else null
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SinglePageDhikrCard(
    dhikr: DhikrItem,
    pageIndex: Int,
    totalPages: Int,
    currentCount: Int,
    isFavorite: Boolean,
    readingFont: ReadingFontFamily,
    readingFontScale: Float,
    useArabicIndic: Boolean,
    onTapCount: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopiedToast: () -> Unit,
    onEditCustom: (() -> Unit)?,
    onDeleteCustom: (() -> Unit)?
) {
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val targetRepeat = dhikr.repeat.coerceAtLeast(1)
    val isCompleted = currentCount >= targetRepeat
    val progress = (currentCount.toFloat() / targetRepeat.toFloat()).coerceIn(0f, 1f)

    val dialScale by animateFloatAsState(
        targetValue = if (isCompleted) 0.98f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(180, easing = FastOutSlowInEasing),
        label = "dhikrDialScale"
    )

    val countFormatted = NumeralFormatter.format(currentCount, useArabicIndic)
    val targetFormatted = NumeralFormatter.format(targetRepeat, useArabicIndic)
    val pageFormatted = "${NumeralFormatter.format(pageIndex + 1, useArabicIndic)} / ${NumeralFormatter.format(totalPages, useArabicIndic)}"

    val shareContent = buildString {
        append(dhikr.text)
        if (!dhikr.source.isNullOrBlank()) {
            append("\n\nالمصدر: ").append(dhikr.source)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onTapCount,
                onLongClick = {
                    copyTextToClipboard(context, "ذكر من تطبيق وردي", shareContent, onCopiedToast)
                }
            )
            .semantics {
                contentDescription = "الذكر: ${dhikr.text}. العدد الحالي $countFormatted من $targetFormatted"
            }
            .testTag("dhikr_card_${dhikr.id}"),
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isCompleted) 1.dp else WirdiDesignTokens.hairlineBorderWidth,
            color = if (isCompleted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = pageFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("fav_btn_${dhikr.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            copyTextToClipboard(context, "ذكر من تطبيق وردي", shareContent, onCopiedToast)
                        },
                        modifier = Modifier.testTag("copy_btn_${dhikr.id}")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Copy,
                            contentDescription = "نسخ الذكر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { shareText(context, shareContent) },
                        modifier = Modifier.testTag("share_btn_${dhikr.id}")
                    ) {
                        Icon(
                            imageVector = WirdiIcons.Share,
                            contentDescription = stringResource(R.string.share_via),
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dhikr.text,
                    style = buildArabicReadingTextStyle(
                        readingFont = readingFont,
                        scale = readingFontScale,
                        baseFontSizeSp = 22f
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!dhikr.virtue.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
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

                if (!dhikr.source.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(dhikr.source, useArabicIndic)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!dhikr.grade.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = dhikr.grade,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onTapCount,
                    shape = CircleShape,
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier
                        .size(122.dp)
                        .scale(dialScale)
                        .testTag("dhikr_bottom_counter_${dhikr.id}")
                ) {
                    WirdiCircularProgressRing(
                        progress = progress,
                        size = 122.dp,
                        strokeWidth = 6.dp,
                        progressColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = WirdiIcons.CheckMark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = "$countFormatted / $targetFormatted",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = if (isCompleted) stringResource(R.string.status_completed) else "اضغط للعدّ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stage 3: Focused «جلسة الذكر» (Dhikr Session) Overlay.
 * Provides a calm, distraction-free dark environment with UI dimming, compact counter mode,
 * anti-rapid-double-tap protection, pause/resume, keep-screen-on, and position persistence.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DhikrSessionOverlay(
    uiState: WirdiUiState,
    onTapDhikr: (DhikrItem, (Boolean) -> Unit) -> Unit,
    onSavePosition: (DhikrCategory, Int) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleDimUi: (Boolean) -> Unit,
    onToggleCompactCounter: (Boolean) -> Unit,
    onCloseSession: () -> Unit,
    onCopiedToast: () -> Unit
) {
    BackHandler {
        onCloseSession()
    }

    val category = uiState.sessionCategory
    val settings = uiState.persisted.settings
    val useArabicIndic = settings.useArabicIndicNumerals
    val counts = uiState.persisted.todayDhikrCounts
    val favoriteIds = uiState.persisted.favoriteIds

    val sessionItems: List<DhikrItem> = remember(
        category,
        uiState.allAdhkar,
        uiState.persisted.customAdhkar,
        favoriteIds
    ) {
        when (category) {
            DhikrCategory.FAVORITES ->
                (uiState.allAdhkar + uiState.persisted.customAdhkar).filter { favoriteIds.contains(it.id) }
            DhikrCategory.CUSTOM -> uiState.persisted.customAdhkar
            else -> uiState.allAdhkar.filter { it.category == category.name }
        }
    }

    var isPaused by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val completedItemsCount = sessionItems.count { (counts[it.id] ?: 0) >= it.repeat }
    val overallProgress = if (sessionItems.isNotEmpty()) {
        completedItemsCount.toFloat() / sessionItems.size.toFloat()
    } else 0f
    val allSessionCompleted = sessionItems.isNotEmpty() && completedItemsCount >= sessionItems.size

    val nonEssentialAlpha = if (settings.sessionDimUi) 0.28f else 1f
    val sessionScheme = remember(settings.themePalette) {
        getPaletteColorScheme(settings.themePalette, darkTheme = true)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dhikr_session_overlay"),
        color = sessionScheme.background
    ) {
        if (sessionItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "لا توجد أذكار في هذا القسم حالياً",
                        color = sessionScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onCloseSession) {
                        Text(stringResource(R.string.session_exit_btn))
                    }
                }
            }
        } else {
            val initialPage = uiState.sessionInitialIndex.coerceIn(0, sessionItems.lastIndex)
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { sessionItems.size }
            )

            LaunchedEffect(category) {
                val target = uiState.sessionInitialIndex.coerceIn(0, sessionItems.lastIndex)
                if (pagerState.currentPage != target) {
                    pagerState.scrollToPage(target)
                }
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        onSavePosition(category, page)
                    }
            }

            val currentItem = sessionItems[pagerState.currentPage]
            val currentCount = counts[currentItem.id] ?: 0
            val targetRepeat = currentItem.repeat.coerceAtLeast(1)
            val isItemCompleted = currentCount >= targetRepeat
            val itemProgress = (currentCount.toFloat() / targetRepeat.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Session Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.alpha(nonEssentialAlpha)) {
                            Text(
                                text = stringResource(R.string.session_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = sessionScheme.primary
                            )
                            Text(
                                text = category.titleAr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = sessionScheme.onBackground
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onToggleDimUi(!settings.sessionDimUi) },
                                modifier = Modifier.testTag("session_dim_ui_btn")
                            ) {
                                Icon(
                                    imageVector = WirdiIcons.EyeDim,
                                    contentDescription = stringResource(R.string.session_dim_ui_toggle),
                                    tint = if (settings.sessionDimUi) sessionScheme.primary else sessionScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onToggleCompactCounter(!settings.sessionCompactCounter) },
                                modifier = Modifier.testTag("session_compact_counter_btn")
                            ) {
                                Icon(
                                    imageVector = WirdiIcons.SettingsSliders,
                                    contentDescription = stringResource(R.string.session_compact_counter_toggle),
                                    tint = if (settings.sessionCompactCounter) sessionScheme.primary else sessionScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onToggleKeepScreenOn(!settings.sessionKeepScreenOn) },
                                modifier = Modifier.testTag("session_keep_screen_on_btn")
                            ) {
                                Icon(
                                    imageVector = WirdiIcons.Clock,
                                    contentDescription = stringResource(R.string.session_keep_screen_on_toggle),
                                    tint = if (settings.sessionKeepScreenOn) sessionScheme.primary else sessionScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { isPaused = !isPaused },
                                modifier = Modifier.testTag("session_pause_resume_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else WirdiIcons.PauseCircle,
                                    contentDescription = if (isPaused) stringResource(R.string.session_resume_btn) else stringResource(R.string.session_pause_btn),
                                    tint = sessionScheme.onBackground
                                )
                            }
                            IconButton(
                                onClick = onCloseSession,
                                modifier = Modifier.testTag("session_exit_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.session_exit_btn),
                                    tint = sessionScheme.onBackground
                                )
                            }
                        }
                    }

                    // Overall Wird progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(nonEssentialAlpha),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50)),
                            color = sessionScheme.primary,
                            trackColor = sessionScheme.surfaceVariant
                        )
                        val doneStr = NumeralFormatter.format(completedItemsCount, useArabicIndic)
                        val totalStr = NumeralFormatter.format(sessionItems.size, useArabicIndic)
                        Text(
                            text = "$doneStr / $totalStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = sessionScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isPaused) {
                    Surface(
                        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                        color = sessionScheme.surface,
                        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, sessionScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("session_paused_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.session_paused_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = sessionScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { isPaused = false }) {
                                Text(stringResource(R.string.session_resume_btn), color = sessionScheme.primary)
                            }
                        }
                    }
                }

                if (allSessionCompleted) {
                    Surface(
                        shape = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
                        color = sessionScheme.primaryContainer,
                        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, sessionScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = WirdiIcons.CheckMark,
                                contentDescription = null,
                                tint = sessionScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.session_wird_completed_msg),
                                style = MaterialTheme.typography.bodySmall,
                                color = sessionScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Pager for session reading
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    pageSpacing = 14.dp
                ) { pageIdx ->
                    val dhikr = sessionItems[pageIdx]
                    Surface(
                        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
                        color = sessionScheme.surface,
                        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, sessionScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = !isPaused) {
                                onTapDhikr(dhikr) { shouldAdvance ->
                                    if (shouldAdvance && pageIdx + 1 < sessionItems.size) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIdx + 1)
                                        }
                                    }
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = dhikr.text,
                                style = buildArabicReadingTextStyle(
                                    readingFont = settings.readingFont,
                                    scale = settings.readingFontScale,
                                    baseFontSizeSp = 24f
                                ),
                                color = sessionScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!dhikr.virtue.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "${stringResource(R.string.virtue_label)} ${dhikr.virtue}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sessionScheme.onSurfaceVariant,
                                    modifier = Modifier.alpha(nonEssentialAlpha)
                                )
                            }

                            if (!dhikr.source.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${stringResource(R.string.source_label)} ${NumeralFormatter.format(dhikr.source, useArabicIndic)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sessionScheme.primary,
                                    modifier = Modifier.alpha(nonEssentialAlpha)
                                )
                            }
                        }
                    }
                }

                // Bottom Counter Area (either Compact Pill or Large Serene Dial)
                val countFormatted = NumeralFormatter.format(currentCount, useArabicIndic)
                val targetFormatted = NumeralFormatter.format(targetRepeat, useArabicIndic)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (settings.sessionCompactCounter) {
                        Surface(
                            onClick = {
                                if (!isPaused) {
                                    onTapDhikr(currentItem) { shouldAdvance ->
                                        if (shouldAdvance && pagerState.currentPage + 1 < sessionItems.size) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = sessionScheme.surfaceVariant,
                            border = BorderStroke(1.dp, sessionScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("session_tap_counter")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isItemCompleted) stringResource(R.string.status_completed) else "اضغط للعدّ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = sessionScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$countFormatted / $targetFormatted",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = sessionScheme.primary
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = {
                                if (!isPaused) {
                                    onTapDhikr(currentItem) { shouldAdvance ->
                                        if (shouldAdvance && pagerState.currentPage + 1 < sessionItems.size) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = sessionScheme.surface,
                            border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, sessionScheme.outlineVariant),
                            modifier = Modifier
                                .size(136.dp)
                                .testTag("session_tap_counter")
                        ) {
                            WirdiCircularProgressRing(
                                progress = itemProgress,
                                size = 136.dp,
                                strokeWidth = 6.dp,
                                progressColor = sessionScheme.primary,
                                trackColor = sessionScheme.surfaceVariant
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$countFormatted / $targetFormatted",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = sessionScheme.onSurface
                                    )
                                    Text(
                                        text = if (isItemCompleted) stringResource(R.string.status_completed) else "اضغط للعدّ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sessionScheme.onSurfaceVariant
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

@Composable
private fun CustomDhikrDialog(
    initialItem: DhikrItem?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Int, String?) -> Unit
) {
    var text by remember { mutableStateOf(initialItem?.text ?: "") }
    var repeatText by remember { mutableStateOf((initialItem?.repeat ?: 33).toString()) }
    var note by remember { mutableStateOf(initialItem?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
        title = {
            Text(
                text = if (initialItem == null) stringResource(R.string.add_custom_dhikr) else stringResource(R.string.edit_custom_dhikr),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.custom_dhikr_text_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dhikr_text_input"),
                    minLines = 3
                )
                OutlinedTextField(
                    value = repeatText,
                    onValueChange = { repeatText = it },
                    label = { Text(stringResource(R.string.custom_dhikr_repeat_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dhikr_repeat_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.custom_dhikr_note_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanRepeat = NumeralFormatter.toWestern(repeatText).toIntOrNull()?.coerceAtLeast(1) ?: 1
                    if (text.isNotBlank()) {
                        onSave(initialItem?.id, text, cleanRepeat, note)
                    }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("save_custom_dhikr_btn")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
