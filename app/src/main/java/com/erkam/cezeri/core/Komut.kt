package com.erkam.cezeri.core

enum class Kanal { SMS, WHATSAPP }

sealed interface Komut {
    data class Ara(val isimVaryantlari: List<String>, val soylenen: String, val numara: String? = null, val tam: Boolean = false) : Komut
    data class Mesaj(val isimVaryantlari: List<String>, val soylenen: String, val metin: String?, val kanal: Kanal?, val numara: String? = null) : Komut
    /** Sadece çağrı: "hey cezerî", "orada mısın" */
    data object Cagri : Komut
    /** Namaz vakti: hangi = imsak/gunes/ogle/ikindi/aksam/yatsi; null = sıradaki; hepsi = bugünün tablosu */
    data class Vakit(val hangi: String?, val hepsi: Boolean = false) : Komut
    data object Kible : Komut
    data class Hatirla(val bilgi: String) : Komut
    data object HafizaListe : Komut
    data class Unut(val sorgu: String) : Komut
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
    data class Hesap(val ifade: String) : Komut
    data class RehberSor(val sorgu: String, val numaraIste: Boolean) : Komut
    data class Sohbet(val metin: String) : Komut
}

/** Hızlı yol: sık komutları büyük modele gitmeden anında yakalar. */
object KomutCozucu {

    private const val ADV = "(?:\\s+l[üu]tfen)?"
    private val araSonda = Regex(
        "^(.+?)\\s+(?:telefonla\\s+|hemen\\s+)?(?:ara|arar\\s*m[ıi]s[ıi]n|arayabilir\\s*m[ıi]s[ıi]n|aray[ıi]n|aramak\\s+istiyorum|ara\\s+bakal[ıi]m)$ADV$"
    )
    private val araBasta = Regex("^ara\\s+(.+)$")
    private val araIpuclu = Regex(
        "^(.+?)\\s+(?:telefonla\\s+|hemen\\s+|bir\\s+)?(?:ara|arar\\s*m[ıi]s[ıi]n|arayabilir\\s*m[ıi]s[ıi]n|aray[ıi]n)[,.]?\\s+(.+)$"
    )
    private val ipucuMu = Regex("sadece|yaln[ıi]z|tam olarak|yazan|kay[ıi]tl[ıi]|olan[ıi]?$|ad[ıi]yla|isimli|hemen|[şs]imdi|acil")
    private val telefonAc = Regex("^(.+?)\\s+(?:bir\\s+)?telefon\\s+(?:a[çc]|et)(?:ar\\s*m[ıi]s[ıi]n|abilir\\s*m[ıi]s[ıi]n)?$")

    /** "onu ara", "ona yaz": son konuşulan kişi */
    val ZAMIRLER = setOf("onu", "ona", "o", "onun", "onla", "onunla", "bunu", "buna", "şunu", "şuna", "aynı kişiyi", "aynı kişiye")


    private val mesajKelime = "(?:bir\\s+)?(?:mesaj|sms|whatsapp(?:'?tan|\\s*mesaj[ıi])?|whatsapptan)"
    private val mesaj1 = Regex("^(\\S+(?:\\s+\\S+)?)\\s+$mesajKelime\\s+(?:at|g[öo]nder|yaz|yolla|ge[çc])(?:ar\\s*m[ıi]s[ıi]n|abilir\\s*m[ıi]s[ıi]n)?\\s*[:,]?\\s*(.*)$")
    private val mesaj2 = Regex("^(\\S+(?:\\s+\\S+)?)\\s+(.+?)\\s+(?:diye\\s+)?(?:yaz|mesaj\\s+at|mesaj\\s+g[öo]nder|s[öo]yle|ilet|yolla)(?:ar\\s*m[ıi]s[ıi]n|abilir\\s*m[ıi]s[ıi]n)?$ADV$")
    private val mesaj3 = Regex("^(\\S+(?:\\s+\\S+)?)\\s+(?:yaz|s[öo]yle|de\\s*ki|ilet)\\s*[:,]?\\s+(.+)$")
    private val sadeceMesaj = Regex("^(\\S+(?:\\s+\\S+)?)\\s+(?:$mesajKelime\\s+)?(?:yaz|mesaj\\s+at|mesaj\\s+g[öo]nder)$ADV$")
    private val yonelme = Regex(".*(?:'?(?:y?e|y?a|ne|na))$")

    fun coz(ham: String): Komut {
        val t = Turkce.clean(ham)
        if (t.isBlank()) return if (ham.isBlank()) Komut.Sohbet(ham) else Komut.Cagri

        // İptal
        if (Regex("^(iptal|vazge[çc]|bo[şs]ver|dur|tamam dur|kapat)( et)?$").matches(t)) return Komut.Iptal

        // Selam
        if (Regex("^(selam[uü]n aleyk[uü]m|selam[uü]naleyk[uü]m|esselam[uü] aleyk[uü]m)").containsMatchIn(t)) return Komut.Selam("selam")

        // Sadece çağrı
        if (Regex("^(?:hey|hay|ey|alo)?\\s*(?:buyur|orada m[ıi]s[ıi]n|beni duyuyor musun|uyan|haz[ıi]r m[ıi]s[ıi]n)?$").matches(t)) return Komut.Cagri

        // Hafıza: "hatırla: annemin doğum günü 3 Mart", "ne hatırlıyorsun", "... unut"
        hafizaKomutu(t, ham)?.let { return it }

        // Hesap: "3³ kaç", "5 çarpı 7", "200'ün yüzde 15'i"
        Hesap.ifadeyeCevir(t)?.let { return Komut.Hesap(it) }

        // Rehber sorusu
        rehberSorusu(t)?.let { return it }

        // Kıble
        if (Regex("k[ıi]ble").containsMatchIn(t)) return Komut.Kible

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

        // Namaz vakitleri
        vakitSorusu(t)?.let { return it }

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

        // Kişisiz: "ara", "mesaj at: geliyorum" -> son konuşulan kişi
        if (Regex("^(?:bir\\s+)?(?:ara|arayal[ıi]m|ara bakal[ıi]m|arar m[ıi]s[ıi]n)$").matches(t)) return Komut.Ara(emptyList(), "onu")
        Regex("^(?:bir\\s+)?(?:mesaj|sms|whatsapp)\\s+(?:at|g[öo]nder|yaz|yolla)(?:ar\\s*m[ıi]s[ıi]n)?\\s*[:,]?\\s*(.*)$").find(tKanalsiz)?.let { m ->
            return Komut.Mesaj(emptyList(), "ona", govdeTemizle(m.groupValues[1]).ifBlank { null }, kanal)
        }

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
        araIpuclu.find(t)?.let { m ->
            val isim = m.groupValues[1].removeSuffix(" telefonla").trim()
            val ipucu = m.groupValues[2]
            if (isim.split(' ').size <= 3 && ipucuMu.containsMatchIn(ipucu)) {
                val tam = Regex("sadece|yaln[ıi]z|tam olarak|yazan|ad[ıi]yla").containsMatchIn(ipucu)
                val ek = Regex("(\\S+(?:\\s+\\S+)?)\\s+(?:yazan|diye|olarak|ad[ıi]yla|olan)").find(ipucu.replace(Regex("^(?:sadece|yaln[ıi]zca|yaln[ıi]z|tam olarak)\\s+"), ""))
                    ?.groupValues?.get(1)?.let { isimVaryant(it, Turkce.Ek.BELIRTME) } ?: emptyList()
                return Komut.Ara((isimVaryant(isim, Turkce.Ek.BELIRTME) + ek).distinct(), isim, tam = tam)
            }
        }
        telefonAc.find(t)?.let { m ->
            val isim = m.groupValues[1].trim()
            if (isim.split(' ').size <= 3) return Komut.Ara(isimVaryant(isim, Turkce.Ek.YONELME), isim)
        }
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

    private val varMi = Regex("(?<!\\p{L})(?:var\\s*m[ıi]|kay[ıi]tl[ıi]\\s*m[ıi]|ekli\\s*m[iı]|bulunuyor\\s*mu|yok\\s*mu|var\\s*olan)(?!\\p{L})")
    private val rehberIpucu = Regex("rehber|telefonum|telefonda|kay[ıi]tl|isimli|ismli|adl[ıi]|ad[ıi]nda|ki[şs]i|birisi|biri(?!\\p{L})")
    private val rehberDolgu = Regex(
        "(?<!\\p{L})(?:rehber(?:im|imiz)?(?:de|e|imde|inde)?|telefonum(?:da|un)?|telefonda|telefon\\s+rehberi(?:mde|nde)?|kay[ıi]tl[ıi]|ekli|isimli|ismli|adl[ıi]|ad[ıi]nda|diye|bir|biri|birisi|ki[şs]i(?:ler)?|kimse|numara(?:s[ıi])?|acaba|benim|m[ıiuü]|var|yok|olan)(?!\\p{L})"
    )

    private fun rehberSorusu(t: String): Komut? {
        fun temiz(x: String) = x.replace(Regex("\\b(numaral[ıi]|numara|ki[şs]i|adl[ıi]|isimli)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        Regex("^(.+?)\\s+kimin\\s+numaras[ıi]").find(t)?.let { return Komut.RehberSor(temiz(it.groupValues[1]), false) }
        Regex("^(.+?)'?\\s*(?:n[ıiuü]n|[ıiuü]n)\\s+(?:telefon\\s+)?numaras[ıi](?:n[ıi])?\\s*(?:ne|ka[çc]|nedir|neydi|ver|s[öo]yle|g[öo]ster|yaz|oku)?").find(t)?.let {
            return Komut.RehberSor(it.groupValues[1].trim(), true)
        }
        if (varMi.containsMatchIn(t) && rehberIpucu.containsMatchIn(t)) {
            val isim = t.replace(varMi, " ").replace(rehberDolgu, " ").replace(Regex("[?.!,]"), " ").replace(Regex("\\s+"), " ").trim()
            if (isim.isNotBlank() && isim.split(' ').size <= 4) return Komut.RehberSor(isim, false)
        }
        return null
    }

    // ---------------- Aşama 4: vakit, kıble, hafıza

    private val vakitAdlari = listOf(
        "imsak" to Regex("imsak|sahur|sabah ezan|sabah namaz"),
        "gunes" to Regex("g[üu]ne[şs] (?:do[ğg]|ka[çc])|g[üu]ne[şs]in do[ğg]|g[üu]ne[şs] ne zaman|do[ğg]u[şs]"),
        "ogle" to Regex("[öo][ğg]le"),
        "ikindi" to Regex("ikindi"),
        "aksam" to Regex("ak[şs]am ezan|ak[şs]am namaz|ak[şs]am vakt|iftar|ak[şs]am ne zaman|ak[şs]am ka[çc]ta|ak[şs]am okun|g[üu]ne[şs] bat"),
        "yatsi" to Regex("yats[ıi]"),
    )

    private fun vakitSorusu(t: String): Komut? {
        if (Regex("nas[ıi]l|rek[aâ]t|neden|ni[çc]in|niye|anlat|dua|abdest|k[ıi]l[ıi]n|k[ıi]lmak|farz|s[üu]nnet|kaza").containsMatchIn(t)) return null
        val soru = Regex("ne zaman|ka[çc]ta|ka[çc]|ne kadar|kald[ıi]|s[öo]yle|nedir|okun|giriyor|girer|vakti|vakitleri|saati|ezan[ıi]|\\?").containsMatchIn(t) ||
            (t.split(' ').size <= 3 && Regex("ezan|vakt|vakit|imsak|iftar|sahur").containsMatchIn(t))
        val namazKelime = Regex("ezan|namaz|vakit|vakt|imsak|sahur|iftar|[öo][ğg]le|ikindi|yats[ıi]").containsMatchIn(t)
        if (!namazKelime && !Regex("ak[şs]am ne zaman|ak[şs]am ka[çc]ta|g[üu]ne[şs] (ne zaman|ka[çc]ta|do[ğg]|bat)").containsMatchIn(t)) return null
        if (!soru) return null
        if (Regex("vakitler|t[üu]m vakit|b[üu]t[üu]n vakit|bug[üu]nk[üu] vakit|namaz vakitleri").containsMatchIn(t)) return Komut.Vakit(null, hepsi = true)
        for ((ad, r) in vakitAdlari) if (r.containsMatchIn(t)) return Komut.Vakit(ad)
        if (Regex("ezan|namaz|vakit|vakt").containsMatchIn(t)) return Komut.Vakit(null)
        return null
    }

    private fun hafizaKomutu(t: String, ham: String): Komut? {
        if (Regex("^(?:sen\\s+)?(?:ne(?:ler)?(?:i)?\\s+hat[ıi]rl[ıi]yorsun|benim hakk[ıi]mda ne(?:ler)? biliyorsun|neler biliyorsun|haf[ıi]zanda ne(?:ler)? var|ne(?:leri)? kaydettin)").containsMatchIn(t)) return Komut.HafizaListe
        Regex("^(?:bunu\\s+|[şs]unu\\s+)?(?:hat[ıi]rla|unutma|akl[ıi]nda tut|not al|haf[ıi]zana (?:al|kaydet|yaz))\\b\\s*[:,]?\\s*(.+)$").find(t)?.let {
            return Komut.Hatirla(bilgiKes(ham, it.groupValues[1]))
        }
        Regex("^(.+?)[,.]?\\s+(?:bunu\\s+|[şs]unu\\s+)?(?:hat[ıi]rla|unutma|akl[ıi]nda tut|not al|haf[ıi]zana (?:al|kaydet|yaz))$").find(t)?.let {
            return Komut.Hatirla(bilgiKes(ham, it.groupValues[1]))
        }
        Regex("^(.+?)\\s+(?:bilgisini\\s+|notunu\\s+)?unut$").find(t)?.let { return Komut.Unut(it.groupValues[1].trim()) }
        Regex("^unut\\s+(.+)$").find(t)?.let { return Komut.Unut(it.groupValues[1].trim()) }
        return null
    }

    private fun bilgiKes(ham: String, kucuk: String): String {
        // Kullanıcının yazdığı büyük/küçük harfi korumaya çalış
        val idx = Turkce.lower(ham).indexOf(kucuk)
        val b = if (idx >= 0) ham.substring(idx, idx + kucuk.length) else kucuk
        return b.trim().trimEnd('.', ',').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Turkce.TR) else it.toString() }
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
