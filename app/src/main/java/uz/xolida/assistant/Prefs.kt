package uz.xolida.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Sozlamalar: API kalit va chaqiruv so'zlari telefonda saqlanadi. */
object Prefs {
    private const val DEFAULT_WAKE = "xolida, holida, xolid, holid"

    private fun sp(c: Context) = c.getSharedPreferences("xolida", Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = sp(c).getString("api_key", "") ?: ""
    fun setApiKey(c: Context, v: String) = sp(c).edit().putString("api_key", v.trim()).apply()

    fun wakeText(c: Context): String = sp(c).getString("wake", DEFAULT_WAKE) ?: DEFAULT_WAKE
    fun setWakeText(c: Context, v: String) = sp(c).edit().putString("wake", v).apply()

    fun wakeWords(c: Context): List<String> =
        wakeText(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

/** Xizmatdagi holatni ilova oynasiga yetkazadi. */
object StatusBus {
    @Volatile
    var listener: ((String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun post(msg: String) {
        main.post { listener?.invoke(msg) }
    }
}
