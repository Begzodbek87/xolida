package uz.xolida.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

/** Sozlash oynasi: kalit, ruxsatlar, ishga tushirish va yozma sinov. */
class MainActivity : Activity() {

    private lateinit var keyEdit: EditText
    private lateinit var wakeEdit: EditText
    private lateinit var testEdit: EditText
    private lateinit var toggleBtn: Button
    private lateinit var logView: TextView
    private val logLines = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())

    private val brain by lazy { Brain(applicationContext) }
    private val runner by lazy { ActionRunner(this) }
    private val speaker by lazy { Speaker(applicationContext) }

    private val permissions: Array<String>
        get() {
            val list = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            return list.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad * 2)
        }

        fun label(t: String) = TextView(this).apply {
            text = t
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 4)
        }.also { root.addView(it) }

        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { onClick() }
        }.also { root.addView(it) }

        root.addView(TextView(this).apply {
            text = "Xolida"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Shaxsiy ovozli yordamchingiz"
            textSize = 14f
        })

        label("1. Claude API kaliti")
        keyEdit = EditText(this).apply {
            hint = "sk-ant-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.apiKey(this@MainActivity))
        }.also { root.addView(it) }

        label("2. Chaqiruv so'zlari (vergul bilan)")
        wakeEdit = EditText(this).apply {
            setText(Prefs.wakeText(this@MainActivity))
        }.also { root.addView(it) }

        button("Saqlash") {
            Prefs.setApiKey(this, keyEdit.text.toString())
            Prefs.setWakeText(this, wakeEdit.text.toString())
            Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
        }

        label("Xolidaning ovozi")
        val voices = listOf(
            "Madina (ayol, o'zbekcha)" to EdgeTts.VOICE_FEMALE,
            "Sardor (erkak, o'zbekcha)" to EdgeTts.VOICE_MALE,
            "Telefonning o'z ovozi" to "phone"
        )
        val voiceBtn = button("") {}
        fun refreshVoice() {
            val cur = Prefs.voice(this)
            voiceBtn.text = "Ovoz: " + (voices.firstOrNull { it.second == cur }?.first ?: cur) + "  (almashtirish)"
        }
        voiceBtn.setOnClickListener {
            val cur = Prefs.voice(this)
            val idx = voices.indexOfFirst { it.second == cur }
            Prefs.setVoice(this, voices[(idx + 1) % voices.size].second)
            refreshVoice()
        }
        refreshVoice()
        button("Ovozni sinash") {
            log("Ovoz sinovi...")
            speaker.speak("Assalomu alaykum! Men Xolidaman, sizga yordam berishga tayyorman.") {
                log("Ovoz sinovi tugadi.")
            }
        }

        label("3. Ruxsatlar (bir marta)")
        button("Ruxsatlarni berish") { requestPermissions(permissions, 1) }
        button("Boshqa ilovalar ustidan ko'rsatish") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        button("Batareya cheklovini o'chirish") {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        label("4. Ishga tushirish")
        toggleBtn = button("") { toggle() }

        label("Yozma sinov (ovozsiz tekshirish)")
        testEdit = EditText(this).apply {
            hint = "Masalan: ertaga soat 7 ga budilnik qo'y"
        }.also { root.addView(it) }
        button("Yuborish") { runTest() }

        label("Jurnal")
        logView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }.also { root.addView(it) }

        setContentView(ScrollView(this).apply { addView(root) })
        updateToggle()
    }

    override fun onResume() {
        super.onResume()
        StatusBus.listener = { log(it) }
        updateToggle()
    }

    override fun onPause() {
        StatusBus.listener = null
        super.onPause()
    }

    private fun toggle() {
        val svc = Intent(this, AssistantService::class.java)
        if (AssistantService.running) {
            stopService(svc)
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Avval ruxsatlarni bering", Toast.LENGTH_LONG).show()
                requestPermissions(permissions, 1)
                return
            }
            if (Prefs.apiKey(this).isBlank()) {
                Toast.makeText(this, "Avval API kalitni kiriting va saqlang", Toast.LENGTH_LONG).show()
                return
            }
            if (!Settings.canDrawOverlays(this)) {
                log("Eslatma: \"ustidan ko'rsatish\" ruxsatisiz budilnik va taymer fonda ishlamasligi mumkin.")
            }
            startForegroundService(svc)
            log("Ishga tushirilmoqda...")
        }
        main.postDelayed({ updateToggle() }, 500)
    }

    private fun updateToggle() {
        if (!::toggleBtn.isInitialized) return
        toggleBtn.text = if (AssistantService.running) "Xolidani to'xtatish" else "Xolidani ishga tushirish"
    }

    private fun runTest() {
        val text = testEdit.text.toString().trim()
        if (text.isEmpty()) return
        log("Siz: $text")
        Thread {
            val res = try {
                brain.ask(text)
            } catch (e: Exception) {
                Brain.Result("Internetga ulanib bo'lmadi: ${e.message}", JSONArray())
            }
            runOnUiThread {
                if (res.actions.length() > 0) log("Amallar: ${res.actions}")
                val extra = runner.run(res.actions)
                val say = listOf(res.say, extra).filter { it.isNotBlank() }.joinToString(" ")
                log("Xolida: $say")
                if (say.isNotBlank()) speaker.speak(say) {}
            }
        }.start()
    }

    private fun log(msg: String) {
        logLines.addFirst(msg)
        while (logLines.size > 40) logLines.removeLast()
        logView.text = logLines.joinToString("\n\n")
        updateToggle()
    }
}
