package com.alikal.sutakibi

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Firebase Realtime Database (REST) ile cihazlar arası eşitleme. Yol: /su/<yyyy-MM-dd>/<kayıt-id> */
object Sync {
    private fun dayUrl(c: Context, day: String, sub: String = "") =
        "${Store.dbUrl(c).trim().trimEnd('/')}/su/$day$sub.json"

    private fun http(method: String, url: String, body: String? = null): String? = try {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = method
        con.connectTimeout = 4000
        con.readTimeout = 4000
        if (body != null) {
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = con.responseCode
        val text = (if (code in 200..299) con.inputStream else con.errorStream)
            ?.bufferedReader()?.use { it.readText() }
        con.disconnect()
        if (code in 200..299) text ?: "" else null
    } catch (e: Exception) {
        null
    }

    /** Bekleyen ekleme/silme işlemlerini buluta gönderir. */
    fun flush(c: Context): Boolean {
        val ops = Store.ops(c).toMutableList()
        while (ops.isNotEmpty()) {
            val a = ops.first().split("|")
            val ok = when {
                a[0] == "P" && a.size == 5 ->
                    http("PUT", dayUrl(c, a[1], "/${a[2]}"), "{\"t\":\"${a[3]}\",\"ml\":${a[4]}}") != null
                a[0] == "D" && a.size == 3 ->
                    http("DELETE", dayUrl(c, a[1], "/${a[2]}")) != null
                else -> true
            }
            if (!ok) { Store.setOps(c, ops); Store.setOnline(c, false); return false }
            ops.removeAt(0)
        }
        Store.setOps(c, ops)
        return true
    }

    /** Önce bekleyenleri gönderir, sonra bugünün kayıtlarını buluttan çeker. Değişiklik olduysa true. */
    fun syncNow(c: Context): Boolean {
        if (!flush(c)) return false
        val txt = http("GET", dayUrl(c, Store.todayKey()))
        if (txt == null) { Store.setOnline(c, false); return false }
        Store.setOnline(c, true)
        val remote = mutableListOf<Entry>()
        val t = txt.trim()
        if (t.isNotEmpty() && t != "null") {
            try {
                val o = JSONObject(t)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val e = o.optJSONObject(k) ?: continue
                    remote.add(Entry(k, e.optString("t", "--:--"), e.optInt("ml", 0)))
                }
            } catch (e: Exception) {
                return false
            }
        }
        remote.sortBy { it.id }
        if (Store.ops(c).isNotEmpty()) return false // bu sırada yeni kayıt eklendi, ezme
        return if (remote != Store.logs(c)) { Store.replaceLogs(c, remote); true } else false
    }
}
