package com.alikal.sutakibi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {

    /** Bir sonraki hatırlatma zamanı (ms). Hedef tamamsa yarın sabaha kurulur. */
    fun nextTrigger(c: Context): Long {
        val interval = Store.interval(c).coerceAtLeast(15)
        val start = Store.startHour(c) * 60
        val end = Store.endHour(c) * 60
        val now = Calendar.getInstance()
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        fun at(min: Int, dayOffset: Int): Long {
            val cal = base.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            cal.add(Calendar.MINUTE, min)
            return cal.timeInMillis
        }

        if (Store.reached(c)) return at(start, 1)
        val minNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val next = (minNow / interval + 1) * interval
        return when {
            next < start -> at(start, 0)
            next > end -> at(start, 1)
            else -> at(next, 0)
        }
    }

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, 1, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val t = nextTrigger(c)
        try {
            when {
                Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                Build.VERSION.SDK_INT >= 23 ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                else -> am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }
}
