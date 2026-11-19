package com.erkam.cezeri.asistan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.erkam.cezeri.MainActivity

/** Cezerî'nin "varsayılan dijital asistan" olabilmesi için gereken sistem servisleri (yan tuş, kilit ekranı). */
class EtkilesimServisi : VoiceInteractionService()

class OturumServisi : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = Oturum(this)
}

class Oturum(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val i = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_ASSIST)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startAssistantActivity(i)
        } catch (e: Exception) {
            try { context.startActivity(i) } catch (_: Exception) {}
        }
        hide()
    }
}

/** Sistem bir tanıma servisi şart koşuyor; Cezerî kendi tanımasını içeride yaptığı için bu servis boş. */
class TanimaServisi : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try { listener?.error(SpeechRecognizer.ERROR_CLIENT) } catch (_: Exception) {}
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
