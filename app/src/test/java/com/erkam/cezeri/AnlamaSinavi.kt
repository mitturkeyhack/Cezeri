package com.erkam.cezeri

import com.erkam.cezeri.core.Kanal
import com.erkam.cezeri.core.KisiEsle
import com.erkam.cezeri.core.Komut
import com.erkam.cezeri.core.KomutCozucu
import com.erkam.cezeri.core.Namaz
import com.erkam.cezeri.core.Not
import com.erkam.cezeri.core.NotEsle
import com.erkam.cezeri.core.Turkce
import com.erkam.cezeri.ses.SayiOku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Cezerî'nin anlama sınavı: her derlemede çalışır, geçemeyen sürüm yayımlanmaz.
 * Her satır gerçek bir kullanım cümlesidir.
 */
class AnlamaSinavi {

    private fun coz(s: String) = KomutCozucu.coz(s)

    private inline fun <reified T : Komut> tur(cumle: String): T {
        val k = coz(cumle)
        if (k !is T) fail("“$cumle” → ${T::class.simpleName} bekleniyordu, $k çıktı")
        return k as T
    }

    private fun ara(cumle: String, isim: String, tam: Boolean = false) {
        val k = tur<Komut.Ara>(cumle)
        assertTrue("“$cumle” → '$isim' bulunmalı, varyantlar: ${k.isimVaryantlari}", k.isimVaryantlari.any { Turkce.norm(it) == Turkce.norm(isim) })
        assertEquals("“$cumle” tam eşleşme", tam, k.tam)
    }

    private fun zamir(cumle: String) {
        val s = when (val k = coz(cumle)) {
            is Komut.Ara -> k.soylenen
            is Komut.Mesaj -> k.soylenen
            else -> { fail("“$cumle” → arama/mesaj bekleniyordu, $k çıktı"); "" }
        }
        assertTrue("“$cumle” → son kişiye gitmeli ($s)", Turkce.lower(s) in KomutCozucu.ZAMIRLER)
    }

    private fun mesaj(cumle: String, isim: String?, metin: String?, kanal: Kanal? = null) {
        val k = tur<Komut.Mesaj>(cumle)
        if (isim != null) assertTrue("“$cumle” → '$isim' bulunmalı: ${k.isimVaryantlari}", k.isimVaryantlari.any { Turkce.norm(it) == Turkce.norm(isim) })
        assertEquals("“$cumle” metni", metin, k.metin)
        if (kanal != null) assertEquals("“$cumle” kanalı", kanal, k.kanal)
    }

    private fun rehber(cumle: String, sorgu: String, numara: Boolean = false) {
        val k = tur<Komut.RehberSor>(cumle)
        assertEquals("“$cumle” sorgu", Turkce.norm(sorgu), Turkce.norm(k.sorgu))
        assertEquals("“$cumle” numara isteği", numara, k.numaraIste)
    }

    private fun vakit(cumle: String, hangi: String?, hepsi: Boolean = false) {
        val k = tur<Komut.Vakit>(cumle)
        assertEquals("“$cumle” vakti", hangi, k.hangi)
        assertEquals("“$cumle” tablo", hepsi, k.hepsi)
    }

    // ------------------------------------------------------------------ Arama

    @Test fun arama() {
        ara("ablamı ara", "ablam")
        ara("Ablamı ara.", "ablam")
        ara("ablamı ara sadece ablam yazanı", "ablam", tam = true)
        ara("ablamı ara yalnızca ablam yazanı", "ablam", tam = true)
        ara("hey cezeri annemi ara", "annem")
        ara("Hey Cezerî, annemi ara lütfen", "annem")
        ara("cezeri babamı hemen ara", "babam")
        ara("babamı arar mısın", "babam")
        ara("babamı ararmısın", "babam")
        ara("lütfen ablamı ara", "ablam")
        ara("Mehmet Ali'yi ara", "mehmet ali")
        ara("mehmetaliyi ara", "mehmetali")
        ara("ara ahmet", "ahmet")
        ara("ahmeti ara", "ahmet")
        ara("Ahmet'i ara", "ahmet")
        ara("ahmete telefon aç", "ahmet")
        ara("ahmete bir telefon et", "ahmet")
        ara("dayımı ara", "dayım")
        ara("eniştemi arayabilir misin", "enişte")
        ara("müdürümü ara", "müdürüm")
        ara("abimi ara", "abim")
        ara("ayşe teyzemi ara", "ayşe teyzem")
    }

    @Test fun aramaNumara() {
        val k = tur<Komut.Ara>("0539 893 45 98'i ara")
        assertEquals("05398934598", k.numara)
        val k2 = tur<Komut.Ara>("ara 05398934598")
        assertEquals("05398934598", k2.numara)
    }

    @Test fun sonKisi() {
        zamir("onu ara")
        zamir("ara")
        zamir("onu arar mısın")
        zamir("ona yaz geliyorum")
        zamir("ona mesaj at")
        zamir("mesaj at geliyorum")
    }

    // ------------------------------------------------------------------ Mesaj

    @Test fun mesajlar() {
        mesaj("babama akşam geliyorum yaz", "baba", "Akşam geliyorum")
        mesaj("ahmete mesaj at", "ahmet", null)
        mesaj("ablama whatsapptan yarın geliyorum yaz", "ablam", "Yarın geliyorum", Kanal.WHATSAPP)
        mesaj("mehmete sms at toplantı 3te", "mehmet", "Toplantı 3te", Kanal.SMS)
        mesaj("ona yaz geliyorum", null, "Geliyorum")
        mesaj("mesaj at geliyorum", null, "Geliyorum")
        val n = tur<Komut.Mesaj>("539 893 45 98'e mesaj gönder: geliyorum")
        assertEquals("5398934598", n.numara)
        assertEquals("Geliyorum", n.metin)
    }

    // ------------------------------------------------------------------ Rehber soruları

    @Test fun rehberSorulari() {
        rehber("mehmetali isimli kişi rehberde varmı", "mehmetali")
        rehber("cezeri telefonumda mehmetali isimli kişi varmı", "mehmetali")
        rehber("rehberimde ahmet var mı", "ahmet")
        rehber("rehberde ahmet yılmaz kayıtlı mı", "ahmet yılmaz")
        rehber("ahmet rehberde kayıtlı mı", "ahmet")
        rehber("telefonumda ayşe adında biri var mı", "ayşe")
        rehber("ablamın numarası ne", "ablam", numara = true)
        rehber("Ahmet'in numarasını söyle", "ahmet", numara = true)
        rehber("babamın telefon numarası kaç", "babam", numara = true)
        tur<Komut.RehberSor>("0539 893 45 98 kimin numarası")
    }

    // ------------------------------------------------------------------ Aşama 4

    @Test fun vakitler() {
        vakit("öğle ezanı ne zaman", "ogle")
        vakit("öğle kaçta", "ogle")
        vakit("akşam ezanına ne kadar var", "aksam")
        vakit("iftar kaçta", "aksam")
        vakit("imsak ne zaman", "imsak")
        vakit("sahur ne zaman bitiyor", "imsak")
        vakit("ikindi ezanı kaçta okunuyor", "ikindi")
        vakit("yatsı kaçta", "yatsi")
        vakit("güneş kaçta doğuyor", "gunes")
        vakit("namaz vakitleri", null, hepsi = true)
        vakit("bugünkü vakitleri söyle", null, hepsi = true)
        vakit("ezana ne kadar kaldı", null)
        vakit("sıradaki vakit ne", null)
        vakit("hey cezeri öğle ezanı", "ogle")
        tur<Komut.Kible>("kıble ne tarafta")
        tur<Komut.Kible>("kıbleyi göster")
        tur<Komut.Kible>("kible nerede")
    }

    @Test fun vakitKarismasin() {
        tur<Komut.Mesaj>("babama akşam geliyorum yaz")
        tur<Komut.Sohbet>("namaz nasıl kılınır")
        tur<Komut.Sohbet>("öğle namazı kaç rekat")
        val al = tur<Komut.Alarm>("akşam 7'de alarm kur")
        assertEquals(19, al.saat)
    }

    @Test fun hafiza() {
        val h = tur<Komut.Hatirla>("şunu hatırla: annemin doğum günü 3 mart")
        assertTrue(h.bilgi, h.bilgi.startsWith("Annemin doğum günü"))
        val h2 = tur<Komut.Hatirla>("arabamın plakası 03 ABC 123, bunu unutma")
        assertTrue(h2.bilgi, h2.bilgi.contains("03 ABC 123"))
        val h3 = tur<Komut.Hatirla>("aklında tut, peynirciye salı günleri gidiyorum")
        assertTrue(h3.bilgi, h3.bilgi.startsWith("Peynirciye"))
        tur<Komut.HafizaListe>("ne hatırlıyorsun")
        tur<Komut.HafizaListe>("benim hakkımda ne biliyorsun")
        assertEquals("plaka", tur<Komut.Unut>("plaka bilgisini unut").sorgu)
        tur<Komut.Unut>("hepsini unut")
        tur<Komut.Sohbet>("yarın beni ara diye hatırlat")
    }

    // ------------------------------------------------------------------ Çağrı ve diğerleri

    @Test fun cagriVeDigerleri() {
        tur<Komut.Cagri>("hey ceziri")
        tur<Komut.Cagri>("Hey Cezerî")
        tur<Komut.Cagri>("cezeri")
        tur<Komut.Cagri>("orada mısın")
        tur<Komut.Cagri>("hey")
        tur<Komut.SaatKac>("saat kaç")
        tur<Komut.BugunNe>("bugün ayın kaçı")
        tur<Komut.Pil>("pil kaç")
        assertEquals(true, tur<Komut.Fener>("feneri aç").ac)
        assertEquals(false, tur<Komut.Fener>("feneri kapat").ac)
        tur<Komut.Hesap>("3 üssü 3 kaç")
        tur<Komut.Hesap>("5 çarpı 7")
        tur<Komut.Iptal>("vazgeç")
        tur<Komut.Selam>("selamün aleyküm")
        tur<Komut.Sohbet>("bana bir hikaye anlat")
        tur<Komut.Sohbet>("nasılsın")
        tur<Komut.Sohbet>("el-Cezerî kimdir")
    }

    @Test fun temizlik() {
        assertEquals("ablamı ara", Turkce.clean("Hey Ceziri, ablamı ara lütfen"))
        assertEquals("mehmetali isimli kişi rehberde var mı", Turkce.clean("mehmetali isimli kişi rehberde varmı"))
        assertEquals("babamı arar mısın", Turkce.clean("babamı ararmısın?"))
    }

    // ------------------------------------------------------------------ Kişi seçimi

    private fun sec(adlar: List<String>, sorgu: String, tam: Boolean = false, ek: Turkce.Ek = Turkce.Ek.BELIRTME): KisiEsle.Karar<String> =
        KisiEsle.karar(KisiEsle.sirala(adlar, { it }, KomutCozucu.isimVaryant(sorgu, ek)), tam)

    @Test fun kisiSecimi() {
        assertEquals(1.0, KisiEsle.puan("MehmetAli", listOf("mehmetali")), 0.001)
        assertEquals(1.0, KisiEsle.puan("Mehmet Ali", listOf("mehmetali")), 0.001)
        assertEquals(1.0, KisiEsle.puan("MehmetAli", listOf("mehmet ali")), 0.001)

        val mehmetler = listOf("MehmetAli", "Müdürüm MEHMET VEHBİ GÖKTAŞ", "Mehmet AKIN", "Mehmet Çaylak")
        assertEquals(KisiEsle.Karar.Tek("MehmetAli"), sec(mehmetler, "mehmetali"))

        val ablalar = listOf("Ablam", "Ablam İş", "Ayşe Ablam")
        assertEquals(KisiEsle.Karar.Tek("Ablam"), sec(ablalar, "ablamı"))
        assertEquals(KisiEsle.Karar.Tek("Ablam"), sec(ablalar, "ablamı", tam = true))
        assertTrue(sec(listOf("Ablam İş", "Ayşe Ablam"), "ablamı", tam = true) is KisiEsle.Karar.Yok)

        val ahmetler = listOf("Ahmet Yılmaz", "Ahmet Kaya", "Mehmet")
        val k = sec(ahmetler, "ahmeti")
        assertTrue("$k", k is KisiEsle.Karar.Sor && k.adaylar.toSet() == setOf("Ahmet Yılmaz", "Ahmet Kaya"))
        assertEquals(KisiEsle.Karar.Tek("Ahmet Yılmaz"), sec(listOf("Ahmet Yılmaz", "Mehmet"), "ahmeti"))
        assertEquals(KisiEsle.Karar.Tek("Ahmet Yılmaz"), sec(ahmetler, "ahmet yılmazı"))

        assertEquals(KisiEsle.Karar.Tek("Annem"), sec(listOf("Annem", "Anneannem"), "annemi"))
        assertEquals(KisiEsle.Karar.Tek("Babam"), sec(listOf("Babam", "Baba İş"), "babama", ek = Turkce.Ek.YONELME))
        assertTrue(sec(listOf("Ahmet", "Ayşe"), "zeynebi") is KisiEsle.Karar.Yok)
        // Küçük yazım hatası
        assertEquals(KisiEsle.Karar.Tek("Mustafa"), sec(listOf("Mustafa", "Murat"), "mustafayı"))
    }

    // ------------------------------------------------------------------ Namaz vakti ve kıble (Diyanet'e göre ±2 dk)

    private fun vakitlerTutar(gun: Namaz.Gun, beklenen: List<String>, yer: String) {
        Namaz.Vakit.entries.forEachIndexed { i, v ->
            val (s, d) = beklenen[i].split(":").map { it.toInt() }
            val fark = abs((gun.dakikalar[v] ?: 0) - (s * 60 + d))
            assertTrue("$yer ${v.ad}: ${gun.saat(v)} hesaplandı, Diyanet ${beklenen[i]}", fark <= 2)
        }
    }

    @Test fun namazVakitleri() {
        vakitlerTutar(Namaz.hesapla(2026, 1, 7, 39.454, 38.564, 3.0), listOf("06:10", "07:39", "12:37", "15:03", "17:25", "18:48"), "İliç")
        vakitlerTutar(Namaz.hesapla(2026, 11, 15, 37.795, 37.915, 3.0), listOf("05:37", "07:01", "12:18", "15:01", "17:25", "18:43"), "Tut")
    }

    @Test fun kible() {
        assertEquals(160.1, Namaz.kible(39.93, 32.85), 1.0)
        assertEquals(151.6, Namaz.kible(41.01, 28.98), 1.0)
        assertEquals("güneydoğu", Namaz.yonAdi(150.0))
        assertEquals("1 saat 5 dakika", Namaz.sureMetni(65))
    }

    // ------------------------------------------------------------------ Sesli okuma ve hafıza eşleşmesi

    @Test fun sayiOkuma() {
        assertEquals("sıfır", SayiOku.yaz(0))
        assertEquals("bin iki yüz elli", SayiOku.yaz(1250))
        assertEquals("iki bin yirmi altı", SayiOku.yaz(2026))
        assertEquals("bir milyon", SayiOku.yaz(1_000_000))
        assertEquals("yüz on bir", SayiOku.yaz(111))
        assertEquals("Saat on iki on sekizde", SayiOku.hazirla("Saat 12:18'de"))
        assertEquals("yüzde yirmi beş", SayiOku.hazirla("%25"))
        assertEquals("üç virgül beş kilo", SayiOku.hazirla("3,5 kilo"))
        assertEquals("beşte", SayiOku.hazirla("5'te"))
    }

    @Test fun notBulma() {
        val notlar = listOf(Not(1, "Annemin doğum günü 3 Mart"), Not(2, "Arabamın plakası 03 ABC 123"))
        assertEquals(1L, NotEsle.enIyi(notlar, "annemin doğum günü ne zaman")?.id)
        assertEquals(2L, NotEsle.enIyi(notlar, "plaka")?.id)
        assertNull(NotEsle.enIyi(notlar, "hava nasıl"))
    }
}

class EvrenSinavi {
    @Test fun akisParcalari() {
        assertEquals("Mer", com.erkam.cezeri.core.EvrenMetin.sseParcasi("""data: {"choices":[{"delta":{"content":"Mer"}}]}"""))
        assertEquals(com.erkam.cezeri.core.EvrenMetin.BITTI, com.erkam.cezeri.core.EvrenMetin.sseParcasi("data: [DONE]"))
        assertNull(com.erkam.cezeri.core.EvrenMetin.sseParcasi(": keep-alive"))
        assertNull(com.erkam.cezeri.core.EvrenMetin.sseParcasi("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
    }

    @Test fun dusunceAyiklama() {
        val s = com.erkam.cezeri.core.EvrenMetin.DusunceSuzgeci()
        val cikti = listOf("<thi", "nk>uzun düşü", "nce</th", "ink>Merhaba", " dünya.").joinToString("") { s.ekle(it) } + s.bitir()
        assertEquals("Merhaba dünya.", cikti)
        assertEquals("Selam", com.erkam.cezeri.core.EvrenMetin.temizle("<think>x</think>Selam"))
        assertEquals("a < b ise", com.erkam.cezeri.core.EvrenMetin.temizle("a < b ise"))
    }

    @Test fun etiketler() {
        assertEquals(Triple("ARA", "Ablam", ""), com.erkam.cezeri.core.EvrenMetin.etiket("[[ARA: Ablam]]"))
        assertEquals(Triple("MESAJ", "Ahmet", "geliyorum"), com.erkam.cezeri.core.EvrenMetin.etiket("[[MESAJ: Ahmet | geliyorum]]"))
        assertEquals(Triple("VAKIT", "", ""), com.erkam.cezeri.core.EvrenMetin.etiket("[[VAKIT]]"))
        assertEquals("HATIRLA", com.erkam.cezeri.core.EvrenMetin.etiket(" [[HATIRLA: annemin doğum günü 3 Mart]]")?.first)
        assertNull(com.erkam.cezeri.core.EvrenMetin.etiket("Merhaba [[ARA: x]]"))
    }

    @Test fun modelListesi() {
        val l = com.erkam.cezeri.core.EvrenMetin.modelleriAyikla("""{"data":[{"id":"gemma-4-31b"},{"id":"qwen3.8-flash-next"},{"id":"auto"}]}""")
        assertEquals(listOf("gemma-4-31b", "qwen3.8-flash-next", "auto"), l)
        assertEquals("qwen3.8-flash-next", com.erkam.cezeri.core.EvrenMetin.hizliSec(l))
        assertEquals("gemma-4-31b", com.erkam.cezeri.core.EvrenMetin.derinSec(l))
    }
}
