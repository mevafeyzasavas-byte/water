package com.alikal.sutakibi

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var ring: RingView
    private lateinit var tvNext: TextView
    private lateinit var logs: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        window.statusBarColor = Color.parseColor("#F0F6FA")
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        ring = findViewById(R.id.ring)
        tvNext = findViewById(R.id.tvNext)
        logs = findViewById(R.id.logs)

        findViewById<View>(R.id.btn250).setOnClickListener { add(250) }
        findViewById<View>(R.id.btn100).setOnClickListener { add(100) }
        findViewById<View>(R.id.btn500).setOnClickListener { add(500) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            doSync()
            handler.postDelayed(this, 15000)
        }
    }

    override fun onResume() {
        super.onResume()
        Scheduler.schedule(this)
        refresh()
        askExactAlarm()
        handler.post(poll) // açıkken 15 sn'de bir buluttan güncel durumu çeker
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(poll)
    }

    private fun doSync() {
        val app = applicationContext
        Thread {
            val changed = Sync.syncNow(app)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (changed) {
                    if (Store.reached(this)) Notifier.cancel(this)
                    Scheduler.schedule(this)
                }
                refresh()
            }
        }.start()
    }

    private fun add(ml: Int) {
        Store.add(this, ml)
        Notifier.cancel(this)
        Scheduler.schedule(this)
        refresh()
        doSync()
    }

    private fun refresh() {
        val total = Store.total(this)
        val goal = Store.goal(this)
        ring.set(total, goal)
        val cloud = findViewById<TextView>(R.id.tvCloud)
        val on = Store.online(this)
        cloud.text = if (on) "☁ Bulutla eşitlendi · diğer cihazlarda da aynı görünür"
        else "☁ Çevrimdışı · bağlanınca otomatik eşitlenir"
        cloud.setTextColor(Color.parseColor(if (on) "#2E9E5B" else "#C0392B"))

        if (Store.reached(this)) {
            tvNext.text = "Bugünkü hedef tamamlandı 🎉"
        } else {
            val t = Scheduler.nextTrigger(this)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
            val tomorrow = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) !=
                Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_YEAR)
            tvNext.text = "Sıradaki hatırlatma ${if (tomorrow) "yarın " else ""}$time · ${goal - total} ml kaldı"
        }

        logs.removeAllViews()
        val list = Store.logs(this)
        for (idx in list.indices.reversed()) {
            val entry = list[idx]
            val time = entry.time
            val ml = entry.ml
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("$time · $ml ml kaydı silinsin mi?")
                        .setPositiveButton("Sil") { _, _ ->
                            Store.removeAt(this@MainActivity, idx)
                            Scheduler.schedule(this@MainActivity)
                            refresh()
                            doSync()
                        }
                        .setNegativeButton("Vazgeç", null).show()
                }
            }
            row.addView(TextView(this).apply {
                text = time; textSize = 15f; setTextColor(Color.parseColor("#0F2A3D"))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "$ml ml"; textSize = 15f; setTextColor(Color.parseColor("#0F2A3D"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            logs.addView(row)
            logs.addView(View(this).apply { setBackgroundColor(Color.parseColor("#DCE6EE")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
        if (list.isEmpty()) {
            logs.addView(TextView(this).apply {
                text = "Henüz kayıt yok"; textSize = 14f
                setTextColor(Color.parseColor("#5B7083")); setPadding(0, dp(14), 0, 0)
            })
        }
    }

    private fun askExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sp = getSharedPreferences("su", Context.MODE_PRIVATE)
        if (am.canScheduleExactAlarms() || sp.getBoolean("asked_exact", false)) return
        sp.edit().putBoolean("asked_exact", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Tam saatinde hatırlatma")
            .setMessage("Hatırlatmaların tam saat başında gelmesi için \"Alarmlar ve hatırlatıcılar\" iznini açın.")
            .setPositiveButton("İzin ver") { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Şimdi değil", null).show()
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        fun field(label: String, value: Int): EditText {
            box.addView(TextView(this).apply { text = label; textSize = 13f; setPadding(0, dp(12), 0, 0) })
            val e = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER; setText(value.toString())
            }
            box.addView(e)
            return e
        }
        val goal = field("Günlük hedef (ml)", Store.goal(this))
        val interval = field("Hatırlatma aralığı (dakika)", Store.interval(this))
        val start = field("Başlangıç saati (0-23)", Store.startHour(this))
        val end = field("Bitiş saati (1-24)", Store.endHour(this))
        box.addView(TextView(this).apply { text = "Firebase veritabanı adresi"; textSize = 13f; setPadding(0, dp(12), 0, 0) })
        val db = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Store.dbUrl(this@MainActivity)); textSize = 12f
        }
        box.addView(db)

        AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setView(box)
            .setPositiveButton("Kaydet") { _, _ ->
                val g = goal.text.toString().toIntOrNull() ?: 2000
                val i = interval.text.toString().toIntOrNull() ?: 60
                val s = start.text.toString().toIntOrNull() ?: 8
                val e = end.text.toString().toIntOrNull() ?: 22
                val u = db.text.toString().trim()
                if (!u.startsWith("https://") || g !in 500..10000 || i !in 15..720 || s !in 0..23 || e !in 1..24 || e <= s) {
                    Toast.makeText(this, "Geçersiz değer, ayarlar kaydedilmedi", Toast.LENGTH_LONG).show()
                } else {
                    Store.saveSettings(this, g, i, s, e)
                    Store.setDbUrl(this, u)
                    Scheduler.schedule(this)
                    refresh()
                    doSync()
                }
            }
            .setNegativeButton("Vazgeç", null).show()
    }
}
