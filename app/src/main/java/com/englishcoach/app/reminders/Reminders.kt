package com.englishcoach.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.englishcoach.app.EnglishApp
import com.englishcoach.app.MainActivity
import com.englishcoach.app.R
import com.englishcoach.app.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ежедневное напоминание через AlarmManager (без лишних библиотек).
 * Неточный будильник с окном 10 минут — не требует разрешения на точные будильники и бережёт батарею.
 */
object Reminders {
    const val ACTION = "com.englishcoach.app.REMIND"
    private const val CHANNEL = "reminders"
    private const val REQUEST = 1001
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Напоминания о занятиях", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, s: Settings) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)
        if (!s.remindersOn) return
        val next = nextTrigger(LocalDateTime.now(), s.reminderHour, s.reminderMinute, s.reminderDays) ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
    }

    /** Ближайшее время напоминания в выбранный день недели, строго позже now. */
    fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int, days: Set<Int>): LocalDateTime? {
        for (i in 0L..7L) {
            val date = now.toLocalDate().plusDays(i)
            val t = date.atTime(hour, minute)
            if (t.isAfter(now) && date.dayOfWeek.value in days) return t
        }
        return null
    }

    fun show(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Время английского")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Срабатывает по будильнику и после перезагрузки телефона (будильники при ней сбрасываются). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as EnglishApp).container
        val pending = goAsync()
        c.appScope.launch {
            try {
                val s = c.settings.current()
                // Не напоминаем, если цель на сегодня уже выполнена
                if (intent.action == Reminders.ACTION && c.study.todayStudyMs() < s.sessionMinutes * 60_000L) {
                    val due = c.study.dueCountFlow(System.currentTimeMillis()).first()
                    Reminders.show(
                        context,
                        if (due > 0) "$due карточек ждут повторения — начнём?" else "Сессия дня ждёт: ${s.sessionMinutes} минут английского.",
                    )
                }
                Reminders.schedule(context, s)
            } finally {
                pending.finish()
            }
        }
    }
}
