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

/**
 * Cezerî'nin ağzı. Aşama 1'de telefonun Türkçe ses motorunu kullanır (erkek ses tercih edilir).
 * Aşama 1b'de bu sınıfın içi Cezerî'ye özel sesle (Supertonic) değişecek; dışarıdan kullanım aynı kalacak.
 */
class Ses(context: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private val ids = AtomicInteger(0)
    private val bitince = ConcurrentHashMap<String, () -> Unit>()
    private val bekleyen = ArrayList<Pair<String, (() -> Unit)?>>()
    @Volatile private var hazir = false
    @Volatile private var sonId: String? = null

    private val _konusuyor = MutableStateFlow(false)
    val konusuyor: StateFlow<Boolean> = _konusuyor

    var secilenSes: String? = null
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Turkce.TR
        sesSec()
        tts.setSpeechRate(0.96f)
        tts.setPitch(0.92f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _konusuyor.value = true }
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
        if (id == sonId) _konusuyor.value = false
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
            secim?.let { tts.voice = it; secilenSes = it.name }
        } catch (_: Exception) {
        }
    }

    /** Bir cümleyi söyler. temizle=true ise o an söylenenleri keser. */
    fun konus(metin: String, temizle: Boolean = true, bitince: (() -> Unit)? = null) {
        val m = metin.trim()
        if (m.isEmpty()) { bitince?.let { main.post(it) }; return }
        if (!hazir) {
            synchronized(bekleyen) { if (temizle) bekleyen.clear(); bekleyen.add(m to bitince) }
            return
        }
        val id = "cz-" + ids.incrementAndGet()
        if (bitince != null) this.bitince[id] = bitince
        sonId = id
        _konusuyor.value = true
        tts.speak(m, if (temizle) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
    }

    /** Sırada bekleyen konuşmaların arkasına ekler (akış hâlinde gelen sohbet cevapları için). */
    fun ekle(metin: String, bitince: (() -> Unit)? = null) = konus(metin, temizle = false, bitince = bitince)

    fun sus() {
        bitince.clear()
        synchronized(bekleyen) { bekleyen.clear() }
        if (hazir) tts.stop()
        _konusuyor.value = false
    }

    fun kapat() { tts.shutdown() }
}
