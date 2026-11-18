package com.erkam.cezeri.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cezerî'nin kulağı: telefonun kendi (mümkünse cihaz içi) Türkçe ses tanıması. Ana iş parçacığında kullanılmalı. */
class Kulak(private val ctx: Context) {

    sealed interface Sonuc {
        data class Metin(val en: String, val alternatifler: List<String>) : Sonuc
        data object Sessiz : Sonuc
        data class Hata(val kod: Int) : Sonuc
    }

    private var rec: SpeechRecognizer? = null
    private var cihazIciDenendi = false
    private var cihazIciCalismiyor = false

    private val _ara = MutableStateFlow("")
    val araMetin: StateFlow<String> = _ara
    private val _seviye = MutableStateFlow(0f)
    val seviye: StateFlow<Float> = _seviye
    private val _dinliyor = MutableStateFlow(false)
    val dinliyor: StateFlow<Boolean> = _dinliyor

    fun mevcut(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx) ||
        SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)

    fun dinle(sonuc: (Sonuc) -> Unit) {
        birak()
        _ara.value = ""
        val cihazIci = !cihazIciCalismiyor && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        val r = try {
            if (cihazIci) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else SpeechRecognizer.createSpeechRecognizer(ctx)
        } catch (e: Exception) {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        rec = r
        var bitti = false
        fun bitir(s: Sonuc) {
            if (bitti) return
            bitti = true
            _dinliyor.value = false
            _seviye.value = 0f
            sonuc(s)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _dinliyor.value = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { _seviye.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Cihaz içi tanıma Türkçe desteklemiyorsa bir kez normal tanımaya geç
                if (cihazIci && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
                    cihazIciCalismiyor = true
                    if (!cihazIciDenendi) {
                        cihazIciDenendi = true
                        bitti = true
                        dinle(sonuc)
                        return
                    }
                }
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> bitir(Sonuc.Sessiz)
                    else -> bitir(Sonuc.Hata(error))
                }
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (list.isEmpty() || list[0].isBlank()) bitir(Sonuc.Sessiz)
                else bitir(Sonuc.Metin(list[0], list))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (list.isNotEmpty() && list[0].isNotBlank()) _ara.value = list[0]
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        _dinliyor.value = true
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            bitir(Sonuc.Hata(-1))
        }
    }

    fun durdur() { rec?.stopListening() }

    fun birak() {
        try { rec?.cancel(); rec?.destroy() } catch (_: Exception) {}
        rec = null
        _dinliyor.value = false
        _seviye.value = 0f
    }
}
