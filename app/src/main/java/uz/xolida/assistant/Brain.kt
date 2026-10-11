package uz.xolida.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Xolidaning "miyasi": gapingizni Claude'ga yuboradi va
 * nima qilish kerakligini (amallar ro'yxati) hamda javob matnini oladi.
 */
class Brain(private val context: Context) {

    data class Result(val say: String, val actions: JSONArray)

    companion object {
        // Kerak bo'lsa boshqa modelga almashtirish mumkin
        const val MODEL = "claude-haiku-5-5"

        private val SYSTEM_PROMPT = """
You are Xolida, a personal voice assistant on the user's Android phone. The user speaks Uzbek (sometimes Russian). Speech recognition may contain mistakes; infer the intended meaning.
Current local date and time: {NOW}.

Reply ONLY with one JSON object and nothing else:
{"say": "short spoken reply in Uzbek, Latin script, 1-2 sentences", "actions": [ ... ]}

Available actions:
{"type":"call","contact":"contact name as saved in the phone, nominative form, e.g. Onam, Akmal","number":"only if the user dictated digits"}
{"type":"sms","contact":"...","number":"only if dictated","text":"message text"}
{"type":"alarm","hour":7,"minute":0,"label":"optional","days":[2,3,4,5,6]}
   - "days" only for repeating alarms: 1=Sunday, 2=Monday, 3=Tuesday, 4=Wednesday, 5=Thursday, 6=Friday, 7=Saturday.
{"type":"timer","seconds":600,"label":"optional"}
{"type":"event","title":"...","start":"YYYY-MM-DDTHH:MM","duration_min":60,"repeat_weekly":false,"reminder_min":10}
   - Use for plans, meetings, reminders and lessons (dars). For a weekly lesson timetable use repeat_weekly true and one event per weekday, starting from the next occurrence of that weekday.
{"type":"schedule","date":"YYYY-MM-DD"}
   - When the user asks what is planned for a day. The app reads the events aloud itself, so keep "say" empty.

Rules:
- 24-hour time. Resolve relative dates (bugun, ertaga, indinga, dushanba, keyingi hafta...) from the current date.
- If something important is missing or unclear, ask a short question in "say" and return no actions.
- For general questions or chat, just answer in "say" with empty actions.
- Never invent phone numbers.
- Family words are contact names as said: onam -> "Onam", dadam -> "Dadam", akam -> "Akam", opam -> "Opam".
- Several actions in one request are allowed.
""".trimIndent()
    }

    // Qisqa suhbat xotirasi ("yana 10 daqiqaga sur" kabi davomli buyruqlar uchun)
    private val history = mutableListOf<JSONObject>()
    private var lastTime = 0L

    fun ask(text: String): Result {
        val key = Prefs.apiKey(context)
        if (key.isBlank()) {
            return Result("API kalit kiritilmagan. Ilovaga Claude API kalitini kiriting.", JSONArray())
        }

        // 10 daqiqadan ko'p jimlikdan keyin suhbatni yangidan boshlaymiz
        if (System.currentTimeMillis() - lastTime > 10 * 60_000L) history.clear()
        lastTime = System.currentTimeMillis()

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm, EEEE", Locale.US).format(Date())
        val messages = JSONArray()
        history.forEach { messages.put(it) }
        val userMsg = JSONObject().put("role", "user").put("content", text)
        messages.put(userMsg)

        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 1024)
            .put("system", SYSTEM_PROMPT.replace("{NOW}", now))
            .put("messages", messages)

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("content-type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                StatusBus.post("API xatosi $code: ${resp.take(300)}")
                val say = when (code) {
                    401 -> "API kalit noto'g'ri."
                    402, 403 -> "API hisobingizda mablag' yoki ruxsat yetarli emas."
                    429 -> "So'rovlar juda ko'p, biroz kuting."
                    else -> "Serverda xatolik bo'ldi."
                }
                return Result(say, JSONArray())
            }

            val content = JSONObject(resp).getJSONArray("content")
            var raw = ""
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") raw += block.optString("text")
            }

            history.add(userMsg)
            history.add(JSONObject().put("role", "assistant").put("content", raw))
            while (history.size > 12) history.removeAt(0)

            return parse(raw)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(raw: String): Result {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return Result(raw.trim(), JSONArray())
        return try {
            val o = JSONObject(raw.substring(start, end + 1))
            val say = if (o.isNull("say")) "" else o.optString("say")
            Result(say, o.optJSONArray("actions") ?: JSONArray())
        } catch (e: Exception) {
            Result(raw.trim(), JSONArray())
        }
    }
}
