package com.erkam.cezeri.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkam.cezeri.Asistan
import com.erkam.cezeri.Durum
import com.erkam.cezeri.Ekran
import com.erkam.cezeri.core.Namaz
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Pusulanın gösterdiği yön (kuzeyden derece), yumuşatılmış. */
@Composable
private fun pusulaYonu(): Float {
    val ctx = LocalContext.current
    var yon by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val r = FloatArray(9); val o = FloatArray(3)
        val dinleyici = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                val d = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
                var fark = d - yon
                if (fark > 180) fark -= 360
                if (fark < -180) fark += 360
                yon = ((yon + fark * 0.15f) + 360) % 360
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(dinleyici, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(dinleyici) }
    }
    return yon
}

@Composable
fun KibleEkrani(vm: Asistan, e: Ekran.Kible) {
    BackHandler { vm.anaEkrana() }
    val yon = pusulaYonu()
    // Okun ekrandaki açısı: kıble açısı − telefonun baktığı yön
    val ok = ((e.derece - yon + 360) % 360).toFloat()
    val hizali = ok < 6 || ok > 354
    Column(
        Modifier.fillMaxSize().background(Renk.Zemin).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            YuvarlakDugme(Ik.GERI, "Geri") { vm.anaEkrana() }
            Spacer(Modifier.width(14.dp))
            Text("Kıble", style = baslik(30.sp))
        }
        Spacer(Modifier.height(30.dp))
        Text(
            if (hizali) "Kâbe'ye bakıyorsun" else "Oku takip ederek dön",
            style = govde(16.sp, if (hizali) Renk.Altin else Renk.SolukAcik, FontWeight.Medium)
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val rr = size.minDimension / 2 - 8.dp.toPx()
                drawCircle(Renk.Kart, rr, c)
                drawCircle(if (hizali) Renk.Altin else Renk.KartCizgi, rr, c, style = Stroke(2.dp.toPx()))
                // Pusula çizgileri (kuzey telefonun yönüne göre döner)
                rotate(-yon, c) {
                    for (i in 0 until 72) {
                        val a = Math.toRadians(i * 5.0)
                        val uzun = i % 18 == 0
                        val r1 = rr - (if (uzun) 18.dp.toPx() else 8.dp.toPx())
                        drawLine(
                            if (i == 0) Renk.Kirmizi else Renk.Soluk.copy(alpha = if (uzun) 0.9f else 0.4f),
                            Offset(c.x + (r1 * sin(a)).toFloat(), c.y - (r1 * cos(a)).toFloat()),
                            Offset(c.x + (rr * sin(a)).toFloat(), c.y - (rr * cos(a)).toFloat()),
                            strokeWidth = if (uzun) 3.dp.toPx() else 1.5.dp.toPx(),
                        )
                    }
                }
                // Kıble oku
                rotate(ok, c) {
                    val p = Path().apply {
                        moveTo(c.x, c.y - rr + 34.dp.toPx())
                        lineTo(c.x - 16.dp.toPx(), c.y + 10.dp.toPx())
                        lineTo(c.x, c.y - 4.dp.toPx())
                        lineTo(c.x + 16.dp.toPx(), c.y + 10.dp.toPx())
                        close()
                    }
                    drawPath(p, if (hizali) Renk.Altin else Renk.Turkuaz)
                }
                drawCircle(Renk.Zemin, 7.dp.toPx(), c)
                drawCircle(Renk.Altin, 4.dp.toPx(), c)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("${Math.round(e.derece)}°", style = baslik(44.sp, Renk.Altin))
        Text("Kuzeyden, ${e.yon} yönünde", style = govde(14.sp, Renk.Soluk))
        Spacer(Modifier.weight(1f))
        Text(
            "Telefonu düz tut, metal eşyalardan uzak dur. Pusula şaşarsa telefonu havada 8 çizerek döndür.",
            style = govde(12.sp, Renk.Soluk), textAlign = TextAlign.Center,
        )
    }
}

/** Ana ekranda: sıradaki vakit ve kalan süre; dokununca bugünün tablosu açılır. */
@Composable
fun VakitKarti(vm: Asistan, d: Durum) {
    if (d.konum == null) return
    var tik by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tik++ } }
    var acik by remember { mutableIntStateOf(0) }
    val s = remember(tik, d.konum) { vm.siradakiVakit() } ?: return
    val g = remember(tik, d.konum) { vm.bugunkuVakitler() }
    val (v, saat, kalan) = s
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Renk.Kart)
            .border(1.dp, Renk.KartCizgi, RoundedCornerShape(18.dp))
            .clickable { acik = 1 - acik }.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.ad.uppercase(com.erkam.cezeri.core.Turkce.TR), style = etiket(Renk.TurkuazAcik))
            Spacer(Modifier.width(10.dp))
            Text("%02d:%02d".format((saat / 60) % 24, saat % 60), style = govde(17.sp, Renk.Yazi, FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            Text("${Namaz.sureMetni(kalan)} kaldı", style = govde(13.sp, if (kalan <= 15) Renk.Altin else Renk.Soluk))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Renk.AltinZemin).clickable { vm.kibleAc() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("Kıble", style = govde(12.sp, Renk.Altin, FontWeight.Medium))
            }
        }
        if (acik == 1 && g != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Namaz.Vakit.entries.forEach { x ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(x.ad, style = govde(11.sp, if (x == v) Renk.Altin else Renk.Soluk))
                        Text(g.saat(x), style = govde(13.sp, if (x == v) Renk.Altin else Renk.SolukAcik, FontWeight.Medium))
                    }
                }
            }
        }
    }
}
