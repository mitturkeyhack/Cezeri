package com.erkam.cezeri.ses;

import ai.onnxruntime.OrtEnvironment;

import java.util.Collections;

/** Supertonic çıkarım sınıflarına Kotlin tarafından tek kapı. Tek iş parçacığından çağrılmalı. */
public final class Motor {
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private TextToSpeech tts;
    private Style stil;
    private String stilYolu;

    public synchronized void yukle(String klasor) throws Exception {
        if (tts == null) tts = Helper.loadTextToSpeech(klasor, false, env);
    }

    public synchronized boolean hazir() { return tts != null && stil != null; }

    public synchronized void stil(String yol) throws Exception {
        if (yol.equals(stilYolu) && stil != null) return;
        Style yeni = Helper.loadVoiceStyle(Collections.singletonList(yol), false, env);
        if (stil != null) try { stil.close(); } catch (Exception ignored) {}
        stil = yeni;
        stilYolu = yol;
    }

    public synchronized int ornekleme() { return tts == null ? 44100 : tts.sampleRate; }

    /** Türkçe metni sese çevirir; mono float PCM döner. */
    public synchronized float[] sentezle(String metin, float hiz, int adim) throws Exception {
        if (tts == null || stil == null) throw new IllegalStateException("ses motoru hazır değil");
        TTSResult r = tts.call(metin, "tr", stil, adim, hiz, 0.18f, env);
        return r.wav;
    }

    public synchronized void kapat() {
        try { if (stil != null) stil.close(); } catch (Exception ignored) {}
        try { if (tts != null) tts.close(); } catch (Exception ignored) {}
        stil = null; tts = null; stilYolu = null;
    }
}
