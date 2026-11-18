package com.erkam.cezeri.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ Yazı stilleri

fun baslik(boyut: TextUnit = 34.sp, renk: Color = Renk.Yazi) =
    TextStyle(fontFamily = Yazi.serif, fontWeight = FontWeight.SemiBold, fontSize = boyut, color = renk, lineHeight = boyut * 1.18f)

fun govde(boyut: TextUnit = 15.sp, renk: Color = Renk.Yazi, agirlik: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Yazi.sans, fontWeight = agirlik, fontSize = boyut, color = renk, lineHeight = boyut * 1.45f)

fun etiket(renk: Color = Renk.Soluk) =
    TextStyle(fontFamily = Yazi.sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = renk, letterSpacing = 1.6.sp)

// ------------------------------------------------------------------ Amblem

private fun yildiz(r: Float, cx: Float = 100f, cy: Float = 100f): Path {
    val ic = r * (cos(Math.toRadians(45.0)) / cos(Math.toRadians(22.5))).toFloat()
    val p = Path()
    for (i in 0 until 16) {
        val a = Math.toRadians(-90.0 + i * 22.5)
        val rr = if (i % 2 == 0) r else ic
        val x = cx + rr * cos(a).toFloat()
        val y = cy + rr * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private val pirinc = Brush.linearGradient(listOf(Renk.AltinParlak, Renk.Altin, Renk.AltinKoyu), Offset(0f, 0f), Offset(200f, 200f))

/** Cezerî amblemi: saat kadranı + Selçuklu yıldızı + turkuaz taş. dön=true iken kadran döner, nabız=true iken taş atar. */
@Composable
fun Amblem(boyut: Dp, don: Boolean = false, nabiz: Boolean = false, aciklama: String = "Cezerî amblemi") {
    val t = rememberInfiniteTransition(label = "amblem")
    val aci by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (nabiz) 14000 else 40000, easing = LinearEasing)), label = "aci")
    val atim by t.animateFloat(1f, 1.14f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "atim")
    val buyuk = remember { yildiz(58f) }
    val kucuk = remember { yildiz(30f) }
    Canvas(Modifier.size(boyut).semantics { contentDescription = aciklama }) {
        val s = size.minDimension / 200f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(pirinc, radius = 95f, center = Offset(100f, 100f), style = Stroke(1.2f))
            rotate(if (don) aci else 0f, Offset(100f, 100f)) { kadran() }
            drawCircle(Renk.Amblem, radius = 74f, center = Offset(100f, 100f))
            drawCircle(Renk.Altin.copy(alpha = 0.35f), radius = 74f, center = Offset(100f, 100f), style = Stroke(1f))
            drawPath(buyuk, Renk.Yildiz)
            drawPath(buyuk, pirinc, style = Stroke(2f, join = StrokeJoin.Miter))
            drawPath(kucuk, Renk.Altin.copy(alpha = 0.55f), style = Stroke(1f))
            val r = 17f * (if (nabiz) atim else 1f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF9BE3DB), Renk.Turkuaz, Color(0xFF1D5F5B)), Offset(93f, 92f), r * 1.4f),
                radius = r, center = Offset(100f, 100f)
            )
            drawCircle(Color(0xFFE6C987).copy(alpha = 0.6f), radius = r, center = Offset(100f, 100f), style = Stroke(1f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = 4f * (r / 17f), center = Offset(95f, 94f))
        }
    }
}

private fun DrawScope.kadran() {
    for (d in 0 until 360 step 6) {
        val a = Math.toRadians(d - 90.0)
        val ana = d % 30 == 0
        val r1 = if (ana) 79f else 84f
        val r2 = 89f
        val p1 = Offset(100f + r1 * cos(a).toFloat(), 100f + r1 * sin(a).toFloat())
        val p2 = Offset(100f + r2 * cos(a).toFloat(), 100f + r2 * sin(a).toFloat())
        if (ana) drawLine(pirinc, p1, p2, strokeWidth = 2.4f, cap = StrokeCap.Round)
        else drawLine(Renk.Altin.copy(alpha = 0.45f), p1, p2, strokeWidth = 1f, cap = StrokeCap.Round)
    }
}

/** Küçük simge (başlıklarda, sohbet balonunda): halka + yıldız + taş */
@Composable
fun KucukAmblem(boyut: Dp) {
    val y = remember { yildiz(58f) }
    Canvas(Modifier.size(boyut)) {
        val s = size.minDimension / 200f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(Renk.Altin, radius = 91f, center = Offset(100f, 100f), style = Stroke(8f))
            drawPath(y, Renk.Yildiz)
            drawPath(y, Renk.Altin, style = Stroke(10f, join = StrokeJoin.Miter))
            drawCircle(Renk.Turkuaz, radius = 23f, center = Offset(100f, 100f))
        }
    }
}

// ------------------------------------------------------------------ İkonlar (24x24 çizgi)

object Ik {
    const val MIKROFON = "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1 -6 0z M5 11a7 7 0 0 0 14 0 M12 18v3"
    const val TELEFON = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1 -2 2A16 16 0 0 1 3 6a2 2 0 0 1 2 -2"
    const val MESAJ = "M4 5h16v11H9l-5 4z"
    const val AYAR = "M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0 -6z M12 2v3 M12 19v3 M2 12h3 M19 12h3 M4.9 4.9l2.1 2.1 M17 17l2.1 2.1 M4.9 19.1L7 17 M17 7l2.1 -2.1"
    const val GERI = "M15 5l-7 7 7 7"
    const val KAPAT = "M6 6l12 12 M18 6L6 18"
    const val KLAVYE = "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2z M7 10h0.01 M11 10h0.01 M15 10h0.01 M7 14h10"
    const val HOPARLOR = "M4 10v4h4l5 4V6L8 10z M16 9a4 4 0 0 1 0 6 M19 6a8 8 0 0 1 0 12"
    const val GONDER = "M4 12l16 -8 -6 16 -3 -7z"
    const val TIK = "M5 12l5 5L20 7"
    const val ALARM = "M12 6a7 7 0 1 1 0 14a7 7 0 1 1 0 -14z M12 10v3l2 2 M5 4l3 2 M19 4l-3 2"
    const val KISI = "M12 4a4 4 0 1 1 0 8a4 4 0 1 1 0 -8z M4 21a8 8 0 0 1 16 0"
    const val ZIL = "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z M10 21h4"
    const val PIL = "M5 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V9a2 2 0 0 1 2 -2z M21 10v4 M10 9l-2 3h4l-2 3"
    const val BILGI = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0 -18z M12 8h0.01 M11 12h1v5h1"
    const val YENI = "M12 5v14 M5 12h14"
}

@Composable
fun Ikon(d: String, renk: Color, boyut: Dp = 20.dp, kalinlik: Float = 1.8f) {
    val yol = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(boyut)) {
        val s = size.minDimension / 24f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawPath(yol, renk, style = Stroke(kalinlik, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ------------------------------------------------------------------ Düğmeler

@Composable
fun AltinDugme(yazi: String, modifier: Modifier = Modifier, ikon: String? = null, tikla: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Renk.Altin)
            .clickable(role = Role.Button, onClick = tikla),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ikon != null) { Ikon(ikon, Renk.Zemin, 18.dp, 2f); Box(Modifier.width(10.dp)) }
        Text(yazi, style = govde(16.sp, Renk.Zemin, FontWeight.SemiBold))
    }
}

@Composable
fun SadeDugme(yazi: String, modifier: Modifier = Modifier, yukseklik: Dp = 52.dp, tikla: () -> Unit) {
    Box(
        modifier
            .height(yukseklik)
            .clip(RoundedCornerShape(yukseklik / 2))
            .background(Renk.Kart)
            .border(1.dp, Renk.Cizgi, RoundedCornerShape(yukseklik / 2))
            .clickable(role = Role.Button, onClick = tikla),
        contentAlignment = Alignment.Center,
    ) { Text(yazi, style = govde(15.sp, Renk.Yazi, FontWeight.Medium)) }
}

@Composable
fun YuvarlakDugme(ikon: String, aciklama: String, boyut: Dp = 44.dp, zemin: Color = Renk.Kart, renk: Color = Renk.Ikon, cerceve: Boolean = true, ikonBoyut: Dp = 18.dp, tikla: () -> Unit) {
    Box(
        Modifier
            .size(boyut)
            .clip(CircleShape)
            .background(zemin)
            .then(if (cerceve) Modifier.border(1.dp, Renk.Cizgi, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = tikla)
            .semantics { contentDescription = aciklama },
        contentAlignment = Alignment.Center,
    ) { Ikon(ikon, renk, ikonBoyut, 2f) }
}

@Composable
fun Kart(modifier: Modifier = Modifier, zemin: Color = Renk.Kart, cizgi: Color = Renk.KartCizgi, kose: Dp = 18.dp, icerik: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(kose))
            .background(zemin)
            .border(1.dp, cizgi, RoundedCornerShape(kose))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = icerik,
    )
}

/** Konuşurken / dinlerken yükselip alçalan çubuklar */
@Composable
fun Dalga(seviye: Float, renkler: List<Color> = listOf(Renk.Turkuaz, Renk.Altin), yukseklik: Dp = 48.dp) {
    val t = rememberInfiniteTransition(label = "dalga")
    val faz by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "faz")
    Canvas(Modifier.width(110.dp).height(yukseklik)) {
        val n = 10
        val bosluk = size.width / n
        for (i in 0 until n) {
            val temel = 0.35f + 0.65f * ((sin(faz + i * 0.8f) + 1f) / 2f)
            val h = size.height * (0.25f + 0.75f * temel * (0.45f + 0.55f * seviye))
            val x = bosluk * i + bosluk / 2
            drawLine(
                if (i % 3 == 1) renkler[1] else renkler[0],
                Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

/** İlerleme halkası (indirme) */
@Composable
fun Halka(oran: Float, boyut: Dp, icerik: @Composable () -> Unit = {}) {
    Box(Modifier.size(boyut), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(boyut)) {
            val w = 8.dp.toPx()
            drawCircle(Renk.KartCizgi, radius = size.minDimension / 2 - w, style = Stroke(w))
            drawArc(
                Renk.Altin, -90f, 360f * oran.coerceIn(0f, 1f), false,
                topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                style = Stroke(w, cap = StrokeCap.Round)
            )
        }
        icerik()
    }
}
