package com.erkam.cezeri.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.sqrt

/**
 * "Hey Cezerî" uyanma kelimesi + konuşmacı (ses kimliği) doğrulaması.
 * Tamamen telefonda: Vosk küçük Türkçe model (~35 MB) ve konuşmacı modeli (~13 MB). Bir kez indirilir.
 */
class Uyanma(private val ctx: Context) {

    sealed interface ModelDurumu {
        data object Yok : ModelDurumu
        data class Iniyor(val yuzde: Int) : ModelDurumu
        data object Hazir : ModelDurumu
        data class Hata(val mesaj: String) : ModelDurumu
    }

    private val kok = File(ctx.filesDir, "vosk").apply { mkdirs() }
    private val trDir = File(kok, "vosk-model-small-tr-0.3")
    private val spkDir = File(kok, "vosk-model-spk-0.4")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val kilit = Mutex()
    private val prefs = ctx.getSharedPreferences("uyanma", Context.MODE_PRIVATE)

    private val _model = MutableStateFlow<ModelDurumu>(if (modellerVar()) ModelDurumu.Hazir else ModelDurumu.Yok)
    val modelDurumu: StateFlow<ModelDurumu> = _model

    private var model: Model? = null
    private var spk: SpeakerModel? = null
    private var servis: SpeechService? = null
    private var tanici: Recognizer? = null

    private val _dinliyor = MutableStateFlow(false)
    val dinliyor: StateFlow<Boolean> = _dinliyor

    /** Ayarlar'daki canlı uyanma testi için: ne duydu, puanlar, karar */
    data class Teshis(
        val duyulan: String = "",
        val kelimePuani: Double = 0.0,
        val kelimeEsigi: Double = 0.0,
        val sesPuani: Double? = null,
        val sesEsigi: Double? = null,
        val karar: String = "",
        val hata: String? = null,
        val zaman: Long = 0L,
    )
    private val _teshis = MutableStateFlow(Teshis())
    val teshis: StateFlow<Teshis> = _teshis
    @Volatile var servisCalisiyor: Boolean = false

    init {
        try { LibVosk.setLogLevel(LogLevel.WARNINGS) } catch (_: Throwable) {}
    }

    fun modellerVar(): Boolean = dolu(trDir) && dolu(spkDir)

    private fun dolu(d: File): Boolean = (d.listFiles()?.size ?: 0) >= 2

    // ------------------------------------------------------------ İndirme

    fun indir() {
        if (modellerVar()) { _model.value = ModelDurumu.Hazir; return }
        if (_model.value is ModelDurumu.Iniyor) return
        scope.launch {
            try {
                _model.value = ModelDurumu.Iniyor(0)
                if (!dolu(trDir)) zipIndir(TR_URLS, 0, 75)
                if (!dolu(spkDir)) zipIndir(SPK_URLS, 75, 25)
                _model.value = if (modellerVar()) ModelDurumu.Hazir else ModelDurumu.Hata("Dosyalar eksik indi.")
            } catch (e: Exception) {
                _model.value = ModelDurumu.Hata("İndirilemedi. İnternet bağlantını kontrol edip tekrar dene.")
            }
        }
    }

    private fun zipIndir(urls: List<String>, baslangic: Int, pay: Int) {
        var son: Exception? = null
        for (u in urls) {
            try {
                val tmp = File(ctx.cacheDir, "vosk.zip")
                var url = URL(u)
                var conn: HttpURLConnection
                var yonlendirme = 0
                while (true) {
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20000; conn.readTimeout = 30000
                    val kod = conn.responseCode
                    if (kod in 300..399 && yonlendirme < 6) {
                        url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); yonlendirme++; continue
                    }
                    if (kod != 200) throw IllegalStateException("HTTP $kod")
                    break
                }
                val toplam = conn.contentLengthLong.takeIf { it > 0 } ?: 40_000_000L
                conn.inputStream.use { inp ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var inen = 0L
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); inen += n
                            _model.value = ModelDurumu.Iniyor(baslangic + ((inen * pay) / toplam).toInt().coerceAtMost(pay))
                        }
                    }
                }
                ZipInputStream(tmp.inputStream().buffered()).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        val hedef = File(kok, e.name)
                        if (!hedef.canonicalPath.startsWith(kok.canonicalPath)) continue
                        if (e.isDirectory) hedef.mkdirs() else {
                            hedef.parentFile?.mkdirs()
                            FileOutputStream(hedef).use { zin.copyTo(it) }
                        }
                    }
                }
                tmp.delete()
                return
            } catch (e: Exception) {
                son = e
            }
        }
        throw son ?: IllegalStateException("indirilemedi")
    }

    // ------------------------------------------------------------ Modelleri yükle

    private suspend fun yukle(): Boolean = withContext(Dispatchers.IO) {
        kilit.withLock {
            if (model != null && spk != null) return@withLock true
            if (!modellerVar()) return@withLock false
            try {
                if (model == null) model = Model(trDir.absolutePath)
                if (spk == null) spk = SpeakerModel(spkDir.absolutePath)
                true
            } catch (e: Throwable) {
                Log.e("Cezeri", "Vosk yüklenemedi", e)
                _teshis.value = _teshis.value.copy(hata = "Uyanma modeli açılamadı: ${e.javaClass.simpleName} ${e.message ?: ""}".trim(), zaman = System.currentTimeMillis())
                false
            }
        }
    }

    // ------------------------------------------------------------ Ses kimliği

    fun kayitliMi(): Boolean = prefs.getString("merkez", null) != null
    fun ornekSayisi(): Int = prefs.getInt("ornek", 0)

    var hassasiyet: Float
        get() = prefs.getFloat("hassasiyet", 0.5f)
        set(v) { prefs.edit().putFloat("hassasiyet", v.coerceIn(0f, 1f)).apply() }

    var acik: Boolean
        get() = prefs.getBoolean("acik", false)
        set(v) { prefs.edit().putBoolean("acik", v).apply() }

    /** Kayıt ekranındayken arka plan dinlemesi durur */
    @Volatile var duraklatildi: Boolean = false

    fun modelleriSil() {
        durdur()
        try { model?.close() } catch (_: Throwable) {}
        try { spk?.close() } catch (_: Throwable) {}
        model = null; spk = null
        kok.deleteRecursively(); kok.mkdirs()
        _model.value = ModelDurumu.Yok
    }

    fun kimligiSil() { prefs.edit().remove("merkez").remove("esik").remove("ornek").apply() }

    fun kimligiKaydet(vektorler: List<DoubleArray>) {
        if (vektorler.isEmpty()) return
        val n = vektorler[0].size
        val merkez = DoubleArray(n)
        for (v in vektorler) for (i in 0 until n) merkez[i] += v[i] / vektorler.size
        val benzerlikler = vektorler.map { kosinus(it, merkez) }
        // Kayıtlardaki en düşük benzerliğin biraz altı eşik olur
        val esik = (benzerlikler.minOrNull() ?: 0.5) - 0.18
        prefs.edit()
            .putString("merkez", merkez.joinToString(","))
            .putFloat("esik", esik.toFloat().coerceIn(0.10f, 0.80f))
            .putInt("ornek", vektorler.size)
            .apply()
    }

    private fun merkez(): DoubleArray? = prefs.getString("merkez", null)?.split(',')?.map { it.toDouble() }?.toDoubleArray()

    /** Konuşan kişi sahibi mi? Kayıt yoksa herkes kabul. */
    fun sesEsigi(): Double = (prefs.getFloat("esik", 0.40f) + (hassasiyet - 0.5f) * 0.3f).toDouble()

    fun sesPuani(v: DoubleArray?): Double? {
        val m = merkez() ?: return null
        if (v == null) return 0.0
        return kosinus(v, m)
    }

    fun sahibiMi(v: DoubleArray?): Boolean {
        val p = sesPuani(v) ?: return true
        return p >= sesEsigi()
    }

    private fun kosinus(a: DoubleArray, b: DoubleArray): Double {
        var d = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0.0 else d / (sqrt(na) * sqrt(nb))
    }

    // ------------------------------------------------------------ Dinleme

    /** Sürekli dinler; "Hey Cezerî" (sahibinin sesiyle) duyulunca uyan() çağrılır. */
    fun baslat(uyan: () -> Unit) {
        scope.launch {
            if (!yukle()) return@launch
            withContext(Dispatchers.Main) { dinlemeyeBasla(kayit = null, uyan = uyan) }
        }
    }

    /** Kayıt için tek bir söyleyiş alır, ses kimliği vektörünü döndürür. */
    fun ornekAl(sonuc: (DoubleArray?, String) -> Unit) {
        scope.launch {
            if (!yukle()) { withContext(Dispatchers.Main) { sonuc(null, "") }; return@launch }
            withContext(Dispatchers.Main) { dinlemeyeBasla(kayit = sonuc, uyan = null) }
        }
    }

    private fun dinlemeyeBasla(kayit: ((DoubleArray?, String) -> Unit)?, uyan: (() -> Unit)?) {
        durdur()
        val m = model ?: return
        val s = spk ?: return
        try {
            val r = Recognizer(m, 16000.0f, s)
            tanici = r
            val sv = SpeechService(r, 16000.0f)
            servis = sv
            var bitti = false
            sv.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (kayit != null || bitti) return
                    val metin = metinAl(hypothesis, "partial")
                    if (metin.isBlank()) return
                    _teshis.value = _teshis.value.copy(duyulan = metin, hata = null, zaman = System.currentTimeMillis())
                    // Ses kimliği kaydı yoksa kısmi sonuçta hemen uyan (daha hızlı)
                    if (!kayitliMi() && uyanmaKelimesiVar(metin)) {
                        _teshis.value = _teshis.value.copy(kelimePuani = kelimePuani(metin), kelimeEsigi = kelimeEsigi(), karar = "Uyandı")
                        bitti = true; durdur(); uyan?.invoke()
                    }
                }

                override fun onResult(hypothesis: String?) {
                    if (bitti) return
                    val metin = metinAl(hypothesis, "text")
                    val v = vektorAl(hypothesis)
                    if (kayit != null) {
                        if (metin.isBlank() && v == null) return
                        bitti = true; durdur(); kayit(v, metin); return
                    }
                    if (metin.isBlank()) return
                    val kp = kelimePuani(metin)
                    val ke = kelimeEsigi()
                    val sp = sesPuani(v)
                    val se = if (sp == null) null else sesEsigi()
                    val kelimeTamam = uyanmaKelimesiVar(metin)
                    val sesTamam = sahibiMi(v)
                    _teshis.value = Teshis(
                        duyulan = metin, kelimePuani = kp, kelimeEsigi = ke, sesPuani = sp, sesEsigi = se,
                        karar = when {
                            kelimeTamam && sesTamam -> "Uyandı"
                            !kelimeTamam -> "Kelime “Cezerî”ye benzemedi"
                            else -> "Ses kimliği tutmadı"
                        },
                        zaman = System.currentTimeMillis(),
                    )
                    if (kelimeTamam && sesTamam) { bitti = true; durdur(); uyan?.invoke() }
                }

                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) { durdur(); if (kayit != null && !bitti) { bitti = true; kayit(null, "") } }
                override fun onTimeout() { durdur() }
            })
            _dinliyor.value = true
        } catch (e: Exception) {
            Log.e("Cezeri", "Uyanma dinlemesi başlatılamadı", e)
            _teshis.value = _teshis.value.copy(hata = "Mikrofon açılamadı: ${e.message ?: e.javaClass.simpleName}", zaman = System.currentTimeMillis())
            durdur()
            kayit?.invoke(null, "")
        }
    }

    fun durdur() {
        try { servis?.stop() } catch (_: Throwable) {}
        try { servis?.shutdown() } catch (_: Throwable) {}
        servis = null
        try { tanici?.close() } catch (_: Throwable) {}
        tanici = null
        _dinliyor.value = false
    }

    private fun metinAl(json: String?, alan: String): String = try {
        JSONObject(json ?: "{}").optString(alan, "")
    } catch (_: Exception) { "" }

    private fun vektorAl(json: String?): DoubleArray? = try {
        val o = JSONObject(json ?: "{}")
        val a = o.optJSONArray("spk")
        if (a == null || o.optInt("spk_frames", 0) < 25) null
        else DoubleArray(a.length()) { a.getDouble(it) }
    } catch (_: Exception) { null }

    /** Küçük Türkçe model "Cezerî"yi bilmediği için ona en yakın duyulan kelimeleri esnek eşleştiririz. */
    fun kelimeEsigi(): Double = 0.66 + (hassasiyet - 0.5) * 0.16

    /** Duyulan cümlede "Cezerî"ye en çok benzeyen kelimenin (ya da iki kelimenin birleşiminin) puanı */
    fun kelimePuani(metin: String): Double {
        val t = Turkce.norm(metin)
        if (t.isBlank()) return 0.0
        val k = t.split(' ').filter { it.isNotBlank() }
        val adaylar = ArrayList<String>()
        for (i in k.indices) {
            adaylar.add(k[i])
            if (i + 1 < k.size) adaylar.add(k[i] + k[i + 1])
            if (i + 2 < k.size) adaylar.add(k[i] + k[i + 1] + k[i + 2])
        }
        return adaylar.maxOfOrNull { w -> HEDEFLER.maxOf { h -> Turkce.similarity(w, h) } } ?: 0.0
    }

    fun uyanmaKelimesiVar(metin: String): Boolean {
        val k = Turkce.norm(metin).split(' ').filter { it.isNotBlank() }
        if (k.isEmpty() || k.size > 8) return false
        return kelimePuani(metin) >= kelimeEsigi()
    }

    companion object {
        private val HEDEFLER = listOf("cezeri", "cezerim", "gezeri", "cezari", "ceziri", "cezire", "cezere", "heycezeri", "eycezeri", "hecezeri")
        val TR_URLS = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
            "https://huggingface.co/rhasspy/vosk-models/resolve/main/tr/vosk-model-small-tr-0.3.zip",
        )
        val SPK_URLS = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip",
            "https://huggingface.co/mychen76/vosk-models/resolve/main/speaker_id/vosk-model-spk-0.4.zip",
        )
    }
}
