package com.erkam.cezeri.core

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** Gemma'nın sohbet sırasında çağırabildiği araçlar: kesin hesap ve rehber araması. */
class CezeriAraclari(private val rehberBul: (String) -> List<Kisi>) : ToolSet {

    @Tool(description = "Bir matematik ifadesini kesin olarak hesaplar. Örnekler: 3^3, (12+8)*4, 250*15/100, sqrt(144).")
    fun hesapla(
        @ToolParam(description = "Hesaplanacak ifade. Sadece rakam, + - * / ^ parantez ve sqrt() kullan.") ifade: String,
    ): String {
        val v = Hesap.hesapla(ifade) ?: Hesap.ifadeyeCevir(ifade)?.let { Hesap.hesapla(it) }
        return if (v == null) "Hesaplanamadı" else Hesap.bicimle(v)
    }

    @Tool(description = "Kullanıcının telefon rehberinde bir isim ya da telefon numarası arar ve bulunan kişileri döndürür.")
    fun rehberdeAra(
        @ToolParam(description = "Aranacak isim (ör. Ahmet) ya da telefon numarası") sorgu: String,
    ): String {
        val l = try { rehberBul(sorgu) } catch (_: Exception) { emptyList() }
        if (l.isEmpty()) return "Rehberde bulunamadı"
        return l.take(5).joinToString("; ") { "${it.ad} (${it.etiket}): ${it.numara}" }
    }
}
