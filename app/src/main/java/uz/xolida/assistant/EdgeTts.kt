package uz.xolida.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * O'zbekcha ovoz: Microsoft Edge brauzeridagi "ovoz chiqarib o'qish" xizmati.
 * Bepul, ro'yxatdan o'tish shart emas. Ovozlar: Madina (ayol), Sardor (erkak).
 * Matnni MP3 ga aylantirib qaytaradi; xatoda null.
 */
object EdgeTts {
    const val VOICE_FEMALE = "uz-UZ-MadinaNeural"
    const val VOICE_MALE = "uz-UZ-SardorNeural"

    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROME = "143"
    private const val GEC_VERSION = "1-143.0.3650.75"
    private const val WIN_EPOCH = 11644473600L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var clockSkew = 0L

    private fun secMsGec(): String {
        var t = System.currentTimeMillis() / 1000 + clockSkew + WIN_EPOCH
        t -= t % 300
        val ticks = t * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((ticks.toString() + TOKEN).toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun timestamp(): String {
        val f = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date()) + " GMT+0000 (Coordinated Universal Time)"
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    fun synthesize(text: String, voice: String): ByteArray? {
        var result = request(text, voice)
        if (result == null && clockSkew != 0L) result = request(text, voice)
        return result
    }

    private fun request(text: String, voice: String): ByteArray? {
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$TOKEN" +
            "&ConnectionId=${UUID.randomUUID().toString().replace("-", "")}" +
            "&Sec-MS-GEC=${secMsGec()}" +
            "&Sec-MS-GEC-Version=$GEC_VERSION"

        val req = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/$CHROME.0.0.0 Safari/537.36 Edg/$CHROME.0.0.0"
            )
            .build()

        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        var failed = false

        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    "X-Timestamp:${timestamp()}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
                        "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                )
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                    "<voice name='$voice'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>" +
                    escape(text) + "</prosody></voice></speak>"
                webSocket.send(
                    "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp()}Z\r\n" +
                        "Path:ssml\r\n\r\n" + ssml
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    done.countDown()
                    webSocket.close(1000, null)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val b = bytes.toByteArray()
                if (b.size < 2) return
                val headerLen = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
                if (b.size < 2 + headerLen) return
                val header = String(b, 2, headerLen, Charsets.UTF_8)
                if (header.contains("Path:audio")) {
                    audio.write(b, 2 + headerLen, b.size - 2 - headerLen)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Soat farqi bo'lsa, serverdagi vaqtga moslashamiz
                response?.header("Date")?.let { d ->
                    try {
                        val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        val server = f.parse(d)?.time ?: 0L
                        if (server > 0) clockSkew = (server - System.currentTimeMillis()) / 1000
                    } catch (e: Exception) {
                    }
                }
                StatusBus.post("Edge ovoz xatosi: ${t.message ?: response?.code}")
                failed = true
                done.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.countDown()
            }
        })

        val finished = done.await(25, TimeUnit.SECONDS)
        ws.cancel()
        if (!finished || failed || audio.size() == 0) return null
        return audio.toByteArray()
    }
}
