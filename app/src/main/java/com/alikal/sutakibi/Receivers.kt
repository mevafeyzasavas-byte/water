package com.alikal.sutakibi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        val pr = goAsync()
        Thread {
            try {
                // Başka cihazdan eklenen su varsa önce onu al
                Sync.syncNow(app)
                val now = Calendar.getInstance()
                val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val inWindow = m >= Store.startHour(app) * 60 && m <= Store.endHour(app) * 60
                if (inWindow && !Store.reached(app)) Notifier.show(app) else Notifier.cancel(app)
            } finally {
                Scheduler.schedule(app)
                pr.finish()
            }
        }.start()
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ml = i.getIntExtra("ml", 0)
        if (ml <= 0) return
        val app = c.applicationContext
        Store.add(app, ml)
        Notifier.cancel(app)
        Scheduler.schedule(app)
        val total = Store.total(app)
        val goal = Store.goal(app)
        val msg = if (total >= goal) "Hedef tamamlandı: $total / $goal ml 🎉"
        else "+$ml ml eklendi · $total / $goal ml"
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        val pr = goAsync()
        Thread {
            try { Sync.flush(app) } finally { pr.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.schedule(c)
    }
}
