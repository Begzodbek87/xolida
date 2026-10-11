package uz.xolida.assistant

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Claude aytgan amallarni telefonda bajaradi. Qo'shimcha aytiladigan gapni qaytaradi. */
class ActionRunner(private val ctx: Context) {

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k).trim()

    fun run(actions: JSONArray): String {
        val extra = StringBuilder()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            val msg = try {
                when (a.str("type")) {
                    "call" -> call(a)
                    "sms" -> sms(a)
                    "alarm" -> alarm(a)
                    "timer" -> timer(a)
                    "event" -> event(a)
                    "schedule" -> schedule(a)
                    else -> null
                }
            } catch (e: SecurityException) {
                StatusBus.post("Ruxsat yo'q: ${e.message}")
                "Bunga ruxsat berilmagan, ilovada ruxsatlarni bering."
            } catch (e: Exception) {
                StatusBus.post("Xato (${a.str("type")}): ${e.message}")
                "Buni bajarib bo'lmadi."
            }
            if (!msg.isNullOrBlank()) extra.append(' ').append(msg)
        }
        return extra.toString().trim()
    }

    // ---------- Qo'ng'iroq va SMS ----------

    private fun findNumber(a: JSONObject): String? {
        val num = a.str("number").filter { it.isDigit() || it == '+' }
        if (num.length >= 3) return num
        val name = a.str("contact")
        if (name.isEmpty()) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun call(a: JSONObject): String? {
        val n = findNumber(a) ?: return "${a.str("contact")} kontaktlarda topilmadi."
        val tm = ctx.getSystemService(TelecomManager::class.java)
        tm.placeCall(Uri.fromParts("tel", n, null), Bundle())
        return null
    }

    private fun sms(a: JSONObject): String? {
        val n = findNumber(a) ?: return "${a.str("contact")} kontaktlarda topilmadi."
        val text = a.str("text")
        if (text.isEmpty()) return "Xabar matni bo'sh."
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        sm.sendMultipartTextMessage(n, null, sm.divideMessage(text), null, null)
        return null
    }

    // ---------- Budilnik va taymer (soat ilovasi orqali) ----------

    private fun alarm(a: JSONObject): String? {
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour", 7))
            putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute", 0))
            val label = a.str("label")
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            val days = a.optJSONArray("days")
            if (days != null && days.length() > 0) {
                val list = ArrayList<Int>()
                for (d in 0 until days.length()) {
                    val v = days.optInt(d)
                    if (v in 1..7) list.add(v)
                }
                if (list.isNotEmpty()) putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, list)
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
        return null
    }

    private fun timer(a: JSONObject): String? {
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds", 60).coerceIn(1, 86_400))
            val label = a.str("label")
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
        return null
    }

    // ---------- Kalendar: rejalar, darslar, eslatmalar ----------

    private fun calendarId(): Long? {
        ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.VISIBLE}=1 AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            null
        )?.use { c ->
            var first: Long? = null
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (first == null) first = id
                if (c.getInt(1) == 1) return id
            }
            return first
        }
        return null
    }

    private fun event(a: JSONObject): String? {
        val calId = calendarId()
            ?: return "Telefonda kalendar topilmadi. Google hisobini qo'shing."
        val start = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(a.str("start"))
            ?: return "Vaqtni tushunmadim."
        val dur = a.optInt("duration_min", 60).coerceAtLeast(1)

        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, a.str("title").ifEmpty { "Reja" })
            put(CalendarContract.Events.DTSTART, start.time)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (a.optBoolean("repeat_weekly", false)) {
                put(CalendarContract.Events.RRULE, "FREQ=WEEKLY")
                put(CalendarContract.Events.DURATION, "PT${dur}M")
            } else {
                put(CalendarContract.Events.DTEND, start.time + dur * 60_000L)
            }
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
            ?: return "Rejani saqlab bo'lmadi."
        val eventId = ContentUris.parseId(uri)

        val rem = if (a.isNull("reminder_min")) 10 else a.optInt("reminder_min", 10)
        if (rem >= 0) {
            val r = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, rem)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            ctx.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r)
        }
        return null
    }

    private fun schedule(a: JSONObject): String {
        val day: Date = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(a.str("date")) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        val cal = Calendar.getInstance().apply {
            time = day
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val begin = cal.timeInMillis
        val end = begin + 24 * 3_600_000L

        val items = mutableListOf<Triple<Long, String, Boolean>>()
        CalendarContract.Instances.query(
            ctx.contentResolver,
            arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.ALL_DAY
            ),
            begin,
            end
        )?.use { c ->
            while (c.moveToNext()) {
                items.add(Triple(c.getLong(0), c.getString(1) ?: "", c.getInt(2) == 1))
            }
        }
        if (items.isEmpty()) return "Bu kunga rejalar yo'q."

        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val parts = items.sortedBy { it.first }.map { (time, title, allDay) ->
            if (allDay) "kun bo'yi: $title" else "soat ${fmt.format(Date(time))} da $title"
        }
        return "Rejalaringiz: " + parts.joinToString(", ") + "."
    }
}
