@file:OptIn(ExperimentalTextApi::class)

package com.erkam.cezeri.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

object Renk {
    val Zemin = Color(0xFF0E1116)
    val ZeminDerin = Color(0xFF0B0E13)
    val Kart = Color(0xFF151A22)
    val KartKoyu = Color(0xFF13171E)
    val KartCizgi = Color(0xFF222833)
    val Cizgi = Color(0xFF2A303A)
    val Altin = Color(0xFFC9A45C)
    val AltinAcik = Color(0xFFE2C383)
    val AltinParlak = Color(0xFFF0D9A0)
    val AltinKoyu = Color(0xFF8A672E)
    val AltinZemin = Color(0xFF1A1710)
    val AltinCizgi = Color(0xFF3A3220)
    val AltinIs = Color(0xFF2A2418)
    val Turkuaz = Color(0xFF3FA7A0)
    val TurkuazAcik = Color(0xFF8FD0CA)
    val TurkuazYazi = Color(0xFFCFE9E6)
    val TurkuazZemin = Color(0xFF101A1A)
    val TurkuazCizgi = Color(0xFF1F3836)
    val Yazi = Color(0xFFECE6DA)
    val YaziSicak = Color(0xFFF1E7D2)
    val Soluk = Color(0xFFA8A196)
    val SolukAcik = Color(0xFFCFC8BB)
    val Ikon = Color(0xFFB8B0A2)
    val Kirmizi = Color(0xFFB5473A)
    val Amblem = Color(0xFF111720)
    val Yildiz = Color(0xFF141B25)
}

object Yazi {
    var serif: FontFamily = FontFamily.Serif
        private set
    var sans: FontFamily = FontFamily.Default
        private set

    fun yukle(ctx: Context) {
        serif = aile(ctx, "fonts/cormorant.ttf", listOf(500, 600, 700)) ?: FontFamily.Serif
        sans = aile(ctx, "fonts/inter.ttf", listOf(400, 500, 600)) ?: FontFamily.Default
    }

    private fun aile(ctx: Context, yol: String, agirliklar: List<Int>): FontFamily? = try {
        ctx.assets.open(yol).close()
        FontFamily(agirliklar.map { w ->
            Font(
                path = yol,
                assetManager = ctx.assets,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        })
    } catch (_: Exception) {
        null
    }
}
