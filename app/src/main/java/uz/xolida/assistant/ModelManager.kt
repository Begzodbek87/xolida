package uz.xolida.assistant

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** O'zbekcha oflayn ovoz modelini (Vosk, ~50 MB) birinchi ishga tushishda yuklab oladi. */
object ModelManager {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-uz-0.22.zip"

    fun ensure(ctx: Context, progress: (String) -> Unit): File {
        val dir = File(ctx.filesDir, "vosk-uz")
        if (File(dir, "ready").exists()) return dir

        dir.deleteRecursively()
        dir.mkdirs()
        val zip = File(ctx.cacheDir, "model.zip")

        progress("Ovoz modeli yuklanmoqda (faqat bir marta, ~50 MB)...")
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { inp ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct / 10 != lastPct / 10) {
                                lastPct = pct
                                progress("Model yuklanmoqda: $pct%")
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        progress("Model ochilmoqda...")
        val root = dir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                // Arxiv ichidagi bosh papkani olib tashlaymiz
                val name = e.name.substringAfter('/', "")
                if (name.isNotEmpty()) {
                    val out = File(dir, name)
                    if (!out.canonicalPath.startsWith(root)) throw IOException("Noto'g'ri arxiv")
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        zip.delete()
        File(dir, "ready").createNewFile()
        return dir
    }
}
