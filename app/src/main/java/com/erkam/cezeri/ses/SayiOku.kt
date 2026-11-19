package com.erkam.cezeri.ses

/** Sesli okumadan önce metni düzeltir: rakamları, saatleri, yüzdeleri Türkçe kelimeye çevirir. */
object SayiOku {
    private val BIR = arrayOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val ON = arrayOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")
    private val BASAMAK = arrayOf("", "bin", "milyon", "milyar", "trilyon")

    private fun ucHane(n: Int): String {
        val y = n / 100; val o = (n / 10) % 10; val b = n % 10
        val p = ArrayList<String>()
        if (y > 0) p += if (y == 1) "yüz" else "${BIR[y]} yüz"
        if (o > 0) p += ON[o]
        if (b > 0) p += BIR[b]
        return p.joinToString(" ")
    }

    fun yaz(sayi: Long): String {
        if (sayi == 0L) return "sıfır"
        if (sayi < 0) return "eksi " + yaz(-sayi)
        val gruplar = ArrayList<Int>()
        var n = sayi
        while (n > 0) { gruplar += (n % 1000).toInt(); n /= 1000 }
        if (gruplar.size > BASAMAK.size) return sayi.toString().map { if (it.isDigit()) yaz((it - '0').toLong()) else it.toString() }.joinToString(" ")
        val p = ArrayList<String>()
        for (i in gruplar.indices.reversed()) {
            val g = gruplar[i]
            if (g == 0) continue
            if (i == 1 && g == 1) p += "bin"
            else { p += ucHane(g); if (BASAMAK[i].isNotEmpty()) p += BASAMAK[i] }
        }
        return p.joinToString(" ")
    }

    private fun rakamRakam(s: String) = s.map { yaz((it - '0').toLong()) }.joinToString(" ")

    private fun sayiMetni(s: String): String =
        if (s.length > 1 && s.startsWith("0")) rakamRakam(s)
        else if (s.length > 15) rakamRakam(s)
        else yaz(s.toLong())

    fun hazirla(metin: String): String {
        var t = metin
        // Markdown ve gereksiz işaretler
        t = t.replace(Regex("[*_#`>|]+"), " ")
        t = t.replace(Regex("\\bvs\\.?\\b"), "ile")
        // Saat: 14:30 / 09.05
        t = Regex("(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d)(?![.,]?\\d)['’]?").replace(t) { m ->
            val s = m.groupValues[1].toInt(); val d = m.groupValues[2].toInt()
            yaz(s.toLong()) + if (d == 0) "" else " " + yaz(d.toLong())
        }
        // Yüzde: %25 ya da 25%
        t = Regex("%\\s?(\\d+(?:,\\d+)?)").replace(t) { "yüzde " + it.groupValues[1] }
        t = Regex("(\\d+(?:,\\d+)?)\\s?%").replace(t) { "yüzde " + it.groupValues[1] }
        // Binlik ayıracı: 1.250.000
        t = Regex("\\b\\d{1,3}(?:\\.\\d{3})+\\b(?!,\\d)").replace(t) { it.value.replace(".", "") }
        // Ondalık: 3,5
        t = Regex("(\\d+),(\\d+)").replace(t) { m -> sayiMetni(m.groupValues[1]) + " virgül " + sayiMetni(m.groupValues[2]) }
        // Basit işlemler
        t = t.replace(Regex("(?<=\\d)\\s*[x×*]\\s*(?=\\d)"), " çarpı ")
            .replace(Regex("(?<=\\d)\\s*÷\\s*(?=\\d)"), " bölü ")
            .replace(Regex("(?<=\\d)\\s*\\+\\s*(?=\\d)"), " artı ")
            .replace(Regex("(?<=\\d)\\s*=\\s*(?=\\d)"), " eşittir ")
        // Kalan tam sayılar ('den, 'te gibi eklerin kesme işaretini de kaldır)
        t = Regex("(\\d+)['’]?").replace(t) { m -> sayiMetni(m.groupValues[1]) }
        t = t.replace("&", " ve ")
        return t.replace(Regex("\\s+"), " ").trim()
    }
}
