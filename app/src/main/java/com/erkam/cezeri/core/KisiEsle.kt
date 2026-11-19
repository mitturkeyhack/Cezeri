package com.erkam.cezeri.core

/**
 * Söylenen ismi rehberdeki adlarla puanlar. Android'e bağlı değildir (testlerde çalışır).
 * 1.00 birebir (boşluklar yok sayılır: "mehmetali" = "Mehmet Ali" = "MehmetAli")
 * 0.97 tüm kelimeler var   0.95 bir kelime birebir   0.86 başı tutuyor   0.55–0.85 benzer
 */
object KisiEsle {
    const val BIREBIR = 0.99
    const val GUCLU = 0.9

    private fun bitisik(s: String) = s.replace(" ", "")

    fun puan(kisiAdi: String, sorgular: List<String>): Double {
        val ad = Turkce.norm(kisiAdi)
        if (ad.isBlank()) return 0.0
        val tokens = ad.split(' ').filter { it.isNotBlank() }
        val adB = bitisik(ad)
        var best = 0.0
        for (ham in sorgular) {
            val q = Turkce.norm(ham)
            if (q.length < 2) continue
            val qB = bitisik(q)
            val qTokens = q.split(' ')
            val p = when {
                ad == q || adB == qB -> 1.0
                qTokens.size > 1 && qTokens.all { qt -> tokens.any { it == qt } } -> 0.97
                tokens.any { it == q } -> 0.95
                qB.length >= 4 && tokens.size > 1 && tokens.indices.any { i -> bitisik(tokens.drop(i).take(qTokens.size + 1).joinToString("")) == qB } -> 0.95
                q.length >= 3 && (ad.startsWith(q) || tokens.any { it.startsWith(q) }) -> 0.86
                q.length >= 4 && tokens.any { q.startsWith(it) && it.length >= 3 } -> 0.8
                else -> {
                    val s = (tokens + adB).maxOf { Turkce.similarity(qB, it) }
                    if (s >= 0.72) 0.55 + 0.3 * s else 0.0
                }
            }
            if (p > best) best = p
        }
        return best
    }

    data class Sonuc<T>(val oge: T, val puan: Double)

    /** Puanı eşiği geçenleri büyükten küçüğe döndürür. */
    fun <T> sirala(ogeler: List<T>, ad: (T) -> String, sorgular: List<String>, esik: Double = 0.7): List<Sonuc<T>> =
        ogeler.mapNotNull { o -> puan(ad(o), sorgular).takeIf { it >= esik }?.let { Sonuc(o, it) } }
            .sortedByDescending { it.puan }

    /**
     * Seçim kararı: birebir eşleşen tek kişi varsa o; yoksa açık ara öndeki; değilse sorulacak adaylar.
     * [tam] true ise ("sadece Ablam yazanı") yalnız birebir eşleşenler sayılır.
     */
    sealed interface Karar<T> {
        data class Tek<T>(val oge: T) : Karar<T>
        data class Sor<T>(val adaylar: List<T>) : Karar<T>
        class Yok<T> : Karar<T>
    }

    fun <T> karar(siralı: List<Sonuc<T>>, tam: Boolean = false): Karar<T> {
        if (siralı.isEmpty()) return Karar.Yok()
        val birebir = siralı.filter { it.puan >= BIREBIR }
        if (birebir.size == 1) return Karar.Tek(birebir[0].oge)
        if (birebir.size > 1) return Karar.Sor(birebir.take(3).map { it.oge })
        if (tam) return Karar.Yok()
        val ilk = siralı[0]
        val ikinci = siralı.getOrNull(1)
        if (ikinci == null || ilk.puan - ikinci.puan >= 0.1) return Karar.Tek(ilk.oge)
        return Karar.Sor(siralı.filter { ilk.puan - it.puan < 0.1 }.take(3).map { it.oge })
    }
}
