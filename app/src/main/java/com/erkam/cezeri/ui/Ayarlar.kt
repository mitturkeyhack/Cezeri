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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.erkam.cezeri.core.Olcum
import kotlinx.coroutines.launch
import com.erkam.cezeri.core.Ses
import com.erkam.cezeri.asistan.VakitHatirlatici
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

            Bolum("“HEY CEZERÎ”Yİ ÖĞRET")
            AyarKarti {
                val ogrenilen = remember(d.kimlikOrnek, d.ekran) { vm.ogrenilenSayisi() }
                AyarSatiri(
                    "Söyleyişin",
                    if (ogrenilen > 0) "Öğrenildi · senin söyleyişine göre uyanır" else "Öğretilmedi · 5 kez söyletip ezberler",
                    Ik.KISI,
                    tamam = ogrenilen > 0,
                ) {}
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(Renk.Altin).clickable { vm.sesKaydiBaslat() },
                        contentAlignment = Alignment.Center
                    ) { Text(if (ogrenilen > 0) "Yeniden öğret" else "Öğret", style = govde(14.sp, Renk.Zemin, FontWeight.SemiBold)) }
                    if (ogrenilen > 0 || d.kimlikOrnek > 0) SadeDugme("Sil", Modifier.weight(0.6f), 46.dp) { vm.kimligiSil() }
                }
                if (d.kimlikOrnek > 0) {
                    Ayrac()
                    var sadece by remember { mutableStateOf(vm.sadeceSahip()) }
                    AyarSatiri("Sadece benim sesime uyan", "İsteğe bağlı · açıkken uyanma biraz zorlaşır", Ik.KISI) {
                        Anahtar(sadece) { sadece = it; vm.sadeceSahipSec(it) }
                    }
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

            Bolum("NAMAZ")
            NamazBolumu(vm, d, yenile) { yenile++ }

            Bolum("HAFIZA")
            HafizaBolumu(vm, d)

            Bolum("AKIL · EVREN")
            EvrenBolumu(vm)

            Bolum("HIZ ÖLÇER")
            HizOlcer()

            Bolum("İZİNLER")
            AyarKarti {
                AyarSatiri("İzinler", "Mikrofon, rehber, telefon, SMS, konum", Ik.AYAR, tikla = { vm.kurulumAdim(1) }) {}
            }
            Spacer(Modifier.height(8.dp))
            Text("Cezerî · Aşama 5 · EVREN", style = govde(12.sp, Renk.Soluk), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
private fun EvrenBolumu(vm: Asistan) {
    val akil = vm.evren
    val durum by akil.durum.collectAsState()
    val test by akil.sonTest.collectAsState()
    val modeller by akil.modeller.collectAsState()
    val hizli by akil.hizliModel.collectAsState()
    val derin by akil.derinModel.collectAsState()
    var anahtarAcik by remember { mutableStateOf(akil.anahtar().isBlank()) }
    var yeni by remember { mutableStateOf("") }
    var deniyor by remember { mutableStateOf(false) }
    val kapsam = rememberCoroutineScope()
    val pano = androidx.compose.ui.platform.LocalClipboardManager.current
    fun dene() { deniyor = true; kapsam.launch { akil.dene(); deniyor = false } }
    AyarKarti {
        AyarSatiri(
            "Bağlantı",
            when {
                deniyor -> "Deneniyor…"
                durum == Akil.Durum.MODEL_YOK -> "Anahtar girilmedi"
                test?.basarili == true -> "Bağlı · son deneme ${test?.ms} ms"
                test != null -> test?.mesaj ?: ""
                else -> "Anahtar kayıtlı"
            },
            Ik.BILGI,
            tamam = test?.basarili == true,
        ) {}
        Ayrac()
        AyarSatiri(
            "API anahtarı",
            if (akil.anahtar().isBlank()) "Girilmedi" else "•••• •••• ${akil.anahtarSonu()}",
            Ik.AYAR,
        ) {
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(11.dp)).background(Renk.AltinZemin)
                    .border(1.dp, Renk.AltinCizgi, RoundedCornerShape(11.dp)).clickable { anahtarAcik = !anahtarAcik }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (anahtarAcik) "Kapat" else "Değiştir", style = govde(13.sp, Renk.Altin, FontWeight.Medium)) }
        }
        if (anahtarAcik) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Renk.KartKoyu)
                    .border(1.dp, Renk.AltinCizgi, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = yeni, onValueChange = { yeni = it.trim() }, singleLine = true,
                    textStyle = govde(14.sp, Renk.Yazi),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Renk.Altin),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    decorationBox = { ic -> if (yeni.isEmpty()) Text("Yeni anahtar", style = govde(14.sp, Renk.Soluk)); ic() },
                )
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).clickable {
                        val p = pano.getText()?.text?.trim().orEmpty()
                        if (yeni.isBlank() && p.isNotBlank()) yeni = p
                        if (yeni.isNotBlank()) { akil.anahtarKaydet(yeni); yeni = ""; anahtarAcik = false; dene() }
                    }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (yeni.isBlank()) "Yapıştır" else "Kaydet", style = govde(13.sp, Renk.Altin, FontWeight.SemiBold)) }
            }
        }
        Ayrac()
        Text("Hızlı mod modeli", style = govde(15.sp, Renk.Yazi, FontWeight.SemiBold))
        Text("Günlük sohbet ve “Hey Cezerî” cevapları", style = govde(12.5.sp, Renk.Soluk))
        Spacer(Modifier.height(10.dp))
        ModelCipleri((modeller.ifEmpty { listOf(hizli) }).sortedByDescending { it.contains("flash", true) }, hizli) { akil.hizliSec(it) }
        Ayrac()
        Text("Derin düşün modeli", style = govde(15.sp, Renk.Yazi, FontWeight.SemiBold))
        Text("Uzun ve zor sorular; biraz daha yavaş", style = govde(12.5.sp, Renk.Soluk))
        Spacer(Modifier.height(10.dp))
        ModelCipleri(modeller.ifEmpty { listOf(derin) }, derin) { akil.derinSec(it) }
        Spacer(Modifier.height(8.dp))
        Text("Liste, bağlanınca EVREN'den güncel olarak çekilir.", style = govde(11.5.sp, Renk.Soluk))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Renk.TurkuazZemin)
                .border(1.dp, Renk.TurkuazCizgi, RoundedCornerShape(14.dp)).clickable(enabled = !deniyor) { dene() },
            contentAlignment = Alignment.Center,
        ) { Text(if (deniyor) "Deneniyor…" else "Bağlantıyı dene", style = govde(15.sp, Renk.TurkuazYazi, FontWeight.Medium)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModelCipleri(liste: List<String>, secili: String, sec: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        liste.forEach { m ->
            val s = m == secili
            Box(
                Modifier.height(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (s) Renk.AltinZemin else Renk.KartKoyu)
                    .border(1.dp, if (s) Renk.Altin else Renk.Cizgi, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { sec(m) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(m, style = govde(13.sp, if (s) Renk.Altin else Renk.SolukAcik, if (s) FontWeight.Medium else FontWeight.Normal)) }
        }
    }
}

@Composable
private fun HizOlcer() {
    val son by Olcum.son.collectAsState()
    val gecmis by Olcum.gecmis.collectAsState()
    fun ms(v: Long?) = when {
        v == null -> "—"
        v >= 1000 -> "%.1f sn".format(v / 1000.0).replace('.', ',')
        else -> "$v ms"
    }
    AyarKarti {
        val k = son
        if (k == null) {
            Text("Henüz ölçüm yok. Cezerî'ye bir şey sor; sözünü bitirmenden ilk sese kadar geçen süre burada görünür.", style = govde(13.sp, Renk.Soluk))
            return@AyarKarti
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ms(k.toplam), style = baslik(40.sp, Renk.Altin))
            Spacer(Modifier.width(10.dp))
            Text(if (k.evren) "sözünü bitirmenden ilk sese kadar" else "komut · sunucuya gitmeden", style = govde(13.sp, Renk.SolukAcik), modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        val enUzun = listOfNotNull(k.uyanma, k.dinleme, k.dusunme, k.ses).maxOrNull()?.coerceAtLeast(1L) ?: 1L
        OlcumSatiri("Uyanma", k.uyanma, enUzun, Renk.TurkuazAcik, ::ms)
        OlcumSatiri("Dinleme (sözün yazıya dökülmesi)", k.dinleme, enUzun, Renk.Turkuaz, ::ms)
        OlcumSatiri("Düşünme · EVREN ilk kelime", k.dusunme, enUzun, Renk.Altin, ::ms)
        OlcumSatiri("Ses · ilk cümle hazır", k.ses, enUzun, Renk.AltinAcik, ::ms)
        Ayrac()
        Row { Text("Son ${gecmis.count { it.evren }} sohbetin ortalaması", style = govde(12.5.sp, Renk.Soluk), modifier = Modifier.weight(1f)); Text(ms(Olcum.ortalama(true)), style = govde(12.5.sp, Renk.SolukAcik)) }
        Spacer(Modifier.height(6.dp))
        Row { Text("Komutlar (ara, vakit…) ortalaması", style = govde(12.5.sp, Renk.Soluk), modifier = Modifier.weight(1f)); Text(ms(Olcum.ortalama(false)), style = govde(12.5.sp, Renk.SolukAcik)) }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(13.dp)).background(Renk.KartKoyu)
                .border(1.dp, Renk.Cizgi, RoundedCornerShape(13.dp)).clickable { Olcum.sifirla() },
            contentAlignment = Alignment.Center,
        ) { Text("Ölçümleri sıfırla", style = govde(14.sp, Renk.SolukAcik)) }
    }
}

@Composable
private fun OlcumSatiri(ad: String, deger: Long?, enUzun: Long, renk: androidx.compose.ui.graphics.Color, yaz: (Long?) -> String) {
    Row { Text(ad, style = govde(13.sp, Renk.Yazi), modifier = Modifier.weight(1f)); Text(yaz(deger), style = govde(13.sp, Renk.Soluk)) }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.KartCizgi)) {
        if (deger != null) Box(Modifier.fillMaxWidth((deger.toFloat() / enUzun).coerceIn(0.03f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(renk))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NamazBolumu(vm: Asistan, d: Durum, yenile: Int, tazele: () -> Unit) {
    val ctx = LocalContext.current
    val izin = remember(yenile) { vm.konumIzniVar() }
    val izinIste = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
        tazele(); if (vm.konumIzniVar()) vm.konumGuncelle()
    }
    var hatirlat by remember { mutableStateOf(VakitHatirlatici.acik(ctx)) }
    var once by remember { mutableIntStateOf(VakitHatirlatici.once(ctx)) }
    var sesli by remember { mutableStateOf(VakitHatirlatici.sesli(ctx)) }
    val tamZaman = remember(yenile) { VakitHatirlatici.tamZamanliIzin(ctx) }
    val araniyor by vm.konumAraniyor.collectAsState()
    LaunchedEffect(izin) { if (izin && d.konum == null) vm.konumGuncelle() }
    AyarKarti {
        AyarSatiri(
            "Konum",
            when {
                !izin -> "Vakitler ve kıble için gerekli"
                d.konum == null && araniyor -> "Konum bulunuyor…"
                d.konum == null -> "Bulunamadı · konum servisi açıkken tekrar dokun"
                else -> "Kayıtlı · %.3f, %.3f".format(d.konum.first, d.konum.second)
            },
            Ik.BILGI,
            tamam = izin && d.konum != null,
            tikla = {
                if (!izin) izinIste.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                else vm.konumGuncelle()
            },
        ) {}
        Ayrac()
        AyarSatiri("Vakit hatırlatıcı", if (hatirlat) "Ezandan $once dakika önce haber veririm" else "Kapalı", Ik.ALARM) {
            Anahtar(hatirlat) { hatirlat = it; VakitHatirlatici.ayarla(ctx, acik = it) }
        }
        if (hatirlat) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(5, 10, 15, 30).forEach { dk ->
                    val secili = dk == once
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (secili) Renk.AltinZemin else Renk.KartKoyu)
                            .border(1.dp, if (secili) Renk.Altin else Renk.Cizgi, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { once = dk; VakitHatirlatici.ayarla(ctx, once = dk) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$dk dk", style = govde(14.sp, if (secili) Renk.Altin else Renk.SolukAcik, FontWeight.Medium)) }
                }
            }
            Ayrac()
            AyarSatiri("Sesli söyle", "Bildirimle birlikte Cezerî de söyler", Ik.HOPARLOR) {
                Anahtar(sesli) { sesli = it; VakitHatirlatici.ayarla(ctx, sesli = it) }
            }
            if (!tamZaman) {
                Ayrac()
                AyarSatiri("Tam vaktinde hatırlat", "Dakikası dakikasına haber vermem için izin ver", Ik.AYAR, tikla = {
                    ac(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }) {}
            }
        }
        Ayrac()
        AyarSatiri("Kıble", "Pusulayla Kâbe yönünü göster", Ik.BILGI, tikla = { vm.kibleAc() }) {}
    }
}

@Composable
private fun HafizaBolumu(vm: Asistan, d: Durum) {
    AyarKarti {
        if (d.notlar.isEmpty()) {
            Text("“Şunu hatırla: annemin doğum günü 3 Mart” dersen burada durur; sorduğunda hatırlarım.", style = govde(13.sp, Renk.Soluk))
        } else {
            Text("Aklımda tuttuklarım", style = govde(15.sp, Renk.Yazi, FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            d.notlar.reversed().forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Renk.KartCizgi))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.metin, style = govde(14.sp, Renk.SolukAcik), modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { vm.notSil(n.id) }
                            .semantics { contentDescription = "Sil" },
                        contentAlignment = Alignment.Center,
                    ) { Ikon(Ik.KAPAT, Renk.Soluk, 14.dp, 2f) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SesBolumu(vm: Asistan) {
    val motor by vm.sesMotoru.collectAsState()
    val stil by vm.sesStili.collectAsState()
    val hiz by vm.sesHizi.collectAsState()
    val tarif by vm.sesTarifi.collectAsState()
    val hata by vm.sesHatasi.collectAsState()
    val onbellek by vm.sesOnbellek.collectAsState()
    val evren = motor == Ses.MOTOR_EVREN
    AyarKarti {
        AyarSatiri(
            "Cezerî'nin sesi",
            when {
                !evren -> "Telefonun Türkçe sesi kullanılıyor"
                hata != null -> hata ?: ""
                else -> "EVREN · birine dokun, dinle; beğendiğin kalır"
            },
            Ik.HOPARLOR,
        ) { Anahtar(evren) { vm.sesMotoruSec(if (it) Ses.MOTOR_EVREN else Ses.MOTOR_TELEFON) } }
        if (evren) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.erkam.cezeri.ses.EvrenSes.SESLER.forEach { s ->
                    val secili = s == stil
                    Column(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (secili) Renk.AltinZemin else Renk.KartKoyu)
                            .border(1.dp, if (secili) Renk.Altin else Renk.Cizgi, RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { vm.sesDene(s) }
                            .semantics { contentDescription = "Ses: $s" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(s.replaceFirstChar { it.titlecase(com.erkam.cezeri.core.Turkce.TR) }, style = govde(15.sp, if (secili) Renk.Altin else Renk.SolukAcik, FontWeight.SemiBold))
                        Text(if (secili) "seçili" else "dinle", style = govde(11.5.sp, if (secili) Renk.AltinAcik else Renk.Soluk))
                    }
                }
            }
            Ayrac()
            AyarSatiri("Cezerî tarifi", "Olgun, sakin, sıcak bir erkek sesi gibi konuşsun", Ik.KISI) {
                Anahtar(tarif) { vm.sesTarifiSec(it) }
            }
        }
        Ayrac()
        Text("Konuşma hızı", style = govde(15.sp, Renk.Yazi, FontWeight.SemiBold))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(1.0f to "Normal", 1.15f to "1,15×", 1.3f to "1,3×", 1.5f to "1,5×").forEach { (h, ad) ->
                val secili = kotlin.math.abs(h - hiz) < 0.01f
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (secili) Renk.AltinZemin else Renk.KartKoyu)
                        .border(1.dp, if (secili) Renk.Altin else Renk.Cizgi, RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { vm.sesHiziSec(h) },
                    contentAlignment = Alignment.Center,
                ) { Text(ad, style = govde(13.5.sp, if (secili) Renk.Altin else Renk.SolukAcik, if (secili) FontWeight.SemiBold else FontWeight.Normal)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Renk.TurkuazZemin)
                .border(1.dp, Renk.TurkuazCizgi, RoundedCornerShape(14.dp)).clickable { vm.sesDene(stil) },
            contentAlignment = Alignment.Center,
        ) { Text("Dinle: “Selâm, ben Cezerî.”", style = govde(15.sp, Renk.TurkuazYazi, FontWeight.Medium)) }
        Spacer(Modifier.height(10.dp))
        Text(
            if (evren) "Sık cümlelerden $onbellek tanesi telefonda hazır, anında çalar. Bağlantı yoksa telefonun Türkçe sesiyle konuşur."
            else "EVREN sesini açarsan ilk ses yaklaşık 1 saniyede gelir.",
            style = govde(12.sp, Renk.Soluk)
        )
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
        Text("“Hey Cezerî”yi bana öğret", style = baslik(34.sp))
        Spacer(Modifier.height(10.dp))
        Text("5 kez, her zamanki gibi söyle. Senin söyleyişini ezberleyip ona göre uyanacağım.", style = govde(15.sp, Renk.Soluk))
        Spacer(Modifier.height(26.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(156.dp).clip(CircleShape).border(1.dp, Renk.Turkuaz.copy(alpha = 0.35f), CircleShape))
                Box(
                    Modifier.size(138.dp).clip(CircleShape).background(Renk.Amblem).border(1.dp, Renk.AltinIs, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (e.bitti) Ikon(Ik.TIK, Renk.Turkuaz, 64.dp, 2.4f) else Dalga(0.6f, yukseklik = 60.dp)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(if (e.bitti) "HAZIR" else "ŞİMDİ SÖYLE", style = etiket(Renk.TurkuazAcik))
            Spacer(Modifier.height(8.dp))
            Text(if (e.bitti) "Öğrendim" else "“Hey Cezerî”", style = baslik(32.sp))
        }
        if (e.duyulanlar.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Renk.Kart)
                    .border(1.dp, Renk.KartCizgi, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("NE DUYDUM", style = etiket(Renk.TurkuazAcik))
                e.duyulanlar.forEachIndexed { i, s -> Text("${i + 1}.  “$s”", style = govde(14.sp, Renk.Yazi)) }
            }
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
