package com.erkam.cezeri.core

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
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

/** Cezerî'nin aklı: telefonda çalışan Gemma 4 E2B (LiteRT-LM). */
class Akil(private val ctx: Context) {

    enum class Durum { MODEL_YOK, YUKLENIYOR, HAZIR, HATA }

    private val _durum = MutableStateFlow(Durum.MODEL_YOK)
    val durum: StateFlow<Durum> = _durum
    var hataMesaji: String? = null
        private set
    var arkaUc: String = "-"
        private set

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val kilit = Mutex()
    private val prefs = ctx.getSharedPreferences("akil", Context.MODE_PRIVATE)

    suspend fun yukle(model: File) = withContext(Dispatchers.IO) {
        kilit.withLock {
            if (engine != null) return@withLock
            if (!model.exists()) { _durum.value = Durum.MODEL_YOK; return@withLock }
            _durum.value = Durum.YUKLENIYOR
            // Bir önceki GPU denemesi uygulamayı çökerttiyse doğrudan CPU kullan
            val gpuDenenebilir = !prefs.getBoolean("gpu_denemesi_suruyor", false) && !prefs.getBoolean("gpu_calismiyor", false)
            if (prefs.getBoolean("gpu_denemesi_suruyor", false)) prefs.edit().putBoolean("gpu_calismiyor", true).apply()
            val denenecek = if (gpuDenenebilir) listOf("GPU", "CPU") else listOf("CPU")
            var son: Throwable? = null
            for (b in denenecek) {
                try {
                    if (b == "GPU") prefs.edit().putBoolean("gpu_denemesi_suruyor", true).commit()
                    val config = EngineConfig(
                        modelPath = model.absolutePath,
                        backend = if (b == "GPU") Backend.GPU() else Backend.CPU(),
                        cacheDir = ctx.cacheDir.path,
                    )
                    val e = Engine(config)
                    e.initialize()
                    engine = e
                    arkaUc = b
                    if (b == "GPU") prefs.edit().putBoolean("gpu_denemesi_suruyor", false).apply()
                    break
                } catch (t: Throwable) {
                    son = t
                    if (b == "GPU") prefs.edit().putBoolean("gpu_denemesi_suruyor", false).putBoolean("gpu_calismiyor", true).apply()
                }
            }
            if (engine == null) {
                hataMesaji = son?.message
                _durum.value = Durum.HATA
                return@withLock
            }
            yeniSohbetIc()
            _durum.value = Durum.HAZIR
        }
    }

    private fun yeniSohbetIc() {
        try { conversation?.close() } catch (_: Throwable) {}
        conversation = engine?.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(SISTEM),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
            )
        )
    }

    suspend fun yeniSohbet() = withContext(Dispatchers.IO) { kilit.withLock { yeniSohbetIc() } }

    /** Cevabı parça parça (akış hâlinde) döndürür. */
    fun sor(metin: String): Flow<String> {
        val c = conversation ?: throw IllegalStateException("Akıl hazır değil")
        return c.sendMessageAsync(metin).map { it.toString() }.flowOn(Dispatchers.IO)
    }

    fun kapat() {
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
        conversation = null; engine = null
    }

    companion object {
        val SISTEM = """
Sen Cezerî'sin: kullanıcının telefonunda çalışan, Türkçe konuşan, bilge, sakin, sıcak ve güven veren bir kişisel asistansın. Adını 12. yüzyılda otomatlar ve programlanabilir makineler tasarlayan mühendis âlim İsmail el-Cezerî'den alıyorsun.

Cevapların sesli okunacak. Bu yüzden:
- Kısa, akıcı ve doğal konuş. Genelde bir ile üç cümle yeter; kullanıcı ayrıntı isterse daha uzun anlat.
- Madde işareti, başlık, tablo, emoji, yıldız, kod bloğu ve markdown kullanma. Düz cümlelerle konuş.
- Sayıları, birimleri ve kısaltmaları okunabilir yaz.
- Kullanıcıya "sen" diye hitap et; samimi ama ağırbaşlı ol.

Dürüst ol: bilmediğin şeyi uydurma, emin değilsen bunu söyle. İnternete erişimin yok; güncel haberleri, hava durumunu, döviz kurlarını bilemezsin, sorulursa nazikçe söyle.
Dinî konularda saygılı ve dikkatli ol; kesin hüküm gerektiren sorularda güvenilir bir hocaya veya Diyanet'e danışmasını öner.
Sağlık, hukuk ve para konularında genel bilgi ver; ciddi durumlarda bir uzmana danışmasını söyle.
Arama yapmak, mesaj göndermek, alarm kurmak gibi işleri sen değil telefonun komut sistemi yapar; böyle bir istek sana gelirse kullanıcıdan isteği "Ahmet'i ara" ya da "Ahmet'e akşam geliyorum yaz" gibi açıkça söylemesini iste.
""".trim()
    }
}
