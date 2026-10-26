package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.WirdiApplication
import com.example.domain.model.PrayerType
import com.example.domain.model.UserSettings
import com.example.domain.util.NumeralFormatter
import com.example.domain.util.PrayerTimeEngine
import com.example.ui.widget.WirdiAppWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object WirdiNotificationChannels {
    const val CHANNEL_ATHAN = "wirdi_channel_athan"
    const val CHANNEL_ADHKAR = "wirdi_channel_adhkar"
    const val CHANNEL_REMINDERS = "wirdi_channel_reminders"
    const val CHANNEL_PERSISTENT_PRAYER = "wirdi_channel_persistent_prayer"

    const val NOTIFICATION_ID_PERSISTENT_PRAYER = 4001

    const val ACTION_TRIGGER_ALARM = "com.example.notifications.ACTION_TRIGGER_ALARM"
    const val ACTION_SNOOZE_REMINDER = "com.example.notifications.ACTION_SNOOZE_REMINDER"

    const val EXTRA_ALARM_TYPE = "extra_alarm_type"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_CHANNEL_ID = "extra_channel_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    const val EXTRA_OPEN_CURRENT_WIRD = "extra_open_current_wird"
}

class WirdiNotificationScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun createNotificationChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val athanChannel = NotificationChannel(
            WirdiNotificationChannels.CHANNEL_ATHAN,
            context.getString(R.string.channel_athan_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_athan_desc)
            setBypassDnd(false) // Explicitly respect Do Not Disturb
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
        }

        val adhkarChannel = NotificationChannel(
            WirdiNotificationChannels.CHANNEL_ADHKAR,
            context.getString(R.string.channel_adhkar_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_adhkar_desc)
            setBypassDnd(false)
            enableVibration(true)
        }

        val remindersChannel = NotificationChannel(
            WirdiNotificationChannels.CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            setBypassDnd(false)
        }

        val persistentChannel = NotificationChannel(
            WirdiNotificationChannels.CHANNEL_PERSISTENT_PRAYER,
            context.getString(R.string.channel_persistent_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_persistent_desc)
            setBypassDnd(false)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }

        nm.createNotificationChannels(
            listOf(athanChannel, adhkarChannel, remindersChannel, persistentChannel)
        )
    }

    /**
     * Checks whether exact alarms are currently granted by the OS.
     * On Android 12+ (minSdk 31), calls AlarmManager.canScheduleExactAlarms().
     */
    fun canScheduleExactAlarms(): Boolean {
        val am = alarmManager ?: return false
        return try {
            am.canScheduleExactAlarms()
        } catch (e: Exception) {
            false
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Updates or cancels the optional persistent silent notification showing the next prayer and remaining time.
     * Disabled by default (`persistentPrayerNotificationEnabled = false`).
     */
    fun updateOrCancelPersistentNotification(
        settings: UserSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        val nmCompat = NotificationManagerCompat.from(context)
        if (!settings.persistentPrayerNotificationEnabled) {
            runCatching {
                nmCompat.cancel(WirdiNotificationChannels.NOTIFICATION_ID_PERSISTENT_PRAYER)
            }
            return
        }
        if (!hasNotificationPermission()) return

        createNotificationChannels()
        val nextPrayer = PrayerTimeEngine.getNextPrayer(now, settings)
        val formattedTime = PrayerTimeEngine.formatTime(nextPrayer.prayerTime, settings.useArabicIndicNumerals)
        val totalMinutes = (nextPrayer.remainingSeconds / 60).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val remainingStr = if (hours > 0) {
            val hStr = NumeralFormatter.format(hours.toInt(), settings.useArabicIndicNumerals)
            val mStr = NumeralFormatter.format(mins.toInt(), settings.useArabicIndicNumerals)
            "متبقي $hStr س و $mStr د"
        } else {
            val mStr = NumeralFormatter.format(mins.toInt(), settings.useArabicIndicNumerals)
            "متبقي $mStr دقيقة"
        }

        val title = "الصلاة القادمة: ${nextPrayer.prayerType.nameAr} ($formattedTime)"
        val body = "${settings.cityNameAr} • $remainingStr"

        val openHomeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openHomePendingIntent = PendingIntent.getActivity(
            context,
            4010,
            openHomeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openWirdIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WirdiNotificationChannels.EXTRA_OPEN_CURRENT_WIRD, true)
        }
        val openWirdPendingIntent = PendingIntent.getActivity(
            context,
            4011,
            openWirdIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(
            context,
            WirdiNotificationChannels.CHANNEL_PERSISTENT_PRAYER
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openHomePendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, context.getString(R.string.widget_open_wird_btn), openWirdPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            nmCompat.notify(WirdiNotificationChannels.NOTIFICATION_ID_PERSISTENT_PRAYER, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    /**
     * Schedules all upcoming prayer alarms, Adhkar reminders (morning/evening/sleep),
     * Friday reminder, and updates the optional persistent notification based on current UserSettings.
     * Uses exact alarm if granted, or falls back gracefully to setWindow / setAndAllowWhileIdle
     * so it NEVER crashes when permission is denied.
     */
    fun scheduleAllAlarms(settings: UserSettings, now: ZonedDateTime = ZonedDateTime.now()) {
        createNotificationChannels()
        updateOrCancelPersistentNotification(settings, now)

        val zoneId = PrayerTimeEngine.resolveZoneId(settings.timeZoneId)
        val zonedNow = now.withZoneSameInstant(zoneId)
        val today = zonedNow.toLocalDate()
        val tomorrow = today.plusDays(1)

        // 1. Schedule Prayer notifications for today and tomorrow
        val schedules = listOf(
            PrayerTimeEngine.calculateForSettings(today, settings),
            PrayerTimeEngine.calculateForSettings(tomorrow, settings)
        )

        var prayerReqCode = 1000
        for (daySchedule in schedules) {
            for ((prayerType, prayerTime) in daySchedule.allOrdered()) {
                val config = settings.prayerNotifications[prayerType]
                if (config != null && config.enabled) {
                    val triggerTime = prayerTime.minusMinutes(config.minutesBefore.toLong())
                    if (triggerTime.isAfter(zonedNow)) {
                        val title = "حان وقت صلاة ${prayerType.nameAr}"
                        val body = if (config.minutesBefore > 0) {
                            "متبقي ${config.minutesBefore} دقائق على أذان ${prayerType.nameAr} (${settings.cityNameAr})"
                        } else {
                            "حي على الصلاة • حي على الفلاح (${settings.cityNameAr})"
                        }
                        scheduleSingleAlarm(
                            requestCode = prayerReqCode,
                            triggerAtMillis = triggerTime.toInstant().toEpochMilli(),
                            alarmType = "PRAYER_${prayerType.name}",
                            title = title,
                            body = body,
                            channelId = WirdiNotificationChannels.CHANNEL_ATHAN,
                            notificationId = prayerReqCode,
                            snoozeMinutes = settings.snoozeMinutes
                        )
                    } else {
                        cancelAlarm(prayerReqCode)
                    }
                } else {
                    cancelAlarm(prayerReqCode)
                }
                prayerReqCode++
            }
        }

        // 2. Schedule Daily Adhkar Reminders (Morning, Evening, Sleep)
        if (settings.morningReminderEnabled) {
            val trigger = nextDailyOccurrence(zonedNow, settings.morningReminderHour, settings.morningReminderMinute)
            scheduleSingleAlarm(
                requestCode = 2001,
                triggerAtMillis = trigger.toInstant().toEpochMilli(),
                alarmType = "ADHKAR_MORNING",
                title = context.getString(R.string.cat_morning),
                body = "ابدأ يومك بذكر الله وطمأنينة القلب ﴿ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ ﴾",
                channelId = WirdiNotificationChannels.CHANNEL_ADHKAR,
                notificationId = 2001,
                snoozeMinutes = settings.snoozeMinutes
            )
        } else {
            cancelAlarm(2001)
        }

        if (settings.eveningReminderEnabled) {
            val trigger = nextDailyOccurrence(zonedNow, settings.eveningReminderHour, settings.eveningReminderMinute)
            scheduleSingleAlarm(
                requestCode = 2002,
                triggerAtMillis = trigger.toInstant().toEpochMilli(),
                alarmType = "ADHKAR_EVENING",
                title = context.getString(R.string.cat_evening),
                body = "حان وقت أذكار المساء، حصّن نفسك وأهلك بذكر الله.",
                channelId = WirdiNotificationChannels.CHANNEL_ADHKAR,
                notificationId = 2002,
                snoozeMinutes = settings.snoozeMinutes
            )
        } else {
            cancelAlarm(2002)
        }

        if (settings.sleepReminderEnabled) {
            val trigger = nextDailyOccurrence(zonedNow, settings.sleepReminderHour, settings.sleepReminderMinute)
            scheduleSingleAlarm(
                requestCode = 2003,
                triggerAtMillis = trigger.toInstant().toEpochMilli(),
                alarmType = "ADHKAR_SLEEP",
                title = context.getString(R.string.cat_sleep),
                body = "اختم يومك بأذكار النوم وآية الكرسي وخواتيم البقرة.",
                channelId = WirdiNotificationChannels.CHANNEL_ADHKAR,
                notificationId = 2003,
                snoozeMinutes = settings.snoozeMinutes
            )
        } else {
            cancelAlarm(2003)
        }

        // 3. Schedule Weekly Friday Reminder (Surah Al-Kahf & Salawat)
        if (settings.fridayReminderEnabled) {
            val trigger = nextFridayOccurrence(zonedNow, settings.fridayReminderHour, settings.fridayReminderMinute)
            scheduleSingleAlarm(
                requestCode = 3001,
                triggerAtMillis = trigger.toInstant().toEpochMilli(),
                alarmType = "FRIDAY_SUNAN",
                title = "سنن يوم الجمعة المبارك",
                body = "لا تنسَ قراءة سورة الكهف والإكثار من الصلاة والسلام على نبينا محمد ﷺ.",
                channelId = WirdiNotificationChannels.CHANNEL_REMINDERS,
                notificationId = 3001,
                snoozeMinutes = settings.snoozeMinutes
            )
        } else {
            cancelAlarm(3001)
        }
    }

    fun scheduleSnoozeAlarm(
        title: String,
        body: String,
        channelId: String,
        notificationId: Int,
        snoozeMinutes: Int
    ) {
        val triggerMillis = System.currentTimeMillis() + (snoozeMinutes.coerceAtLeast(1) * 60_000L)
        scheduleSingleAlarm(
            requestCode = notificationId + 5000,
            triggerAtMillis = triggerMillis,
            alarmType = "SNOOZE_$notificationId",
            title = title,
            body = body,
            channelId = channelId,
            notificationId = notificationId,
            snoozeMinutes = snoozeMinutes
        )
    }

    private fun scheduleSingleAlarm(
        requestCode: Int,
        triggerAtMillis: Long,
        alarmType: String,
        title: String,
        body: String,
        channelId: String,
        notificationId: Int,
        snoozeMinutes: Int
    ) {
        val am = alarmManager ?: return
        val intent = Intent(context, WirdiAlarmReceiver::class.java).apply {
            action = WirdiNotificationChannels.ACTION_TRIGGER_ALARM
            putExtra(WirdiNotificationChannels.EXTRA_ALARM_TYPE, alarmType)
            putExtra(WirdiNotificationChannels.EXTRA_TITLE, title)
            putExtra(WirdiNotificationChannels.EXTRA_BODY, body)
            putExtra(WirdiNotificationChannels.EXTRA_CHANNEL_ID, channelId)
            putExtra(WirdiNotificationChannels.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(WirdiNotificationChannels.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                // Safe fallback when SCHEDULE_EXACT_ALARM is denied by user on Android 14+
                am.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    10 * 60 * 1000L, // 10-minute window as per Android 14 official docs
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            // Ultimate safety net: never crash on any OEM security exception
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val am = alarmManager ?: return
        val intent = Intent(context, WirdiAlarmReceiver::class.java).apply {
            action = WirdiNotificationChannels.ACTION_TRIGGER_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
        }
    }

    fun nextDailyOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val candidate = now.with(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59), 0))
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    fun nextFridayOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        var candidate = now.with(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59), 0))
        while (candidate.dayOfWeek != DayOfWeek.FRIDAY || !candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun enqueueDailyPreventiveWork() {
        runCatching {
            val workRequest = PeriodicWorkRequestBuilder<DailyRescheduleWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "wirdi_daily_preventive_reschedule",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}

class WirdiAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? WirdiApplication
        val scheduler = app?.container?.notificationScheduler ?: WirdiNotificationScheduler(context)

        when (intent.action) {
            WirdiNotificationChannels.ACTION_SNOOZE_REMINDER -> {
                val notificationId = intent.getIntExtra(WirdiNotificationChannels.EXTRA_NOTIFICATION_ID, 2001)
                val title = intent.getStringExtra(WirdiNotificationChannels.EXTRA_TITLE) ?: "تذكير وِرْدِي"
                val body = intent.getStringExtra(WirdiNotificationChannels.EXTRA_BODY) ?: ""
                val channelId = intent.getStringExtra(WirdiNotificationChannels.EXTRA_CHANNEL_ID)
                    ?: WirdiNotificationChannels.CHANNEL_ADHKAR
                val snoozeMins = intent.getIntExtra(WirdiNotificationChannels.EXTRA_SNOOZE_MINUTES, 15)

                NotificationManagerCompat.from(context).cancel(notificationId)
                scheduler.scheduleSnoozeAlarm(title, body, channelId, notificationId, snoozeMins)
            }

            WirdiNotificationChannels.ACTION_TRIGGER_ALARM -> {
                val alarmType = intent.getStringExtra(WirdiNotificationChannels.EXTRA_ALARM_TYPE) ?: ""
                val title = intent.getStringExtra(WirdiNotificationChannels.EXTRA_TITLE) ?: "وِرْدِي"
                val body = intent.getStringExtra(WirdiNotificationChannels.EXTRA_BODY) ?: ""
                val channelId = intent.getStringExtra(WirdiNotificationChannels.EXTRA_CHANNEL_ID)
                    ?: WirdiNotificationChannels.CHANNEL_ATHAN
                val notificationId = intent.getIntExtra(WirdiNotificationChannels.EXTRA_NOTIFICATION_ID, 1001)
                val snoozeMins = intent.getIntExtra(WirdiNotificationChannels.EXTRA_SNOOZE_MINUTES, 15)

                showNotification(context, scheduler, alarmType, title, body, channelId, notificationId, snoozeMins)

                // Refresh Glance Widget and reschedule next alarms in background
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val prefs = app?.container?.preferencesRepository
                        if (prefs != null) {
                            val state = prefs.stateFlow.first()
                            scheduler.scheduleAllAlarms(state.settings)
                        }
                        WirdiAppWidget.updateAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        scheduler: WirdiNotificationScheduler,
        alarmType: String,
        title: String,
        body: String,
        channelId: String,
        notificationId: Int,
        snoozeMinutes: Int
    ) {
        if (!scheduler.hasNotificationPermission()) return
        scheduler.createNotificationChannels()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (alarmType.isNotBlank()) {
                putExtra(WirdiNotificationChannels.EXTRA_ALARM_TYPE, alarmType)
            }
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, WirdiAlarmReceiver::class.java).apply {
            action = WirdiNotificationChannels.ACTION_SNOOZE_REMINDER
            putExtra(WirdiNotificationChannels.EXTRA_TITLE, title)
            putExtra(WirdiNotificationChannels.EXTRA_BODY, body)
            putExtra(WirdiNotificationChannels.EXTRA_CHANNEL_ID, channelId)
            putExtra(WirdiNotificationChannels.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(WirdiNotificationChannels.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 9000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeLabel = "${context.getString(R.string.action_snooze_later)} (${snoozeMinutes} د)"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setCategory(
                if (channelId == WirdiNotificationChannels.CHANNEL_ATHAN)
                    NotificationCompat.CATEGORY_REMINDER
                else
                    NotificationCompat.CATEGORY_EVENT
            )
            .addAction(0, snoozeLabel, snoozePendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }
}

class WirdiBootAndTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (intent.action !in validActions) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? WirdiApplication
                val prefs = app?.container?.preferencesRepository
                val scheduler = app?.container?.notificationScheduler ?: WirdiNotificationScheduler(context)
                if (prefs != null) {
                    val state = prefs.stateFlow.first()
                    scheduler.scheduleAllAlarms(state.settings)
                }
                WirdiAppWidget.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class DailyRescheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? WirdiApplication
            val prefs = app?.container?.preferencesRepository
            val scheduler = app?.container?.notificationScheduler ?: WirdiNotificationScheduler(applicationContext)
            if (prefs != null) {
                prefs.ensureDailyReset(LocalDate.now().toString())
                val state = prefs.stateFlow.first()
                scheduler.scheduleAllAlarms(state.settings)
            }
            WirdiAppWidget.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
