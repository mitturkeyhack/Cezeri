package com.erkam.cezeri.core

import android.content.Context

data class SonIs(val tur: String, val baslik: String, val alt: String, val zaman: Long)

/** Telefonda kalan küçük hafıza: kişi seçimleri, son işler, kurulum durumu. */
class Hafiza(ctx: Context) {
    private val p = ctx.getSharedPreferences("hafiza", Context.MODE_PRIVATE)

    var kurulumBitti: Boolean
        get() = p.getBoolean("kurulum", false)
        set(v) { p.edit().putBoolean("kurulum", v).apply() }

    fun secimKaydet(soylenen: String, anahtar: String) {
        p.edit().putString("secim:" + Turkce.norm(soylenen), anahtar).apply()
    }

    fun secim(soylenen: String): String? = p.getString("secim:" + Turkce.norm(soylenen), null)

    fun isEkle(tur: String, baslik: String, alt: String) {
        val satir = listOf(tur, baslik, alt, System.currentTimeMillis().toString()).joinToString("\u001F")
        val eski = p.getString("isler", "").orEmpty().split('\u001E').filter { it.isNotBlank() }
        val yeni = (listOf(satir) + eski).take(20).joinToString("\u001E")
        p.edit().putString("isler", yeni).apply()
    }

    fun isler(): List<SonIs> = p.getString("isler", "").orEmpty().split('\u001E').mapNotNull {
        val f = it.split('\u001F')
        if (f.size == 4) SonIs(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0L) else null
    }
}
