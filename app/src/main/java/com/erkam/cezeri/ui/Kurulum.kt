package com.erkam.cezeri.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.erkam.cezeri.Asistan
import com.erkam.cezeri.Durum
import com.erkam.cezeri.core.ModelIndirici

@Composable
fun KurulumHosgeldin(vm: Asistan) {
    LaunchedEffect(Unit) { vm.karsila() }
    Column(
        Modifier.fillMaxSize().background(Renk.ZeminDerin).statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CEZERÎ", style = baslik(16.sp, Renk.Altin).copy(fontWeight = FontWeight.Bold, letterSpacing = 5.sp))
        Spacer(Modifier.height(64.dp))
        Amblem(220.dp, don = true)
        Spacer(Modifier.height(40.dp))
        Text("Selâm, ben Cezerî.", style = baslik(40.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            "Senin için arar, mesaj yazar, vakitleri hatırlatır ve sorularını cevaplarım.",
            style = govde(16.sp, Renk.Soluk), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(Modifier.weight(1f))
        AltinDugme("Başlayalım") { vm.kurulumAdim(1) }
        Spacer(Modifier.height(14.dp))
        Text("Kurulum yaklaşık 5 dakika sürer", style = govde(12.sp, Renk.Soluk))
    }
}

private data class IzinSatiri(val ad: String, val neden: String, val ikon: String, val izin: String?, val ozel: String? = null)

private fun izinVar(ctx: Context, s: IzinSatiri): Boolean = when {
    s.izin != null -> ContextCompat.checkSelfPermission(ctx, s.izin) == PackageManager.PERMISSION_GRANTED
    s.ozel == "pil" -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    else -> false
}

@Composable
fun KurulumIzinler(vm: Asistan) {
    val ctx = LocalContext.current
    var yenile by remember { mutableIntStateOf(0) }
    val satirlar = remember {
        listOf(
            IzinSatiri("Mikrofon", "Seni duyabilmem için", Ik.MIKROFON, Manifest.permission.RECORD_AUDIO),
            IzinSatiri("Rehber", "Kişileri doğru bulmam için", Ik.KISI, Manifest.permission.READ_CONTACTS),
            IzinSatiri("Telefon", "Senin için arama yapabilmem için", Ik.TELEFON, Manifest.permission.CALL_PHONE),
            IzinSatiri("SMS", "Senin yerine mesaj göndermem için", Ik.MESAJ, Manifest.permission.SEND_SMS),
            IzinSatiri("Bildirimler", "Hazırlık durumunu gösterebilmem için", Ik.ZIL, Manifest.permission.POST_NOTIFICATIONS),
            IzinSatiri("Pil: Kısıtlanmamış", "Arka planda kapanmamam için", Ik.PIL, null, "pil"),
        )
    }
    val sesler = remember {
        mapOf(
            "Mikrofon" to "Seni duyabilmem için mikrofon iznine ihtiyacım var.",
            "Rehber" to "Kişileri doğru bulabilmem için rehberine bakmam gerekiyor.",
            "Telefon" to "Arama yapabilmem için telefon iznine ihtiyacım var.",
            "SMS" to "Senin yerine mesaj gönderebilmem için SMS iznine ihtiyacım var.",
        )
    }
    val baslatici = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { yenile++ }
    // Ayarlardan dönünce durumları yenile
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) yenile++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val durumlar = remember(yenile) { satirlar.map { izinVar(ctx, it) } }
    val tamam = durumlar.count { it }
    val siradaki = durumlar.indexOfFirst { !it }

    fun iste(s: IzinSatiri) {
        sesler[s.ad]?.let { vm.konusKisa(it) }
        if (s.izin != null) baslatici.launch(s.izin)
        else if (s.ozel == "pil") {
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
            } catch (_: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            YuvarlakDugme(Ik.GERI, "Geri") { vm.kurulumAdim(0) }
            Spacer(Modifier.weight(1f))
            Text("$tamam / ${satirlar.size} tamam", style = govde(13.sp, Renk.Soluk, FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Sana yardım edebilmem için", style = baslik(32.sp))
            Spacer(Modifier.height(8.dp))
            Text("Her izni neden istediğimi söylüyorum. Sen sadece onayla.", style = govde(14.sp, Renk.Soluk))
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(20.dp)).background(Renk.KartKoyu).border(1.dp, Renk.KartCizgi, RoundedCornerShape(20.dp))
        ) {
            satirlar.forEachIndexed { i, s ->
                val verildi = durumlar[i]
                val vurgulu = i == siradaki
                Row(
                    Modifier.fillMaxWidth().background(if (vurgulu) Renk.AltinZemin else Renk.KartKoyu)
                        .clickable(enabled = !verildi) { iste(s) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ikon(s.ikon, if (vurgulu) Renk.Altin else Renk.Soluk, 20.dp, 1.7f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.ad, style = govde(14.sp, if (vurgulu) Renk.YaziSicak else Renk.Yazi, FontWeight.SemiBold))
                        Text(s.neden, style = govde(12.sp, if (vurgulu) Renk.SolukAcik else Renk.Soluk))
                    }
                    when {
                        verildi -> Box(Modifier.size(24.dp).clip(CircleShape).background(Renk.Turkuaz), contentAlignment = Alignment.Center) { Ikon(Ik.TIK, Renk.Zemin, 14.dp, 2.6f) }
                        vurgulu -> Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Renk.Altin).clickable { iste(s) }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("İzin ver", style = govde(13.sp, Renk.Zemin, FontWeight.SemiBold)) }
                        else -> Text("Sırada", style = govde(12.sp, Renk.Soluk))
                    }
                }
                if (i < satirlar.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color1D))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "“Hey Cezerî”, ses kimliği ve yan tuş ayarları Ayarlar ekranında.",
            style = govde(12.sp, Renk.Soluk), modifier = Modifier.padding(horizontal = 6.dp)
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Renk.AltinZemin)
                .border(1.dp, Renk.AltinCizgi, RoundedCornerShape(28.dp)).clickable { vm.kurulumAdim(2) },
            contentAlignment = Alignment.Center
        ) { Text("Devam et", style = govde(15.sp, Renk.AltinAcik, FontWeight.SemiBold)) }
    }
}

private val Color1D = androidx.compose.ui.graphics.Color(0xFF1D222B)

@Composable
fun KurulumHazirlik(vm: Asistan, d: Durum) {
    val m = d.model
    val oran = when (m) {
        is ModelIndirici.Durum.Iniyor -> m.inen.toFloat() / maxOf(1L, m.toplam).toFloat()
        ModelIndirici.Durum.Hazir -> 1f
        else -> 0f
    }
    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Halka(oran, 150.dp) {
            Text("%" + (oran * 100).toInt(), style = baslik(30.sp).copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(22.dp))
        Text(if (m == ModelIndirici.Durum.Hazir) "Cezerî hazır" else "Cezerî hazırlanıyor", style = baslik(32.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Tek seferlik indirme · Wi‑Fi önerilir", style = govde(14.sp, Renk.Soluk))
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Renk.KartKoyu)
                .border(1.dp, Renk.KartCizgi, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("Akıl", style = govde(14.sp, Renk.Yazi, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                val gb = { b: Long -> "%.1f".format(b / 1_000_000_000.0).replace('.', ',') }
                Text(
                    when (m) {
                        is ModelIndirici.Durum.Iniyor -> "${gb(m.inen)} / ${gb(m.toplam)} GB"
                        ModelIndirici.Durum.Hazir -> "Tamam"
                        is ModelIndirici.Durum.Hata -> "Hata"
                        else -> "2,7 GB"
                    },
                    style = govde(12.sp, Renk.Altin)
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.KartCizgi)) {
                Box(Modifier.fillMaxWidth(oran).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.Altin))
            }
            if (m is ModelIndirici.Durum.Hata) {
                Text(m.mesaj, style = govde(12.sp, Renk.SolukAcik))
                Text("Tekrar dene", style = govde(13.sp, Renk.Altin, FontWeight.SemiBold), modifier = Modifier.clickable { vm.modeliIndir() }.padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Arama, mesaj, alarm gibi işler hemen çalışır. Sohbet için aklın inmesini beklemen yeterli; indirme arka planda devam eder.",
            style = govde(13.sp, Renk.Soluk), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        AltinDugme(if (m == ModelIndirici.Durum.Hazir) "Başla" else "Devam et") { vm.kurulumBitir() }
        Spacer(Modifier.height(12.dp))
        Text("İndirme arka planda devam eder", style = govde(12.sp, Renk.Soluk))
    }
}
