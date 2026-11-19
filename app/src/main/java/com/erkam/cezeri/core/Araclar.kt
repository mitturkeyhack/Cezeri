package com.erkam.cezeri.core

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Gemma'nın sohbet sırasında çağırabildiği araçlar. Arama/mesaj/alarm araçları işi kendisi yapmaz:
 * uygulamaya bildirir, uygulama kullanıcıdan onay alır.
 */
class CezeriAraclari(
    private val rehberBul: (String) -> List<Kisi>,
    private val eylem: (tur: String, a: String, b: String) -> String,
) : ToolSet {

    @Tool(description = "Bir matematik ifadesini kesin olarak hesaplar. Örnekler: 3^3, (12+8)*4, 250*15/100, sqrt(144).")
    fun hesapla(
        @ToolParam(description = "Hesaplanacak ifade. Sadece rakam, + - * / ^ parantez ve sqrt() kullan.") ifade: String,
    ): String {
        val v = Hesap.hesapla(ifade) ?: Hesap.ifadeyeCevir(ifade)?.let { Hesap.hesapla(it) }
        return if (v == null) "Hesaplanamadı" else Hesap.bicimle(v)
    }

    @Tool(description = "Kullanıcının telefon rehberinde bir isim ya da telefon numarası arar. Okumak için onay gerekmez.")
    fun rehberdeAra(
        @ToolParam(description = "Aranacak isim (ör. Ahmet, Ablam, MehmetAli) ya da telefon numarası") sorgu: String,
    ): String {
        val l = try { rehberBul(sorgu) } catch (_: Exception) { emptyList() }
        if (l.isEmpty()) return "Rehberde bulunamadı"
        return "${l.size} kişi: " + l.take(5).joinToString("; ") { "${it.ad} (${it.etiket})" }
    }

    @Tool(description = "Kullanıcı birini aramak istediğinde çağır. Uygulama kişiyi bulur ve kullanıcıdan onay alır.")
    fun aramaYap(
        @ToolParam(description = "Aranacak kişinin rehberdeki adı, ya da telefon numarası (ör. Ablam, Mehmet Ali)") kisi: String,
    ): String = eylem("ara", kisi, "")

    @Tool(description = "Kullanıcı birine mesaj göndermek istediğinde çağır. Uygulama onay alıp gönderir.")
    fun mesajGonder(
        @ToolParam(description = "Mesajın gideceği kişinin adı ya da numarası") kisi: String,
        @ToolParam(description = "Gönderilecek mesajın metni; kullanıcının söylediği gibi, birinci ağızdan") metin: String,
    ): String = eylem("mesaj", kisi, metin)

    @Tool(description = "Alarm kurar.")
    fun alarmKur(
        @ToolParam(description = "Alarm saati, 24 saat biçiminde HH:MM (ör. 06:30)") saat: String,
    ): String = eylem("alarm", saat, "")

    @Tool(description = "Kullanıcı bir bilgiyi hatırlamanı istediğinde kaydeder (ör. annesinin doğum günü).")
    fun hatirla(
        @ToolParam(description = "Hatırlanacak bilgi, tek ve açık bir cümle") bilgi: String,
    ): String = eylem("hatirla", bilgi, "")

    @Tool(description = "Bugünün namaz vakitlerini ve sıradaki vakti verir.")
    fun namazVakitleri(): String = eylem("vakit", "", "")
}
