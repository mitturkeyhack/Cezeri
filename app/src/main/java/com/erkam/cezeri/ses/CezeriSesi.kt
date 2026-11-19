package com.erkam.cezeri.ses

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Cezerî'nin kendi sesi: Supertonic 3 (telefonda, ONNX Runtime ile).
 * Cümleler sırayla sentezlenir; biri çalarken bir sonraki hazırlanır.
 */
class CezeriSesi(private val ctx: Context) {

    sealed interface Durum {
        data object Yok : Durum
        data class Iniyor(val inen: Long, val toplam: Long) : Durum
        data object Yukleniyor : Durum
        data object Hazir : Durum
        data class Hata(val mesaj: String) : Durum
    }

    private val klasor = File(ctx.filesDir, "supertonic").apply { mkdirs() }
    private val onnx = File(klasor, "onnx").apply { mkdirs() }
    private val stiller = File(klasor, "voice_styles").apply { mkdirs() }
    private val motor = Motor()
    private val sentezIsci = Executors.newSingleThreadExecutor { Thread(it, "cezeri-sentez").apply { priority = Thread.MAX_PRIORITY - 1 } }
    private val calIsci = Executors.newSingleThreadExecutor { Thread(it, "cezeri-cal").apply { priority = Thread.MAX_PRIORITY } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var indirme: Job? = null

    private val nesil = AtomicInteger(0)
    private val sirada = AtomicInteger(0)
    @Volatile private var iz: AudioTrack? = null
    @Volatile private var izOrnek = 0

    private val _durum = MutableStateFlow<Durum>(if (tamamMi()) Durum.Yukleniyor else Durum.Yok)
    val durum: StateFlow<Durum> = _durum
    private val _konusuyor = MutableStateFlow(false)
    val konusuyor: StateFlow<Boolean> = _konusuyor
    @Volatile var stil: String = "M1"; private set

    fun tamamMi(): Boolean = DOSYALAR.all { File(klasor, it).let { f -> f.exists() && f.length() > 0 } }

    fun kullanilabilir(): Boolean = _durum.value is Durum.Hazir || _durum.value is Durum.Yukleniyor

    // ---------- indirme ----------

    fun indir(sonra: (() -> Unit)? = null) {
        if (tamamMi()) { hazirla(stil); sonra?.invoke(); return }
        if (indirme?.isActive == true) return
        indirme = scope.launch {
            try {
                val boyutlar = DOSYALAR.associateWith { boyutSor(it) }
                val toplam = boyutlar.values.sum().coerceAtLeast(1L)
                var biten = 0L
                for (ad in DOSYALAR) {
                    val hedef = File(klasor, ad)
                    if (hedef.exists() && hedef.length() > 0) { biten += hedef.length(); continue }
                    indirDosya(ad, hedef) { inen -> _durum.value = Durum.Iniyor(biten + inen, toplam) }
                    biten += hedef.length()
                    _durum.value = Durum.Iniyor(biten, toplam)
                    if (!isActive) return@launch
                }
                hazirla(stil)
                sonra?.let { main.post(it) }
            } catch (e: Exception) {
                _durum.value = Durum.Hata("Ses indirilemedi. İnternet bağlantını kontrol edip tekrar dene.")
            }
        }
    }

    private fun baglan(url: String, aralik: Long = 0): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000; readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Cezeri/1.0")
                if (aralik > 0) setRequestProperty("Range", "bytes=$aralik-")
            }
            val kod = c.responseCode
            if (kod in 300..399) {
                val yer = c.getHeaderField("Location") ?: throw IllegalStateException("yönlendirme yok")
                c.disconnect(); u = URL(u, yer); return@repeat
            }
            return c
        }
        throw IllegalStateException("çok fazla yönlendirme")
    }

    private fun boyutSor(ad: String): Long {
        val yerel = File(klasor, ad)
        if (yerel.exists() && yerel.length() > 0) return yerel.length()
        for (taban in TABANLAR) {
            try {
                val c = baglan(taban + ad)
                val n = c.contentLengthLong
                c.disconnect()
                if (n > 0) return n
            } catch (_: Exception) { }
        }
        return 0L
    }

    private fun indirDosya(ad: String, hedef: File, ilerleme: (Long) -> Unit) {
        val parca = File(hedef.path + ".part")
        hedef.parentFile?.mkdirs()
        var son: Exception? = null
        for (taban in TABANLAR) {
            try {
                val bas = if (parca.exists()) parca.length() else 0L
                val c = baglan(taban + ad, bas)
                val kod = c.responseCode
                if (kod !in 200..299) { c.disconnect(); throw IllegalStateException("HTTP $kod") }
                val devam = kod == 206 && bas > 0
                if (!devam) parca.delete()
                var inen = if (devam) bas else 0L
                c.inputStream.use { giris ->
                    FileOutputStream(parca, devam).use { cikis ->
                        val tampon = ByteArray(1 shl 16)
                        var sonBildirim = 0L
                        while (true) {
                            val n = giris.read(tampon)
                            if (n < 0) break
                            cikis.write(tampon, 0, n)
                            inen += n
                            if (inen - sonBildirim > 512 * 1024) { ilerleme(inen); sonBildirim = inen }
                        }
                    }
                }
                c.disconnect()
                if (parca.length() <= 0) throw IllegalStateException("boş dosya")
                hedef.delete(); parca.renameTo(hedef)
                return
            } catch (e: Exception) { son = e }
        }
        throw son ?: IllegalStateException("indirilemedi")
    }

    fun sil() {
        indirme?.cancel()
        sus()
        sentezIsci.execute { motor.kapat() }
        klasor.deleteRecursively(); klasor.mkdirs(); onnx.mkdirs(); stiller.mkdirs()
        _durum.value = Durum.Yok
    }

    // ---------- yükleme ----------

    /** Modeli (bir kez) yükler ve ses stilini seçer. Sentez kuyruğu bunu bekler. */
    fun hazirla(yeniStil: String) {
        if (!tamamMi()) { _durum.value = Durum.Yok; return }
        stil = yeniStil
        if (_durum.value !is Durum.Hazir) _durum.value = Durum.Yukleniyor
        sentezIsci.execute {
            try {
                motor.yukle(onnx.path)
                motor.stil(File(stiller, "$yeniStil.json").path)
                _durum.value = Durum.Hazir
            } catch (e: Throwable) {
                _durum.value = Durum.Hata("Ses motoru açılamadı.")
            }
        }
    }

    // ---------- konuşma ----------

    /**
     * Metni söyler. Hata olursa [yedek] çağrılır (telefonun sesiyle söylemek için).
     * [bitince] ses tamamen çalınınca ana iş parçacığında çağrılır.
     */
    fun soyle(metin: String, temizle: Boolean, bitince: (() -> Unit)?, yedek: (String, (() -> Unit)?) -> Unit) {
        if (temizle) sus()
        val n = nesil.get()
        val okunacak = SayiOku.hazirla(metin)
        if (okunacak.isBlank()) { bitince?.let { main.post(it) }; return }
        sirada.incrementAndGet(); _konusuyor.value = true
        sentezIsci.execute {
            if (n != nesil.get()) return@execute
            val wav = try {
                motor.sentezle(okunacak, HIZ, ADIM)
            } catch (e: Throwable) {
                null
            }
            if (n != nesil.get()) return@execute
            if (wav == null || wav.isEmpty()) {
                main.post { if (n == nesil.get()) { bitir(n); yedek(metin, bitince) } }
                return@execute
            }
            val oran = motor.ornekleme()
            calIsci.execute {
                if (n != nesil.get()) return@execute
                cal(wav, oran, n)
                if (n != nesil.get()) return@execute
                main.post { if (n == nesil.get()) { bitir(n); bitince?.invoke() } }
            }
        }
    }

    private fun bitir(n: Int) {
        if (n != nesil.get()) return
        if (sirada.decrementAndGet() <= 0) { sirada.set(0); _konusuyor.value = false }
    }

    private fun izAl(oran: Int): AudioTrack {
        iz?.let { if (izOrnek == oran) return it; it.release() }
        val min = AudioTrack.getMinBufferSize(oran, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(oran)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes((min * 2).coerceAtLeast(oran / 5 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        iz = t; izOrnek = oran
        return t
    }

    private fun cal(wav: FloatArray, oran: Int, n: Int) {
        // Ses seviyesini dengele (çok kısık ya da patlak çıkmasın).
        var tepe = 0f
        for (v in wav) { val a = abs(v); if (a > tepe) tepe = a }
        if (tepe > 0f) {
            val kazanc = (0.9f / tepe).coerceIn(0.5f, 3f)
            for (i in wav.indices) wav[i] *= kazanc
        }
        val t = izAl(oran)
        try {
            t.flush()
            t.play()
            var i = 0
            val parca = oran / 10
            while (i < wav.size) {
                if (n != nesil.get()) { t.pause(); t.flush(); return }
                val k = minOf(parca, wav.size - i)
                val yazilan = t.write(wav, i, k, AudioTrack.WRITE_BLOCKING)
                if (yazilan <= 0) break
                i += yazilan
            }
            // Son örnekler de çalınsın (mikrofon kuyruğu duymasın diye bitişi bekle).
            val bitis = System.currentTimeMillis() + 1500
            while (t.playbackHeadPosition < wav.size && System.currentTimeMillis() < bitis) {
                if (n != nesil.get()) { t.pause(); t.flush(); return }
                Thread.sleep(15)
            }
            t.stop()
        } catch (_: Exception) {
        }
    }

    fun sus() {
        nesil.incrementAndGet()
        sirada.set(0)
        _konusuyor.value = false
        try { iz?.pause(); iz?.flush() } catch (_: Exception) { }
    }

    companion object {
        private const val REV = "aafc6e32416a594460b32413efc49d7fe4ce6d46"
        private val TABANLAR = listOf(
            "https://huggingface.co/supertone-oss-archive/supertonic-3/resolve/$REV/",
            "https://huggingface.co/Supertone/supertonic-3/resolve/main/",
        )
        val STILLER = listOf("M1", "M2", "M3", "M4", "M5")
        private val DOSYALAR = listOf(
            "onnx/tts.json", "onnx/unicode_indexer.json",
            "onnx/duration_predictor.onnx", "onnx/text_encoder.onnx",
            "onnx/vector_estimator.onnx", "onnx/vocoder.onnx",
        ) + STILLER.map { "voice_styles/$it.json" }
        private const val HIZ = 1.0f
        private const val ADIM = 8
    }
}
