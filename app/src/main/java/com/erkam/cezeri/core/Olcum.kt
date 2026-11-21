package com.erkam.cezeri.core

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hız ölçer: her istekte uyanma, dinleme, düşünme ve sese kadar geçen süreler (ms).
 * Toplam = sözünü bitirdiğin (ya da yazıp gönderdiğin) andan ilk sese / ilk yazıya kadar.
 */
object Olcum {

    data class Kayit(
        val uyanma: Long?, val dinleme: Long?, val dusunme: Long?, val ses: Long?, val toplam: Long,
        val evren: Boolean, val zaman: Long = System.currentTimeMillis(),
    )

    private val _son = MutableStateFlow<Kayit?>(null)
    val son: StateFlow<Kayit?> = _son
    private val _gecmis = MutableStateFlow<List<Kayit>>(emptyList())
    val gecmis: StateFlow<List<Kayit>> = _gecmis

    private fun simdi() = SystemClock.elapsedRealtime()

    @Volatile private var tUyandi = 0L
    @Volatile private var tDinlemeHazir = 0L
    @Volatile private var tSozBitti = 0L
    @Volatile private var tSonuc = 0L
    @Volatile private var tIstek = 0L
    @Volatile private var tIlkKelime = 0L
    @Volatile private var tSeseVerildi = 0L
    @Volatile private var bitti = true

    @Synchronized fun uyandi() { tUyandi = simdi(); tDinlemeHazir = 0 }
    @Synchronized fun dinlemeHazir() { if (tDinlemeHazir == 0L) tDinlemeHazir = simdi() }
    @Synchronized fun sozBitti() { tSozBitti = simdi() }

    /** Konuşma yazıya döküldü: yeni ölçüm başlar */
    @Synchronized fun sonucGeldi() {
        tSonuc = simdi(); if (tSozBitti == 0L || tSozBitti > tSonuc) tSozBitti = tSonuc
        tIstek = 0; tIlkKelime = 0; tSeseVerildi = 0; bitti = false
    }

    /** Yazıyla gönderildi: yeni ölçüm başlar */
    @Synchronized fun yazildi() {
        tUyandi = 0; tDinlemeHazir = 0; tSozBitti = simdi(); tSonuc = tSozBitti
        tIstek = 0; tIlkKelime = 0; tSeseVerildi = 0; bitti = false
    }

    @Synchronized fun istek() { tIstek = simdi() }
    @Synchronized fun ilkKelime() { if (tIlkKelime == 0L && tIstek != 0L) tIlkKelime = simdi() }
    @Synchronized fun seseVerildi() { if (tSeseVerildi == 0L && !bitti) tSeseVerildi = simdi() }

    /** İlk ses çıktı (ya da sessiz modda ilk cevap ekranda): ölçümü kaydet */
    @Synchronized fun ilkCikti() {
        if (bitti || tSonuc == 0L) return
        bitti = true
        val t = simdi()
        val k = Kayit(
            uyanma = if (tUyandi != 0L && tDinlemeHazir > tUyandi) tDinlemeHazir - tUyandi else null,
            dinleme = if (tSonuc >= tSozBitti && tDinlemeHazir != 0L) tSonuc - tSozBitti else null,
            dusunme = if (tIstek != 0L && tIlkKelime >= tIstek) tIlkKelime - tIstek else null,
            ses = if (tSeseVerildi != 0L) t - tSeseVerildi else null,
            toplam = t - tSozBitti,
            evren = tIstek != 0L,
        )
        _son.value = k
        _gecmis.value = (_gecmis.value + k).takeLast(20)
        tUyandi = 0
    }

    fun ortalama(evren: Boolean): Long? = _gecmis.value.filter { it.evren == evren }.map { it.toplam }.takeIf { it.isNotEmpty() }?.average()?.toLong()

    fun sifirla() { _son.value = null; _gecmis.value = emptyList() }
}
