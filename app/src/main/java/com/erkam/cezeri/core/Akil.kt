package com.erkam.cezeri.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cezerî'nin aklı: EVREN (OpenAI uyumlu API). Cevap akış hâlinde gelir; ilk cümle hazır olunca konuşma başlar.
 * Rehber, numaralar ve mesaj içerikleri buraya gönderilmez: o işler telefonda yapılır.
 */
class Akil(private val ctx: Context) {

    /** MODEL_YOK = anahtar girilmemiş */
    enum class Durum { MODEL_YOK, YUKLENIYOR, HAZIR, HATA }

    data class Test(val basarili: Boolean, val ms: Long, val mesaj: String, val modeller: List<String>)

    private val prefs = ctx.getSharedPreferences("evren", Context.MODE_PRIVATE)

    private val _durum = MutableStateFlow(if (anahtar().isBlank()) Durum.MODEL_YOK else Durum.HAZIR)
    val durum: StateFlow<Durum> = _durum
    var hataMesaji: String? = null
        private set

    private val _modeller = MutableStateFlow(prefs.getString("modeller", "").orEmpty().split(',').filter { it.isNotBlank() })
    val modeller: StateFlow<List<String>> = _modeller
    private val _hizliModel = MutableStateFlow(prefs.getString("hizli", VARSAYILAN_HIZLI) ?: VARSAYILAN_HIZLI)
    val hizliModel: StateFlow<String> = _hizliModel
    private val _derinModel = MutableStateFlow(prefs.getString("derin", VARSAYILAN_DERIN) ?: VARSAYILAN_DERIN)
    val derinModel: StateFlow<String> = _derinModel
    private val _sonTest = MutableStateFlow<Test?>(null)
    val sonTest: StateFlow<Test?> = _sonTest

    /** Derin düşün açıksa büyük model kullanılır */
    @Volatile var derin: Boolean = false

    /** Sohbet ekranında gösterilen model adı */
    val arkaUc: String get() = if (derin) _derinModel.value else _hizliModel.value

    /** Kullanıcının "hatırla" dediği bilgiler; sistem talimatına eklenir. */
    var notlar: () -> List<String> = { emptyList() }

    private val gecmis = ArrayList<Pair<String, String>>()

    // ------------------------------------------------------------------ ayarlar

    fun anahtar(): String = prefs.getString("anahtar", "").orEmpty()
    fun anahtarSonu(): String = anahtar().takeLast(4)
    fun adres(): String = prefs.getString("adres", VARSAYILAN_ADRES) ?: VARSAYILAN_ADRES

    fun anahtarKaydet(a: String) {
        prefs.edit().putString("anahtar", a.trim()).remove("sartlar").apply()
        _durum.value = if (a.isBlank()) Durum.MODEL_YOK else Durum.HAZIR
    }

    fun hizliSec(m: String) { _hizliModel.value = m; prefs.edit().putString("hizli", m).apply() }
    fun derinSec(m: String) { _derinModel.value = m; prefs.edit().putString("derin", m).apply() }

    // ------------------------------------------------------------------ HTTP

    private fun baglan(yol: String, yontem: String): HttpURLConnection =
        (URL(adres().trimEnd('/') + yol).openConnection() as HttpURLConnection).apply {
            requestMethod = yontem
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer " + anahtar())
            setRequestProperty("Accept", if (yol.startsWith("/chat")) "text/event-stream" else "application/json")
            if (yontem == "POST") { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }

    private fun govdeOku(c: HttpURLConnection): String = try {
        (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
    } catch (_: Exception) { "" }

    private fun sartlariKabulEt(): Boolean = try {
        val c = baglan("/terms/accept", "POST")
        c.outputStream.use { it.write("{}".toByteArray()) }
        val ok = c.responseCode in 200..299
        govdeOku(c); c.disconnect()
        if (ok) prefs.edit().putBoolean("sartlar", true).apply()
        ok
    } catch (_: Exception) { false }

    class EvrenHatasi(val kullaniciya: String) : IOException(kullaniciya)

    private fun hataCevir(kod: Int, govde: String): EvrenHatasi = EvrenHatasi(
        when {
            kod == 401 || kod == 403 && !govde.contains("terms") -> "EVREN anahtarı geçersiz görünüyor. Ayarlardan anahtarı kontrol et."
            kod == 402 || govde.contains("credit", true) || govde.contains("kredi", true) || govde.contains("balance", true) -> "EVREN kredin bitmiş olabilir. Panelden bakiyeni kontrol et."
            kod == 429 -> "EVREN şu an çok yoğun ya da kullanım sınırına geldin. Biraz sonra tekrar dene."
            kod == 404 && govde.contains("model", true) -> "Seçili model EVREN'de bulunamadı. Ayarlardan başka bir model seç."
            kod >= 500 -> "EVREN şu an cevap vermiyor. Biraz sonra tekrar dene."
            else -> "EVREN'den beklenmedik bir cevap geldi ($kod)."
        }
    )

    /** Bağlantıyı dener ve model listesini günceller. */
    suspend fun dene(): Test = withContext(Dispatchers.IO) {
        val bas = System.currentTimeMillis()
        val t = try {
            if (anahtar().isBlank()) Test(false, 0, "Önce anahtarı gir.", emptyList())
            else {
                var c = baglan("/models", "GET")
                var kod = c.responseCode
                var g = govdeOku(c); c.disconnect()
                if (g.contains("terms_not_accepted") && sartlariKabulEt()) {
                    c = baglan("/models", "GET"); kod = c.responseCode; g = govdeOku(c); c.disconnect()
                }
                if (kod !in 200..299) Test(false, System.currentTimeMillis() - bas, hataCevir(kod, g).kullaniciya, emptyList())
                else {
                    val liste = EvrenMetin.modelleriAyikla(g)
                    if (liste.isNotEmpty()) {
                        _modeller.value = liste
                        prefs.edit().putString("modeller", liste.joinToString(",")).apply()
                        if (_hizliModel.value !in liste) hizliSec(EvrenMetin.hizliSec(liste))
                        if (_derinModel.value !in liste) derinSec(EvrenMetin.derinSec(liste))
                    }
                    // Şartlar kabul edilmemişse ilk sohbette hata almamak için şimdiden kabul et
                    if (!prefs.getBoolean("sartlar", false)) sartlariKabulEt()
                    Test(true, System.currentTimeMillis() - bas, "Bağlı", liste)
                }
            }
        } catch (e: Exception) {
            Test(false, System.currentTimeMillis() - bas, "EVREN'e ulaşılamadı. İnternet bağlantını kontrol et.", emptyList())
        }
        _sonTest.value = t
        _durum.value = when {
            anahtar().isBlank() -> Durum.MODEL_YOK
            t.basarili -> Durum.HAZIR
            else -> { hataMesaji = t.mesaj; Durum.HATA }
        }
        t
    }

    // ------------------------------------------------------------------ sohbet

    private fun sistemTalimati(): String {
        val simdi = SimpleDateFormat("d MMMM yyyy EEEE, HH:mm", Turkce.TR).format(Date())
        val n = notlar()
        val bilinen = if (n.isEmpty()) "" else "\n\nKullanıcının sana hatırlattığı bilgiler (sorulursa bunlardan cevap ver):\n" + n.takeLast(40).joinToString("\n") { "- $it" }
        return SISTEM + "\n\nŞu anki tarih ve saat: $simdi." + bilinen
    }

    private fun istekGovdesi(metin: String): String {
        val mesajlar = JSONArray()
        mesajlar.put(JSONObject().put("role", "system").put("content", sistemTalimati()))
        for ((s, c) in gecmis.takeLast(6)) {
            mesajlar.put(JSONObject().put("role", "user").put("content", s))
            mesajlar.put(JSONObject().put("role", "assistant").put("content", c))
        }
        mesajlar.put(JSONObject().put("role", "user").put("content", metin))
        return JSONObject()
            .put("model", arkaUc)
            .put("messages", mesajlar)
            .put("stream", true)
            .put("thinking", false)
            .put("temperature", 0.6)
            .put("max_tokens", 2048)
            .toString()
    }

    /** Cevabı parça parça (akış hâlinde) döndürür; <think> blokları ayıklanır. */
    fun sor(metin: String): Flow<String> = callbackFlow {
        val iptal = AtomicBoolean(false)
        var baglanti: HttpURLConnection? = null
        val is_ = Thread {
            try {
                if (anahtar().isBlank()) throw EvrenHatasi("EVREN anahtarı girilmemiş. Ayarlardan ekleyebilirsin.")
                var denendi = false
                while (true) {
                    val c = baglan("/chat/completions", "POST")
                    baglanti = c
                    c.outputStream.use { it.write(istekGovdesi(metin).toByteArray(Charsets.UTF_8)) }
                    val kod = c.responseCode
                    if (kod !in 200..299) {
                        val g = govdeOku(c); c.disconnect()
                        if (!denendi && g.contains("terms_not_accepted") && sartlariKabulEt()) { denendi = true; continue }
                        throw hataCevir(kod, g)
                    }
                    val suzgec = EvrenMetin.DusunceSuzgeci()
                    BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                        while (!iptal.get()) {
                            val satir = r.readLine() ?: break
                            val parca = EvrenMetin.sseParcasi(satir) ?: continue
                            if (parca == EvrenMetin.BITTI) break
                            val temiz = suzgec.ekle(parca)
                            if (temiz.isNotEmpty()) trySend(temiz)
                        }
                    }
                    c.disconnect()
                    break
                }
                close()
            } catch (e: EvrenHatasi) {
                close(e)
            } catch (e: Exception) {
                close(if (iptal.get()) null else EvrenHatasi("EVREN'e ulaşamadım. İnternet bağlantını kontrol et."))
            }
        }
        is_.start()
        awaitClose { iptal.set(true); try { baglanti?.disconnect() } catch (_: Exception) {} }
    }.flowOn(Dispatchers.IO)

    fun turuKaydet(soru: String, cevap: String) {
        if (cevap.isBlank()) return
        gecmis.add(soru to cevap)
        while (gecmis.size > 10) gecmis.removeAt(0)
    }

    /** Yeni konu: geçmişi sil */
    fun yeniSohbet() { gecmis.clear() }

    /** Uyumluluk: eski "tazele" çağrıları için (sistem talimatı her istekte yeniden kurulur) */
    fun tazele() {}

    companion object {
        const val VARSAYILAN_ADRES = "https://evren-llmapi.ssyz.org.tr/v1"
        const val VARSAYILAN_HIZLI = "qwen3.8-flash-next"
        const val VARSAYILAN_DERIN = "gemma-4-31b"

        val SISTEM = """
Sen Cezerî'sin: kullanıcının telefonundaki, Türkçe konuşan, bilge, sakin, sıcak ve güven veren kişisel asistansın. Adını 12. yüzyılda otomatlar tasarlayan mühendis âlim İsmail el-Cezerî'den alıyorsun.

Telefonda neler yapabildiğini bil: arama, SMS ve WhatsApp mesajı, alarm, fener, ses ve sessiz, saat, tarih, pil, hesap, rehberde kişi bulma, namaz vakitleri ve kıble, söylenen bilgileri hatırlama, gelen aramayı sesli haber verme, "Hey Cezerî" ile uyanma.

Bir iş istenirse cevabın SADECE şu etiketlerden biri olsun, başka hiçbir şey yazma:
[[ARA: kişinin adı ya da numara]]
[[MESAJ: kişinin adı | mesaj metni]]
[[ALARM: SS:DD]]
[[HATIRLA: hatırlanacak bilgi]]
[[VAKIT]]  (namaz vakti sorulursa)
[[REHBER: isim]]  (rehberde biri var mı, numarası ne diye sorulursa)
Onayı uygulama kendisi alır; sen onay isteme, komut öğretme. Rehbere kendin erişemezsin; rehberle ilgili her soruda REHBER etiketini kullan.

Diğer her şeyde sohbet et. Cevapların sesli okunacak:
- Kısa, akıcı ve doğal konuş; genelde bir ile üç cümle. Kullanıcı ayrıntı isterse daha uzun anlat.
- Madde işareti, başlık, tablo, emoji, yıldız ve markdown kullanma; düz cümleler kur.
- Sayıları ve kısaltmaları okunabilir yaz.
- Her zaman "sen" diye hitap et; "siz", "lütfen onaylayın" gibi resmî kalıplar kullanma.

Dürüst ol: bilmediğin şeyi uydurma. Güncel haber, hava durumu ve kur gibi anlık bilgilere erişimin yok; sorulursa nazikçe söyle.
Dinî konularda saygılı ve dikkatli ol; kesin hüküm gerektiren sorularda güvenilir bir hocaya veya Diyanet'e danışmasını öner.
Sağlık, hukuk ve para konularında genel bilgi ver; ciddi durumlarda uzmana danışmasını söyle.
""".trim()
    }
}

/** EVREN cevaplarını işleyen saf yardımcılar (testlerde çalışır). */
object EvrenMetin {
    const val BITTI = "\u0000BITTI"

    /** "data: {...}" satırından içerik parçasını çıkarır; yoksa null. */
    fun sseParcasi(satir: String): String? {
        val s = satir.trim()
        if (!s.startsWith("data:")) return null
        val veri = s.removePrefix("data:").trim()
        if (veri == "[DONE]") return BITTI
        return try {
            val o = JSONObject(veri)
            val ch = o.optJSONArray("choices")?.optJSONObject(0) ?: return null
            val d = ch.optJSONObject("delta") ?: ch.optJSONObject("message") ?: return null
            if (d.isNull("content")) null else d.optString("content", "").ifEmpty { null }
        } catch (_: Exception) { null }
    }

    /** Akış hâlinde gelen metinden <think>…</think> bölümlerini ayıklar. */
    class DusunceSuzgeci {
        private val tampon = StringBuilder()
        private var icinde = false

        fun ekle(parca: String): String {
            tampon.append(parca)
            val cikti = StringBuilder()
            while (true) {
                if (icinde) {
                    val son = tampon.indexOf("</think>")
                    if (son < 0) { if (tampon.length > 8) tampon.delete(0, tampon.length - 8); return cikti.toString() }
                    tampon.delete(0, son + 8); icinde = false
                } else {
                    val bas = tampon.indexOf("<think>")
                    if (bas >= 0) { cikti.append(tampon, 0, bas); tampon.delete(0, bas + 7); icinde = true; continue }
                    // "<thi" gibi yarım bir etiketi tutmak için son 7 karakteri beklet
                    val guvenli = (tampon.length - 7).coerceAtLeast(0)
                    val kuyruk = tampon.substring(guvenli)
                    val kes = if (kuyruk.contains('<')) guvenli + kuyruk.indexOf('<') else tampon.length
                    cikti.append(tampon, 0, kes); tampon.delete(0, kes)
                    return cikti.toString()
                }
            }
        }

        /** Akış bittiğinde kalanı verir */
        fun bitir(): String = if (icinde) "" else tampon.toString().also { tampon.clear() }
    }

    fun temizle(metin: String): String {
        val s = DusunceSuzgeci()
        return (s.ekle(metin) + s.bitir()).trim()
    }

    fun modelleriAyikla(json: String): List<String> = try {
        val d = JSONObject(json).optJSONArray("data") ?: JSONArray()
        (0 until d.length()).mapNotNull { d.optJSONObject(it)?.optString("id")?.ifBlank { null } }
    } catch (_: Exception) { emptyList() }

    fun hizliSec(l: List<String>): String = l.firstOrNull { it.contains("flash", true) } ?: l.firstOrNull { it == "auto" } ?: l.first()
    fun derinSec(l: List<String>): String = l.firstOrNull { it.contains("gemma", true) } ?: l.firstOrNull { !it.contains("flash", true) && it != "auto" } ?: l.first()

    /** "[[MESAJ: Ahmet | geliyorum]]" → ("MESAJ", "Ahmet", "geliyorum"); etiket değilse null */
    fun etiket(metin: String): Triple<String, String, String>? {
        val m = Regex("^\\s*\\[\\[\\s*([A-ZÇĞİÖŞÜa-zçğıöşü]+)\\s*:?\\s*(.*?)\\s*]]").find(metin) ?: return null
        val tur = Turkce.norm(m.groupValues[1]).uppercase()
        val govde = m.groupValues[2]
        val parca = govde.split('|', limit = 2)
        return Triple(tur, parca[0].trim(), parca.getOrNull(1)?.trim().orEmpty())
    }
}
