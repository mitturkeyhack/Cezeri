package com.erkam.cezeri.core

import android.content.Context

data class SonIs(val tur: String, val baslik: String, val alt: String, val zaman: Long)

data class Not(val id: Long, val metin: String)

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

    // ---------------- Kişisel notlar ("hatırla: ...")

    fun notlar(): List<Not> = p.getString("notlar", "").orEmpty().split('\u001E').mapNotNull {
        val f = it.split('\u001F')
        if (f.size == 2) Not(f[0].toLongOrNull() ?: 0L, f[1]) else null
    }

    private fun notlariYaz(l: List<Not>) {
        p.edit().putString("notlar", l.joinToString("\u001E") { "${it.id}\u001F${it.metin.replace('\u001E', ' ').replace('\u001F', ' ')}" }).apply()
    }

    fun notEkle(metin: String): Not {
        val n = Not(System.currentTimeMillis(), metin.trim())
        val eski = notlar().filterNot { Turkce.norm(it.metin) == Turkce.norm(metin) }
        notlariYaz((eski + n).takeLast(200))
        return n
    }

    fun notSil(id: Long) = notlariYaz(notlar().filterNot { it.id == id })

    fun notlariTemizle() = notlariYaz(emptyList())

    /** Sorguyla en çok ortak kelimesi olan not (en az bir anlamlı kelime ortak olmalı). */
    fun notBul(sorgu: String): Not? = NotEsle.enIyi(notlar(), sorgu)
}

object NotEsle {
    private val bos = setOf("bir", "bu", "şu", "su", "ve", "ile", "de", "da", "ki", "mi", "mı", "ne", "benim", "bilgi", "bilgisi", "notu", "not", "olan", "olarak")

    fun kelimeler(s: String) = Turkce.norm(s).split(' ').filter { it.length >= 3 && it !in bos }.map { it.take(5) }.toSet()

    fun enIyi(notlar: List<Not>, sorgu: String): Not? {
        val q = kelimeler(sorgu)
        if (q.isEmpty()) return null
        return notlar.map { it to (kelimeler(it.metin) intersect q).size }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }
}
