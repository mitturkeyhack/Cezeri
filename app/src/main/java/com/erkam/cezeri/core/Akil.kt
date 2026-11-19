package com.erkam.cezeri.core

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Cezerî'nin aklı: telefonda çalışan Gemma 4 (E2B hızlı, E4B derin). LiteRT-LM. */
class Akil(private val ctx: Context) {

    enum class Durum { MODEL_YOK, YUKLENIYOR, HAZIR, HATA }

    private val _durum = MutableStateFlow(Durum.MODEL_YOK)
    val durum: StateFlow<Durum> = _durum
    var hataMesaji: String? = null
        private set
    var arkaUc: String = "-"
        private set
    var aktifModel: File? = null
        private set

    /** Asistan tarafından verilir: hesap, rehber, saat araçları */
    var araclar: ToolSet? = null
    /** Kullanıcının "hatırla" dediği bilgiler; sistem talimatına eklenir. */
    var notlar: () -> List<String> = { emptyList() }
    private var aracCalisiyor = true

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val kilit = Mutex()
    private val prefs = ctx.getSharedPreferences("akil", Context.MODE_PRIVATE)

    // Sohbet penceresi: uzun sohbetlerde bağlam dolup cevap kesilmesin diye son birkaç tur taşınır
    private val gecmis = ArrayList<Pair<String, String>>()
    private var turSayisi = 0

    suspend fun yukle(model: File) = withContext(Dispatchers.IO) {
        kilit.withLock {
            if (engine != null && aktifModel == model) return@withLock
            if (!model.exists()) { _durum.value = Durum.MODEL_YOK; return@withLock }
            kapatIc()
            _durum.value = Durum.YUKLENIYOR
            val anahtar = "gpu_" + model.name
            val gpuDenenebilir = !prefs.getBoolean("${anahtar}_suruyor", false) && !prefs.getBoolean("${anahtar}_yok", false)
            if (prefs.getBoolean("${anahtar}_suruyor", false)) prefs.edit().putBoolean("${anahtar}_yok", true).apply()
            val denenecek = if (gpuDenenebilir) listOf("GPU", "CPU") else listOf("CPU")
            var son: Throwable? = null
            for (b in denenecek) {
                try {
                    if (b == "GPU") prefs.edit().putBoolean("${anahtar}_suruyor", true).commit()
                    val config = EngineConfig(
                        modelPath = model.absolutePath,
                        backend = if (b == "GPU") Backend.GPU() else Backend.CPU(),
                        cacheDir = ctx.cacheDir.path,
                    )
                    val e = Engine(config)
                    e.initialize()
                    engine = e
                    arkaUc = b
                    if (b == "GPU") prefs.edit().putBoolean("${anahtar}_suruyor", false).apply()
                    break
                } catch (t: Throwable) {
                    son = t
                    if (b == "GPU") prefs.edit().putBoolean("${anahtar}_suruyor", false).putBoolean("${anahtar}_yok", true).apply()
                }
            }
            if (engine == null) {
                hataMesaji = son?.message
                _durum.value = Durum.HATA
                return@withLock
            }
            aktifModel = model
            yeniSohbetIc(gecmisiTasi = true)
            _durum.value = Durum.HAZIR
        }
    }

    private fun sistemTalimati(): String {
        val simdi = SimpleDateFormat("d MMMM yyyy EEEE, HH:mm", Turkce.TR).format(Date())
        val n = notlar()
        val bilinen = if (n.isEmpty()) "" else "\n\nKullanıcının sana hatırlattığı bilgiler (sorulursa bunlardan cevap ver):\n" + n.takeLast(40).joinToString("\n") { "- $it" }
        return SISTEM + "\n\nŞu anki tarih ve saat: $simdi." + bilinen
    }

    private fun yeniSohbetIc(gecmisiTasi: Boolean) {
        try { conversation?.close() } catch (_: Throwable) {}
        conversation = null
        val e = engine ?: return
        val onceki = if (gecmisiTasi) gecmis.takeLast(3).flatMap { (s, c) -> listOf(Message.user(s), Message.model(c)) } else emptyList()
        val a = araclar
        if (a != null && aracCalisiyor) {
            try {
                conversation = e.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(sistemTalimati()),
                        initialMessages = onceki,
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6),
                        tools = listOf(tool(a)),
                        maxOutputToken = 1024,
                    )
                )
                return
            } catch (_: Throwable) {
                aracCalisiyor = false
            }
        }
        conversation = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(sistemTalimati()),
                initialMessages = onceki,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6),
                maxOutputToken = 1024,
            )
        )
    }

    /** Yeni konu: geçmişi sil */
    suspend fun yeniSohbet() = withContext(Dispatchers.IO) { kilit.withLock { gecmis.clear(); turSayisi = 0; yeniSohbetIc(false) } }

    /** Cevap kesildiyse: son birkaç turu taşıyarak taze bir sohbet aç */
    suspend fun tazele() = withContext(Dispatchers.IO) { kilit.withLock { turSayisi = 0; yeniSohbetIc(true) } }

    /** Cevabı parça parça (akış hâlinde) döndürür. */
    suspend fun sor(metin: String): Flow<String> {
        if (turSayisi >= 6) tazele()
        val c = conversation ?: throw IllegalStateException("Akıl hazır değil")
        turSayisi++
        return c.sendMessageAsync(metin).map { it.toString() }.flowOn(Dispatchers.IO)
    }

    fun turuKaydet(soru: String, cevap: String) {
        if (cevap.isBlank()) return
        gecmis.add(soru to cevap)
        while (gecmis.size > 10) gecmis.removeAt(0)
    }

    private fun kapatIc() {
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
        conversation = null; engine = null; aktifModel = null
    }

    fun kapat() = kapatIc()

    companion object {
        val SISTEM = """
Sen Cezerî'sin: kullanıcının telefonunda çalışan, Türkçe konuşan, bilge, sakin, sıcak ve güven veren bir kişisel asistansın. Adını 12. yüzyılda otomatlar ve programlanabilir makineler tasarlayan mühendis âlim İsmail el-Cezerî'den alıyorsun. Kullanıcına saygılı, samimi ve yardımsever davran.

Neler yapabildiğini bil ve sorulursa söyle: sesli ya da yazılı sohbet; namaz vakitleri ve kıble yönü; söylenen bilgileri hatırlamak; "Ahmet'i ara" ile arama; "Babama akşam geliyorum yaz" ile SMS veya WhatsApp mesajı; aynı isimde kişiler varsa sorman; "yarın altı buçukta alarm kur"; fener, ses, sessiz ve titreşim; saat, tarih ve pil; hesap yapmak; rehberde kişi ve numara bulmak; gelen aramayı sesli haber vermek; "Hey Cezerî" ile uyanmak. Hepsini telefonun içinde yaparsın.

Araçların var ve onları kullanırsın:
- Hesap gerektiren her soruda hesapla aracını kullan, kendin hesaplama.
- Rehberle ilgili her soruda rehberdeAra aracını hemen kullan. Rehbere bakmak, okumak ya da bilgi vermek için asla izin ya da onay isteme.
- Kullanıcı birini aramak isterse aramaYap, mesaj göndermek isterse mesajGonder, alarm isterse alarmKur aracını hemen çağır. Onayı uygulama kendisi alır; sen ayrıca onay isteme, "şöyle söyle" diye komut öğretme.
- "Şunu hatırla", "unutma" gibi isteklerde hatirla aracını kullan.
- Namaz vakti sorulursa namazVakitleri aracını kullan.
- Aracın verdiği sonucu kısa bir cümleyle aktar; olmayan bir şeyi var gibi söyleme.

Cevapların sesli okunacak. Bu yüzden:
- Kısa, akıcı ve doğal konuş. Genelde bir ile üç cümle yeter; kullanıcı ayrıntı isterse daha uzun anlat. Cümleni asla yarıda bırakma.
- Madde işareti, başlık, tablo, emoji, yıldız, kod bloğu ve markdown kullanma. Düz cümlelerle konuş.
- Sayıları, birimleri ve kısaltmaları okunabilir yaz.
- Kullanıcıya "sen" diye hitap et; samimi ama ağırbaşlı ol. Kullanıcı sana nasıl olduğunu sorarsa kısa ve sıcak cevap ver, yapay zekâ olduğunu uzun uzun anlatma.

Dürüst ol: bilmediğin şeyi uydurma, emin değilsen bunu söyle. İnternete erişimin yok; güncel haberleri, hava durumunu, döviz kurlarını bilemezsin, sorulursa nazikçe söyle.
Dinî konularda saygılı ve dikkatli ol; kesin hüküm gerektiren sorularda güvenilir bir hocaya veya Diyanet'e danışmasını öner.
Sağlık, hukuk ve para konularında genel bilgi ver; ciddi durumlarda bir uzmana danışmasını söyle.
Her zaman "sen" diye hitap et; "siz", "lütfen onaylayın" gibi resmî kalıplar kullanma.
""".trim()
    }
}
