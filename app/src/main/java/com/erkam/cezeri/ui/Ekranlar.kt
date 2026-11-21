package com.erkam.cezeri.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkam.cezeri.Asistan
import com.erkam.cezeri.Durum
import com.erkam.cezeri.Ekran
import com.erkam.cezeri.core.Akil
import com.erkam.cezeri.core.Kanal
import com.erkam.cezeri.core.Kisi
import com.erkam.cezeri.core.SonIs
import com.erkam.cezeri.core.Turkce
import java.util.Calendar

@Composable
fun Uygulama(vm: Asistan, d: Durum) {
    Box(Modifier.fillMaxSize().background(Renk.Zemin)) {
        when (val e = d.ekran) {
            is Ekran.Kurulum -> when (e.adim) {
                0 -> KurulumHosgeldin(vm)
                1 -> KurulumIzinler(vm)
                else -> KurulumHazirlik(vm, d)
            }
            Ekran.Ana -> AnaEkran(vm, d)
            Ekran.Sohbet -> SohbetEkrani(vm, d)
            is Ekran.KisiSec -> KisiSecEkrani(vm, e)
            is Ekran.AramaOnay -> AramaOnayEkrani(vm, e.kisi)
            is Ekran.MesajOnay -> MesajOnayEkrani(vm, e)
            Ekran.Ayarlar -> AyarlarEkrani(vm, d)
            is Ekran.GelenArama -> GelenAramaEkrani(vm, e)
            is Ekran.SesKaydi -> SesKaydiEkrani(vm, e)
            is Ekran.Kible -> KibleEkrani(vm, e)
        }
        val tamEkran = d.ekran == Ekran.Ana || d.ekran == Ekran.Sohbet
        AnimatedVisibility(d.dinliyor && tamEkran, enter = fadeIn(), exit = fadeOut()) {
            DinliyorKatmani(vm, d)
        }
        // Onay ve seçim ekranlarında seçenekler görünür kalsın: altta küçük dinleme şeridi
        AnimatedVisibility(
            d.dinliyor && !tamEkran && d.ekran !is Ekran.Kurulum,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Renk.ZeminDerin).border(1.dp, Renk.TurkuazCizgi)
                    .clickable { vm.dinlemeyiBirak() }
                    .navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Renk.Turkuaz))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (d.araMetin.isBlank()) "Dinliyorum…" else "“${d.araMetin}”",
                    style = govde(16.sp, Renk.TurkuazYazi, FontWeight.Medium), modifier = Modifier.weight(1f)
                )
                Dalga(d.seviye, yukseklik = 28.dp)
            }
        }
    }
}

// ------------------------------------------------------------------ Ana ekran

private fun selamlama(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "Hayırlı sabahlar"
    in 11..16 -> "Hayırlı günler"
    in 17..21 -> "Hayırlı akşamlar"
    else -> "Hayırlı geceler"
}

@Composable
fun AnaEkran(vm: Asistan, d: Durum) {
    var bilgi by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KucukAmblem(26.dp)
            Spacer(Modifier.width(10.dp))
            Text("CEZERÎ", style = baslik(20.sp, Renk.Altin).copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp))
            Spacer(Modifier.weight(1f))
            YuvarlakDugme(Ik.AYAR, "Ayarlar", ikonBoyut = 20.dp) { vm.ayarlar() }
        }
        Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val dokunma = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(Modifier.clickable(interactionSource = dokunma, indication = null, role = Role.Button) { vm.mikrofon() }) {
                Amblem(190.dp, don = d.konusuyor, nabiz = false)
            }
            Spacer(Modifier.height(22.dp))
            Text(selamlama(), style = baslik(36.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Buyur, seni dinliyorum.", style = govde(15.sp, Renk.Soluk))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF121A1C)).border(1.dp, Renk.TurkuazCizgi, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(durumRengi(d)))
                Spacer(Modifier.width(8.dp))
                Text(durumYazisi(d), style = govde(13.sp, Renk.TurkuazAcik, FontWeight.Medium))
            }
        }
        Spacer(Modifier.height(20.dp))
        VakitKarti(vm, d)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SON İŞLER", style = etiket())
            Spacer(Modifier.weight(1f))
            if (d.sohbet.isNotEmpty()) Text("Sohbet", style = govde(13.sp, Renk.Altin), modifier = Modifier.clickable { vm.sohbeteGit() }.padding(4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (d.isler.isEmpty()) {
                Text("“Babamı ara”, “Ahmet'e akşam geliyorum yaz”, “yarın altıda alarm kur” ya da aklına gelen her şeyi sorabilirsin.", style = govde(14.sp, Renk.Soluk))
            }
            d.isler.take(3).forEach { IsSatiri(it) }
        }
        GirisCubugu(vm, "Yaz ya da konuş…")
    }
    if (bilgi) BilgiPenceresi(vm, d) { bilgi = false }
}

private fun durumRengi(d: Durum): Color = when (d.akil) {
    Akil.Durum.HAZIR -> Renk.Turkuaz
    Akil.Durum.HATA -> Renk.Kirmizi
    else -> Renk.Altin
}

private fun durumYazisi(d: Durum): String = when {
    d.akil == Akil.Durum.HAZIR && d.uyanmaAcik && d.uyanmaModel is com.erkam.cezeri.core.Uyanma.ModelDurumu.Hazir -> "“Hey Cezerî” dediğinde uyanır"
    d.akil == Akil.Durum.HAZIR -> "Mikrofona dokun ve konuş"
    d.akil == Akil.Durum.YUKLENIYOR -> "Bağlanıyor…"
    d.akil == Akil.Durum.HATA -> "EVREN'e ulaşılamadı · Komutlar çalışıyor"
    else -> "Sohbet için Ayarlar'dan EVREN anahtarını ekle"
}

@Composable
private fun IsSatiri(i: SonIs) {
    val (ikon, renk, zemin) = when (i.tur) {
        "ara" -> Triple(Ik.TELEFON, Renk.Altin, Color(0xFF1E1A12))
        "mesaj" -> Triple(Ik.MESAJ, Renk.Turkuaz, Color(0xFF12201F))
        else -> Triple(Ik.ALARM, Renk.Altin, Color(0xFF1E1A12))
    }
    Kart(Modifier.fillMaxWidth(), kose = 16.dp) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(zemin), contentAlignment = Alignment.Center) { Ikon(ikon, renk, 18.dp, 1.7f) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(i.baslik, style = govde(14.sp, Renk.Yazi, FontWeight.Medium))
            val saat = java.text.SimpleDateFormat("HH:mm", Turkce.TR).format(java.util.Date(i.zaman))
            Text("${i.alt} · $saat", style = govde(12.sp, Renk.Soluk))
        }
    }
}

@Composable
private fun BilgiPenceresi(vm: Asistan, d: Durum, kapat: () -> Unit) {
    AlertDialog(
        onDismissRequest = kapat,
        containerColor = Renk.Kart,
        title = { Text("Cezerî", style = baslik(26.sp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Akıl: " + when (d.akil) {
                    Akil.Durum.HAZIR -> "EVREN · ${vm.arkaUc()}"
                    Akil.Durum.YUKLENIYOR -> "bağlanıyor"
                    Akil.Durum.HATA -> "EVREN'e ulaşılamadı"
                    Akil.Durum.MODEL_YOK -> "EVREN anahtarı girilmedi"
                }, style = govde(14.sp))
                Text("Ses: " + (vm.sesAdi() ?: "telefonun Türkçe sesi"), style = govde(14.sp))
                Text("Sürüm: Aşama 5 · EVREN", style = govde(14.sp, Renk.Soluk))
            }
        },
        confirmButton = { TextButton(onClick = kapat) { Text("Tamam", style = govde(14.sp, Renk.Altin, FontWeight.SemiBold)) } },
        dismissButton = {
            TextButton(onClick = { kapat(); vm.kurulumAdim(1) }) { Text("İzinler", style = govde(14.sp, Renk.Soluk)) }
        },
    )
}

// ------------------------------------------------------------------ Giriş çubuğu

@Composable
fun GirisCubugu(vm: Asistan, ipucu: String) {
    var metin by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Renk.Kart)
                .border(1.dp, Renk.Cizgi, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (metin.isEmpty()) Text(ipucu, style = govde(15.sp, Renk.Soluk))
            BasicTextField(
                value = metin, onValueChange = { metin = it }, singleLine = true,
                textStyle = govde(15.sp, Renk.Yazi), cursorBrush = SolidColor(Renk.Altin),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.yazdi(metin); metin = "" }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (metin.isNotBlank()) {
            YuvarlakDugme(Ik.GONDER, "Gönder", 56.dp, Renk.Altin, Renk.Zemin, cerceve = false, ikonBoyut = 20.dp) { vm.yazdi(metin); metin = "" }
        } else {
            YuvarlakDugme(Ik.MIKROFON, "Konuşarak sor", 56.dp, Renk.Altin, Renk.Zemin, cerceve = false, ikonBoyut = 22.dp) { vm.mikrofon() }
        }
    }
}

// ------------------------------------------------------------------ Dinliyor

@Composable
fun DinliyorKatmani(vm: Asistan, d: Durum) {
    BackHandler { vm.dinlemeyiBirak() }
    Column(
        Modifier.fillMaxSize().background(Renk.ZeminDerin).clickable(enabled = false) {}
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Renk.Turkuaz))
            Spacer(Modifier.width(8.dp))
            Text("DİNLİYORUM", style = etiket(Renk.TurkuazAcik))
        }
        Spacer(Modifier.height(90.dp))
        // Sabit boyutlu alan: hiçbir şey büyüyüp küçülmez, sadece kadran döner
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(232.dp).clip(CircleShape).background(Renk.Turkuaz.copy(alpha = 0.06f)))
            Amblem(196.dp, don = true, nabiz = false, aciklama = "Cezerî dinliyor")
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Dalga(d.seviye) }
        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                if (d.araMetin.isBlank()) "…" else "“${d.araMetin}”",
                style = baslik(32.sp), textAlign = TextAlign.Center, maxLines = 3,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SadeDugme("Vazgeç", Modifier.weight(1f), 54.dp) { vm.dinlemeyiBirak() }
            Spacer(Modifier.width(12.dp))
            YuvarlakDugme(Ik.KLAVYE, "Klavyeye geç", 54.dp) { vm.dinlemeyiBirak() }
        }
    }
}

// ------------------------------------------------------------------ Onay ekranları ortak başlık

@Composable
private fun CezeriSoruyor(alt: String, soru: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KucukAmblem(44.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("CEZERÎ", style = baslik(18.sp, Renk.Altin).copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ikon(Ik.HOPARLOR, Renk.TurkuazAcik, 14.dp, 2f)
                Spacer(Modifier.width(6.dp))
                Text(alt, style = govde(12.sp, Renk.TurkuazAcik))
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).background(Renk.Kart)
            .border(1.dp, Renk.AltinIs, RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).padding(20.dp)
    ) { Text(soru, style = baslik(27.sp)) }
}

@Composable
private fun SesIpucu(icerik: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Renk.TurkuazZemin)
            .border(1.dp, Renk.TurkuazCizgi, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Ikon(Ik.MIKROFON, Renk.Turkuaz, 20.dp, 1.8f)
        Spacer(Modifier.width(12.dp))
        Text(icerik, style = govde(14.sp, Renk.TurkuazYazi))
    }
}

@Composable
fun KisiSecEkrani(vm: Asistan, e: Ekran.KisiSec) {
    BackHandler { vm.iptal() }
    Column(Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 26.dp)) {
        CezeriSoruyor("Sesli soruyor", e.soru)
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            e.secenekler.forEachIndexed { i, k -> KisiKarti(i, k, i == 0) { vm.kisiSec(k) } }
        }
        Spacer(Modifier.height(20.dp))
        SesIpucu("Sesle cevap verebilirsin: “birinci”, “ikinci” ya da soyadını söyle.")
        Spacer(Modifier.weight(1f))
        Text("Seçimini hatırlarım, bir dahakine sormam.", style = govde(12.sp, Renk.Soluk), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        SadeDugme("Vazgeç", Modifier.fillMaxWidth(), 54.dp) { vm.iptal() }
    }
}

@Composable
private fun KisiKarti(i: Int, k: Kisi, vurgulu: Boolean, tikla: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (vurgulu) Renk.AltinZemin else Renk.Kart)
            .border(if (vurgulu) 1.5.dp else 1.dp, if (vurgulu) Renk.Altin else Renk.Cizgi, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = tikla).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (vurgulu) Renk.Altin else Renk.KartCizgi), contentAlignment = Alignment.Center) {
            Text("${i + 1}", style = govde(15.sp, if (vurgulu) Renk.Zemin else Renk.Yazi, FontWeight.SemiBold))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(k.ad, style = govde(16.sp, Renk.Yazi, FontWeight.SemiBold))
            Text("${k.etiket} · ${k.gizliNumara}", style = govde(13.sp, Renk.Soluk))
        }
        Ikon(Ik.TELEFON, if (vurgulu) Renk.Altin else Renk.Ikon, 20.dp, 1.8f)
    }
}

@Composable
private fun KisiBasligi(k: Kisi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Renk.AltinIs), contentAlignment = Alignment.Center) {
            Text(k.ad.take(1).uppercase(Turkce.TR), style = baslik(17.sp, Renk.Altin).copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(k.ad, style = govde(15.sp, Renk.Yazi, FontWeight.SemiBold))
            Text("${k.etiket} · ${k.gizliNumara}", style = govde(12.sp, Renk.Soluk))
        }
    }
}

@Composable
fun AramaOnayEkrani(vm: Asistan, k: Kisi) {
    BackHandler { vm.onayHayir() }
    Column(Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 26.dp)) {
        CezeriSoruyor("Sesli soruyor", "${vm.kime(k, belirtme = true)} arayayım mı?")
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Renk.KartKoyu).border(1.dp, Renk.Cizgi, RoundedCornerShape(20.dp)).padding(16.dp)) {
            KisiBasligi(k)
        }
        Spacer(Modifier.height(18.dp))
        SesIpucu("“Ara” ya da “vazgeç” diyebilirsin.")
        Spacer(Modifier.weight(1f))
        AltinDugme("Ara", ikon = Ik.TELEFON) { vm.onayEvet() }
        Spacer(Modifier.height(10.dp))
        SadeDugme("Vazgeç", Modifier.fillMaxWidth(), 50.dp) { vm.onayHayir() }
    }
}

@Composable
fun MesajOnayEkrani(vm: Asistan, e: Ekran.MesajOnay) {
    BackHandler { vm.onayHayir() }
    Column(Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 26.dp)) {
        CezeriSoruyor("Mesajı sesli okuyor", "${vm.kime(e.kisi, belirtme = false)} şunu yazıyorum. Göndereyim mi?")
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Renk.KartKoyu).border(1.dp, Renk.Cizgi, RoundedCornerShape(20.dp))) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) { KisiBasligi(e.kisi) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.KartCizgi))
            Text(e.metin, style = govde(17.sp, Renk.Yazi), modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            KanalSecenegi("WhatsApp", e.kanal == Kanal.WHATSAPP, Modifier.weight(1f)) { vm.kanalSec(Kanal.WHATSAPP) }
            Spacer(Modifier.width(10.dp))
            KanalSecenegi("SMS", e.kanal == Kanal.SMS, Modifier.weight(1f)) { vm.kanalSec(Kanal.SMS) }
        }
        Spacer(Modifier.height(18.dp))
        SesIpucu("“Gönder”, “değiştir” ya da “SMS olsun” diyebilirsin.")
        Spacer(Modifier.weight(1f))
        AltinDugme("Gönder", ikon = Ik.GONDER) { vm.onayEvet() }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            SadeDugme("Düzelt", Modifier.weight(1f), 50.dp) { vm.onayDegistir() }
            Spacer(Modifier.width(10.dp))
            SadeDugme("İptal", Modifier.weight(1f), 50.dp) { vm.onayHayir() }
        }
    }
}

@Composable
private fun KanalSecenegi(ad: String, secili: Boolean, modifier: Modifier, tikla: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (secili) Renk.TurkuazZemin else Renk.Kart)
            .border(if (secili) 1.5.dp else 1.dp, if (secili) Renk.Turkuaz else Renk.Cizgi, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = tikla),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (secili) { Ikon(Ik.TIK, Renk.Turkuaz, 16.dp, 2f); Spacer(Modifier.width(8.dp)) }
        Text(ad, style = govde(14.sp, if (secili) Renk.TurkuazYazi else Renk.Ikon, if (secili) FontWeight.SemiBold else FontWeight.Medium))
    }
}

// ------------------------------------------------------------------ Sohbet

@Composable
fun SohbetEkrani(vm: Asistan, d: Durum) {
    BackHandler { vm.iptal() }
    val liste = rememberLazyListState()
    LaunchedEffect(d.sohbet.size, d.sohbet.lastOrNull()?.metin?.length) {
        if (d.sohbet.isNotEmpty()) liste.animateScrollToItem(d.sohbet.lastIndex)
    }
    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YuvarlakDugme(Ik.GERI, "Geri") { vm.iptal() }
            Spacer(Modifier.weight(1f))
            Text("CEZERÎ", style = baslik(18.sp, Renk.Altin).copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            Spacer(Modifier.weight(1f))
            YuvarlakDugme(Ik.YENI, "Yeni sohbet") { vm.yeniSohbet() }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Renk.Kart).border(1.dp, Renk.KartCizgi, RoundedCornerShape(16.dp)).padding(4.dp)
        ) {
            val derin = d.derinMod
            val derinYazi = "Derin düşün"
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (!derin) Renk.KartCizgi else Color.Transparent)
                    .clickable(role = Role.RadioButton) { vm.derinSec(false) },
                contentAlignment = Alignment.Center
            ) { Text("Hızlı", style = govde(13.sp, if (!derin) Renk.Yazi else Renk.Soluk, if (!derin) FontWeight.SemiBold else FontWeight.Medium)) }
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (derin) Renk.AltinZemin else Color.Transparent)
                    .clickable(role = Role.RadioButton) { vm.derinSec(true) },
                contentAlignment = Alignment.Center
            ) { Text(derinYazi, style = govde(13.sp, if (derin) Renk.AltinAcik else Renk.Soluk, if (derin) FontWeight.SemiBold else FontWeight.Medium)) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = liste,
            contentPadding = PaddingValues(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(d.sohbet) { i, m ->
                if (m.benden) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(
                            Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(Renk.AltinIs)
                                .border(1.dp, Renk.AltinCizgi, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).padding(horizontal = 16.dp, vertical = 13.dp)
                        ) { Text(m.metin, style = govde(15.sp, Renk.YaziSicak)) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        KucukAmblem(30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).background(Renk.Kart)
                                .border(1.dp, Renk.KartCizgi, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).padding(16.dp)
                        ) {
                            val goster = if (m.metin.isBlank() && !m.bitti) "Düşünüyorum…" else m.metin + if (!m.bitti) " ▍" else ""
                            Text(goster, style = govde(15.sp, if (m.metin.isBlank()) Renk.Soluk else Renk.Yazi).copy(lineHeight = 24.sp))
                            if (m.bitti && m.metin.isNotBlank() && i == d.sohbet.lastIndex) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF101419))
                                        .border(1.dp, Renk.Cizgi, RoundedCornerShape(18.dp)).clickable { vm.konusKisa(m.metin) }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Ikon(Ik.HOPARLOR, Renk.Ikon, 14.dp, 2f)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Dinle", style = govde(12.sp, Renk.Ikon))
                                }
                            }
                        }
                    }
                }
            }
        }
        GirisCubugu(vm, "Bir şey sor…")
    }
}

// ------------------------------------------------------------------ Gelen arama

@Composable
fun GelenAramaEkrani(vm: Asistan, e: Ekran.GelenArama) {
    Column(
        Modifier.fillMaxSize().background(Renk.ZeminDerin).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GELEN ARAMA", style = etiket())
        Spacer(Modifier.height(70.dp))
        Box(Modifier.size(132.dp).clip(CircleShape).border(1.5.dp, Renk.Altin, CircleShape).background(Renk.AltinZemin), contentAlignment = Alignment.Center) {
            Text(e.kim.take(1).uppercase(Turkce.TR), style = baslik(52.sp, Renk.Altin).copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(28.dp))
        Text(e.kim, style = baslik(38.sp), textAlign = TextAlign.Center)
        if (e.numara.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(e.numara, style = govde(15.sp, Renk.Soluk))
        }
        Spacer(Modifier.height(30.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Renk.Kart)
                .border(1.dp, Renk.AltinIs, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            KucukAmblem(34.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("“${e.kim} arıyor. Açayım mı?”", style = baslik(21.sp))
                Spacer(Modifier.height(6.dp))
                Text("“Aç”, “reddet” ya da “meşgulüm de” diyebilirsin.", style = govde(13.sp, Renk.Soluk))
            }
        }
        Spacer(Modifier.weight(1f))
        SadeDugme("“Müsait değilim, sonra ararım” yaz", Modifier.fillMaxWidth(), 48.dp) { vm.aramaMesgul() }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                YuvarlakDugme(Ik.TELEFON, "Reddet", 72.dp, Renk.Kirmizi, Color.White, cerceve = false, ikonBoyut = 28.dp) { vm.aramaReddet() }
                Spacer(Modifier.height(8.dp))
                Text("Reddet", style = govde(13.sp, Renk.Soluk))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                YuvarlakDugme(Ik.TELEFON, "Aç", 72.dp, Renk.Turkuaz, Renk.ZeminDerin, cerceve = false, ikonBoyut = 28.dp) { vm.aramaAc() }
                Spacer(Modifier.height(8.dp))
                Text("Aç", style = govde(13.sp, Renk.Soluk))
            }
        }
    }
}
