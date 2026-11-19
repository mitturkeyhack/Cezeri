package com.erkam.cezeri.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Küçük ama kesin hesap makinesi: dil modeli aritmetikte hata yaptığı için hesaplar burada yapılır. */
object Hesap {

    /** "3³ kaç", "5 çarpı 7", "100'ün yüzde 15'i", "144'ün karekökü" -> ifade ya da null */
    fun ifadeyeCevir(ham: String): String? {
        var t = Turkce.lower(ham).replace('’', '\'').trim()
        if (!t.any { it.isDigit() }) return null
        t = t.replace(Regex("\\b(kaç eder|kaç yapar|kaçtır|kaç|nedir|ne eder|ne yapar|eder|yapar|hesapla|sonucu ne|sonuç)\\b"), " ")
        t = t.replace("?", " ").replace("=", " ")
        // Yüzde: "200'ün yüzde 15'i" / "%15'i"
        Regex("(\\d+(?:[.,]\\d+)?)\\s*'?\\s*(?:[ıiuü]n|n[ıiuü]n)?\\s+(?:y[üu]zde\\s*|%)(\\d+(?:[.,]\\d+)?)\\s*'?\\s*(?:[ıiuü]|s[ıiuü])?").find(t)?.let {
            return "(${it.groupValues[1].replace(',', '.')})*(${it.groupValues[2].replace(',', '.')})/100"
        }
        Regex("(\\d+(?:[.,]\\d+)?)\\s*'?\\s*(?:[ıiuü]n|n[ıiuü]n)?\\s+kare\\s*k[öo]k[üu]").find(t)?.let {
            return "sqrt(${it.groupValues[1].replace(',', '.')})"
        }
        Regex("kare\\s*k[öo]k\\s*(\\d+(?:[.,]\\d+)?)").find(t)?.let { return "sqrt(${it.groupValues[1].replace(',', '.')})" }
        t = t.replace("²", "^2").replace("³", "^3").replace("⁴", "^4")
            .replace(Regex("'?\\s*[ıiuü]n\\s+karesi|\\s+kare(si)?\\b"), "^2")
            .replace(Regex("'?\\s*[ıiuü]n\\s+k[üu]p[üu]|\\s+k[üu]p(ü)?\\b"), "^3")
            .replace(Regex("\\b(artı|arti|ve)\\b"), "+")
            .replace(Regex("\\b(eksi|çıkar|cikar)\\b"), "-")
            .replace(Regex("\\b(çarpı|carpi|kere|çarp|x)\\b"), "*")
            .replace("×", "*").replace("÷", "/")
            .replace(Regex("\\b(bölü|bolu|böl)\\b"), "/")
            .replace(Regex("\\b(üssü|ussu|üzeri|uzeri)\\b"), "^")
            .replace(Regex("(\\d),(\\d)"), "$1.$2")
        t = t.replace(Regex("\\s+"), "")
        if (t.isEmpty()) return null
        if (!Regex("^[0-9.+\\-*/^()]+$").matches(t)) return null
        if (!Regex("[+\\-*/^]").containsMatchIn(t.drop(1))) return null
        return t
    }

    fun hesapla(ifade: String): Double? = try {
        val p = Cozucu(ifade.replace(" ", ""))
        val v = p.ifade()
        if (p.i != p.s.length || v.isNaN() || v.isInfinite()) null else v
    } catch (_: Exception) { null }

    fun bicimle(v: Double): String {
        if (abs(v - v.roundToLong()) < 1e-9 && abs(v) < 1e15) return v.roundToLong().toString()
        val s = "%.4f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
        return s.replace('.', ',')
    }

    private class Cozucu(val s: String) {
        var i = 0
        fun ifade(): Double {
            var v = terim()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val o = s[i++]; val r = terim(); v = if (o == '+') v + r else v - r
            }
            return v
        }
        fun terim(): Double {
            var v = us()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val o = s[i++]; val r = us(); v = if (o == '*') v * r else v / r
            }
            return v
        }
        fun us(): Double {
            val b = tekli()
            if (i < s.length && s[i] == '^') { i++; return b.pow(us()) }
            return b
        }
        fun tekli(): Double {
            if (i < s.length && s[i] == '-') { i++; return -tekli() }
            if (i < s.length && s[i] == '+') { i++; return tekli() }
            return oge()
        }
        fun oge(): Double {
            if (s.startsWith("sqrt(", i)) { i += 5; val v = ifade(); bekle(')'); return sqrt(v) }
            if (i < s.length && s[i] == '(') { i++; val v = ifade(); bekle(')'); return v }
            val bas = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (bas == i) throw IllegalArgumentException("sayı bekleniyordu")
            return s.substring(bas, i).toDouble()
        }
        fun bekle(c: Char) { if (i < s.length && s[i] == c) i++ else throw IllegalArgumentException("$c") }
    }
}
