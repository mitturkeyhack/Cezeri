package com.erkam.cezeri.core

enum class Kanal { SMS, WHATSAPP }

sealed interface Komut {
    data class Ara(val isimVaryantlari: List<String>, val soylenen: String, val numara: String? = null) : Komut
    data class Mesaj(val isimVaryantlari: List<String>, val soylenen: String, val metin: String?, val kanal: Kanal?, val numara: String? = null) : Komut
    data class Fener(val ac: Boolean) : Komut
    data class Alarm(val saat: Int?, val dakika: Int?) : Komut
    data object SaatKac : Komut
    data object BugunNe : Komut
    data object Pil : Komut
    data class Ses(val yukselt: Boolean) : Komut
    data object Sessiz : Komut
    data object Titresim : Komut
    data object Iptal : Komut
    data class Selam(val tur: String) : Komut
    data class Sohbet(val metin: String) : Komut
}

/** Hızlı yol: sık komutları büyük modele gitmeden anında yakalar. */
object KomutCozucu {

    private const val ADV = "(?:\\s+l[üu]tfen)?"
    private val araSonda = Regex(
        "^(.+?)\\s+(?:telefonla\\s+|hemen\\s+)?(?:ara|arar\\s*m[ıi]s[ıi]n|arayabilir\\s*m[ıi]s[ıi]n|aray[ıi]n|aramak\\s+istiyorum|ara\\s+bakal[ıi]m)$ADV$"
    )
    private val araBasta = Regex("^ara\\s+(.+)$")

    private val mesajKelime = "(?:bir\\s+)?(?:mesaj|sms|whatsapp(?:'?tan|\\s*mesaj[ıi])?|whatsapptan)"
    private val mesaj1 = Regex("^(\\S+(?:\\s+\\S+)?)\\s+$mesajKelime\\s+(?:at|g[öo]nder|yaz|yolla|ge[çc])(?:ar\\s*m[ıi]s[ıi]n|abilir\\s*m[ıi]s[ıi]n)?\\s*[:,]?\\s*(.*)$")
    private val mesaj2 = Regex("^(\\S+(?:\\s+\\S+)?)\\s+(.+?)\\s+(?:diye\\s+)?(?:yaz|mesaj\\s+at|mesaj\\s+g[öo]nder|s[öo]yle|ilet|yolla)(?:ar\\s*m[ıi]s[ıi]n|abilir\\s*m[ıi]s[ıi]n)?$ADV$")
    private val mesaj3 = Regex("^(\\S+(?:\\s+\\S+)?)\\s+(?:yaz|s[öo]yle|de\\s*ki|ilet)\\s*[:,]?\\s+(.+)$")
    private val sadeceMesaj = Regex("^(\\S+(?:\\s+\\S+)?)\\s+(?:$mesajKelime\\s+)?(?:yaz|mesaj\\s+at|mesaj\\s+g[öo]nder)$ADV$")
    private val yonelme = Regex(".*(?:'?(?:y?e|y?a|ne|na))$")

    fun coz(ham: String): Komut {
        val t = Turkce.clean(ham)
        if (t.isBlank()) return Komut.Sohbet(ham)

        // İptal
        if (Regex("^(iptal|vazge[çc]|bo[şs]ver|dur|tamam dur|kapat)( et)?$").matches(t)) return Komut.Iptal

        // Selam
        if (Regex("^(selam[uü]n aleyk[uü]m|selam[uü]naleyk[uü]m|esselam[uü] aleyk[uü]m)").containsMatchIn(t)) return Komut.Selam("selam")

        // Fener
        if (t.contains("fener") || t.contains("feneri") || t.contains("el feneri")) {
            if (Regex("kapat|s[öo]nd[üu]r").containsMatchIn(t)) return Komut.Fener(false)
            if (Regex("\\ba[çc]|yak").containsMatchIn(t)) return Komut.Fener(true)
        }

        // Alarm
        if (t.contains("alarm") && Regex("kur|ayarla|koy|a[çc]").containsMatchIn(t)) {
            val zaman = Turkce.parseTime(t)
            return Komut.Alarm(zaman?.first, zaman?.second)
        }

        // Saat / tarih / pil
        if (Regex("^saat ka[çc]|saat\\s+ka[çc]\\b|saat ne").containsMatchIn(t)) return Komut.SaatKac
        if (Regex("bug[üu]n (ay[ıi]n ka[çc][ıi]|ne|g[üu]nlerden ne|hangi g[üu]n)|tarih ne|bug[üu]n[üu]n tarihi").containsMatchIn(t)) return Komut.BugunNe
        if (Regex("(pil|şarj|sarj)").containsMatchIn(t) && Regex("ka[çc]|ne kadar|durum|y[üu]zde").containsMatchIn(t)) return Komut.Pil

        // Ses
        if (Regex("^(telefonu\\s+)?sessize al").containsMatchIn(t)) return Komut.Sessiz
        if (Regex("^(telefonu\\s+)?titre[şs]ime al").containsMatchIn(t)) return Komut.Titresim
        if (Regex("^sesi\\s+(a[çc]|y[üu]kselt|art[ıi]r|ykselt)").containsMatchIn(t)) return Komut.Ses(true)
        if (Regex("^sesi\\s+(k[ıi]s|azalt|d[üu][şs][üu]r)").containsMatchIn(t)) return Komut.Ses(false)

        val kanal = when {
            t.contains("whatsapp") -> Kanal.WHATSAPP
            Regex("\\bsms\\b|k[ıi]sa mesaj").containsMatchIn(t) -> Kanal.SMS
            else -> null
        }
        val tKanalsiz = t.replace(Regex("whatsapp'?tan|whatsapp\\s+ile|whatsapp\\s+[üu]zerinden|sms\\s+ile|smsle|sms\\s+olarak"), " ")
            .replace(Regex("\\s+"), " ").trim()

        // Numarayla: "0539 893 45 98'i ara", "539 893 45 98'e mesaj gönder: geliyorum"
        numaraKomutu(tKanalsiz, kanal)?.let { return it }

        // Mesaj
        mesaj1.find(tKanalsiz)?.let { m ->
            val alici = m.groupValues[1]
            if (yonelme.matches(alici)) {
                val body = govdeTemizle(m.groupValues[2])
                return Komut.Mesaj(isimVaryant(alici, Turkce.Ek.YONELME), alici, body.ifBlank { null }, kanal)
            }
        }
        sadeceMesaj.find(tKanalsiz)?.let { m ->
            val alici = m.groupValues[1]
            if (yonelme.matches(alici)) return Komut.Mesaj(isimVaryant(alici, Turkce.Ek.YONELME), alici, null, kanal)
        }
        for (r in listOf(mesaj2, mesaj3)) {
            r.find(tKanalsiz)?.let { m ->
                val g1 = m.groupValues[1]
                // İki kelimelik alıcıda ikinci kelime yönelme ekli olmalı; değilse tek kelimeye düş
                val parts = g1.split(' ')
                val (alici, ekMetin) = if (parts.size == 2 && !yonelme.matches(g1) ) {
                    parts[0] to parts[1]
                } else if (parts.size == 2 && yonelme.matches(parts[1]) ) {
                    g1 to ""
                } else if (parts.size == 2) {
                    parts[0] to parts[1]
                } else g1 to ""
                if (yonelme.matches(alici)) {
                    val body = govdeTemizle((ekMetin + " " + m.groupValues[2]).trim())
                    if (body.isNotBlank()) return Komut.Mesaj(isimVaryant(alici, Turkce.Ek.YONELME), alici, body, kanal)
                }
            }
        }

        // Arama
        araSonda.find(t)?.let { m ->
            val isim = m.groupValues[1].removeSuffix(" telefonla").trim()
            if (isim.split(' ').size <= 3) return Komut.Ara(isimVaryant(isim, Turkce.Ek.BELIRTME), isim)
        }
        araBasta.find(t)?.let { m ->
            val isim = m.groupValues[1].trim()
            if (isim.split(' ').size <= 3) return Komut.Ara(isimVaryant(isim, Turkce.Ek.BELIRTME), isim)
        }

        return Komut.Sohbet(ham.trim())
    }

    private val numaraBasta = Regex("^(\\+?\\d[\\d\\s-]{5,}\\d)\\s*(?:'?\\s*(?:y?[ıiuüea]|n[ıiuü]|ya|ye))?\\s+(.+)$")
    private val numaraSonda = Regex("^ara\\s+(\\+?\\d[\\d\\s-]{5,}\\d)$")

    private fun numaraKomutu(t: String, kanal: Kanal?): Komut? {
        numaraSonda.find(t)?.let { m ->
            val n = m.groupValues[1].filter { it.isDigit() || it == '+' }
            return Komut.Ara(emptyList(), n, numara = n)
        }
        val m = numaraBasta.find(t) ?: return null
        val n = m.groupValues[1].filter { it.isDigit() || it == '+' }
        if (n.count { it.isDigit() } < 7) return null
        val geri = m.groupValues[2].trim()
        if (Regex("^(?:telefonla\\s+|hemen\\s+)?(?:ara|arar\\s*m[ıi]s[ıi]n|arayabilir\\s*m[ıi]s[ıi]n|aray[ıi]n)(?:\\s+l[üu]tfen)?$").matches(geri)) {
            return Komut.Ara(emptyList(), n, numara = n)
        }
        Regex("^(?:bir\\s+)?(?:mesaj|sms|whatsapp(?:\\s*mesaj[ıi])?)?\\s*(?:at|g[öo]nder|yaz|yolla)(?:ar\\s*m[ıi]s[ıi]n|abilir\\s*m[ıi]s[ıi]n)?\\s*[:,]?\\s*(.*)$").find(geri)?.let { mm ->
            val body = govdeTemizle(mm.groupValues[1])
            return Komut.Mesaj(emptyList(), n, body.ifBlank { null }, kanal, numara = n)
        }
        Regex("^(.+?)\\s+(?:diye\\s+)?(?:yaz|mesaj\\s+at|mesaj\\s+g[öo]nder|ilet|yolla)$").find(geri)?.let { mm ->
            return Komut.Mesaj(emptyList(), n, govdeTemizle(mm.groupValues[1]), kanal, numara = n)
        }
        return null
    }

    /** "ahmet yılmaz'ı" -> ["ahmet yılmaz", "ahmet yılmaz'ı", ...]; son kelimenin eki atılır. */
    fun isimVaryant(isim: String, ek: Turkce.Ek): List<String> {
        val words = isim.trim().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val head = words.dropLast(1).joinToString(" ")
        val out = LinkedHashSet<String>()
        for (v in Turkce.nameVariants(words.last(), ek)) {
            out.add((if (head.isBlank()) v else "$head $v").trim())
        }
        // Tek kelimeyle de dene ("annemi" -> "anne")
        if (words.size > 1) for (v in Turkce.nameVariants(words.last(), ek)) out.add(v)
        return out.toList()
    }

    private fun govdeTemizle(s: String): String {
        var b = s.trim()
        b = b.removePrefix("şunu ").removePrefix("bunu ").trim()
        b = b.replace(Regex("\\s+diye$"), "").trim()
        // İlk harfi büyüt
        return b.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Turkce.TR) else it.toString() }
    }
}
