package uz.xolida.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Fonda ishlaydigan xizmat:
 * 1) "Xolida" so'zini oflayn kutadi (Vosk)
 * 2) Signal beradi va buyruqni Google orqali o'zbekcha tinglaydi
 * 3) Claude'dan nima qilishni so'raydi, bajaradi va ovoz bilan javob beradi
 */
class AssistantService : Service(), RecognitionListener {

    companion object {
        @Volatile
        var running = false
        private const val CHANNEL = "xolida"
        private const val NOTIF_ID = 1
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var brain: Brain
    private lateinit var runner: ActionRunner

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var wakeService: SpeechService? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private lateinit var speaker: Speaker
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        if (!startInForeground()) {
            running = false
            stopSelf()
            return
        }
        brain = Brain(this)
        runner = ActionRunner(this)
        speaker = Speaker(this)
        tone = try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (e: Exception) { null }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xolida:listen")
            .apply { acquire() }
        loadModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startInForeground(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Xolida", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Xolida tinglayapti")
            .setContentText("\"Xolida\" deb chaqiring")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
            true
        } catch (e: Exception) {
            StatusBus.post("Xizmatni ishga tushirib bo'lmadi: ${e.message}")
            false
        }
    }

    // ---------- 1. "Xolida" so'zini kutish ----------

    private fun loadModel() {
        Thread {
            try {
                val dir = ModelManager.ensure(this) { StatusBus.post(it) }
                val m = Model(dir.absolutePath)
                main.post {
                    if (!running) {
                        m.close()
                        return@post
                    }
                    model = m
                    StatusBus.post("Tayyor! \"Xolida\" deb chaqiring.")
                    startWake()
                }
            } catch (e: Exception) {
                StatusBus.post("Ovoz modelini yuklab bo'lmadi: ${e.message}. Internetni tekshiring.")
                main.post { stopSelf() }
            }
        }.start()
    }

    private fun startWake() {
        val m = model ?: return
        if (busy || !running) return
        try {
            recognizer?.close()
            val r = Recognizer(m, 16000f)
            recognizer = r
            wakeService = SpeechService(r, 16000f).also { it.startListening(this) }
        } catch (e: Exception) {
            StatusBus.post("Mikrofon band yoki xato: ${e.message}")
            main.postDelayed({ startWake() }, 3000)
        }
    }

    private fun stopWake() {
        wakeService?.let {
            it.stop()
            it.shutdown()
        }
        wakeService = null
    }

    override fun onPartialResult(hypothesis: String?) = checkWake(hypothesis, "partial")
    override fun onResult(hypothesis: String?) = checkWake(hypothesis, "text")
    override fun onFinalResult(hypothesis: String?) {}
    override fun onTimeout() {}

    override fun onError(exception: Exception?) {
        StatusBus.post("Tinglash xatosi: ${exception?.message}")
        stopWake()
        main.postDelayed({ startWake() }, 2000)
    }

    private fun checkWake(json: String?, key: String) {
        if (busy || json == null) return
        val raw = try {
            JSONObject(json).optString(key)
        } catch (e: Exception) {
            ""
        }
        val heard = raw.lowercase()
        if (heard.isBlank()) return
        if (key == "text") StatusBus.post("Eshitildi: $heard")
        if (Prefs.wakeWords(this).any { heard.contains(it) }) onWake()
    }

    // ---------- 2. Buyruqni tinglash ----------

    private fun onWake() {
        busy = true
        stopWake()
        StatusBus.post("Labbay! Tinglayapman...")
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        main.postDelayed({ listenCommand() }, 350)
    }

    private fun listenCommand() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            finish("Telefonda ovozni tanish xizmati topilmadi. Google ilovasini o'rnating.")
            return
        }
        commandRecognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        commandRecognizer = sr
        sr.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) finish("Eshitmadim.") else handleCommand(text)
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Eshitmadim, qaytadan chaqiring."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet bilan muammo bor."
                    12, 13 -> "Telefonda o'zbek tilini tanish yoqilmagan."
                    else -> "Ovozni tanishda xato bo'ldi."
                }
                StatusBus.post("Ovozni tanish xatosi: kod $error")
                finish(msg)
            }
        })
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    // ---------- 3. O'ylash, bajarish, javob berish ----------

    private fun handleCommand(text: String) {
        StatusBus.post("Siz: $text")
        Thread {
            val res = try {
                brain.ask(text)
            } catch (e: Exception) {
                StatusBus.post("Tarmoq xatosi: ${e.message}")
                Brain.Result("Internetga ulanib bo'lmadi.", JSONArray())
            }
            main.post {
                val extra = runner.run(res.actions)
                finish(listOf(res.say, extra).filter { it.isNotBlank() }.joinToString(" "))
            }
        }.start()
    }

    private fun finish(say: String) {
        commandRecognizer?.destroy()
        commandRecognizer = null
        if (say.isBlank()) {
            resume()
            return
        }
        StatusBus.post("Xolida: $say")
        speaker.speak(say) { resume() }
    }

    private fun resume() {
        busy = false
        startWake()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        stopWake()
        recognizer?.close()
        model?.close()
        commandRecognizer?.destroy()
        if (::speaker.isInitialized) speaker.shutdown()
        tone?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        StatusBus.post("Xolida to'xtatildi.")
        super.onDestroy()
    }
}
