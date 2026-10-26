package com.example.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.WirdiApplication
import com.example.data.AssetsContentRepository
import com.example.data.WirdiPreferencesRepository
import com.example.domain.model.WirdiThemePalette
import com.example.domain.util.HijriCalendarCalculator
import com.example.domain.util.NumeralFormatter
import com.example.domain.util.PrayerTimeEngine
import com.example.notifications.WirdiNotificationChannels
import com.example.ui.theme.getPaletteColorScheme
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

class WirdiAppWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_SIZE = DpSize(140.dp, 85.dp)
        private val EXPANDED_SIZE = DpSize(260.dp, 155.dp)

        suspend fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(WirdiAppWidget::class.java)
                val widget = WirdiAppWidget()
                glanceIds.forEach { id ->
                    widget.update(context, id)
                }
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? WirdiApplication
        val prefsRepo = app?.container?.preferencesRepository ?: WirdiPreferencesRepository(context)
        val assetsRepo = app?.container?.assetsRepository ?: AssetsContentRepository(context)

        val state = prefsRepo.stateFlow.first()
        val settings = state.settings
        val now = ZonedDateTime.now()
        val nextPrayer = PrayerTimeEngine.getNextPrayer(now, settings)
        val formattedTime = PrayerTimeEngine.formatTime(nextPrayer.prayerTime, settings.useArabicIndicNumerals)
        val totalMinutes = (nextPrayer.remainingSeconds / 60).coerceAtLeast(0)
        val remHours = totalMinutes / 60
        val remMins = totalMinutes % 60
        val remainingText = if (remHours > 0) {
            val hStr = NumeralFormatter.format(remHours.toInt(), settings.useArabicIndicNumerals)
            val mStr = NumeralFormatter.format(remMins.toInt(), settings.useArabicIndicNumerals)
            "متبقي $hStr س و $mStr د"
        } else {
            val mStr = NumeralFormatter.format(remMins.toInt(), settings.useArabicIndicNumerals)
            "متبقي $mStr د"
        }

        val hijri = HijriCalendarCalculator.formatHijriDate(
            LocalDate.now(),
            settings.hijriOffsetDays,
            settings.useArabicIndicNumerals
        )
        val dailyDhikr = assetsRepo.getDailyHighlightDhikr(LocalDate.now())

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    themePalette = settings.themePalette,
                    cityName = settings.cityNameAr,
                    nextPrayerName = nextPrayer.prayerType.nameAr,
                    nextPrayerTime = formattedTime,
                    remainingText = remainingText,
                    hijriDateStr = hijri.fullFormattedAr,
                    dailyDhikrText = dailyDhikr.text
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        themePalette: WirdiThemePalette,
        cityName: String,
        nextPrayerName: String,
        nextPrayerTime: String,
        remainingText: String,
        hijriDateStr: String,
        dailyDhikrText: String
    ) {
        val size = LocalSize.current
        val isExpanded = size.height >= 130.dp || size.width >= 240.dp

        val openHomeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openWirdIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WirdiNotificationChannels.EXTRA_OPEN_CURRENT_WIRD, true)
        }

        // Centralized Contemporary Serenity palette colors from Color.kt
        val scheme = getPaletteColorScheme(themePalette, darkTheme = !themePalette.prefersLightByDefault)
        val surfaceBg = ColorProvider(scheme.surface)
        val elevatedBg = ColorProvider(scheme.surfaceVariant)
        val textPrimary = ColorProvider(scheme.onSurface)
        val textSecondary = ColorProvider(scheme.onSurfaceVariant)
        val accentPrimary = ColorProvider(scheme.primary)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(22.dp)
                .background(surfaceBg)
                .clickable(actionStartActivity(openHomeIntent))
                .padding(14.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$cityName • $remainingText",
                        style = TextStyle(
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "وِرْدِي • $hijriDateStr",
                        style = TextStyle(
                            color = accentPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nextPrayerTime,
                        style = TextStyle(
                            color = accentPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "الصلاة القادمة: $nextPrayerName",
                        style = TextStyle(
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = dailyDhikrText.replace("\n", " "),
                        style = TextStyle(
                            color = textSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 2
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(elevatedBg)
                            .clickable(actionStartActivity(openWirdIntent))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "وِرد اليوم",
                            style = TextStyle(
                                color = accentPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(elevatedBg)
                            .clickable(actionStartActivity(openHomeIntent))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "الرئيسية",
                            style = TextStyle(
                                color = textPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

class WirdiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WirdiAppWidget()
}
