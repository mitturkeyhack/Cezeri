package com.erkam.cezeri

import android.speech.SpeechRecognizer
import com.erkam.cezeri.asistan.AramaKontrol
import com.erkam.cezeri.asistan.WhatsappErisim
import com.erkam.cezeri.asistan.WhatsappGonderim
import com.erkam.cezeri.core.Akil
import com.erkam.cezeri.core.Kanal
import com.erkam.cezeri.core.Kisi
import com.erkam.cezeri.core.KisiEsle
import com.erkam.cezeri.core.Namaz
import com.erkam.cezeri.core.Not
import com.erkam.cezeri.core.Komut
import com.erkam.cezeri.core.KomutCozucu
import com.erkam.cezeri.core.Kulak
import com.erkam.cezeri.core.Olcum
import com.erkam.cezeri.core.EvrenMetin
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
    data class GelenArama(val kim: String, val numara: String) : Ekran
    data class SesKaydi(val adim: Int, val toplam: Int, val mesaj: String, val bitti: Boolean = false) : Ekran
    data class Kible(val derece: Double, val yon: String) : Ekran
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
    val akil: Akil.Durum = Akil.Durum.MODEL_YOK,
    val bekliyor: Boolean = false,
    val uyanmaModel: Uyanma.ModelDurumu = Uyanma.ModelDurumu.Yok,
    val uyanmaAcik: Boolean = false,
    val kimlikOrnek: Int = 0,
    val derinMod: Boolean = false,
    val aramaHaber: Boolean = true,
    val uyanmaHassasiyet: Float = 0.5f,
    val notlar: List<Not> = emptyList(),
    val konum: Pair<Double, Double>? = null,
)

class Asistan(private val a: CezeriApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ses = a.ses
    private val akil = a.akil
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
        data class Onay(val evet: () -> Unit, val hayir: () -> Unit, val degistir: (() -> Unit)?, val deneme: Int = 0, val yumusak: Boolean = false) : Bekleyen
        data class Secim(val secenekler: List<Kisi>, val sec: (Kisi) -> Unit, val deneme: Int = 0) : Bekleyen
        data class Govde(val kisi: Kisi, val kanal: Kanal?) : Bekleyen
        data class AlarmSaati(val deneme: Int = 0) : Bekleyen
        data class Arama(val numara: String?, val deneme: Int = 0) : Bekleyen
    }

    private val uyanma = a.uyanma
    private val prefs = a.getSharedPreferences("asistan", android.content.Context.MODE_PRIVATE)
    private var derinIstek: Boolean = prefs.getBoolean("derin", false)
    private val kayitVektorleri = ArrayList<DoubleArray>()

    private var bekleyen: Bekleyen? = null
        set(v) { field = v; _durum.update { it.copy(bekliyor = v != null) } }
    private var sohbetIsi: Job? = null

    /** Kullanıcı sesle mi konuştu? Yazınca Cezerî sessiz cevap verir; kilit ekranında hep sesli. */
    private var sesli = false
    private var buTurYazili = false
    /** "onu ara", "ona yaz": son konuşulan kişi */
    private var sonKisi: Kisi? = null
    @Volatile private var modelEylemi = false
    private val anaIs = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        akil.notlar = { hafiza.notlar().map { it.metin } }
        _durum.update { it.copy(notlar = hafiza.notlar()) }
        scope.launch { a.konum.konum.collect { v -> _durum.update { it.copy(konum = v) } } }
        _durum.update { it.copy(derinMod = derinIstek, aramaHaber = prefs.getBoolean("arama_haber", true)) }
        akil.derin = derinIstek
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
        // Açılışta EVREN bağlantısını sessizce dene (model listesini de tazeler)
        if (akil.anahtar().isNotBlank()) scope.launch { akil.dene() }
    }

    // ---------------------------------------------------------------- Kurulum

    fun kurulumAdim(adim: Int) {
        _durum.update { it.copy(ekran = Ekran.Kurulum(adim)) }
    }

    fun karsila() = ses.konus("Selâm, ben Cezerî. Senin için arar, mesaj yazar, vakitleri hatırlatır ve sorularını cevaplarım.")

    fun kurulumBitir() {
        hafiza.kurulumBitti = true
        _durum.update { it.copy(ekran = Ekran.Ana) }
        ses.konus("Her şey hazır. Mikrofona dokunup benimle konuşabilirsin.")
    }

    // EVREN
    val evren get() = akil
    fun evrenAnahtar(a: String) { akil.anahtarKaydet(a); scope.launch { akil.dene() } }
    fun evrenDene() = scope.launch { akil.dene() }
    fun hizliModelSec(m: String) = akil.hizliSec(m)
    fun derinModelSec(m: String) = akil.derinSec(m)

    fun konusKisa(metin: String) = ses.konus(metin)

    fun arkaUc(): String = akil.arkaUc

    fun sesAdi(): String? = ses.secilenSes

    // Cezerî'nin sesi
    val sesMotoru get() = ses.motor
    val sesStili get() = ses.stil
    val cezeriSesDurumu get() = ses.cezeri.durum
    fun sesMotoruSec(m: String) = ses.motorSec(m)
    fun sesDene(stil: String) { kulak.birak(); ses.dene(stil) }
    fun cezeriSesiIndir() = ses.sesIndir()
    fun cezeriSesiSil() { ses.cezeri.sil() }

    // ---------------------------------------------------------------- Dinleme

    fun mikrofon() {
        if (_durum.value.dinliyor) { kulak.durdur(); return }
        sesli = true
        ses.sus()
        dinle()
    }

    /** Yan tuş / asistan kısayolu / "Hey Cezerî" ile açılınca */
    fun cagrildi() {
        if (_durum.value.ekran is Ekran.Kurulum) return
        sesli = true
        ses.konus("Buyur.") { dinle() }
    }

    private fun kilitli(): Boolean = try {
        a.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
    } catch (_: Exception) { false }

    private fun sesliMi(): Boolean = sesli || kilitli()

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
        if (sesliMi()) ses.konus(metin) { dinle() } else yaziyaEkle(metin)
    }

    private fun soyle(metin: String, sonra: (() -> Unit)? = null) {
        if (sesliMi()) ses.konus(metin, bitince = sonra)
        else { yaziyaEkle(metin); sonra?.invoke() }
    }

    /** Sessiz modda cevaplar sohbete yazılır. */
    private fun yaziyaEkle(metin: String) {
        Olcum.ilkCikti()
        _durum.update {
            val ekran = if (it.ekran is Ekran.Ana) Ekran.Sohbet else it.ekran
            it.copy(ekran = ekran, sohbet = it.sohbet + SohbetMesaji(false, metin))
        }
    }

    private fun kullaniciSatiri(metin: String) {
        if (buTurYazili) return
        buTurYazili = true
        _durum.update { it.copy(sohbet = it.sohbet + SohbetMesaji(true, metin)) }
    }

    /** Bilgi cevabı: sohbette gösterilir, sesli moddaysa okunur. */
    private fun cevapGoster(soru: String, cevap: String, sonra: (() -> Unit)? = null) {
        kullaniciSatiri(soru)
        _durum.update { it.copy(ekran = Ekran.Sohbet, sohbet = it.sohbet + SohbetMesaji(false, cevap)) }
        if (sesliMi()) ses.konus(cevap, bitince = sonra) else { Olcum.ilkCikti(); sonra?.invoke() }
    }

    /** İş bitince dönülecek ekran: sesle çalışırken ana ekran, yazarken sohbet. */
    private fun donus() {
        if (sesli || _durum.value.sohbet.isEmpty()) anaEkrana()
        else _durum.update { it.copy(ekran = Ekran.Sohbet, isler = hafiza.isler()) }
    }

    private fun duydu(s: Kulak.Sonuc) {
        when (s) {
            is Kulak.Sonuc.Metin -> {
                Olcum.sonucGeldi()
                buTurYazili = false
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
        Olcum.yazildi()
        sesli = false
        buTurYazili = false
        val ekran = _durum.value.ekran
        if (ekran is Ekran.Ana || ekran is Ekran.Sohbet) {
            kullaniciSatiri(metin)
            _durum.update { it.copy(ekran = Ekran.Sohbet) }
        } else kullaniciSatiri(metin)
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
            is Komut.Ara -> if (k.numara != null) aramaOnayla(numaraKisi(k.numara)) else kisiBul(k.isimVaryantlari, k.soylenen, mesajMi = false, tam = k.tam) { aramaOnayla(it) }
            Komut.Cagri -> sorVeDinle("Buyur, dinliyorum.")
            is Komut.Vakit -> vakitCevapla(k, metin)
            Komut.Kible -> kibleGoster(metin)
            is Komut.Hatirla -> {
                hafiza.notEkle(k.bilgi); notlariYenile()
                cevapGoster(metin, "Tamam, aklımda tutuyorum: ${k.bilgi}")
            }
            Komut.HafizaListe -> {
                val n = hafiza.notlar()
                cevapGoster(metin,
                    if (n.isEmpty()) "Henüz bana hatırlamam için bir şey söylemedin. “Şunu hatırla” diye başlaman yeterli."
                    else "Aklımda ${n.size} şey var. " + n.takeLast(5).reversed().joinToString(" ") { it.metin.trimEnd('.') + "." } + if (n.size > 5) " Gerisi Ayarlar'da." else ""
                )
            }
            is Komut.Unut -> {
                val t = Turkce.norm(k.sorgu)
                if (Regex("^(hepsini|her seyi|tumunu|butun notlari|hersey)$").matches(t)) {
                    val sayi = hafiza.notlar().size
                    hafiza.notlariTemizle(); notlariYenile()
                    cevapGoster(metin, if (sayi == 0) "Zaten aklımda bir şey yoktu." else "Tamam, $sayi bilginin hepsini unuttum.")
                } else {
                    val n = hafiza.notBul(k.sorgu)
                    if (n == null) cevapGoster(metin, "Bununla ilgili aklımda bir şey yok.")
                    else { hafiza.notSil(n.id); notlariYenile(); cevapGoster(metin, "Unuttum: ${n.metin}") }
                }
            }
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
                else cevapGoster(metin, "Sonuç ${com.erkam.cezeri.core.Hesap.bicimle(v)}.")
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
            rehber.kisiler(KomutCozucu.isimVaryant(sorgu, Turkce.Ek.BELIRTME)).map { it.kisi }
        }
    }

    private fun rehberCevapla(k: Komut.RehberSor, metin: String) {
        if (!rehber.izinVar()) { cevapGoster(metin, "Rehberine erişemiyorum. Kurulumdan rehber iznini vermen gerekiyor."); return }
        val rakamMi = k.sorgu.count { it.isDigit() } >= 7
        if (rakamMi) {
            val l = rehberBul(k.sorgu)
            if (l.isEmpty()) cevapGoster(metin, "Bu numara rehberinde kayıtlı değil.")
            else { sonKisi = l.first(); cevapGoster(metin, "Bu numara rehberinde ${l.first().ad} olarak kayıtlı.") }
            return
        }
        val es = rehber.kisiler(KomutCozucu.isimVaryant(k.sorgu, Turkce.Ek.BELIRTME))
        val birebir = es.filter { it.puan >= KisiEsle.BIREBIR }
        val guclu = es.filter { it.puan >= KisiEsle.GUCLU }
        val ad = k.sorgu.trim().replaceFirstChar { it.titlecase(Turkce.TR) }
        fun liste(l: List<com.erkam.cezeri.core.Eslesme>) = l.take(3).joinToString(", ") { it.kisi.ad }
        when {
            es.isEmpty() -> cevapGoster(metin, "Rehberinde “$ad” diye biri yok.")
            birebir.size == 1 || (birebir.isEmpty() && guclu.size == 1) -> {
                val kisi = (birebir.firstOrNull() ?: guclu.first()).kisi
                sonKisi = kisi
                val bas = when {
                    k.numaraIste -> "${kisi.ad}: ${bicimliNumara(kisi.numara)}."
                    birebir.isNotEmpty() -> "Evet, ${kisi.ad} rehberinde kayıtlı."
                    else -> "Tam “$ad” diye biri yok ama ${kisi.ad} var."
                }
                val diger = es.size - 1
                val ek = if (diger > 0 && !k.numaraIste) " Benzer isimde $diger kişi daha var." else ""
                yumusakSor(metin, "$bas$ek Arayayım mı?", kisi)
            }
            birebir.size > 1 -> {
                val c = "Rehberinde ${birebir.size} tane ${birebir.first().kisi.ad} var: ${liste(birebir)}" +
                    (if (k.numaraIste) ". " + birebir.take(3).joinToString(". ") { "${it.kisi.ad}: ${bicimliNumara(it.kisi.numara)}" } else "") + "."
                cevapGoster(metin, c)
            }
            else -> {
                val l = if (guclu.isNotEmpty()) guclu else es
                val c = "“$ad” diye birebir kayıtlı biri yok. Benzer ${l.size} kişi var" + (if (l.size > 3) ", ilk üçü: " else ": ") + liste(l) + "."
                cevapGoster(metin, c)
            }
        }
    }

    /** Bilgi verdikten sonra "Arayayım mı?" gibi yumuşak bir soru: cevap evet/hayır değilse yeni komut sayılır. */
    private fun yumusakSor(soru: String, cevap: String, kisi: Kisi) {
        bekleyen = Bekleyen.Onay(
            evet = { aramaOnayla(kisi) },
            hayir = { soyle("Peki.") },
            degistir = null,
            yumusak = true,
        )
        cevapGoster(soru, cevap) { if (sesliMi() && bekleyen is Bekleyen.Onay) dinle() }
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

    private fun kisiBul(varyantlar: List<String>, soylenen: String, mesajMi: Boolean, tam: Boolean = false, devam: (Kisi) -> Unit) {
        // "onu ara", "ona yaz": son konuşulan kişi
        if (Turkce.lower(soylenen.trim()) in KomutCozucu.ZAMIRLER || soylenen.isBlank()) {
            val s = sonKisi
            if (s != null) { devam(s); return }
            soyle(if (mesajMi) "Kime yazayım?" else "Kimi arayayım?")
            return
        }
        if (!rehber.izinVar()) { soyle("Rehberine erişemiyorum. Kurulumdan rehber iznini vermen gerekiyor."); return }
        if (!tam) hafiza.secim(soylenen)?.let { anahtar -> rehber.idIle(anahtar)?.let { sonKisi = it; devam(it); return } }
        val es = rehber.kisiler(varyantlar)
        when (val kr = KisiEsle.karar(es.map { KisiEsle.Sonuc(it.kisi, it.puan) }, tam)) {
            is KisiEsle.Karar.Yok -> soyle(if (tam) "Rehberde tam “$soylenen” diye kayıtlı birini bulamadım." else "Rehberde “$soylenen” diye birini bulamadım.")
            is KisiEsle.Karar.Tek -> { sonKisi = kr.oge; devam(kr.oge) }
            is KisiEsle.Karar.Sor -> {
                val secenekler = kr.adaylar
                val sayi = listOf("", "bir", "iki", "üç")[secenekler.size]
                val ortak = secenekler.first().ad.split(' ').first()
                val soru = "Rehberinde $sayi $ortak var. Hangisini ${if (mesajMi) "seçeyim" else "arayayım"}?"
                _durum.update { it.copy(ekran = Ekran.KisiSec(soru, secenekler, mesajMi)) }
                bekleyen = Bekleyen.Secim(secenekler, { k -> hafiza.secimKaydet(soylenen, k.anahtar); sonKisi = k; devam(k) })
                sorVeDinle(soru)
            }
        }
    }

    fun kisiSec(k: Kisi) {
        val b = bekleyen as? Bekleyen.Secim ?: return
        kulak.birak(); ses.sus()
        bekleyen = null
        b.sec(k)
    }

    private fun aramaOnayla(k: Kisi) {
        sonKisi = k
        _durum.update { it.copy(ekran = Ekran.AramaOnay(k)) }
        bekleyen = Bekleyen.Onay(
            evet = {
                hafiza.isEkle("ara", "${Turkce.ekle(k.ad, belirtme = true)} aradı", "Sesli onayla")
                donus()
                soyle("Tamam, arıyorum.") { eylem.ara(k.numara) }
            },
            hayir = { donus(); soyle("Peki, vazgeçtim.") },
            degistir = null,
        )
        sorVeDinle("${kime(k, belirtme = true)} arayayım mı?")
    }

    private fun mesajOnayla(k: Kisi, metin: String, kanal: Kanal) {
        sonKisi = k
        val kanal2 = if (kanal == Kanal.WHATSAPP && !eylem.whatsappVar()) Kanal.SMS else kanal
        _durum.update { it.copy(ekran = Ekran.MesajOnay(k, metin, kanal2)) }
        bekleyen = Bekleyen.Onay(
            evet = { gonder(k, metin, kanal2) },
            hayir = { donus(); soyle("Peki, vazgeçtim.") },
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
            hayir = { donus(); soyle("Peki, vazgeçtim.") },
            degistir = { bekleyen = Bekleyen.Govde(e.kisi, kanal); sorVeDinle("Yeni hâlini söyler misin?") },
        )
    }

    private fun gonder(k: Kisi, metin: String, kanal: Kanal) {
        donus()
        when (kanal) {
            Kanal.SMS -> {
                if (eylem.sms(k.numara, metin)) {
                    hafiza.isEkle("mesaj", "${Turkce.ekle(k.ad, belirtme = false)} mesaj gönderdi", "SMS")
                    soyle("Mesajını gönderdim.")
                } else soyle("Mesaj gönderilemedi. SMS iznini kontrol eder misin?")
            }
            Kanal.WHATSAPP -> {
                if (WhatsappErisim.acikMi(a)) {
                    WhatsappGonderim.bekle { ok ->
                        if (ok) {
                            hafiza.isEkle("mesaj", "${kime(k, belirtme = false)} WhatsApp mesajı gönderdi", "WhatsApp"); isleriYenile()
                            soyle("Mesajını WhatsApp'tan gönderdim.")
                        }
                    }
                    if (!eylem.whatsapp(k.numara, metin)) {
                        WhatsappGonderim.tamam(false); soyle("WhatsApp açılamadı.")
                    } else scope.launch {
                        delay(15_000)
                        if (WhatsappGonderim.bekliyor()) { WhatsappGonderim.tamam(false); soyle("Gönder tuşunu bulamadım, sen basar mısın?") }
                    }
                } else {
                    soyle("WhatsApp'ta mesajını hazırladım, gönder tuşuna basman yeterli. Ayarlardan WhatsApp otomatik gönderimi açarsan bunu da ben yaparım.") {
                        if (eylem.whatsapp(k.numara, metin)) hafiza.isEkle("mesaj", "${kime(k, belirtme = false)} WhatsApp mesajı", "WhatsApp")
                    }
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
                val cvp = if (metin.isBlank()) Turkce.Cevap.BILINMIYOR else Turkce.answer(metin)
                if (b.yumusak && cvp != Turkce.Cevap.EVET && cvp != Turkce.Cevap.HAYIR) {
                    // Bilgi sorusunun ardından gelen başka bir istek: yeni komut olarak işle
                    bekleyen = null
                    if (metin.isNotBlank()) isle(metin, listOf(metin) + alternatifler)
                    return
                }
                when (cvp) {
                    Turkce.Cevap.EVET -> { bekleyen = null; b.evet() }
                    Turkce.Cevap.HAYIR -> { bekleyen = null; b.hayir() }
                    Turkce.Cevap.DEGISTIR -> { bekleyen = null; (b.degistir ?: b.hayir)() }
                    Turkce.Cevap.BILINMIYOR -> {
                        if (b.deneme >= 1) { bekleyen = null; donus(); soyle("Cevap gelmedi, işlemi iptal ettim.") }
                        else { bekleyen = b.copy(deneme = b.deneme + 1); sorVeDinle("Evet mi, hayır mı?") }
                    }
                }
            }
            is Bekleyen.Secim -> {
                if (metin.isNotBlank() && Turkce.answer(metin) == Turkce.Cevap.HAYIR) { iptal(sessiz = false); return }
                val secilen = secimiAnla(b.secenekler, listOf(metin) + alternatifler)
                if (secilen != null) { bekleyen = null; b.sec(secilen); return }
                if (b.deneme >= 1) { bekleyen = null; donus(); soyle("Anlayamadım, işlemi iptal ettim.") }
                else { bekleyen = b.copy(deneme = b.deneme + 1); sorVeDinle(if (b.secenekler.size == 2) "Birinciyi mi, ikinciyi mi?" else "Kaçıncısı?") }
            }
            is Bekleyen.Govde -> {
                if (metin.isBlank()) { bekleyen = null; donus(); soyle("Cevap gelmedi, işlemi iptal ettim."); return }
                if (Turkce.norm(metin) in setOf("iptal", "vazgec", "bosver")) { iptal(sessiz = false); return }
                bekleyen = null
                val m = metin.trim().replaceFirstChar { it.titlecase(Turkce.TR) }
                mesajOnayla(b.kisi, m, b.kanal ?: Kanal.SMS)
            }
            is Bekleyen.Arama -> {
                val t = Turkce.norm(metin)
                when {
                    Regex("mesgul|musait degil|sonra ara|toplanti|yazarsin|mesaj at").containsMatchIn(t) -> aramaMesgul()
                    Regex("\\b(reddet|kapat|acma|istemiyorum|hayir|bosver|sustur)\\b").containsMatchIn(t) -> aramaReddet()
                    Regex("\\b(ac|cevapla|evet|olur|tamam|ac bakalim|baglan)\\b").containsMatchIn(t) -> aramaAc()
                    b.deneme >= 1 || metin.isBlank() && b.deneme >= 1 -> { bekleyen = null; AramaKontrol.ziliAc(a) }
                    else -> { bekleyen = b.copy(deneme = b.deneme + 1); sorVeDinle("Açayım mı, reddedeyim mi?") }
                }
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
        donus()
        if (!sessiz) soyle("Peki.")
    }

    fun anaEkrana() {
        _durum.update { it.copy(ekran = Ekran.Ana, isler = hafiza.isler()) }
    }

    fun sohbeteGit() { _durum.update { it.copy(ekran = Ekran.Sohbet) } }

    private fun isleriYenile() { _durum.update { it.copy(isler = hafiza.isler()) } }

    // ---------------------------------------------------------------- Gelen arama (Aşama 3)

    private var aramada = false

    fun aramaHaberAyarla(ac: Boolean) {
        prefs.edit().putBoolean("arama_haber", ac).apply()
        _durum.update { it.copy(aramaHaber = ac) }
    }

    fun gelenArama(numara: String?) {
        if (!hafiza.kurulumBitti || aramada || !prefs.getBoolean("arama_haber", true)) return
        aramada = true
        sesli = true
        val kisi = numara?.let { n -> if (rehber.izinVar()) rehberBul(n).firstOrNull() else null }
        val kim = kisi?.ad ?: if (numara.isNullOrBlank()) "Bir numara" else "Kayıtlı olmayan bir numara"
        ses.sus(); kulak.birak(); uyanma.durdur()
        sohbetIsi?.cancel()
        AramaKontrol.ziliKis(a)
        _durum.update { it.copy(ekran = Ekran.GelenArama(kim, numara?.let { bicimliNumara(it) } ?: "")) }
        bekleyen = Bekleyen.Arama(numara)
        scope.launch {
            delay(500)
            if (bekleyen is Bekleyen.Arama) sorVeDinle("$kim arıyor. Açayım mı?")
        }
    }

    fun aramaBitti() {
        if (!aramada) return
        aramada = false
        AramaKontrol.ziliAc(a)
        if (bekleyen is Bekleyen.Arama) { bekleyen = null; kulak.birak(); ses.sus() }
        if (_durum.value.ekran is Ekran.GelenArama) anaEkrana()
    }

    fun aramaAc() {
        val b = bekleyen as? Bekleyen.Arama
        bekleyen = null; kulak.birak(); ses.sus()
        if (!AramaKontrol.izinVar(a)) { AramaKontrol.ziliAc(a); soyle("Aramayı açabilmem için izin vermen gerekiyor. Ayarlardan verebilirsin."); return }
        if (!AramaKontrol.ac(a)) { AramaKontrol.ziliAc(a); soyle("Açamadım, ekrandan açar mısın?") }
        else b?.numara?.let { hafiza.isEkle("ara", "Gelen aramayı açtı", bicimliNumara(it)) }
    }

    fun aramaReddet() {
        bekleyen = null; kulak.birak(); ses.sus()
        AramaKontrol.ziliAc(a)
        if (!AramaKontrol.izinVar(a) || !AramaKontrol.reddet(a)) { soyle("Reddedemedim, ekrandan kapatır mısın?"); return }
        soyle("Reddettim.")
    }

    fun aramaMesgul() {
        val numara = (bekleyen as? Bekleyen.Arama)?.numara
        bekleyen = null; kulak.birak(); ses.sus()
        AramaKontrol.ziliAc(a)
        if (!AramaKontrol.izinVar(a) || !AramaKontrol.reddet(a)) { soyle("Reddedemedim, ekrandan kapatır mısın?"); return }
        if (!numara.isNullOrBlank() && eylem.sms(numara, "Şu an müsait değilim, sonra ararım.")) {
            hafiza.isEkle("mesaj", "Meşgul mesajı gönderdi", bicimliNumara(numara)); isleriYenile()
            soyle("Reddettim ve müsait olmadığını yazdım.")
        } else soyle("Reddettim.")
    }

    // ---------------------------------------------------------------- Derin Düşün (E4B)

    fun derinSec(ac: Boolean) {
        if (ac == derinIstek) return
        derinIstek = ac
        akil.derin = ac
        prefs.edit().putBoolean("derin", ac).apply()
        _durum.update { it.copy(derinMod = ac) }
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
        kullaniciSatiri(metin)
        _durum.update { it.copy(ekran = Ekran.Sohbet) }
        modelEylemi = false
        if (akil.durum.value == Akil.Durum.MODEL_YOK) {
            cevapEkle("Sohbet edebilmem için EVREN anahtarı gerekiyor. Ayarlar'daki Akıl bölümünden ekleyebilirsin. Arama, mesaj, vakit gibi işleri yine yaparım.")
            return
        }
        _durum.update { it.copy(dusunuyor = true, sohbet = it.sohbet + SohbetMesaji(false, "", bitti = false)) }
        sohbetIsi = scope.launch {
            val tampon = StringBuilder()
            val tum = StringBuilder()
            var ilk = true
            try {
                Olcum.istek()
                akil.sor(metin)
                    .collect { parca ->
                        if (modelEylemi) return@collect
                        Olcum.ilkKelime()
                        tum.append(parca)
                        // Cevap bir iş etiketiyle başlıyorsa ([[ARA: …]]) gösterme; etiket tamamlanınca işi başlat
                        val bas = tum.trimStart()
                        if (bas.startsWith("[")) {
                            if (bas.length < 2 || bas.startsWith("[[")) {
                                val e = EvrenMetin.etiket(bas.toString())
                                if (e != null) { modelEylemi = true; etiketIsle(e, metin) }
                                return@collect
                            }
                        }
                        tampon.append(parca)
                        sonMesajiGuncelle(temizMetin(tum.toString()), bitti = false)
                        if (ilk) { _durum.update { it.copy(dusunuyor = false) }; ilk = false; if (!sesliMi()) Olcum.ilkCikti() }
                        cumleleriSoyle(tampon, son = false)
                    }
                if (!modelEylemi && tum.trimStart().startsWith("[[")) {
                    // Etiket kapanmadan bittiyse yine de yorumla
                    EvrenMetin.etiket(tum.toString() + "]]")?.let { modelEylemi = true; etiketIsle(it, metin) }
                }
                if (modelEylemi) {
                    // İşi uygulama devraldı (etiketIsle); model cümlesi gösterilmez
                    akil.turuKaydet(metin, "(İstek uygulamaya iletildi.)")
                    return@launch
                }
                cumleleriSoyle(tampon, son = true)
                var cevap = temizMetin(tum.toString())
                if (cevap.isBlank()) {
                    cevap = "Bir cevap gelmedi, bir daha sorar mısın?"
                    if (sesliMi()) ses.konus(cevap)
                }
                akil.turuKaydet(metin, cevap)
                sonMesajiGuncelle(cevap, bitti = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (!modelEylemi) sonMesajiGuncelle(temizMetin(tum.toString()), bitti = true)
                throw e
            } catch (e: Throwable) {
                val neden = (e as? Akil.EvrenHatasi)?.kullaniciya ?: "Bir sorun oldu, bir daha dener misin?"
                val komutlar = " Arama, mesaj, vakit ve alarm gibi işleri yine yapabilirim."
                val c = if (e is Akil.EvrenHatasi && neden.contains("ulaşamadım")) "Şu an internete ulaşamıyorum.$komutlar" else neden
                sonMesajiGuncelle(if (tum.isBlank()) c else temizMetin(tum.toString()), bitti = true)
                if (tum.isBlank() && sesliMi()) ses.konus(c)
            } finally {
                _durum.update { it.copy(dusunuyor = false) }
            }
        }
    }

    /** Modelin verdiği iş etiketi ([[ARA: …]], [[VAKIT]] …): işi uygulama telefonda yapar. */
    private fun etiketIsle(e: Triple<String, String, String>, metin: String) {
        val (tur, x, y) = e
        anaIs.post {
            // Model cümlesini gösterme: akışı uygulama devraldı
            _durum.update { d -> d.copy(dusunuyor = false, sohbet = d.sohbet.toMutableList().also { l -> if (l.isNotEmpty() && !l.last().benden) l.removeAt(l.lastIndex) }) }
            buTurYazili = true
            when (tur) {
                "ARA" -> modelEylem("ara", x, "")
                "MESAJ" -> modelEylem("mesaj", x, y)
                "ALARM" -> modelEylem("alarm", x, "")
                "HATIRLA" -> if (x.isNotBlank()) {
                    val b = x.trim().replaceFirstChar { it.titlecase(Turkce.TR) }
                    hafiza.notEkle(b); notlariYenile()
                    cevapGoster(metin, "Tamam, aklımda tutuyorum: $b")
                }
                "VAKIT" -> vakitCevapla(KomutCozucu.coz(metin) as? Komut.Vakit ?: Komut.Vakit(null), metin)
                "REHBER" -> rehberCevapla(Komut.RehberSor(x.ifBlank { metin }, Regex("numara").containsMatchIn(Turkce.lower(metin))), metin)
                else -> cevapGoster(metin, "Bunu tam anlayamadım, bir daha söyler misin?")
            }
        }
    }

    private fun cumleleriSoyle(tampon: StringBuilder, son: Boolean) {
        while (true) {
            val s = tampon.toString()
            val idx = Regex("[.!?…:;](\\s|$)").find(s)?.range?.first
            if (idx == null || (idx == s.length - 1 && !son)) break
            val cumle = temizMetin(s.substring(0, idx + 1))
            tampon.delete(0, idx + 1)
            if (cumle.isNotBlank() && sesliMi()) ses.ekle(cumle)
        }
        if (son) {
            val kalan = temizMetin(tampon.toString())
            tampon.clear()
            if (kalan.isNotBlank() && sesliMi()) ses.ekle(kalan)
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
        if (sesliMi()) ses.konus(metin)
    }

    fun yeniSohbet() {
        sohbetIsi?.cancel(); ses.sus()
        _durum.update { it.copy(sohbet = emptyList()) }
        akil.yeniSohbet()
    }


    // ---------------------------------------------------------------- Modelin başlattığı işler

    /** Model bir iş etiketi verince gelir. */
    private fun modelEylem(tur: String, x: String, y: String): String {
        return when (tur) {
            "ara" -> {
                modelEylemi = true
                anaIs.post {
                    ses.sus()
                    val n = x.filter { it.isDigit() }
                    if (n.length >= 7) aramaOnayla(numaraKisi(x))
                    else kisiBul(KomutCozucu.isimVaryant(x, Turkce.Ek.BELIRTME), x, mesajMi = false) { aramaOnayla(it) }
                }
                "Uygulama arama onayını kullanıcıya soruyor. Başka bir şey söyleme."
            }
            "mesaj" -> {
                modelEylemi = true
                anaIs.post {
                    ses.sus()
                    val n = x.filter { it.isDigit() }
                    val govde = y.trim().replaceFirstChar { it.titlecase(Turkce.TR) }
                    val devam: (Kisi) -> Unit = { k ->
                        if (govde.isBlank()) { bekleyen = Bekleyen.Govde(k, null); sorVeDinle("Ne yazayım?") }
                        else mesajOnayla(k, govde, Kanal.SMS)
                    }
                    if (n.length >= 7) devam(numaraKisi(x))
                    else kisiBul(KomutCozucu.isimVaryant(x, Turkce.Ek.YONELME), x, mesajMi = true, devam = devam)
                }
                "Uygulama mesaj onayını kullanıcıya soruyor. Başka bir şey söyleme."
            }
            "alarm" -> {
                val z = Turkce.parseTime(x) ?: return "Saat anlaşılamadı; kullanıcıya saati sor."
                modelEylemi = true
                anaIs.post { ses.sus(); alarmKur(z.first, z.second) }
                "Alarm kuruluyor."
            }
            "hatirla" -> {
                if (x.isBlank()) return "Bilgi boş."
                hafiza.notEkle(x.trim().replaceFirstChar { it.titlecase(Turkce.TR) })
                anaIs.post { notlariYenile() }
                "Kaydedildi. Kullanıcıya kısaca aklında tuttuğunu söyle."
            }
            "vakit" -> {
                val k = a.konum.kayitli() ?: return "Konum bilinmiyor; kullanıcı Ayarlar'dan konum izni vermeli."
                val g = Namaz.bugun(k.first, k.second)
                val (v, saat, kalan) = Namaz.siradaki(k.first, k.second)
                Namaz.Vakit.entries.joinToString(", ") { "${it.ad} ${g.saat(it)}" } +
                    ". Sıradaki: ${v.ad} ${"%02d:%02d".format(saat / 60 % 24, saat % 60)}, ${Namaz.sureMetni(kalan)} kaldı."
            }
            else -> "Bilinmeyen istek"
        }
    }

    // ---------------------------------------------------------------- Aşama 4: vakit, kıble, hafıza

    private fun konumla(metin: String, is_: (Pair<Double, Double>) -> Unit) {
        val k = a.konum.kayitli()
        if (k != null) { is_(k); a.konum.guncelle(); return }
        if (!a.konum.izinVar()) {
            cevapGoster(metin, "Vakitleri ve kıbleyi hesaplamam için konum izni gerekiyor. Ayarlar'daki Namaz bölümünden verebilirsin.")
            return
        }
        cevapGoster(metin, "Konumunu buluyorum, bir saniye.")
        buTurYazili = true
        a.konum.guncelle { y ->
            if (y == null) soyle("Konumunu bulamadım. Konum servisini açıp tekrar dener misin?")
            else { buTurYazili = true; is_(y) }
        }
    }

    private fun hhmm(dk: Int) = "%02d:%02d".format((dk / 60) % 24, dk % 60)

    private fun vakitCevapla(k: Komut.Vakit, metin: String) = konumla(metin) { (enlem, boylam) ->
        val g = Namaz.bugun(enlem, boylam)
        val cal = java.util.Calendar.getInstance()
        val simdi = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val cevap = when {
            k.hepsi -> "Bugün imsak ${g.saat(Namaz.Vakit.IMSAK)}, güneş ${g.saat(Namaz.Vakit.GUNES)}, öğle ${g.saat(Namaz.Vakit.OGLE)}, " +
                "ikindi ${g.saat(Namaz.Vakit.IKINDI)}, akşam ${g.saat(Namaz.Vakit.AKSAM)}, yatsı ${g.saat(Namaz.Vakit.YATSI)}."
            k.hangi != null -> {
                val v = Namaz.Vakit.bul(k.hangi) ?: Namaz.Vakit.OGLE
                val t = g.dakikalar[v] ?: 0
                val iftar = v == Namaz.Vakit.AKSAM && Turkce.lower(metin).contains("iftar")
                val ad = if (iftar) "İftar" else if (v == Namaz.Vakit.GUNES) "Güneş" else "${v.ad} ezanı"
                if (t > simdi) "$ad ${hhmm(t)}'${ekDe(t)}. ${Namaz.sureMetni(t - simdi)} kaldı."
                else {
                    val (s2, st, kalan) = Namaz.siradaki(enlem, boylam)
                    "${v.ad} bugün ${hhmm(t)}'${ekDe(t)} girdi. Sıradaki ${s2.ad}, ${hhmm(st)}'${ekDe(st)}; ${Namaz.sureMetni(kalan)} var."
                }
            }
            else -> {
                val (v, t, kalan) = Namaz.siradaki(enlem, boylam)
                "Sıradaki vakit ${v.ad.lowercase(Turkce.TR)}, ${hhmm(t)}'${ekDe(t)}. ${Namaz.sureMetni(kalan)} kaldı."
            }
        }
        cevapGoster(metin, cevap)
    }

    /** "12:30'da", "17:40'ta": son sayının okunuşuna göre -da/-de/-ta/-te */
    private fun ekDe(dk: Int): String {
        val d = dk % 60
        val okunus = com.erkam.cezeri.ses.SayiOku.yaz((if (d == 0) (dk / 60) % 24 else d).toLong())
        val son = okunus.last()
        val sert = son in "fstkçşhp"
        val unlu = okunus.lastOrNull { it in "aeıioöuü" } ?: 'e'
        val kalin = unlu in "aıou"
        return (if (sert) "t" else "d") + (if (kalin) "a" else "e")
    }

    private fun kibleGoster(metin: String) = konumla(metin) { (enlem, boylam) ->
        val d = Namaz.kible(enlem, boylam)
        val yon = Namaz.yonAdi(d)
        kullaniciSatiri(metin)
        _durum.update { it.copy(ekran = Ekran.Kible(d, yon)) }
        val c = "Kıble, kuzeyden ${Math.round(d)} derece, $yon yönünde. Telefonu düz tut; ok altın rengine dönünce Kâbe'ye bakıyorsun."
        _durum.update { it.copy(sohbet = it.sohbet + SohbetMesaji(false, c)) }
        if (sesliMi()) ses.konus(c)
    }

    fun kibleAc() = kibleGoster("Kıble ne tarafta?")

    /** Vakit hatırlatıcısı sesli seçiliyse */
    fun vakitHatirlat(metin: String) {
        if (bekleyen != null || _durum.value.dinliyor || aramada) return
        ses.konus(metin)
    }

    fun siradakiVakit(): Triple<Namaz.Vakit, Int, Int>? = a.konum.kayitli()?.let { Namaz.siradaki(it.first, it.second) }
    fun bugunkuVakitler(): Namaz.Gun? = a.konum.kayitli()?.let { Namaz.bugun(it.first, it.second) }
    fun konumGuncelle() = a.konum.guncelle { com.erkam.cezeri.asistan.VakitHatirlatici.planla(a) }
    fun konumIzniVar() = a.konum.izinVar()

    private fun notlariYenile() {
        _durum.update { it.copy(notlar = hafiza.notlar()) }

    }

    fun notSil(id: Long) { hafiza.notSil(id); notlariYenile() }

    companion object { const val ORNEK = 6 }
}
