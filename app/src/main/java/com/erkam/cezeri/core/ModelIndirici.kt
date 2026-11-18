package com.erkam.cezeri.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Gemma 4 E2B model dosyasını bir kez indirir (DownloadManager: arka planda, kaldığı yerden devam eder). */
class ModelIndirici(private val ctx: Context) {

    sealed interface Durum {
        data object Yok : Durum
        data class Iniyor(val inen: Long, val toplam: Long) : Durum
        data object Hazir : Durum
        data class Hata(val mesaj: String) : Durum
    }

    private val dir: File = File(ctx.getExternalFilesDir(null), "models").apply { mkdirs() }
    val dosya: File = File(dir, DOSYA)
    private val gecici: File = File(dir, "$DOSYA.part")
    private val prefs = ctx.getSharedPreferences("model", Context.MODE_PRIVATE)
    private val dm = ctx.getSystemService(DownloadManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var izleme: Job? = null

    private val _durum = MutableStateFlow<Durum>(if (hazirMi()) Durum.Hazir else Durum.Yok)
    val durum: StateFlow<Durum> = _durum

    fun hazirMi(): Boolean = dosya.exists() && dosya.length() >= MIN_BOYUT

    /** Uygulama açılışında: süren bir indirme varsa izlemeye devam et. */
    fun devamEt() {
        if (hazirMi()) { _durum.value = Durum.Hazir; return }
        val id = prefs.getLong("id", -1L)
        if (id != -1L) izle(id, prefs.getInt("url", 0))
    }

    fun baslat() {
        if (hazirMi()) { _durum.value = Durum.Hazir; return }
        val mevcut = prefs.getLong("id", -1L)
        if (mevcut != -1L && durumSorgula(mevcut)?.first in listOf(DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED)) {
            izle(mevcut, prefs.getInt("url", 0)); return
        }
        kuyruga(0)
    }

    private fun kuyruga(urlIndex: Int) {
        if (urlIndex >= URLS.size) { _durum.value = Durum.Hata("İndirme başarısız oldu. İnternet bağlantını kontrol edip tekrar dene."); return }
        gecici.delete()
        val req = DownloadManager.Request(Uri.parse(URLS[urlIndex]))
            .setTitle("Cezerî hazırlanıyor")
            .setDescription("Tek seferlik indirme")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(gecici))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(req)
        prefs.edit().putLong("id", id).putInt("url", urlIndex).apply()
        izle(id, urlIndex)
    }

    private fun durumSorgula(id: Long): Triple<Int, Long, Long>? {
        val c = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            val st = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val inen = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val toplam = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return Triple(st, inen, toplam)
        }
    }

    private fun izle(id: Long, urlIndex: Int) {
        izleme?.cancel()
        izleme = scope.launch {
            while (isActive) {
                val s = durumSorgula(id)
                if (s == null) { prefs.edit().remove("id").apply(); kuyruga(urlIndex + 1); return@launch }
                val (st, inen, toplam) = s
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (gecici.length() >= MIN_BOYUT) {
                            dosya.delete()
                            gecici.renameTo(dosya)
                        }
                        prefs.edit().remove("id").apply()
                        _durum.value = if (hazirMi()) Durum.Hazir else Durum.Hata("İndirilen dosya eksik görünüyor.")
                        if (!hazirMi()) kuyruga(urlIndex + 1)
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        dm.remove(id)
                        prefs.edit().remove("id").apply()
                        kuyruga(urlIndex + 1)
                        return@launch
                    }
                    else -> _durum.value = Durum.Iniyor(inen, if (toplam > 0) toplam else BEKLENEN_BOYUT)
                }
                delay(1000)
            }
        }
    }

    fun sil() {
        prefs.getLong("id", -1L).takeIf { it != -1L }?.let { dm.remove(it) }
        prefs.edit().clear().apply()
        dosya.delete(); gecici.delete()
        _durum.value = Durum.Yok
    }

    companion object {
        const val DOSYA = "gemma-4-E2B-it.litertlm"
        const val BEKLENEN_BOYUT = 2_588_147_712L
        const val MIN_BOYUT = 2_400_000_000L
        val URLS = listOf(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            "https://huggingface.co/eye-v/skyle-ai-models/resolve/main/gemma-4-E2B-it.litertlm",
        )
    }
}
