package com.erkam.cezeri.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.erkam.cezeri.Asistan
import com.erkam.cezeri.Durum
import com.erkam.cezeri.Ekran
import com.erkam.cezeri.core.Akil
import com.erkam.cezeri.core.Ses
import com.erkam.cezeri.ses.CezeriSesi
import com.erkam.cezeri.core.Uyanma

private fun varsayilanAsistanMi(ctx: Context): Boolean = try {
    ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
} catch (_: Exception) { false }

private fun ac(ctx: Context, vararg niyetler: Intent) {
    for (i in niyetler) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
    }
}

@Composable
fun AyarlarEkrani(vm: Asistan, d: Durum) {
    BackHandler { vm.anaEkrana() }
    val ctx = LocalContext.current
    var yenile by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) yenile++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val asistanMi = remember(yenile) { varsayilanAsistanMi(ctx) }
    val ustteGoster = remember(yenile) { Settings.canDrawOverlays(ctx) }
    val aramaIzinleri = arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.ANSWER_PHONE_CALLS)
    val aramaIzni = remember(yenile) { aramaIzinleri.all { ctx.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED } }
    val whatsappAcik = remember(yenile) { com.erkam.cezeri.asistan.WhatsappErisim.acikMi(ctx) }
    val izinIste = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { yenile++ }
    val pilSerbest = remember(yenile) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }

    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YuvarlakDugme(Ik.GERI, "Geri") { vm.anaEkrana() }
            Spacer(Modifier.width(14.dp))
            Text("Ayarlar", style = baslik(30.sp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Bolum("UYANMA")
            AyarKarti {
                val m = d.uyanmaModel
                AyarSatiri(
                    "“Hey Cezerî” ile uyan",
                    when (m) {
                        is Uyanma.ModelDurumu.Iniyor -> "Uyanma modeli iniyor · %${m.yuzde}"
                        is Uyanma.ModelDurumu.Hata -> m.mesaj
                        Uyanma.ModelDurumu.Hazir -> if (d.uyanmaAcik) "Ekran kapalıyken de dinliyor" else "Kapalı"
                        Uyanma.ModelDurumu.Yok -> "Açınca bir kez ~50 MB indirilir"
                    },
                    Ik.MIKROFON,
                ) { Anahtar(d.uyanmaAcik) { vm.uyanmaAyarla(it) } }
                if (d.uyanmaModel is Uyanma.ModelDurumu.Hata) {
                    Text("Tekrar dene", style = govde(13.sp, Renk.Altin, FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 46.dp, top = 6.dp).clickable { vm.uyanmaModeliIndir() })
                }
                Spacer(Modifier.height(14.dp))
                Text("Hassasiyet", style = govde(13.sp, Renk.Soluk, FontWeight.Medium), modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Rahat" to 0.2f, "Dengeli" to 0.5f, "Katı" to 0.8f).forEach { (ad, v) ->
                        val secili = kotlin.math.abs(d.uyanmaHassasiyet - v) < 0.1f
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (secili) Renk.TurkuazZemin else Renk.KartKoyu)
                                .border(if (secili) 1.5.dp else 1.dp, if (secili) Renk.Turkuaz else Renk.Cizgi, RoundedCornerShape(12.dp))
                                .clickable(role = Role.RadioButton) { vm.hassasiyetAyarla(v) },
                            contentAlignment = Alignment.Center
                        ) { Text(ad, style = govde(13.sp, if (secili) Renk.TurkuazYazi else Renk.Ikon, FontWeight.Medium)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Yanlışlıkla uyanıyorsa “Katı”, seni duymuyorsa “Rahat” seç.", style = govde(12.sp, Renk.Soluk))
                if (d.uyanmaAcik) {
                    Ayrac()
                    UyanmaTesti(vm)
                }
            }

            Bolum("SES KİMLİĞİ")
            AyarKarti {
                AyarSatiri(
                    "Sesini tanıma",
                    if (d.kimlikOrnek > 0) "${d.kimlikOrnek} kayıtla tanınıyor · yalnızca sana cevap verir" else "Kaydedilmedi · herkesin sesine uyanır",
                    Ik.KISI,
                ) {}
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(Renk.Altin).clickable { vm.sesKaydiBaslat() },
                        contentAlignment = Alignment.Center
                    ) { Text(if (d.kimlikOrnek > 0) "Yeniden kaydet" else "Sesimi kaydet", style = govde(14.sp, Renk.Zemin, FontWeight.SemiBold)) }
                    if (d.kimlikOrnek > 0) SadeDugme("Sil", Modifier.weight(0.6f), 46.dp) { vm.kimligiSil() }
                }
            }

            Bolum("ARAMA VE MESAJ")
            AyarKarti {
                AyarSatiri(
                    "Gelen aramayı sesli haber ver",
                    if (!aramaIzni) "İzin gerekiyor · dokun" else if (d.aramaHaber) "“Baban arıyor, açayım mı?”" else "Kapalı",
                    Ik.ZIL,
                    tikla = if (!aramaIzni) ({ izinIste.launch(aramaIzinleri) }) else null,
                ) { if (aramaIzni) Anahtar(d.aramaHaber) { vm.aramaHaberAyarla(it) } }
                Text(
                    "Sesle açıp reddetmen için “Hey Cezerî” de açık olmalı; kapalıyken yalnızca kimin aradığını söyler.",
                    style = govde(12.sp, Renk.Soluk), modifier = Modifier.padding(start = 46.dp, top = 6.dp)
                )
                Ayrac()
                AyarSatiri(
                    "WhatsApp'ta otomatik gönder",
                    if (whatsappAcik) "Açık · onayladığın mesajı ben gönderirim" else "Erişilebilirlikten Cezerî'yi aç",
                    Ik.MESAJ,
                    tamam = whatsappAcik,
                    tikla = { ac(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                ) {}
                if (!whatsappAcik) Text(
                    "Erişilebilirlik › Yüklü uygulamalar › Cezerî. Gri görünürse: Ayarlar › Uygulamalar › Cezerî › sağ üstteki ⋮ › “Kısıtlı ayarlara izin ver”, sonra tekrar dene.",
                    style = govde(12.sp, Renk.Soluk), modifier = Modifier.padding(start = 46.dp, top = 6.dp)
                )
            }

            Bolum("KISAYOLLAR")
            AyarKarti {
                AyarSatiri(
                    "Varsayılan asistan",
                    if (asistanMi) "Cezerî seçili" else "Yan tuşla açılmam için beni seç",
                    Ik.TELEFON,
                    tamam = asistanMi,
                    tikla = {
                        ac(ctx, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                    },
                ) {}
                if (asistanMi) {
                    Text(
                        "Yan tuş için: Ayarlar › Gelişmiş özellikler › Yan tuş › Basılı tut › “Dijital asistanı uyandır”.",
                        style = govde(12.sp, Renk.Soluk), modifier = Modifier.padding(start = 46.dp, top = 6.dp)
                    )
                } else {
                    Text(
                        "Açılan ekranda “Dijital asistan uygulaması”na dokunup Cezerî'yi seç.",
                        style = govde(12.sp, Renk.Soluk), modifier = Modifier.padding(start = 46.dp, top = 6.dp)
                    )
                }
                Ayrac()
                AyarSatiri(
                    "Ekranı açma",
                    if (ustteGoster) "Uyandığımda ekranda görünebilirim" else "Uyandığımda ekranı açabilmem için izin ver",
                    Ik.BILGI,
                    tamam = ustteGoster,
                    tikla = { ac(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) },
                ) {}
                Ayrac()
                AyarSatiri(
                    "Pil: Kısıtlanmamış",
                    if (pilSerbest) "Arka planda kapanmam" else "Samsung beni kapatmasın diye gerekli",
                    Ik.PIL,
                    tamam = pilSerbest,
                    tikla = {
                        ac(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)),
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                ) {}
            }

            Bolum("SES")
            SesBolumu(vm)

            Bolum("AKIL")
            AyarKarti {
                AyarSatiri(
                    "Gemma 4 E2B",
                    when (d.akil) {
                        Akil.Durum.HAZIR -> "Hazır · ${vm.arkaUc()} ile çalışıyor"
                        Akil.Durum.YUKLENIYOR -> "Yükleniyor"
                        Akil.Durum.HATA -> "Yüklenemedi"
                        Akil.Durum.MODEL_YOK -> "Henüz inmedi"
                    },
                    Ik.BILGI,
                    tamam = d.akil == Akil.Durum.HAZIR,
                ) {}
                Ayrac()
                AyarSatiri("İzinler", "Mikrofon, rehber, telefon, SMS", Ik.AYAR, tikla = { vm.kurulumAdim(1) }) {}
            }
            Spacer(Modifier.height(8.dp))
            Text("Cezerî · Aşama 3 · Yeni ses", style = govde(12.sp, Renk.Soluk), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UyanmaTesti(vm: Asistan) {
    val t by vm.uyanmaTeshis.collectAsState()
    var servis by remember { mutableStateOf(false) }
    var dinliyor by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            servis = vm.uyanmaServisiCalisiyor(); dinliyor = vm.uyanmaDinliyor()
            delay(700)
        }
    }
    fun yuzde(x: Double) = "%" + (x * 100).toInt().coerceIn(0, 100)
    Text("UYANMA TESTİ", style = etiket(Renk.TurkuazAcik))
    Spacer(Modifier.height(6.dp))
    Text("“Hey Cezerî” de ve burada ne duyduğuma bak.", style = govde(12.sp, Renk.Soluk))
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Renk.KartKoyu)
            .border(1.dp, Renk.Cizgi, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (dinliyor) Renk.Turkuaz else if (servis) Renk.Altin else Renk.Kirmizi))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    dinliyor -> "Dinliyor"
                    servis -> "Beklemede (meşgul ya da hazırlanıyor)"
                    else -> "Arka plan servisi çalışmıyor"
                },
                style = govde(13.sp, Renk.Yazi, FontWeight.Medium)
            )
        }
        Text("Duyduğum: " + if (t.duyulan.isBlank()) "—" else "“${t.duyulan}”", style = govde(13.sp, Renk.SolukAcik))
        if (t.kelimeEsigi > 0) {
            Text("Kelime benzerliği: ${yuzde(t.kelimePuani)} (gereken ${yuzde(t.kelimeEsigi)})", style = govde(13.sp, if (t.kelimePuani >= t.kelimeEsigi) Renk.TurkuazAcik else Renk.SolukAcik))
            Text(
                if (t.sesPuani == null) "Ses kimliği: kayıtlı değil (herkese uyanır)"
                else "Ses benzerliği: ${yuzde(t.sesPuani!!)} (gereken ${yuzde(t.sesEsigi ?: 0.0)})",
                style = govde(13.sp, if (t.sesPuani == null || t.sesPuani!! >= (t.sesEsigi ?: 0.0)) Renk.TurkuazAcik else Renk.SolukAcik)
            )
        }
        if (t.karar.isNotBlank()) Text("Sonuç: ${t.karar}", style = govde(13.sp, Renk.Altin, FontWeight.SemiBold))
        t.hata?.let { Text(it, style = govde(12.sp, Renk.Kirmizi)) }
    }
}

@Composable
private fun SesBolumu(vm: Asistan) {
    val motor by vm.sesMotoru.collectAsState()
    val stil by vm.sesStili.collectAsState()
    val durum by vm.cezeriSesDurumu.collectAsState()
    val cezeri = motor == Ses.MOTOR_CEZERI
    AyarKarti {
        AyarSatiri(
            "Cezerî'nin kendi sesi",
            when (val d = durum) {
                is CezeriSesi.Durum.Yok -> "Bir kez indirilir · olgun, akıcı erkek sesi"
                is CezeriSesi.Durum.Iniyor -> "İndiriliyor · ${d.inen / 1_000_000} / ${d.toplam / 1_000_000} MB"
                CezeriSesi.Durum.Yukleniyor -> "Hazırlanıyor"
                CezeriSesi.Durum.Hazir -> if (cezeri) "Kullanılıyor · telefonda üretilir" else "Hazır"
                is CezeriSesi.Durum.Hata -> d.mesaj
            },
            Ik.HOPARLOR,
            tamam = cezeri && durum is CezeriSesi.Durum.Hazir,
        ) { Anahtar(cezeri) { vm.sesMotoruSec(if (it) Ses.MOTOR_CEZERI else Ses.MOTOR_TELEFON) } }
        val d = durum
        if (d is CezeriSesi.Durum.Iniyor) {
            Spacer(Modifier.height(10.dp))
            val oran = if (d.toplam > 0) (d.inen.toFloat() / d.toplam).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Renk.KartCizgi)) {
                Box(Modifier.fillMaxWidth(oran).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Renk.Altin))
            }
        }
        if (d is CezeriSesi.Durum.Yok || d is CezeriSesi.Durum.Hata) {
            Spacer(Modifier.height(12.dp))
            AltinDugme("Sesi indir", Modifier.fillMaxWidth(), Ik.HOPARLOR) { vm.cezeriSesiIndir() }
        }
        if (cezeri && (d is CezeriSesi.Durum.Hazir || d is CezeriSesi.Durum.Yukleniyor)) {
            Ayrac()
            Text("SES TONU", style = etiket(Renk.TurkuazAcik))
            Spacer(Modifier.height(4.dp))
            Text("Birine dokun, dinle; beğendiğin kalır.", style = govde(12.sp, Renk.Soluk))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CezeriSesi.STILLER.forEachIndexed { i, s ->
                    val secili = s == stil
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (secili) Renk.AltinZemin else Renk.KartKoyu)
                            .border(1.dp, if (secili) Renk.Altin else Renk.Cizgi, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { vm.sesDene(s) }
                            .semantics { contentDescription = "Ses ${i + 1}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = govde(16.sp, if (secili) Renk.Altin else Renk.SolukAcik, FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bolum(ad: String) {
    Text(ad, style = etiket(), modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

@Composable
private fun AyarKarti(icerik: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Renk.Kart)
            .border(1.dp, Renk.KartCizgi, RoundedCornerShape(20.dp)).padding(16.dp),
        content = icerik,
    )
}

@Composable
private fun Ayrac() {
    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Renk.KartCizgi))
}

@Composable
private fun AyarSatiri(
    baslik: String, alt: String, ikon: String, tamam: Boolean = false,
    tikla: (() -> Unit)? = null, sag: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(if (tikla != null) Modifier.clickable(role = Role.Button, onClick = tikla) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Renk.AltinZemin), contentAlignment = Alignment.Center) {
            Ikon(ikon, Renk.Altin, 18.dp, 1.7f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(baslik, style = govde(15.sp, Renk.Yazi, FontWeight.SemiBold))
            Text(alt, style = govde(12.sp, Renk.Soluk))
        }
        if (tamam) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Renk.Turkuaz), contentAlignment = Alignment.Center) { Ikon(Ik.TIK, Renk.Zemin, 14.dp, 2.6f) }
        } else if (tikla != null) {
            Ikon("M9 5l7 7-7 7", Renk.Soluk, 18.dp, 2f)
        }
        sag()
    }
}

@Composable
fun Anahtar(acik: Boolean, degis: (Boolean) -> Unit) {
    Box(
        Modifier.width(52.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
            .background(if (acik) Renk.Turkuaz else Renk.KartCizgi)
            .border(1.dp, if (acik) Renk.Turkuaz else Renk.Cizgi, RoundedCornerShape(15.dp))
            .clickable(role = Role.Switch) { degis(!acik) }
            .semantics { contentDescription = if (acik) "Açık" else "Kapalı" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = if (acik) 25.dp else 3.dp).size(24.dp).clip(CircleShape)
                .background(if (acik) Renk.Zemin else Renk.Soluk)
        )
    }
}

// ------------------------------------------------------------------ Ses kaydı

@Composable
fun SesKaydiEkrani(vm: Asistan, e: Ekran.SesKaydi) {
    BackHandler { vm.sesKaydiKapat() }
    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YuvarlakDugme(Ik.KAPAT, "Kapat") { vm.sesKaydiKapat() }
            Spacer(Modifier.weight(1f))
            Text(if (e.bitti) "Tamamlandı" else "Kayıt ${minOf(e.adim + 1, e.toplam)} / ${e.toplam}", style = govde(13.sp, Renk.Soluk, FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until e.toplam) {
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(
                        when {
                            i < e.adim -> Renk.Altin
                            i == e.adim && !e.bitti -> Renk.AltinKoyu
                            else -> Renk.Cizgi
                        }
                    )
                )
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("Sesini tanıyayım", style = baslik(34.sp))
        Spacer(Modifier.height(10.dp))
        Text("Böylece yalnızca sen “Hey Cezerî” dediğinde uyanırım. Başkasının sesine cevap vermem.", style = govde(15.sp, Renk.Soluk))
        Spacer(Modifier.height(38.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(196.dp).clip(CircleShape).border(1.dp, Renk.Turkuaz.copy(alpha = 0.35f), CircleShape))
                Box(
                    Modifier.size(176.dp).clip(CircleShape).background(Renk.Amblem).border(1.dp, Renk.AltinIs, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (e.bitti) Ikon(Ik.TIK, Renk.Turkuaz, 64.dp, 2.4f) else Dalga(0.6f, yukseklik = 60.dp)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(if (e.bitti) "HAZIR" else "ŞİMDİ SÖYLE", style = etiket(Renk.TurkuazAcik))
            Spacer(Modifier.height(8.dp))
            Text(if (e.bitti) "Seni tanıyorum" else "“Hey Cezerî”", style = baslik(32.sp))
        }
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Renk.Kart)
                .border(1.dp, Renk.KartCizgi, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Ikon(Ik.BILGI, Renk.Altin, 20.dp, 1.8f)
            Spacer(Modifier.width(12.dp))
            Text(e.mesaj, style = govde(13.sp, Renk.SolukAcik))
        }
        Spacer(Modifier.weight(1f))
        if (e.bitti) AltinDugme("Tamam") { vm.sesKaydiKapat() }
        else SadeDugme("Bu kaydı tekrarla", Modifier.fillMaxWidth(), 52.dp) { vm.sesKaydiTekrar() }
    }
}
