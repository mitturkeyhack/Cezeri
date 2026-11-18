package com.erkam.cezeri.core

import java.util.Locale

/** Türkçe metin yardımcıları: küçük harfe çevirme, sadeleştirme, ek atma, benzerlik. */
object Turkce {
    val TR: Locale = Locale.forLanguageTag("tr-TR")

    fun lower(s: String): String = s.lowercase(TR)

    /** Karşılaştırma için sadeleştirir: küçük harf, Türkçe harfleri Latin karşılığına çevirir, noktalama atar. */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (c in lower(s)) {
            val m = when (c) {
                'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'i' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
                'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                else -> c
            }
            if (m.isLetterOrDigit() || m == ' ') sb.append(m) else sb.append(' ')
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** Kesme işaretlerini tekleştirir, baştaki "hey cezerî" gibi hitapları ve sondaki noktalamayı atar. */
    fun clean(s: String): String {
        var t = lower(s).replace('’', '\'').replace('`', '\'').trim()
        t = t.replace(Regex("^(hey\\s+)?cezer[iî]\\s*[,!]?\\s*"), "")
        t = t.trimEnd('.', '!', '?', ',', ' ')
        return t.trim()
    }

    private val accusative = listOf("yı", "yi", "yu", "yü", "nı", "ni", "nu", "nü", "ı", "i", "u", "ü")
    private val dative = listOf("ye", "ya", "ne", "na", "e", "a")
    private val possessive = listOf("ım", "im", "um", "üm", "m")

    /** "Ahmet'i" -> "Ahmet"; "annemi" -> annemi, annem, anne ... olası kökler. */
    fun nameVariants(word: String, kind: Ek): List<String> {
        val w = word.trim()
        val out = LinkedHashSet<String>()
        if (w.contains('\'')) {
            out.add(w.substringBefore('\''))
        } else {
            out.add(w)
            val list = if (kind == Ek.BELIRTME) accusative else dative
            for (suf in list) {
                if (w.length > suf.length + 1 && w.endsWith(suf)) out.add(w.dropLast(suf.length))
            }
        }
        // İyelik: "annem" -> "anne", "babam" -> "baba"
        for (base in out.toList()) {
            for (suf in possessive) {
                if (base.length > suf.length + 2 && base.endsWith(suf)) out.add(base.dropLast(suf.length))
            }
        }
        return out.toList()
    }

    enum class Ek { BELIRTME, YONELME }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun similarity(a: String, b: String): Double {
        val m = maxOf(a.length, b.length)
        if (m == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / m
    }

    private val sayilar = mapOf(
        "bir" to 1, "iki" to 2, "üç" to 3, "dört" to 4, "beş" to 5, "altı" to 6, "yedi" to 7,
        "sekiz" to 8, "dokuz" to 9, "on" to 10, "on bir" to 11, "onbir" to 11, "on iki" to 12, "oniki" to 12,
        "yirmi" to 20, "otuz" to 30, "kırk" to 40, "elli" to 50
    )

    /** "saat 5:40", "altı buçukta", "sabah 6'da", "akşam 7" -> (saat, dakika) */
    fun parseTime(text: String): Pair<Int, Int>? {
        val t = lower(text)
        Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{2})").find(t)?.let {
            val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) return adjust(t, h, m)
        }
        var hour: Int? = null
        Regex("(?:saat\\s*)?(\\d{1,2})(?:'?\\s*(?:de|da|te|ta|e|a|ye|ya))?\\b").find(t)?.let {
            hour = it.groupValues[1].toInt()
        }
        if (hour == null) {
            // Yazıyla söylenen saatler, uzun olanlar önce
            for ((word, n) in sayilar.entries.sortedByDescending { it.key.length }) {
                if (n <= 12 && Regex("(^|\\s)" + Regex.escape(word) + "(\\s|'|da|de|ta|te|ya|ye|a|e|$)").containsMatchIn(t)) {
                    hour = n; break
                }
            }
        }
        val h = hour ?: return null
        if (h !in 0..23) return null
        var m = 0
        if (t.contains("buçuk")) m = 30
        Regex("(\\d{1,2})\\s*geçe").find(t)?.let { m = it.groupValues[1].toInt().coerceIn(0, 59) }
        return adjust(t, h, m)
    }

    private fun adjust(t: String, h: Int, m: Int): Pair<Int, Int> {
        var hh = h
        if ((t.contains("akşam") || t.contains("öğleden sonra") || t.contains("gece")) && hh in 1..11) {
            if (!(t.contains("gece") && hh <= 4)) hh += 12
        }
        return hh to m
    }

    fun ordinal(text: String): Int? {
        val t = norm(text)
        return when {
            Regex("\\b(birinci|ilk|bir|1)\\b").containsMatchIn(t) -> 0
            Regex("\\b(ikinci|iki|2)\\b").containsMatchIn(t) -> 1
            Regex("\\b(ucuncu|uc|3)\\b").containsMatchIn(t) -> 2
            Regex("\\b(dorduncu|dort|4)\\b").containsMatchIn(t) -> 3
            Regex("\\b(sonuncu|son)\\b").containsMatchIn(t) -> -1
            else -> null
        }
    }

    /** İsme ünlü uyumuna göre ek getirir: belirtme "Ahmet'i", "Ayşe'yi"; yönelme "Ahmet'e", "Ayşe'ye". */
    fun ekle(isim: String, belirtme: Boolean): String {
        val ad = isim.trim()
        val l = lower(ad)
        val unluler = "aeıioöuüâîû"
        val sonUnlu = l.lastOrNull { it in unluler } ?: return ad
        val unluyleBiter = l.last() in unluler
        val kaynastirma = if (unluyleBiter) "y" else ""
        val ek = if (belirtme) {
            when (sonUnlu) { 'a', 'ı', 'â' -> "ı"; 'e', 'i', 'î' -> "i"; 'o', 'u', 'û' -> "u"; else -> "ü" }
        } else {
            when (sonUnlu) { 'a', 'ı', 'o', 'u', 'â', 'û' -> "a"; else -> "e" }
        }
        return "$ad'$kaynastirma$ek"
    }

    enum class Cevap { EVET, HAYIR, DEGISTIR, BILINMIYOR }

    fun answer(text: String): Cevap {
        val t = norm(text)
        val hayir = Regex("\\b(hayir|iptal|vazgec|vazgectim|dur|gonderme|arama|istemiyorum|bosver|yok|olmaz|yapma)\\b")
        val degistir = Regex("\\b(degistir|duzelt|degisiklik|yeniden yaz|baska)\\b")
        val evet = Regex("\\b(evet|gonder|ara|tamam|olur|onayliyorum|onay|aynen|yap|tabii|tabi|elbette|he|hi hi|ok|okey|kesinlikle|evt)\\b")
        return when {
            hayir.containsMatchIn(t) -> Cevap.HAYIR
            degistir.containsMatchIn(t) -> Cevap.DEGISTIR
            evet.containsMatchIn(t) -> Cevap.EVET
            else -> Cevap.BILINMIYOR
        }
    }
}
