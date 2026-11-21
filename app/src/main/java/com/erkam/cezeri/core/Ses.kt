package com.erkam.cezeri.core

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import com.erkam.cezeri.ses.CezeriSesi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Cezerî'nin ağzı. Önce Cezerî'nin kendi sesi (Supertonic 3, telefonda) kullanılır;
 * o hazır değilse ya da hata verirse telefonun Türkçe sesi devreye girer.
 */
class Ses(context: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private val ids = AtomicInteger(0)
    private val bitince = ConcurrentHashMap<String, () -> Unit>()
    private val bekleyen = ArrayList<Pair<String, (() -> Unit)?>>()
    @Volatile private var hazir = false
    @Volatile private var sonId: String? = null

    private val _androidKonusuyor = MutableStateFlow(false)
    private val _konusuyor = MutableStateFlow(false)
    val konusuyor: StateFlow<Boolean> = _konusuyor

    private val prefs = context.applicationContext.getSharedPreferences("ses", Context.MODE_PRIVATE)
    val cezeri = CezeriSesi(context.applicationContext)
    private val _motor = MutableStateFlow(prefs.getString("motor", MOTOR_TELEFON) ?: MOTOR_TELEFON)
    /** "cezeri" ya da "telefon" */
    val motor: StateFlow<String> = _motor
    private val _stil = MutableStateFlow(prefs.getString("stil", "M1") ?: "M1")
    val stil: StateFlow<String> = _stil

    init {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            combine(_androidKonusuyor, cezeri.konusuyor) { a, b -> a || b }.collect { _konusuyor.value = it }
        }
        if (cezeri.tamamMi()) cezeri.hazirla(_stil.value)
    }

    private fun cezeriyle(): Boolean = _motor.value == MOTOR_CEZERI && cezeri.kullanilabilir()

    fun motorSec(m: String) {
        _motor.value = m; prefs.edit().putString("motor", m).apply()
        if (m == MOTOR_CEZERI && !cezeri.tamamMi()) cezeri.indir()
    }

    fun stilSec(s: String) {
        _stil.value = s; prefs.edit().putString("stil", s).apply()
        if (cezeri.tamamMi()) cezeri.hazirla(s)
    }

    fun sesIndir() = cezeri.indir()

    fun dene(s: String) {
        if (s != _stil.value) stilSec(s)
        konus("Selâm, ben Cezerî. Emrine hazırım; ara, yaz, hatırlat, sor.")
    }

    private var telefonSesi: String? = null
    val secilenSes: String?
        get() = if (cezeriyle()) "Cezerî sesi · ${_stil.value}" else telefonSesi

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Turkce.TR
        sesSec()
        tts.setSpeechRate(0.96f)
        tts.setPitch(0.92f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _androidKonusuyor.value = true; Olcum.ilkCikti() }
            override fun onDone(utteranceId: String?) { bitti(utteranceId) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { bitti(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { bitti(utteranceId) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { bitti(utteranceId) }
        })
        hazir = true
        val kopya = synchronized(bekleyen) { bekleyen.toList().also { bekleyen.clear() } }
        kopya.forEachIndexed { i, (m, cb) -> konus(m, temizle = i == 0, bitince = cb) }
    }

    private fun bitti(id: String?) {
        if (id == null) return
        if (id == sonId) _androidKonusuyor.value = false
        bitince.remove(id)?.let { cb -> main.post { cb() } }
    }

    private fun sesSec() {
        try {
            val voices: Set<Voice> = tts.voices ?: return
            val tr = voices.filter { it.locale.language == "tr" && !it.isNetworkConnectionRequired && Voice.QUALITY_NORMAL <= it.quality }
            if (tr.isEmpty()) return
            fun erkekMi(v: Voice): Boolean {
                val n = v.name.lowercase()
                val feats = v.features?.joinToString(" ")?.lowercase() ?: ""
                return ("male" in n && "female" !in n) || ("male" in feats && "female" !in feats) ||
                    Regex("(-|_)m\\d|smtm|#male|trm|tr-tr-x-.*-(local|network)?.*m").containsMatchIn(n)
            }
            val secim = tr.filter { erkekMi(it) }.maxByOrNull { it.quality } ?: tr.maxByOrNull { it.quality }
            secim?.let { tts.voice = it; telefonSesi = it.name }
        } catch (_: Exception) {
        }
    }

    /** Bir cümleyi söyler. temizle=true ise o an söylenenleri keser. */
    fun konus(metin: String, temizle: Boolean = true, bitince: (() -> Unit)? = null) {
        val m = metin.trim()
        if (m.isEmpty()) { bitince?.let { main.post(it) }; return }
        Olcum.seseVerildi()
        if (cezeriyle()) {
            if (temizle && hazir) { this.bitince.clear(); tts.stop(); _androidKonusuyor.value = false }
            cezeri.soyle(m, temizle, bitince) { yedekMetin, yedekBitince -> telefonla(yedekMetin, false, yedekBitince) }
            return
        }
        if (temizle) cezeri.sus()
        telefonla(m, temizle, bitince)
    }

    private fun telefonla(m: String, temizle: Boolean, bitince: (() -> Unit)?) {
        if (!hazir) {
            synchronized(bekleyen) { if (temizle) bekleyen.clear(); bekleyen.add(m to bitince) }
            return
        }
        val id = "cz-" + ids.incrementAndGet()
        if (bitince != null) this.bitince[id] = bitince
        sonId = id
        _androidKonusuyor.value = true
        tts.speak(m, if (temizle) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
    }

    /** Sırada bekleyen konuşmaların arkasına ekler (akış hâlinde gelen sohbet cevapları için). */
    fun ekle(metin: String, bitince: (() -> Unit)? = null) = konus(metin, temizle = false, bitince = bitince)

    fun sus() {
        bitince.clear()
        synchronized(bekleyen) { bekleyen.clear() }
        if (hazir) tts.stop()
        _androidKonusuyor.value = false
        cezeri.sus()
    }

    fun kapat() { tts.shutdown() }

    companion object {
        const val MOTOR_CEZERI = "cezeri"
        const val MOTOR_TELEFON = "telefon"
    }
}
