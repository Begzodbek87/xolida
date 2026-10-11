package uz.xolida.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale

/**
 * Xolidaning ovozi. Avval o'zbekcha Edge ovozi (internet orqali),
 * ishlamasa telefonning o'z ovozi. Gap tugaganda onDone chaqiriladi.
 */
class Speaker(private val ctx: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(ctx, this)
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private var pendingDone: (() -> Unit)? = null

    fun speak(text: String, onDone: () -> Unit) {
        stop()
        pendingDone = onDone
        Thread {
            val voice = Prefs.voice(ctx)
            val mp3 = if (voice == "phone") null else EdgeTts.synthesize(text, voice)
            main.post {
                if (pendingDone !== onDone) return@post // bekor qilingan
                if (mp3 != null) playMp3(mp3) else speakLocal(text)
            }
        }.start()
    }

    private fun playMp3(data: ByteArray) {
        try {
            val f = File(ctx.cacheDir, "reply.mp3")
            f.writeBytes(data)
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { finish() }
            p.setOnErrorListener { _, _, _ -> finish(); true }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            StatusBus.post("Ovozni ijro etib bo'lmadi: ${e.message}")
            finish()
        }
    }

    private fun speakLocal(text: String) {
        val t = tts
        if (!ttsReady || t == null) {
            finish()
            return
        }
        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply") != TextToSpeech.SUCCESS) finish()
    }

    private fun finish() {
        player?.release()
        player = null
        val cb = pendingDone
        pendingDone = null
        cb?.invoke()
    }

    fun stop() {
        pendingDone = null
        player?.release()
        player = null
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        if (t.setLanguage(Locale("uz", "UZ")) < TextToSpeech.LANG_AVAILABLE) {
            t.setLanguage(Locale("tr", "TR"))
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { finish() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finish() }
            }
        })
        ttsReady = true
    }
}
