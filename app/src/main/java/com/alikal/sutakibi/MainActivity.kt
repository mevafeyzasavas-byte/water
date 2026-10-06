package com.alikal.sutakibi

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
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
        cloud.text = "☁ Çevrimdışı · bağlanınca otomatik eşitlenir"
        cloud.visibility = if (on) View.GONE else View.VISIBLE
        cloud.setTextColor(Color.parseColor("#C0392B"))

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

    private fun stepper(
        host: LinearLayout, init: Int, min: Int, max: Int, step: Int,
        big: Boolean, fmt: (Int) -> String
    ): () -> Int {
        var v = init
        host.removeAllViews()
        host.orientation = LinearLayout.HORIZONTAL
        host.gravity = Gravity.CENTER_VERTICAL
        val value = TextView(this).apply {
            text = fmt(v); gravity = Gravity.CENTER
            textSize = if (big) 28f else 18f
            setTextColor(Color.parseColor("#0F2A3D"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun btn(label: String, d: Int) = TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 22f
            setTextColor(Color.parseColor("#0A6EBD"))
            setBackgroundResource(R.drawable.bg_step_btn)
            setOnClickListener { v = (v + d).coerceIn(min, max); value.text = fmt(v) }
        }
        val sz = dp(if (big) 44 else 38)
        host.addView(btn("−", -step), LinearLayout.LayoutParams(sz, sz))
        host.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        host.addView(btn("+", step), LinearLayout.LayoutParams(sz, sz))
        return { v }
    }

    private fun showSettings() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = layoutInflater.inflate(R.layout.dialog_settings, null)
        d.setContentView(v)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setWindowAnimations(R.style.SheetAnim)
            setDimAmount(0.45f)
        }
        v.findViewById<View>(R.id.root).setOnClickListener { d.dismiss() }
        v.findViewById<View>(R.id.btnClose).setOnClickListener { d.dismiss() }
        v.findViewById<View>(R.id.btnCancel).setOnClickListener { d.dismiss() }

        val getGoal = stepper(v.findViewById(R.id.goalHost), Store.goal(this), 500, 10000, 250, true) { "$it ml" }
        val getStart = stepper(v.findViewById(R.id.startHost), Store.startHour(this), 0, 23, 1, false) { "%02d:00".format(it) }
        val getEnd = stepper(v.findViewById(R.id.endHost), Store.endHour(this), 1, 24, 1, false) { "%02d:00".format(it) }

        // Aralık çipleri
        var interval = Store.interval(this)
        val chipRow = v.findViewById<LinearLayout>(R.id.chipRow)
        val chips = mutableListOf<Pair<Int, TextView>>()
        fun paint() = chips.forEach { (m, t) ->
            val on = m == interval
            t.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
            t.setTextColor(Color.parseColor(if (on) "#FFFFFF" else "#0F2A3D"))
        }
        for (m in (listOf(30, 45, 60, 90, 120, 180) + interval).distinct().sorted()) {
            val t = TextView(this).apply {
                text = if (m >= 60 && m % 60 == 0) "${m / 60} sa" else "$m dk"
                textSize = 14f; gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { interval = m; paint() }
            }
            chips.add(m to t)
            chipRow.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(8) })
        }
        paint()

        v.findViewById<View>(R.id.btnSave).setOnClickListener {
            val g = getGoal(); val s = getStart(); val e = getEnd()
            when {
                e <= s -> Toast.makeText(this, "Bitiş saati başlangıçtan sonra olmalı", Toast.LENGTH_LONG).show()
                else -> {
                    Store.saveSettings(this, g, interval, s, e)
                    Scheduler.schedule(this)
                    refresh()
                    doSync()
                    d.dismiss()
                }
            }
        }
        d.show()
    }
}
