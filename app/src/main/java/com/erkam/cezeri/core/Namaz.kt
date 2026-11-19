package com.erkam.cezeri.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * İnternetsiz namaz vakti hesabı (Diyanet yöntemi): imsak 18°, yatsı 17°, ikindi gölge = 1 boy,
 * temkinler: güneş −7, öğle +5, ikindi +4, akşam +7 dakika. Android'e bağlı değildir.
 */
object Namaz {

    enum class Vakit(val ad: String, val anahtar: String) {
        IMSAK("İmsak", "imsak"), GUNES("Güneş", "gunes"), OGLE("Öğle", "ogle"),
        IKINDI("İkindi", "ikindi"), AKSAM("Akşam", "aksam"), YATSI("Yatsı", "yatsi");

        companion object { fun bul(a: String) = entries.firstOrNull { it.anahtar == a } }
    }

    /** Gün içindeki vakitler, o günün yerel gece yarısından itibaren dakika olarak. */
    data class Gun(val dakikalar: Map<Vakit, Int>) {
        fun saat(v: Vakit): String = dakikalar[v]?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--"
    }

    private fun d2r(d: Double) = d * Math.PI / 180.0
    private fun r2d(r: Double) = r * 180.0 / Math.PI
    private fun fix(a: Double, b: Double): Double { val x = a - b * floor(a / b); return if (x < 0) x + b else x }

    private fun julian(y: Int, m: Int, d: Int): Double {
        var yy = y; var mm = m
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = floor(yy / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    /** Güneşin sapma açısı (derece) ve zaman denklemi (saat) */
    private fun gunesKonumu(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(d2r(g)) + 0.020 * sin(d2r(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = fix(r2d(atan2(cos(d2r(e)) * sin(d2r(l)), cos(d2r(l)))) / 15.0, 24.0)
        val eqt = q / 15.0 - ra
        val decl = r2d(asin(sin(d2r(e)) * sin(d2r(l))))
        return decl to (eqt - 24.0 * Math.round(eqt / 24.0))
    }

    fun hesapla(yil: Int, ay: Int, gun: Int, enlem: Double, boylam: Double, tzSaat: Double): Gun {
        val jdBase = julian(yil, ay, gun) - boylam / (15.0 * 24.0)

        fun ogleSaat(t: Double): Double {
            val (_, eqt) = gunesKonumu(jdBase + t / 24.0)
            return fix(12.0 - eqt, 24.0)
        }

        fun aciZamani(aci: Double, t: Double, yonSabah: Boolean): Double {
            val (decl, _) = gunesKonumu(jdBase + t / 24.0)
            val noon = ogleSaat(t)
            val x = (-sin(d2r(aci)) - sin(d2r(decl)) * sin(d2r(enlem))) / (cos(d2r(decl)) * cos(d2r(enlem)))
            val h = r2d(acos(x.coerceIn(-1.0, 1.0))) / 15.0
            return noon + if (yonSabah) -h else h
        }

        fun ikindiZamani(t: Double): Double {
            val (decl, _) = gunesKonumu(jdBase + t / 24.0)
            val aci = -r2d(atan(1.0 / (1.0 + tan(d2r(abs(enlem - decl))))))
            return aciZamani(aci, t, yonSabah = false)
        }

        // İki geçişli hesap (ilk tahminlerle güneş konumu inceltilir)
        var imsak = 5.0; var gunes = 6.0; var ogle = 12.0; var ikindi = 13.0; var aksam = 18.0; var yatsi = 18.0
        repeat(2) {
            imsak = aciZamani(18.0, imsak, true)
            gunes = aciZamani(0.833, gunes, true)
            ogle = ogleSaat(ogle)
            ikindi = ikindiZamani(ikindi)
            aksam = aciZamani(0.833, aksam, false)
            yatsi = aciZamani(17.0, yatsi, false)
        }
        val fark = tzSaat - boylam / 15.0
        fun dk(h: Double, temkin: Int): Int = Math.round((h + fark) * 60.0).toInt() + temkin
        return Gun(
            mapOf(
                Vakit.IMSAK to dk(imsak, 0),
                Vakit.GUNES to dk(gunes, -7),
                Vakit.OGLE to dk(ogle, 5),
                Vakit.IKINDI to dk(ikindi, 4),
                Vakit.AKSAM to dk(aksam, 7),
                Vakit.YATSI to dk(yatsi, 0),
            )
        )
    }

    fun bugun(enlem: Double, boylam: Double, cal: Calendar = Calendar.getInstance()): Gun {
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        return hesapla(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), enlem, boylam, tz)
    }

    /** Şu andan sonraki ilk vakit ve ona kalan dakika (yatsıdan sonra yarının imsakı). */
    fun siradaki(enlem: Double, boylam: Double, simdi: Calendar = Calendar.getInstance()): Triple<Vakit, Int, Int> {
        val g = bugun(enlem, boylam, simdi)
        val dk = simdi.get(Calendar.HOUR_OF_DAY) * 60 + simdi.get(Calendar.MINUTE)
        for (v in Vakit.entries) {
            val t = g.dakikalar[v] ?: continue
            if (t > dk) return Triple(v, t, t - dk)
        }
        val yarin = (simdi.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val t = bugun(enlem, boylam, yarin).dakikalar[Vakit.IMSAK] ?: 0
        return Triple(Vakit.IMSAK, t, t + 24 * 60 - dk)
    }

    /** Kâbe'ye kuzeyden saat yönünde açı (derece). */
    fun kible(enlem: Double, boylam: Double): Double {
        val kEnlem = d2r(21.4225); val kBoylam = d2r(39.8262)
        val f = d2r(enlem); val dl = kBoylam - d2r(boylam)
        val a = atan2(sin(dl), cos(f) * tan(kEnlem) - sin(f) * cos(dl))
        return fix(r2d(a), 360.0)
    }

    fun yonAdi(derece: Double): String {
        val adlar = listOf("kuzey", "kuzeydoğu", "doğu", "güneydoğu", "güney", "güneybatı", "batı", "kuzeybatı")
        return adlar[(fix(derece + 22.5, 360.0) / 45.0).toInt() % 8]
    }

    fun sureMetni(dakika: Int): String {
        val s = dakika / 60; val d = dakika % 60
        return when {
            s == 0 -> "$d dakika"
            d == 0 -> "$s saat"
            else -> "$s saat $d dakika"
        }
    }
}
