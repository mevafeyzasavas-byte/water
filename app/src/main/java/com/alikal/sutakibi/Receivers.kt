package com.alikal.sutakibi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val now = Calendar.getInstance()
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val inWindow = m >= Store.startHour(c) * 60 && m <= Store.endHour(c) * 60
        if (inWindow && !Store.reached(c)) Notifier.show(c)
        Scheduler.schedule(c)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ml = i.getIntExtra("ml", 0)
        if (ml <= 0) return
        Store.add(c, ml)
        Notifier.cancel(c)
        Scheduler.schedule(c)
        val total = Store.total(c)
        val goal = Store.goal(c)
        val msg = if (total >= goal) "Hedef tamamlandı: $total / $goal ml 🎉"
        else "+$ml ml eklendi · $total / $goal ml"
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.schedule(c)
    }
}
