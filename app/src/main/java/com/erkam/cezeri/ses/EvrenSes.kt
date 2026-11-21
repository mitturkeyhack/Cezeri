package com.erkam.cezeri.ses

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import com.erkam.cezeri.core.Olcum
import com.erkam.cezeri.core.Turkce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cezerî'nin sesi: EVREN voxcpm2 (/v1/audio/speech). Cümleler sırayla indirilir; biri çalarken
 * bir sonraki iner. WAV akış hâlinde gelirse inerken çalmaya başlar. Kısa cümleler telefonda saklanır.
 */
class EvrenSes(private val ctx: Context) {

    private val evren = ctx.getSharedPreferences("evren", Context.MODE_PRIVATE)
    private val prefs = ctx.getSharedPreferences("ses", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val indirIsci = Executors.newSingleThreadExecutor { Thread(it, "evren-ses-indir") }
    private val calIsci = Executors.newSingleThreadExecutor { Thread(it, "evren-ses-cal").apply { priority = Thread.MAX_PRIORITY } }
    private val onbellekIsci = Executors.newSingleThreadExecutor { Thread(it, "evren-ses-onbellek").apply { priority = Thread.MIN_PRIORITY } }
    private val onbellek = File(ctx.filesDir, "ses_onbellek").apply { mkdirs() }

    private val nesil = AtomicInteger(0)
    private val sirada = AtomicInteger(0)
    private val _konusuyor = MutableStateFlow(false)
    val konusuyor: StateFlow<Boolean> = _konusuyor
    private val _sonHata = MutableStateFlow<String?>(null)
    val sonHata: StateFlow<String?> = _sonHata
    private val _onbellekSayisi = MutableStateFlow(onbellekSay())
    val onbellekSayisi: StateFlow<Int> = _onbellekSayisi

    /** Hata sonrası bir süre doğrudan telefonun sesine geç (her cümlede beklememek için) */
    @Volatile private var dinlenmeBitis = 0L
    @Volatile private var iz: AudioTrack? = null
    @Volatile private var izAnahtar = ""
    @Volatile private var oynatici: MediaPlayer? = null

    var ses: String
        get() = prefs.getString("evren_ses", "kaan") ?: "kaan"
        set(v) { prefs.edit().putString("evren_ses", v).apply(); _onbellekSayisi.value = onbellekSay(); hazirla() }
    var tarif: Boolean
        get() = prefs.getBoolean("evren_tarif", true)
        set(v) { prefs.edit().putBoolean("evren_tarif", v).apply(); _onbellekSayisi.value = onbellekSay(); hazirla() }
    var hiz: Float
        get() = prefs.getFloat("hiz", 1.15f)
        set(v) { prefs.edit().putFloat("hiz", v).apply() }

    private fun anahtar() = evren.getString("anahtar", "").orEmpty()
    private fun adres() = (evren.getString("adres", null) ?: "https://evren-llmapi.ssyz.org.tr/v1").trimEnd('/')
    fun kullanilabilir(): Boolean = anahtar().isNotBlank() && System.currentTimeMillis() > dinlenmeBitis

    // ------------------------------------------------------------------ önbellek

    private fun anahtarOf(metin: String): String {
        val h = MessageDigest.getInstance("SHA-1").digest("$ses|$tarif|${Turkce.norm(metin)}".toByteArray())
        return ses + "_" + h.joinToString("") { "%02x".format(it) }.take(20)
    }
    private fun onbellekDosyasi(metin: String) = File(onbellek, anahtarOf(metin) + ".wav")
    private fun onbellekSay() = onbellek.listFiles()?.count { it.name.startsWith(ses + "_") } ?: 0

    private fun onbellegeYaz(metin: String, bayt: ByteArray) {
        if (bayt.size < 64 || !riffMi(bayt)) return
        try {
            onbellekDosyasi(metin).writeBytes(bayt)
            val l = onbellek.listFiles()?.sortedBy { it.lastModified() } ?: return
            if (l.size > 400) l.take(l.size - 400).forEach { it.delete() }
            _onbellekSayisi.value = onbellekSay()
        } catch (_: Exception) {}
    }

    /** Sık cümleleri seçili sesle önceden üretir (arka planda, sırayla) */
    fun hazirla() {
        if (anahtar().isBlank()) return
        onbellekIsci.execute {
            for (c in SIK_CUMLELER) {
                if (onbellekDosyasi(c).exists()) continue
                val b = try { istek(c) } catch (_: Exception) { null } ?: break
                val tum = try { b.use { it.readBytes() } } catch (_: Exception) { null } ?: break
                onbellegeYaz(c, tum)
            }
        }
    }

    // ------------------------------------------------------------------ HTTP

    private class Hata(val mesaj: String) : Exception(mesaj)

    /** İsteği gönderir, sesin bayt akışını döndürür. Metin alanının adı ilk denemede tutmazsa diğerini dener. */
    private fun istek(metin: String): InputStream {
        val alanlar = listOf(prefs.getString("evren_alan", "input") ?: "input").let { it + listOf("input", "text").filter { a -> a !in it } }
        var son: Hata? = null
        for ((i, alan) in alanlar.withIndex()) {
            val c = (URL(adres() + "/audio/speech").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 8000; readTimeout = 30000
                setRequestProperty("Authorization", "Bearer " + anahtar())
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val govde = JSONObject().put("model", "voxcpm2").put(alan, metin).put("voice", ses).put("response_format", "wav")
            if (tarif) govde.put("instructions", TARIF)
            c.outputStream.use { it.write(govde.toString().toByteArray(Charsets.UTF_8)) }
            val kod = c.responseCode
            if (kod in 200..299) {
                if (i > 0) prefs.edit().putString("evren_alan", alan).apply()
                return c.inputStream
            }
            val hata = try { c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() } catch (_: Exception) { "" }
            c.disconnect()
            son = Hata(
                when {
                    kod == 401 || kod == 403 -> "EVREN anahtarı ses için kabul edilmedi ($kod)."
                    kod == 402 || hata.contains("credit", true) || hata.contains("kredi", true) -> "EVREN kredin ses için yetmiyor."
                    kod == 429 -> "EVREN ses sınırına gelindi, biraz sonra tekrar dene."
                    kod == 400 || kod == 422 -> "EVREN sesi isteği kabul etmedi ($kod): ${hata.take(140)}"
                    else -> "EVREN sesi cevap vermedi ($kod)."
                }
            )
            if (kod != 400 && kod != 422) break
        }
        throw son ?: Hata("EVREN sesi cevap vermedi.")
    }

    // ------------------------------------------------------------------ konuşma

    private class Parca {
        val kuyruk = LinkedBlockingQueue<ByteArray>()
        @Volatile var hata: String? = null
    }

    private class KuyrukAkisi(private val p: Parca, private val nesil: AtomicInteger, private val n: Int) : InputStream() {
        private var mevcut: ByteArray? = null
        private var konum = 0
        private var bitti = false
        override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 0xFF }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (bitti) return -1
            while (mevcut == null || konum >= mevcut!!.size) {
                if (nesil.get() != n) { bitti = true; return -1 }
                val x = p.kuyruk.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (x.isEmpty()) { bitti = true; return -1 }
                mevcut = x; konum = 0
            }
            val k = minOf(len, mevcut!!.size - konum)
            System.arraycopy(mevcut!!, konum, b, off, k)
            konum += k
            return k
        }
    }

    /**
     * Metni söyler. Hata olursa [yedek] çağrılır (telefonun sesiyle söylemek için).
     * [bitince] ses bitince ana iş parçacığında çağrılır.
     */
    fun soyle(metin: String, temizle: Boolean, bitince: (() -> Unit)?, yedek: (String, (() -> Unit)?) -> Unit) {
        if (temizle) sus()
        val n = nesil.get()
        sirada.incrementAndGet(); _konusuyor.value = true
        val dosya = onbellekDosyasi(metin)
        val p = Parca()
        if (!dosya.exists()) {
            indirIsci.execute {
                if (n != nesil.get()) { p.kuyruk.put(ByteArray(0)); return@execute }
                val kisa = metin.length <= 60
                val kayit = if (kisa) ByteArrayOutputStream() else null
                try {
                    istek(metin).use { giris ->
                        val tampon = ByteArray(8192)
                        while (true) {
                            if (n != nesil.get()) break
                            val k = giris.read(tampon)
                            if (k < 0) break
                            if (k == 0) continue
                            p.kuyruk.put(tampon.copyOf(k))
                            kayit?.write(tampon, 0, k)
                        }
                    }
                    if (kayit != null && n == nesil.get()) onbellegeYaz(metin, kayit.toByteArray())
                } catch (e: Hata) {
                    p.hata = e.mesaj
                } catch (e: Exception) {
                    p.hata = "EVREN sesine ulaşılamadı."
                }
                p.kuyruk.put(ByteArray(0))
            }
        }
        calIsci.execute {
            if (n != nesil.get()) return@execute
            val giris: InputStream = if (dosya.exists()) FileInputStream(dosya).also { dosya.setLastModified(System.currentTimeMillis()) } else KuyrukAkisi(p, nesil, n)
            val calindi = try { giris.use { cal(it, n) } } catch (_: Exception) { false }
            if (n != nesil.get()) return@execute
            if (!calindi) {
                val h = p.hata ?: "EVREN sesi okunamadı."
                _sonHata.value = h
                dinlenmeBitis = System.currentTimeMillis() + 60_000
                main.post { if (n == nesil.get()) { bitir(n); yedek(metin, bitince) } }
            } else {
                _sonHata.value = null
                main.post { if (n == nesil.get()) { bitir(n); bitince?.invoke() } }
            }
        }
    }

    private fun bitir(n: Int) {
        if (n != nesil.get()) return
        if (sirada.decrementAndGet() <= 0) { sirada.set(0); _konusuyor.value = false }
    }

    private fun riffMi(b: ByteArray) = b.size >= 4 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte()

    private fun tamOku(g: InputStream, n: Int): ByteArray? {
        val b = ByteArray(n); var o = 0
        while (o < n) { val k = g.read(b, o, n - o); if (k < 0) return null; o += k }
        return b
    }

    private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, i: Int) = le16(b, i) or (le16(b, i + 2) shl 16)

    /** Sesi çalar; WAV ise akış hâlinde, değilse (mp3 vb.) dosyaya alıp MediaPlayer ile. */
    private fun cal(g: InputStream, n: Int): Boolean {
        val bas = tamOku(g, 12) ?: return false
        if (!riffMi(bas)) return digerBicim(bas, g, n)
        var oran = 24000; var kanal = 1; var bit = 16; var bicim = 1
        while (true) {
            val h = tamOku(g, 8) ?: return false
            val id = String(h, 0, 4, Charsets.US_ASCII)
            val boy = le32(h, 4)
            if (id == "fmt ") {
                val f = tamOku(g, boy) ?: return false
                bicim = le16(f, 0); kanal = le16(f, 2); oran = le32(f, 4); bit = le16(f, 14)
                if (boy % 2 == 1) g.read()
            } else if (id == "data") break
            else { if (boy < 0 || boy > 1_000_000) return false; tamOku(g, boy + (boy % 2)) ?: return false }
        }
        val kodlama = when {
            bicim == 3 && bit == 32 -> AudioFormat.ENCODING_PCM_FLOAT
            bit == 8 -> AudioFormat.ENCODING_PCM_8BIT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }
        val ornekBayt = maxOf(1, bit / 8) * kanal
        val t = izAl(oran, kanal, kodlama)
        try { t.playbackParams = PlaybackParams().setSpeed(hiz).setPitch(1f) } catch (_: Exception) {}
        t.flush(); t.play()
        var yazilan = 0L
        var ilk = true
        val tampon = ByteArray(8192)
        var artik = ByteArray(0)
        while (true) {
            if (n != nesil.get()) { t.pause(); t.flush(); return true }
            val k = g.read(tampon)
            if (k < 0) break
            if (k == 0) continue
            // Örnek sınırına hizala
            val veri = if (artik.isEmpty()) tampon.copyOf(k) else artik + tampon.copyOf(k)
            val tam = veri.size - (veri.size % ornekBayt)
            artik = veri.copyOfRange(tam, veri.size)
            if (tam == 0) continue
            if (ilk) { Olcum.ilkCikti(); ilk = false }
            t.write(ByteBuffer.wrap(veri, 0, tam), tam, AudioTrack.WRITE_BLOCKING)
            yazilan += tam / ornekBayt
        }
        if (yazilan == 0L) return false
        val kalanMs = ((yazilan - t.playbackHeadPosition).coerceAtLeast(0) * 1000L / oran / hiz).toLong()
        val son = System.currentTimeMillis() + kalanMs + 1500
        while (t.playbackHeadPosition < yazilan && System.currentTimeMillis() < son) {
            if (n != nesil.get()) { t.pause(); t.flush(); return true }
            Thread.sleep(15)
        }
        t.stop()
        return true
    }

    private fun digerBicim(bas: ByteArray, g: InputStream, n: Int): Boolean {
        val f = File(ctx.cacheDir, "evren_ses.tmp")
        f.outputStream().use { o -> o.write(bas); g.copyTo(o) }
        if (n != nesil.get() || f.length() < 64) return n != nesil.get()
        val bitti = java.util.concurrent.CountDownLatch(1)
        var basarili = true
        main.post {
            try {
                val mp = MediaPlayer()
                oynatici = mp
                mp.setAudioAttributes(nitelik())
                mp.setDataSource(f.path)
                mp.setOnCompletionListener { bitti.countDown() }
                mp.setOnErrorListener { _, _, _ -> basarili = false; bitti.countDown(); true }
                mp.prepare()
                try { mp.playbackParams = PlaybackParams().setSpeed(hiz).setPitch(1f) } catch (_: Exception) {}
                Olcum.ilkCikti()
                mp.start()
            } catch (_: Exception) { basarili = false; bitti.countDown() }
        }
        while (!bitti.await(100, TimeUnit.MILLISECONDS)) {
            if (n != nesil.get()) break
        }
        main.post { try { oynatici?.release() } catch (_: Exception) {}; oynatici = null }
        return basarili
    }

    private fun nitelik() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun izAl(oran: Int, kanal: Int, kodlama: Int): AudioTrack {
        val anahtar = "$oran/$kanal/$kodlama"
        iz?.let { if (izAnahtar == anahtar) return it; try { it.release() } catch (_: Exception) {} }
        val maske = if (kanal == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val min = AudioTrack.getMinBufferSize(oran, maske, kodlama)
        val t = AudioTrack.Builder()
            .setAudioAttributes(nitelik())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(oran).setEncoding(kodlama).setChannelMask(maske).build())
            .setBufferSizeInBytes(maxOf(min * 2, oran / 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        iz = t; izAnahtar = anahtar
        return t
    }

    fun sus() {
        nesil.incrementAndGet()
        sirada.set(0)
        _konusuyor.value = false
        try { iz?.pause(); iz?.flush() } catch (_: Exception) {}
        main.post { try { oynatici?.stop() } catch (_: Exception) {} }
    }

    fun onbellegiSil() {
        onbellek.listFiles()?.forEach { it.delete() }
        _onbellekSayisi.value = 0
    }

    companion object {
        val SESLER = listOf("kaan", "emre", "deniz", "ece")
        const val TARIF = "A mature, calm and warm Turkish male voice; wise, trustworthy and fluent, speaking at a natural, unhurried but lively pace."
        val SIK_CUMLELER = listOf(
            "Buyur.", "Buyur, dinliyorum.", "Peki.", "Tamam.", "Evet mi, hayır mı?",
            "Kimi arayayım?", "Kime yazayım?", "Ne yazayım?", "İptal ettim.",
            "Selâm, ben Cezerî. Emrine hazırım.", "Her şey hazır. Mikrofona dokunup benimle konuşabilirsin.",
        )
    }
}
