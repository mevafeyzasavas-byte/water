package com.alikal.sutakibi

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Entry(val id: String, val time: String, val ml: Int)

object Store {
    const val DEFAULT_DB = "https://takvim-82a3b-default-rtdb.europe-west1.firebasedatabase.app"

    private fun p(c: Context) = c.getSharedPreferences("su", Context.MODE_PRIVATE)

    fun goal(c: Context) = p(c).getInt("goal", 2000)
    fun interval(c: Context) = p(c).getInt("interval", 60)
    fun startHour(c: Context) = p(c).getInt("start", 8)
    fun endHour(c: Context) = p(c).getInt("end", 22)

    fun saveSettings(c: Context, goal: Int, interval: Int, start: Int, end: Int) {
        p(c).edit().putInt("goal", goal).putInt("interval", interval)
            .putInt("start", start).putInt("end", end).apply()
    }

    fun dbUrl(c: Context) = p(c).getString("db", DEFAULT_DB) ?: DEFAULT_DB
    fun setDbUrl(c: Context, v: String) = p(c).edit().putString("db", v).apply()
    fun online(c: Context) = p(c).getBoolean("online", true)
    fun setOnline(c: Context, v: Boolean) = p(c).edit().putBoolean("online", v).apply()

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun todayKey() = today()

    // ---- Buluta gönderilmeyi bekleyen işlemler: "P|gün|id|saat|ml" ve "D|gün|id" ----
    fun ops(c: Context): List<String> =
        (p(c).getString("ops", "") ?: "").split(";").filter { it.isNotBlank() }

    fun setOps(c: Context, l: List<String>) =
        p(c).edit().putString("ops", l.joinToString(";")).apply()

    private fun queue(c: Context, op: String) = setOps(c, ops(c) + op)

    // ---- Bugünün kayıtları (eskiden yeniye) ----
    fun logs(c: Context): MutableList<Entry> {
        val sp = p(c)
        if (sp.getString("day", "") != today()) {
            sp.edit().putString("day", today()).putString("logs", "").apply()
            return mutableListOf()
        }
        val raw = sp.getString("logs", "") ?: ""
        val out = mutableListOf<Entry>()
        var migrated = false
        raw.split(";").filter { it.isNotBlank() }.forEachIndexed { idx, s ->
            val a = s.split("|")
            if (a.size == 3) {
                out.add(Entry(a[0], a[1], a[2].toIntOrNull() ?: 0))
            } else if (a.size == 2) { // eski sürümden kalan kayıt
                val e = Entry("0000000000000-%03d".format(idx), a[0], a[1].toIntOrNull() ?: 0)
                out.add(e)
                queue(c, "P|${today()}|${e.id}|${e.time}|${e.ml}")
                migrated = true
            }
        }
        if (migrated) save(c, out)
        return out
    }

    private fun save(c: Context, list: List<Entry>) {
        p(c).edit().putString("day", today())
            .putString("logs", list.joinToString(";") { "${it.id}|${it.time}|${it.ml}" }).apply()
    }

    fun replaceLogs(c: Context, list: List<Entry>) = save(c, list.sortedBy { it.id })

    fun total(c: Context) = logs(c).sumOf { it.ml }
    fun reached(c: Context) = total(c) >= goal(c)

    fun add(c: Context, ml: Int) {
        val l = logs(c)
        val now = Date()
        val e = Entry("${now.time}-${(100..999).random()}", SimpleDateFormat("HH:mm", Locale.US).format(now), ml)
        l.add(e)
        save(c, l)
        queue(c, "P|${today()}|${e.id}|${e.time}|${e.ml}")
    }

    fun removeAt(c: Context, index: Int) {
        val l = logs(c)
        if (index in l.indices) {
            val e = l.removeAt(index)
            save(c, l)
            queue(c, "D|${today()}|${e.id}")
        }
    }
}
