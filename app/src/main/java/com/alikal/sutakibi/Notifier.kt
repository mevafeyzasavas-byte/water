package com.alikal.sutakibi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CH = "su_hatirlatma"
    private const val ID = 1

    private fun actionIntent(c: Context, ml: Int): PendingIntent {
        val i = Intent(c, ActionReceiver::class.java).putExtra("ml", ml)
        return PendingIntent.getBroadcast(
            c, ml, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun show(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Su hatırlatma", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Su içme hatırlatmaları"
            ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        val total = Store.total(c)
        val goal = Store.goal(c)
        val left = (goal - total).coerceAtLeast(0)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle("Su içme zamanı")
            .setContentText("Bugün $total / $goal ml. Hedefe $left ml kaldı.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "+250 ml", actionIntent(c, 250))
            .addAction(0, "+500 ml", actionIntent(c, 500))
            .build()
        try {
            NotificationManagerCompat.from(c).notify(ID, n)
        } catch (e: SecurityException) {
            // bildirim izni verilmemiş
        }
    }

    fun cancel(c: Context) = NotificationManagerCompat.from(c).cancel(ID)
}
