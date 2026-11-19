package com.erkam.cezeri

import android.speech.SpeechRecognizer
import com.erkam.cezeri.core.Akil
import com.erkam.cezeri.core.Kanal
import com.erkam.cezeri.core.Kisi
import com.erkam.cezeri.core.Komut
import com.erkam.cezeri.core.KomutCozucu
import com.erkam.cezeri.core.Kulak
import com.erkam.cezeri.core.ModelIndirici
import com.erkam.cezeri.core.SonIs
import com.erkam.cezeri.core.Turkce
import com.erkam.cezeri.core.Uyanma
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Ekran {
    data class Kurulum(val adim: Int) : Ekran
    data object Ana : Ekran
    data object Sohbet : Ekran
    data class KisiSec(val soru: String, val secenekler: List<Kisi>, val mesajMi: Boolean) : Ekran
    data class AramaOnay(val kisi: Kisi) : Ekran
    data class MesajOnay(val kisi: Kisi, val metin: String, val kanal: Kanal) : Ekran
    data object Ayarlar : Ekran
    data class SesKaydi(val adim: Int, val toplam: Int, val mesaj: String, val bitti: Boolean = false) : Ekran
}

data class SohbetMesaji(val benden: Boolean, val metin: String, val bitti: Boolean = true)

data class Durum(
    val ekran: Ekran = Ekran.Ana,
    val dinliyor: Boolean = false,
    val araMetin: String = "",
    val seviye: Float = 0f,
    val duyulan: String = "",
    val konusuyor: Boolean = false,
    val dusunuyor: Boolean = false,
    val sohbet: List<SohbetMesaji> = emptyList(),
    val isler: List<SonIs> = emptyList(),
    val model: ModelIndirici.Durum = ModelIndirici.Durum.Yok,
    val akil: Akil.Durum = Akil.Durum.MODEL_YOK,
    val bekliyor: Boolean = false,
    val uyanmaModel: Uyanma.ModelDurumu = Uyanma.ModelDurumu.Yok,
    val uyanmaAcik: Boolean = false,
    val kimlikOrnek: Int = 0,
    val derinMod: Boolean = false,
    val derinModel: ModelIndirici.Durum = ModelIndirici.Durum.Yok,
    val uyanmaHassasiyet: Float = 0.5f,
)

class Asistan(private val a: CezeriApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ses = a.ses
    private val akil = a.akil
    private val model = a.model
    private val rehber = a.rehber
    private val eylem = a.eylem
    private val hafiza = a.hafiza
    val kulak = Kulak(a)

    private val _durum = MutableStateFlow(
        Durum(
            ekran = if (hafiza.kurulumBitti) Ekran.Ana else Ekran.Kurulum(0),
            isler = hafiza.isler(),
        )
    )
    val durum: StateFlow<Durum> = _durum

    private sealed interface Bekleyen {
        data class Onay(val evet: () -> Unit, val hayir: () -> Unit, val degistir: (() -> Unit)?, val deneme: Int = 0) : Bekleyen
        data class Secim(val secenekler: List<Kisi>, val sec: (Kisi) -> Unit, val deneme: Int = 0) : Bekleyen
        data class Govde(val kisi: Kisi, val kanal: Kanal?) : Bekleyen
        data class AlarmSaati(val deneme: Int = 0) : Bekleyen
    }

    private val uyanma = a.uyanma
    private val prefs = a.getSharedPreferences("asistan", android.content.Context.MODE_PRIVATE)
    private var derinIstek: Boolean = prefs.getBoolean("derin", false)
    private val kayitVektorleri = ArrayList<DoubleArray>()

    private var bekleyen: Bekleyen? = null
        set(v) { field = v; _durum.update { it.copy(bekliyor = v != null) } }
    private var sohbetIsi: Job? = null

    init {
        akil.araclar = com.erkam.cezeri.core.CezeriAraclari { rehberBul(it) }
        _durum.update { it.copy(derinMod = derinIstek) }
        if (derinIstek && a.modelDerin.hazirMi()) scope.launch { akil.yukle(a.modelDerin.dosya) }
        scope.launch { kulak.dinliyor.collect { v -> _durum.update { it.copy(dinliyor = v) } } }
        scope.launch { kulak.araMetin.collect { v -> _durum.update { it.copy(araMetin = v) } } }
        scope.launch { kulak.seviye.collect { v -> _durum.update { it.copy(seviye = v) } } }
        scope.launch { ses.konusuyor.collect { v -> _durum.update { it.copy(konusuyor = v) } } }
        scope.launch { akil.durum.collect { v -> _durum.update { it.copy(akil = v) } } }
        _durum.update { it.copy(uyanmaAcik = uyanma.acik, kimlikOrnek = uyanma.ornekSayisi(), uyanmaHassasiyet = uyanma.hassasiyet) }
        scope.launch {
            uyanma.modelDurumu.collect { v ->
                _durum.update { it.copy(uyanmaModel = v) }
                // Model inince, açık bırakıldıysa dinlemeyi başlat
                if (v is Uyanma.ModelDurumu.Hazir && uyanma.acik && a.onPlanda) UyanikServis.baslat(a)
            }
        }
        scope.launch {
            model.durum.collect { v ->
                _durum.update { it.copy(model = v) }
                if (v is ModelIndirici.Durum.Hazir && akil.durum.value == Akil.Durum.MODEL_YOK && !derinIstek) {
                    launch { akil.yukle(model.dosya) }
                }
            }
        }
        scope.launch {
            a.modelDerin.durum.collect { v ->
                _durum.update { it.copy(derinModel = v) }
                if (v is ModelIndirici.Durum.Hazir && derinIstek && akil.aktifModel != a.modelDerin.dosya) {
                    launch { akil.yukle(a.modelDerin.dosya) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Kurulum

    fun kurulumAdim(adim: Int) {
        _durum.update { it.copy(ekran = Ekran.Kurulum(adim)) }
        if (adim == 2) model.baslat()
    }

    fun karsila() = ses.konus("Selâm, ben Cezerî. Senin için arar, mesaj yazar, vakitleri hatırlatır ve sorularını cevaplarım.")

    fun kurulumBitir() {
        hafiza.kurulumBitti = true
        _durum.update { it.copy(ekran = Ekran.Ana) }
        ses.konus("Her şey hazır. Mikrofona dokunup benimle konuşabilirsin.")
    }

    fun modeliIndir() = model.baslat()

    fun konusKisa(metin: String) = ses.konus(metin)

    fun arkaUc(): String = akil.arkaUc

    fun sesAdi(): String? = ses.secilenSes

    // ---------------------------------------------------------------- Dinleme

    fun mikrofon() {
        if (_durum.value.dinliyor) { kulak.durdur(); return }
        ses.sus()
        dinle()
    }

    /** Yan tuş / asistan kısayoluyla açılınca */
    fun cagrildi() {
        if (_durum.value.ekran is Ekran.Kurulum) return
        ses.konus("Buyur.") { dinle() }
    }

    fun dinle() {
        // Arka plandaki "Hey Cezerî" dinlemesi mikrofonu tutuyorsa önce bırakılır
        if (uyanma.dinliyor.value) {
            uyanma.durdur()
            scope.launch { delay(250); kulak.dinle { sonuc -> duydu(sonuc) } }
        } else kulak.dinle { sonuc -> duydu(sonuc) }
    }

    fun dinlemeyiBirak() {
        kulak.birak()
    }

    private fun sorVeDinle(metin: String) {
        ses.konus(metin) { dinle() }
    }

    private fun soyle(metin: String, sonra: (() -> Unit)? = null) = ses.konus(metin, bitince = sonra)

    private fun duydu(s: Kulak.Sonuc) {
        when (s) {
            is Kulak.Sonuc.Metin -> {
                _durum.update { it.copy(duyulan = s.en) }
                val b = bekleyen
                if (b != null) cevapla(b, s.en, s.alternatifler) else isle(s.en, s.alternatifler)
            }
            Kulak.Sonuc.Sessiz -> {
                val b = bekleyen
                if (b != null) cevapla(b, "", emptyList())
            }
            is Kulak.Sonuc.Hata -> {
                if (s.kod == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    soyle("Mikrofona erişemiyorum. İzin vermen gerekiyor.")
                } else if (bekleyen != null) {
                    cevapla(bekleyen!!, "", emptyList())
                } else if (s.kod == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || s.kod == SpeechRecognizer.ERROR_CLIENT) {
                    // sessizce geç
                } else {
                    soyle("Kusura bakma, tam duyamadım. Bir daha söyler misin?")
                }
            }
        }
    }

    // ---------------------------------------------------------------- Yazarak

    fun yazdi(metin: String) {
        if (metin.isBlank()) return
        ses.sus(); kulak.birak()
        val b = bekleyen
        if (b != null) cevapla(b, metin, listOf(metin)) else isle(metin, listOf(metin))
    }

    // ---------------------------------------------------------------- Komutlar

    private fun isle(metin: String, alternatifler: List<String>) {
        var komut = KomutCozucu.coz(metin)
        // İlk tahmin sohbet çıktıysa diğer duyulma ihtimallerinde bir komut var mı bak
        if (komut is Komut.Sohbet) {
            for (alt in alternatifler.drop(1).take(3)) {
                val k = KomutCozucu.coz(alt)
                if (k !is Komut.Sohbet) { komut = k; break }
            }
        }
        when (val k = komut) {
            is Komut.Ara -> if (k.numara != null) aramaOnayla(numaraKisi(k.numara)) else kisiBul(k.isimVaryantlari, k.soylenen, mesajMi = false) { aramaOnayla(it) }
            is Komut.Mesaj -> mesajKomutu(k)
            is Komut.Fener -> {
                val ok = eylem.fener(k.ac)
                soyle(if (!ok) "Fener açılamadı." else if (k.ac) "Feneri açtım." else "Feneri kapattım.")
            }
            is Komut.Alarm -> {
                if (k.saat == null) { bekleyen = Bekleyen.AlarmSaati(); sorVeDinle("Saat kaça kurayım?") }
                else alarmKur(k.saat, k.dakika ?: 0)
            }
            Komut.SaatKac -> soyle(eylem.saatCumlesi())
            Komut.BugunNe -> soyle(eylem.tarihCumlesi())
            Komut.Pil -> soyle("Pil yüzde ${eylem.pil()}.")
            is Komut.Ses -> { eylem.sesAyarla(k.yukselt); soyle(if (k.yukselt) "Sesi açtım." else "Sesi kıstım.") }
            Komut.Sessiz -> soyle(if (eylem.sessiz()) "Telefonu sessize aldım." else "Bunu yapabilmem için Rahatsız Etmeyin iznini vermen gerekiyor. Ayarları açıyorum.")
            Komut.Titresim -> { eylem.titresim(); soyle("Titreşime aldım.") }
            Komut.Iptal -> { iptal(sessiz = false) }
            is Komut.Selam -> sorVeDinle("Ve aleyküm selam. Buyur, dinliyorum.")
            is Komut.Hesap -> {
                val v = com.erkam.cezeri.core.Hesap.hesapla(k.ifade)
                if (v == null) sohbet(metin)
                else {
                    val c = "Sonuç ${com.erkam.cezeri.core.Hesap.bicimle(v)}."
                    _durum.update { it.copy(ekran = Ekran.Sohbet, sohbet = it.sohbet + SohbetMesaji(true, metin) + SohbetMesaji(false, c)) }
                    soyle(c)
                }
            }
            is Komut.RehberSor -> rehberCevapla(k, metin)
            is Komut.Sohbet -> sohbet(k.metin)
        }
    }

    private fun bicimliNumara(n: String): String {
        var d = n.filter { it.isDigit() }
        if (d.startsWith("90") && d.length == 12) d = "0" + d.drop(2)
        if (d.length == 10 && d.startsWith("5")) d = "0$d"
        return if (d.length == 11) "${d.substring(0, 4)} ${d.substring(4, 7)} ${d.substring(7, 9)} ${d.substring(9)}" else n
    }

    fun rehberBul(sorgu: String): List<Kisi> {
        val rakam = sorgu.filter { it.isDigit() }
        return if (rakam.length >= 7) {
            val son = rakam.takeLast(10)
            rehber.hepsi().filter { it.numara.filter { c -> c.isDigit() }.takeLast(10) == son }
        } else {
            rehber.bul(KomutCozucu.isimVaryant(sorgu, Turkce.Ek.BELIRTME)).groupBy { it.kisi.contactId }.map { it.value.first().kisi }
        }
    }

    private fun rehberCevapla(k: Komut.RehberSor, metin: String) {
        if (!rehber.izinVar()) { soyle("Rehberine erişemiyorum. Kurulumdan rehber iznini vermen gerekiyor."); return }
        val bulunan = rehberBul(k.sorgu)
        val rakamMi = k.sorgu.count { it.isDigit() } >= 7
        val cevap = when {
            bulunan.isEmpty() -> if (rakamMi) "Bu numara rehberinde kayıtlı değil." else "Rehberinde bu isimde birini bulamadım."
            rakamMi -> "Evet, bu numara rehberinde ${bulunan.first().ad} olarak kayıtlı."
            k.numaraIste && bulunan.size == 1 -> "${bulunan[0].ad}: ${bicimliNumara(bulunan[0].numara)}."
            k.numaraIste -> bulunan.take(3).joinToString(". ") { "${it.ad}: ${bicimliNumara(it.numara)}" } + "."
            bulunan.size == 1 -> "Evet, rehberinde ${bulunan[0].ad} var."
            else -> "Evet, rehberinde ${bulunan.size} kişi var: " + bulunan.take(4).joinToString(", ") { it.ad } + "."
        }
        _durum.update { it.copy(ekran = Ekran.Sohbet, sohbet = it.sohbet + SohbetMesaji(true, metin) + SohbetMesaji(false, cevap)) }
        soyle(cevap)
    }

    private fun numaraKisi(numara: String): Kisi {
        var d = numara.filter { it.isDigit() }
        if (d.startsWith("90") && d.length == 12) d = "0" + d.drop(2)
        if (d.length == 10 && d.startsWith("5")) d = "0$d"
        val bicim = if (d.length == 11) "${d.substring(0, 4)} ${d.substring(4, 7)} ${d.substring(7, 9)} ${d.substring(9)}" else numara
        return Kisi(-1L, bicim, if (numara.startsWith("+")) numara else d, "Numara", 0L)
    }

    fun kime(k: Kisi, belirtme: Boolean): String =
        if (k.contactId == -1L) (if (belirtme) "Bu numarayı" else "Bu numaraya") else Turkce.ekle(k.ad, belirtme)

    private fun mesajKomutu(k: Komut.Mesaj) {
        if (k.numara != null) {
            val kisi = numaraKisi(k.numara)
            if (k.metin.isNullOrBlank()) { bekleyen = Bekleyen.Govde(kisi, k.kanal); sorVeDinle("Ne yazayım?") }
            else mesajOnayla(kisi, k.metin, k.kanal ?: Kanal.SMS)
            return
        }
        var varyant = k.isimVaryantlari
        var metin = k.metin
        // "babama akşama geliyorum yaz": iki kelimelik alıcı bulunamazsa ilk kelimeyi dene
        val kelimeler = k.soylenen.split(' ')
        if (kelimeler.size == 2 && rehber.bul(varyant).isEmpty()) {
            varyant = KomutCozucu.isimVaryant(kelimeler[0], Turkce.Ek.YONELME)
            metin = listOfNotNull(kelimeler[1], metin).joinToString(" ").replaceFirstChar { it.titlecase(Turkce.TR) }
        }
        kisiBul(varyant, k.soylenen, mesajMi = true) { kisi ->
            if (metin.isNullOrBlank()) {
                bekleyen = Bekleyen.Govde(kisi, k.kanal)
                sorVeDinle("Ne yazayım?")
            } else {
                mesajOnayla(kisi, metin!!, k.kanal ?: Kanal.SMS)
            }
        }
    }

    private fun kisiBul(varyantlar: List<String>, soylenen: String, mesajMi: Boolean, devam: (Kisi) -> Unit) {
        if (!rehber.izinVar()) { soyle("Rehberine erişemiyorum. Kurulumdan rehber iznini vermen gerekiyor."); return }
        hafiza.secim(soylenen)?.let { anahtar -> rehber.idIle(anahtar)?.let { devam(it); return } }
        val eslesmeler = rehber.bul(varyantlar)
        if (eslesmeler.isEmpty()) {
            soyle("Rehberde bu isimde birini bulamadım.")
            return
        }
        // Kişi başına en iyi numara
        val kisiBasi = eslesmeler.groupBy { it.kisi.contactId }.map { (_, l) -> l.first() }.sortedByDescending { it.puan }
        val ilk = kisiBasi[0]
        val ikinci = kisiBasi.getOrNull(1)
        if (ikinci == null || ilk.puan - ikinci.puan >= 0.12) { devam(ilk.kisi); return }
        val secenekler = kisiBasi.filter { ilk.puan - it.puan < 0.12 }.take(3).map { it.kisi }
        val sayi = listOf("", "bir", "iki", "üç")[secenekler.size]
        val ortak = secenekler.first().ad.split(' ').first()
        val soru = "Rehberinde $sayi $ortak var. Hangisini ${if (mesajMi) "seçeyim" else "arayayım"}?"
        _durum.update { it.copy(ekran = Ekran.KisiSec(soru, secenekler, mesajMi)) }
        bekleyen = Bekleyen.Secim(secenekler, { k -> hafiza.secimKaydet(soylenen, k.anahtar); devam(k) })
        sorVeDinle(soru)
    }

    fun kisiSec(k: Kisi) {
        val b = bekleyen as? Bekleyen.Secim ?: return
        kulak.birak(); ses.sus()
        bekleyen = null
        b.sec(k)
    }

    private fun aramaOnayla(k: Kisi) {
        _durum.update { it.copy(ekran = Ekran.AramaOnay(k)) }
        bekleyen = Bekleyen.Onay(
            evet = {
                hafiza.isEkle("ara", "${Turkce.ekle(k.ad, belirtme = true)} aradı", "Sesli onayla")
                anaEkrana()
                soyle("Tamam, arıyorum.") { eylem.ara(k.numara) }
            },
            hayir = { anaEkrana(); soyle("Peki, vazgeçtim.") },
            degistir = null,
        )
        sorVeDinle("${kime(k, belirtme = true)} arayayım mı?")
    }

    private fun mesajOnayla(k: Kisi, metin: String, kanal: Kanal) {
        val kanal2 = if (kanal == Kanal.WHATSAPP && !eylem.whatsappVar()) Kanal.SMS else kanal
        _durum.update { it.copy(ekran = Ekran.MesajOnay(k, metin, kanal2)) }
        bekleyen = Bekleyen.Onay(
            evet = { gonder(k, metin, kanal2) },
            hayir = { anaEkrana(); soyle("Peki, vazgeçtim.") },
            degistir = { bekleyen = Bekleyen.Govde(k, kanal2); sorVeDinle("Yeni hâlini söyler misin?") },
        )
        sorVeDinle("${kime(k, belirtme = false)} şunu yazıyorum: $metin. Göndereyim mi?")
    }

    fun kanalSec(kanal: Kanal) {
        val e = _durum.value.ekran as? Ekran.MesajOnay ?: return
        if (e.kanal == kanal) return
        _durum.update { it.copy(ekran = e.copy(kanal = kanal)) }
        bekleyen = Bekleyen.Onay(
            evet = { gonder(e.kisi, e.metin, kanal) },
            hayir = { anaEkrana(); soyle("Peki, vazgeçtim.") },
            degistir = { bekleyen = Bekleyen.Govde(e.kisi, kanal); sorVeDinle("Yeni hâlini söyler misin?") },
        )
    }

    private fun gonder(k: Kisi, metin: String, kanal: Kanal) {
        anaEkrana()
        when (kanal) {
            Kanal.SMS -> {
                if (eylem.sms(k.numara, metin)) {
                    hafiza.isEkle("mesaj", "${Turkce.ekle(k.ad, belirtme = false)} mesaj gönderdi", "SMS")
                    soyle("Mesajını gönderdim.")
                } else soyle("Mesaj gönderilemedi. SMS iznini kontrol eder misin?")
            }
            Kanal.WHATSAPP -> {
                soyle("WhatsApp'ta mesajını hazırladım. Göndermek için gönder tuşuna basman yeterli.") {
                    if (eylem.whatsapp(k.numara, metin)) hafiza.isEkle("mesaj", "${Turkce.ekle(k.ad, belirtme = false)} WhatsApp mesajı", "WhatsApp")
                }
            }
        }
        isleriYenile()
    }

    private fun alarmKur(saat: Int, dakika: Int) {
        val s = "%02d:%02d".format(saat, dakika)
        if (eylem.alarm(saat, dakika)) {
            hafiza.isEkle("alarm", "Alarm kurdu", "Saat $s"); isleriYenile()
            soyle("Alarmı saat $s için kurdum.")
        } else soyle("Alarm kurulamadı.")
    }

    // ---------------------------------------------------------------- Cevaplar

    private fun cevapla(b: Bekleyen, metin: String, alternatifler: List<String>) {
        when (b) {
            is Bekleyen.Onay -> {
                val e = _durum.value.ekran
                val t = Turkce.lower(metin)
                if (e is Ekran.MesajOnay && t.isNotBlank()) {
                    if (t.contains("sms") || t.contains("kısa mesaj")) { kanalSec(Kanal.SMS); sorVeDinle("Tamam, SMS olarak göndereyim mi?"); return }
                    if (t.contains("whatsapp")) { kanalSec(Kanal.WHATSAPP); sorVeDinle("Tamam, WhatsApp'tan göndereyim mi?"); return }
                }
                when (if (metin.isBlank()) Turkce.Cevap.BILINMIYOR else Turkce.answer(metin)) {
                    Turkce.Cevap.EVET -> { bekleyen = null; b.evet() }
                    Turkce.Cevap.HAYIR -> { bekleyen = null; b.hayir() }
                    Turkce.Cevap.DEGISTIR -> { bekleyen = null; (b.degistir ?: b.hayir)() }
                    Turkce.Cevap.BILINMIYOR -> {
                        if (b.deneme >= 1) { bekleyen = null; anaEkrana(); soyle("Cevap gelmedi, işlemi iptal ettim.") }
                        else { bekleyen = b.copy(deneme = b.deneme + 1); sorVeDinle("Evet mi, hayır mı?") }
                    }
                }
            }
            is Bekleyen.Secim -> {
                if (metin.isNotBlank() && Turkce.answer(metin) == Turkce.Cevap.HAYIR) { iptal(sessiz = false); return }
                val secilen = secimiAnla(b.secenekler, listOf(metin) + alternatifler)
                if (secilen != null) { bekleyen = null; b.sec(secilen); return }
                if (b.deneme >= 1) { bekleyen = null; anaEkrana(); soyle("Anlayamadım, işlemi iptal ettim.") }
                else { bekleyen = b.copy(deneme = b.deneme + 1); sorVeDinle(if (b.secenekler.size == 2) "Birinciyi mi, ikinciyi mi?" else "Kaçıncısı?") }
            }
            is Bekleyen.Govde -> {
                if (metin.isBlank()) { bekleyen = null; anaEkrana(); soyle("Cevap gelmedi, işlemi iptal ettim."); return }
                if (Turkce.norm(metin) in setOf("iptal", "vazgec", "bosver")) { iptal(sessiz = false); return }
                bekleyen = null
                val m = metin.trim().replaceFirstChar { it.titlecase(Turkce.TR) }
                mesajOnayla(b.kisi, m, b.kanal ?: Kanal.SMS)
            }
            is Bekleyen.AlarmSaati -> {
                val z = Turkce.parseTime(metin)
                if (z != null) { bekleyen = null; alarmKur(z.first, z.second); return }
                if (b.deneme >= 1 || metin.isBlank()) { bekleyen = null; soyle("Saati anlayamadım, alarmı kurmadım.") }
                else { bekleyen = b.copy(deneme = 1); sorVeDinle("Saati tekrar söyler misin? Mesela altı buçuk.") }
            }
        }
    }

    private fun secimiAnla(secenekler: List<Kisi>, duyulanlar: List<String>): Kisi? {
        for (d in duyulanlar) {
            if (d.isBlank()) continue
            Turkce.ordinal(d)?.let { i -> return if (i == -1) secenekler.last() else secenekler.getOrNull(i) }
            val t = Turkce.norm(d)
            val ortak = Turkce.norm(secenekler.first().ad.split(' ').first())
            val adaylar = secenekler.filter { k ->
                val tokens = Turkce.norm(k.ad).split(' ').filter { it != ortak && it.length >= 2 }
                tokens.any { tok -> t.split(' ').any { w -> w.startsWith(tok.take(4)) || Turkce.similarity(w, tok) >= 0.75 } } ||
                    t.contains(Turkce.norm(k.etiket))
            }
            if (adaylar.size == 1) return adaylar[0]
        }
        return null
    }

    // ---------------------------------------------------------------- Ekran düğmeleri

    fun onayEvet() { val b = bekleyen as? Bekleyen.Onay ?: return; kulak.birak(); ses.sus(); bekleyen = null; b.evet() }
    fun onayHayir() { val b = bekleyen as? Bekleyen.Onay ?: return; kulak.birak(); ses.sus(); bekleyen = null; b.hayir() }
    fun onayDegistir() { val b = bekleyen as? Bekleyen.Onay ?: return; kulak.birak(); ses.sus(); bekleyen = null; (b.degistir ?: b.hayir)() }

    fun iptal(sessiz: Boolean = true) {
        bekleyen = null
        kulak.birak()
        sohbetIsi?.cancel()
        ses.sus()
        anaEkrana()
        if (!sessiz) soyle("Peki.")
    }

    fun anaEkrana() {
        _durum.update { it.copy(ekran = Ekran.Ana, isler = hafiza.isler()) }
    }

    fun sohbeteGit() { _durum.update { it.copy(ekran = Ekran.Sohbet) } }

    private fun isleriYenile() { _durum.update { it.copy(isler = hafiza.isler()) } }

    // ---------------------------------------------------------------- Derin Düşün (E4B)

    fun derinSec(ac: Boolean) {
        if (ac == derinIstek) return
        val derin = a.modelDerin
        if (ac && !derin.hazirMi()) {
            derin.baslat()
            derinIstek = true
            prefs.edit().putBoolean("derin", true).apply()
            _durum.update { it.copy(derinMod = true) }
            soyle("Derin Düşün için daha güçlü aklı indiriyorum. Yaklaşık 3,7 GB; inince kendiliğinden geçerim. O zamana kadar hızlı akılla devam ediyorum.")
            return
        }
        derinIstek = ac
        prefs.edit().putBoolean("derin", ac).apply()
        _durum.update { it.copy(derinMod = ac) }
        val hedef = if (ac) derin.dosya else model.dosya
        if (hedef.exists()) {
            soyle(if (ac) "Derin düşünmeye geçiyorum, bir dakika sürebilir." else "Hızlı moda geçiyorum.")
            scope.launch { akil.yukle(hedef) }
        }
    }

    // ---------------------------------------------------------------- Ayarlar ve Aşama 2

    fun ayarlar() { _durum.update { it.copy(ekran = Ekran.Ayarlar) } }

    fun uyanmaAyarla(ac: Boolean) {
        uyanma.acik = ac
        _durum.update { it.copy(uyanmaAcik = ac) }
        if (ac) {
            if (uyanma.modellerVar()) UyanikServis.baslat(a) else uyanma.indir()
            soyle(if (uyanma.modellerVar()) "Tamam. Artık “Hey Cezerî” dediğinde uyanırım." else "Önce uyanma modelini indiriyorum, birkaç dakika sürer.")
        } else {
            UyanikServis.durdur(a)
            uyanma.durdur()
        }
    }

    fun hassasiyetAyarla(v: Float) {
        uyanma.hassasiyet = v
        _durum.update { it.copy(uyanmaHassasiyet = v) }
    }

    fun uyanmaModeliIndir() = uyanma.indir()

    val uyanmaTeshis get() = uyanma.teshis
    fun uyanmaServisiCalisiyor(): Boolean = uyanma.servisCalisiyor
    fun uyanmaDinliyor(): Boolean = uyanma.dinliyor.value

    fun sesKaydiBaslat() {
        if (!uyanma.modellerVar()) {
            uyanma.indir()
            soyle("Önce ses tanıma modelini indirmem gerekiyor. İnince tekrar dene.")
            return
        }
        ses.sus(); kulak.birak()
        uyanma.duraklatildi = true
        uyanma.durdur()
        kayitVektorleri.clear()
        kayitAdimi(ilk = true)
    }

    private fun kayitAdimi(ilk: Boolean = false, tekrar: Boolean = false) {
        val n = kayitVektorleri.size
        val mesaj = when {
            tekrar -> "Bu kayıt net olmadı, bir daha söyler misin?"
            ilk -> "Şimdi sesini tanıyacağım. Ben söyle dediğimde “Hey Cezerî” de."
            n == 4 -> "Şimdi dışarıda ya da biraz gürültülü bir yerde söyle."
            else -> "Güzel. Bir daha söyle."
        }
        _durum.update { it.copy(ekran = Ekran.SesKaydi(n, ORNEK, mesaj)) }
        ses.sus()
        ses.konus(mesaj) {
            if (_durum.value.ekran !is Ekran.SesKaydi) return@konus
            uyanma.ornekAl { v, _ ->
                if (_durum.value.ekran !is Ekran.SesKaydi) return@ornekAl
                if (v == null) { kayitAdimi(tekrar = true); return@ornekAl }
                kayitVektorleri.add(v)
                if (kayitVektorleri.size >= ORNEK) {
                    uyanma.kimligiKaydet(kayitVektorleri)
                    uyanma.duraklatildi = false
                    _durum.update { it.copy(ekran = Ekran.SesKaydi(ORNEK, ORNEK, "Sesini tanıdım. Artık yalnızca sana cevap veririm.", bitti = true), kimlikOrnek = uyanma.ornekSayisi()) }
                    soyle("Sesini tanıdım. Artık yalnızca sana cevap veririm.")
                } else kayitAdimi()
            }
        }
    }

    fun sesKaydiTekrar() {
        uyanma.durdur()
        kayitAdimi(tekrar = true)
    }

    fun sesKaydiKapat() {
        uyanma.durdur()
        uyanma.duraklatildi = false
        ses.sus()
        _durum.update { it.copy(ekran = Ekran.Ayarlar, kimlikOrnek = uyanma.ornekSayisi()) }
    }

    fun kimligiSil() {
        uyanma.kimligiSil()
        _durum.update { it.copy(kimlikOrnek = 0) }
    }

    // ---------------------------------------------------------------- Sohbet

    private fun sohbet(metin: String) {
        sohbetIsi?.cancel()
        _durum.update { it.copy(ekran = Ekran.Sohbet, sohbet = it.sohbet + SohbetMesaji(true, metin)) }
        when (akil.durum.value) {
            Akil.Durum.HAZIR -> Unit
            Akil.Durum.YUKLENIYOR -> { cevapEkle("Bir saniye, aklımı topluyorum. Birazdan tekrar sorar mısın?"); return }
            Akil.Durum.HATA -> { cevapEkle("Aklımı yüklerken bir sorun oldu. Uygulamayı kapatıp yeniden açar mısın?"); return }
            Akil.Durum.MODEL_YOK -> {
                val m = model.durum.value
                cevapEkle(
                    if (m is ModelIndirici.Durum.Iniyor) "Aklım hâlâ hazırlanıyor, indirme yüzde ${(m.inen * 100 / maxOf(1, m.toplam))}'de. Bu arada arama, mesaj ve alarm gibi işleri yapabilirim."
                    else "Sohbet edebilmem için önce aklımı indirmem gerekiyor. Ayarlardan indirmeyi başlatabilirsin."
                )
                return
            }
        }
        _durum.update { it.copy(dusunuyor = true, sohbet = it.sohbet + SohbetMesaji(false, "", bitti = false)) }
        sohbetIsi = scope.launch {
            val tampon = StringBuilder()
            val tum = StringBuilder()
            var ilk = true
            try {
                akil.sor(metin)
                    .catch { e -> throw e }
                    .collect { parca ->
                        tum.append(parca)
                        tampon.append(parca)
                        sonMesajiGuncelle(temizMetin(tum.toString()), bitti = false)
                        if (ilk) { _durum.update { it.copy(dusunuyor = false) }; ilk = false }
                        cumleleriSoyle(tampon, son = false)
                    }
                cumleleriSoyle(tampon, son = true)
                var cevap = temizMetin(tum.toString())
                // Cümle yarıda kaldıysa (bağlam doldu) sohbeti tazeleyip bir kez yeniden sor
                if (!tekrarDenendi && cevap.isNotBlank() && !Regex("[.!?…)\"”]$").containsMatchIn(cevap)) {
                    tekrarDenendi = true
                    akil.tazele()
                    val ek = StringBuilder()
                    akil.sor("Son cevabın yarıda kesildi. Aynı soruya kısa ve tam bir cevap ver: $metin").collect { p ->
                        ek.append(p)
                        sonMesajiGuncelle(temizMetin(ek.toString()), bitti = false)
                    }
                    if (ek.isNotBlank()) {
                        cevap = temizMetin(ek.toString())
                        ses.sus(); ses.konus(cevap)
                    }
                }
                tekrarDenendi = false
                akil.turuKaydet(metin, cevap)
                sonMesajiGuncelle(cevap, bitti = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                sonMesajiGuncelle(temizMetin(tum.toString()), bitti = true)
                throw e
            } catch (e: Throwable) {
                sonMesajiGuncelle(if (tum.isBlank()) "Bir sorun oldu, bir daha dener misin?" else temizMetin(tum.toString()), bitti = true)
                if (tum.isBlank()) soyle("Bir sorun oldu, bir daha dener misin?")
            } finally {
                _durum.update { it.copy(dusunuyor = false) }
            }
        }
    }

    private var tekrarDenendi = false

    private fun cumleleriSoyle(tampon: StringBuilder, son: Boolean) {
        while (true) {
            val s = tampon.toString()
            val idx = Regex("[.!?…:;](\\s|$)").find(s)?.range?.first
            if (idx == null || (idx == s.length - 1 && !son)) break
            val cumle = temizMetin(s.substring(0, idx + 1))
            tampon.delete(0, idx + 1)
            if (cumle.isNotBlank()) ses.ekle(cumle)
        }
        if (son) {
            val kalan = temizMetin(tampon.toString())
            tampon.clear()
            if (kalan.isNotBlank()) ses.ekle(kalan)
        }
    }

    private fun temizMetin(s: String): String =
        s.replace(Regex("[*#`_>]+"), "").replace(Regex("\\s+"), " ").trim()

    private fun sonMesajiGuncelle(metin: String, bitti: Boolean) {
        _durum.update { d ->
            val l = d.sohbet.toMutableList()
            if (l.isNotEmpty() && !l.last().benden) l[l.lastIndex] = SohbetMesaji(false, metin, bitti)
            d.copy(sohbet = l)
        }
    }

    private fun cevapEkle(metin: String) {
        _durum.update { it.copy(sohbet = it.sohbet + SohbetMesaji(false, metin)) }
        soyle(metin)
    }

    fun yeniSohbet() {
        sohbetIsi?.cancel(); ses.sus()
        _durum.update { it.copy(sohbet = emptyList()) }
        scope.launch { if (akil.durum.value == Akil.Durum.HAZIR) akil.yeniSohbet() }
    }


    companion object { const val ORNEK = 6 }
}
