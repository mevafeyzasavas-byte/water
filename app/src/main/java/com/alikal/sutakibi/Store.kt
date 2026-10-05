package com.alikal.sutakibi

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    private fun p(c: Context) = c.getSharedPreferences("su", Context.MODE_PRIVATE)

    fun goal(c: Context) = p(c).getInt("goal", 2000)
    fun interval(c: Context) = p(c).getInt("interval", 60)
    fun startHour(c: Context) = p(c).getInt("start", 8)
    fun endHour(c: Context) = p(c).getInt("end", 22)

    fun saveSettings(c: Context, goal: Int, interval: Int, start: Int, end: Int) {
        p(c).edit().putInt("goal", goal).putInt("interval", interval)
            .putInt("start", start).putInt("end", end).apply()
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Bugünün kayıtları (eskiden yeniye): saat to ml */
    fun logs(c: Context): MutableList<Pair<String, Int>> {
        val sp = p(c)
        if (sp.getString("day", "") != today()) {
            sp.edit().putString("day", today()).putString("logs", "").apply()
            return mutableListOf()
        }
        val raw = sp.getString("logs", "") ?: ""
        return raw.split(";").filter { it.contains("|") }.map {
            val a = it.split("|")
            a[0] to a[1].toInt()
        }.toMutableList()
    }

    private fun save(c: Context, list: List<Pair<String, Int>>) {
        p(c).edit().putString("day", today())
            .putString("logs", list.joinToString(";") { "${it.first}|${it.second}" }).apply()
    }

    fun total(c: Context) = logs(c).sumOf { it.second }
    fun reached(c: Context) = total(c) >= goal(c)

    fun add(c: Context, ml: Int) {
        val l = logs(c)
        l.add(SimpleDateFormat("HH:mm", Locale.US).format(Date()) to ml)
        save(c, l)
    }

    fun removeAt(c: Context, index: Int) {
        val l = logs(c)
        if (index in l.indices) {
            l.removeAt(index)
            save(c, l)
        }
    }
}
