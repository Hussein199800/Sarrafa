package com.example.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notifications.WirdiNotificationScheduler
import com.example.ui.WirdiTab
import com.example.ui.WirdiViewModel
import com.example.ui.components.WirdiIcons
import com.example.ui.screens.AdhkarScreen
import com.example.ui.screens.DhikrSessionOverlay
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.WirdiDesignTokens

private data class NavTabItem(
    val tab: WirdiTab,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun WirdiAppRoot(
    viewModel: WirdiViewModel,
    notificationScheduler: WirdiNotificationScheduler,
    widthSizeClass: WindowWidthSizeClass
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val isExpandedOrMedium = widthSizeClass != WindowWidthSizeClass.Compact

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val tabs = remember {
        listOf(
            NavTabItem(
                tab = WirdiTab.HOME,
                labelRes = R.string.nav_home,
                icon = WirdiIcons.HomeHorizon,
                testTag = "nav_tab_home"
            ),
            NavTabItem(
                tab = WirdiTab.ADHKAR,
                labelRes = R.string.nav_adhkar,
                icon = WirdiIcons.AdhkarLeaf,
                testTag = "nav_tab_adhkar"
            ),
            NavTabItem(
                tab = WirdiTab.DHIKR_GATES,
                labelRes = R.string.nav_dhikr_gates,
                icon = WirdiIcons.SparkleStar,
                testTag = "nav_tab_gates"
            ),
            NavTabItem(
                tab = WirdiTab.LIBRARY,
                labelRes = R.string.nav_library,
                icon = WirdiIcons.BookLibrary,
                testTag = "nav_tab_library"
            ),
            NavTabItem(
                tab = WirdiTab.MORE,
                labelRes = R.string.nav_more,
                icon = WirdiIcons.MoreCircles,
                testTag = "nav_tab_more"
            )
        )
    }

    val copiedMsg = stringResource(R.string.copied_to_clipboard)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedOrMedium) {
                    WirdiRoundedSideRail(
                        tabs = tabs,
                        selectedTab = uiState.selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (uiState.selectedTab) {
                        WirdiTab.HOME -> HomeScreen(
                            uiState = uiState,
                            isExpandedScreen = isExpandedOrMedium,
                            onOpenCategory = viewModel::selectDhikrCategory,
                            onOpenCurrentWird = { asSession ->
                                viewModel.openCurrentRecommendedWird(asFocusSession = asSession)
                            },
                            onOpenFridaySunan = {
                                viewModel.selectLibrarySection(com.example.ui.LibrarySection.FRIDAY_SUNAN)
                            },
                            onOpenMoreSubScreen = viewModel::openMoreSubScreen,
                            onToggleDarkMode = viewModel::toggleDarkModeQuick,
                            onSetCompactMode = viewModel::setHomeCompactMode,
                            onToggleSectionVisibility = viewModel::toggleHomeSectionVisibility,
                            onMoveSectionOrder = viewModel::moveHomeSectionOrder,
                            onTogglePinnedGate = viewModel::togglePinnedGateCategory,
                            onUpdatePrayerOffset = viewModel::updatePrayerOffset,
                            onUpdatePrayerNotification = viewModel::updatePrayerNotification,
                            onUpdateThemePalette = viewModel::updateThemePalette,
                            onSelectCity = viewModel::selectCity,
                            onUpdateCalcMethod = viewModel::updateCalcMethod,
                            onCompleteOnboarding = viewModel::completeOnboarding,
                            onTimeBoundaryReached = viewModel::onTimeBoundaryReached,
                            onCopiedMessage = { viewModel.postBannerMessage(copiedMsg) }
                        )

                        WirdiTab.ADHKAR, WirdiTab.DHIKR_GATES -> AdhkarScreen(
                            uiState = uiState,
                            isExpandedScreen = isExpandedOrMedium,
                            isGatesMode = uiState.selectedTab == WirdiTab.DHIKR_GATES || uiState.selectedDhikrCategory.isStandaloneGate,
                            onSwitchGatesMode = { gatesMode ->
                                viewModel.selectTab(if (gatesMode) WirdiTab.DHIKR_GATES else WirdiTab.ADHKAR)
                            },
                            onSelectCategory = viewModel::selectDhikrCategory,
                            onTapDhikr = viewModel::onDhikrCardTap,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onResetCategory = viewModel::resetCurrentCategoryCounts,
                            onSaveCustomDhikr = viewModel::saveCustomDhikr,
                            onDeleteCustomDhikr = viewModel::deleteCustomDhikr,
                            onOpenFocusSession = { cat, index ->
                                viewModel.openDhikrSession(cat, index)
                            },
                            onSaveLastReadPosition = viewModel::saveLastReadDhikrPosition,
                            onUpdateSearchQuery = viewModel::updateSearchQuery,
                            onCopiedToast = { viewModel.postBannerMessage(copiedMsg) }
                        )

                        WirdiTab.LIBRARY -> LibraryScreen(
                            uiState = uiState,
                            isExpandedScreen = isExpandedOrMedium,
                            onSelectSection = viewModel::selectLibrarySection,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onSaveLastReadItem = viewModel::saveLastReadLibraryItem,
                            onCopiedToast = { viewModel.postBannerMessage(copiedMsg) }
                        )

                        WirdiTab.MORE -> MoreScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            notificationScheduler = notificationScheduler,
                            isExpandedScreen = isExpandedOrMedium
                        )
                    }
                }
            }

            if (!isExpandedOrMedium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WirdiFloatingPillNavBar(
                        tabs = tabs,
                        selectedTab = uiState.selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }
            }

            if (uiState.isDhikrSessionActive) {
                DhikrSessionOverlay(
                    uiState = uiState,
                    onTapDhikr = viewModel::onDhikrCardTap,
                    onSavePosition = viewModel::saveLastReadDhikrPosition,
                    onToggleKeepScreenOn = viewModel::updateSessionKeepScreenOn,
                    onToggleDimUi = viewModel::updateSessionDimUi,
                    onToggleCompactCounter = viewModel::updateSessionCompactCounter,
                    onCloseSession = viewModel::closeDhikrSession,
                    onCopiedToast = { viewModel.postBannerMessage(copiedMsg) }
                )
            }
        }
    }
}

@Composable
private fun WirdiFloatingPillNavBar(
    tabs: List<NavTabItem>,
    selectedTab: WirdiTab,
    onSelectTab: (WirdiTab) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current

    Surface(
        shape = RoundedCornerShape(WirdiDesignTokens.pillCornerPercent),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compact_bottom_nav")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { item ->
                val selected = selectedTab == item.tab
                val pillBg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = if (reduceMotion) snap() else tween(180, easing = FastOutSlowInEasing),
                    label = "navPillBg"
                )
                val contentTint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = if (reduceMotion) snap() else tween(180, easing = FastOutSlowInEasing),
                    label = "navPillTint"
                )

                Surface(
                    onClick = { onSelectTab(item.tab) },
                    shape = RoundedCornerShape(WirdiDesignTokens.pillCornerPercent),
                    color = pillBg,
                    modifier = Modifier.testTag(item.testTag)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = contentTint,
                            modifier = Modifier.size(21.dp)
                        )
                        AnimatedVisibility(visible = selected) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentTint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WirdiRoundedSideRail(
    tabs: List<NavTabItem>,
    selectedTab: WirdiTab,
    onSelectTab: (WirdiTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(WirdiDesignTokens.heroCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(WirdiDesignTokens.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .width(96.dp)
            .fillMaxHeight()
            .testTag("expanded_nav_rail")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabs.forEach { item ->
                val selected = selectedTab == item.tab
                Surface(
                    onClick = { onSelectTab(item.tab) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(item.testTag)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
