package com.pingai.app.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.pingai.app.R
import com.pingai.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bilgiyi Ping AI imzalı bir fotoğraf kartına çevirip paylaşır: köşegen gradyan + parıltı, sağ üst köşeden
 * yayılan ping halkaları, serif metin. Düzen hep aynı, renk paleti her paylaşımda değişir (12 palet).
 */
object ShareCard {

    private const val W = 1080
    private const val H = 1350
    private const val MARGIN = 96f

    private class CardColors(val bg: Int, val accent: Int, val text: Int, val dim: Int, val light: Boolean = false)

    private fun hsl(h: Float, s: Float, l: Float) = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, s, l))

    /** Birbirinden net ayrılan 8 ton (koyu) + 4 özel palet. */
    private val PALETTES: List<CardColors> = buildList {
        listOf(355f, 28f, 48f, 140f, 175f, 215f, 265f, 325f).forEach { h ->
            val accentL = if (h in 40f..190f) 0.62f else 0.66f
            add(CardColors(hsl(h, 0.60f, 0.10f), hsl(h, 0.90f, accentL), 0xFFFFFFFF.toInt(), hsl(h, 0.30f, 0.76f)))
        }
        add(CardColors(0xFFF6EFE3.toInt(), 0xFFC2410C.toInt(), 0xFF2B1B12.toInt(), 0xFF7A6A5C.toInt(), true)) // krem
        add(CardColors(0xFFEAF7F1.toInt(), 0xFF0F8A5F.toInt(), 0xFF10261D.toInt(), 0xFF4F7A68.toInt(), true)) // nane
        add(CardColors(0xFFFDEDF1.toInt(), 0xFFD6336C.toInt(), 0xFF3A1020.toInt(), 0xFF8A5A6A.toInt(), true)) // pudra
        add(CardColors(0xFF101114.toInt(), 0xFFE5E7EB.toInt(), 0xFFFFFFFF.toInt(), 0xFF9AA0AA.toInt())) // grafit
    }
    private var lastPalette = -1

    private fun pickPalette(): CardColors {
        var i: Int
        do { i = kotlin.random.Random.nextInt(PALETTES.size) } while (i == lastPalette)
        lastPalette = i
        return PALETTES[i]
    }

    suspend fun share(context: Context, topic: String, info: String) {
        val app = context.applicationContext
        com.pingai.app.analytics.Track.topicEvent(app, "share_tapped", topic)
        val theme = pickPalette()
        val uri = withContext(Dispatchers.Default) {
            val bmp = render(app, theme, topic, info)
            val dir = File(app.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "ping_ai_${System.currentTimeMillis()}.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun render(ctx: Context, theme: CardColors, topic: String, info: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val serif = Typeface.create("serif", Typeface.BOLD)
        val sans = Typeface.create("sans-serif-medium", Typeface.BOLD)

        // Arka plan: köşegen gradyan + iki köşede vurgu rengi parıltısı
        paint.shader = LinearGradient(
            0f, 0f, W.toFloat(), H.toFloat(),
            theme.bg, ColorUtils.blendARGB(theme.bg, theme.accent, if (theme.light) 0.14f else 0.22f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = RadialGradient(
            W * 0.9f, H * 0.08f, 760f,
            ColorUtils.setAlphaComponent(theme.accent, if (theme.light) 56 else 90), ColorUtils.setAlphaComponent(theme.accent, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = RadialGradient(
            W * 0.05f, H * 0.98f, 620f,
            ColorUtils.setAlphaComponent(theme.accent, if (theme.light) 36 else 55), ColorUtils.setAlphaComponent(theme.accent, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        // İmza: sağ üst köşeden yayılan ping halkaları
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7.5f
        listOf(150f to 0.60f, 262f to 0.38f, 375f to 0.22f).forEach { (r, a) ->
            paint.color = ColorUtils.setAlphaComponent(theme.accent, (a * 255).toInt())
            c.drawCircle(W.toFloat(), 0f, r, paint)
        }
        paint.style = Paint.Style.FILL

        // Konu "hap" etiketi
        val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent; textSize = 38f; typeface = sans
        }
        val label = topic.take(30)
        val pillW = pillText.measureText(label) + 72f
        val pill = RectF(MARGIN, 100f, MARGIN + pillW.coerceAtMost(W - 2 * MARGIN), 100f + 80f)
        paint.color = ColorUtils.setAlphaComponent(theme.accent, 51)
        c.drawRoundRect(pill, 40f, 40f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
        paint.color = ColorUtils.setAlphaComponent(theme.accent, 153)
        c.drawRoundRect(pill, 40f, 40f, paint)
        paint.style = Paint.Style.FILL
        c.drawText(label, pill.left + 36f, pill.centerY() + 13f, pillText)

        // Büyük, silik tırnak işareti
        val quote = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(theme.accent, 90); textSize = 380f; typeface = serif
        }
        c.drawText("\u201C", MARGIN - 8f, 540f, quote)

        // Bilgi metni (serif): alana sığacak en büyük punto; yıllar/sayılar vurgu renginde
        val areaTop = 390f
        val areaBottom = H - 330f
        val width = (W - 2 * MARGIN).toInt()
        val spannable = SpannableString(info)
        Regex("""\b\d[\d.,]*\b""").findAll(info).forEach {
            spannable.setSpan(ForegroundColorSpan(theme.accent), it.range.first, it.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.text; typeface = serif }
        fun build(sz: Float): StaticLayout {
            body.textSize = sz
            return StaticLayout.Builder.obtain(spannable, 0, spannable.length, body, width)
                .setLineSpacing(0f, 1.18f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        }
        var size = 66f
        var layout = build(size)
        while (layout.height > areaBottom - areaTop && size > 30f) {
            size -= 2f
            layout = build(size)
        }
        c.save()
        c.translate(MARGIN, areaTop)
        layout.draw(c)
        c.restore()

        // Vurgu çizgisi
        paint.color = theme.accent
        c.drawRoundRect(RectF(MARGIN, H - 290f, MARGIN + 140f, H - 280f), 5f, 5f, paint)

        // Alt şerit: logo + ad + "günün bilgisi"
        val icon = 100f
        val iconLeft = MARGIN
        val iconTop = H - 100f - icon
        val iconRect = RectF(iconLeft, iconTop, iconLeft + icon, iconTop + icon)
        paint.color = if (theme.light) 0xFF1F3A5F.toInt() else 0xFFFFFFFF.toInt()
        c.drawRoundRect(iconRect, 25f, 25f, paint)
        c.save()
        c.clipPath(Path().apply { addRoundRect(iconRect, 25f, 25f, Path.Direction.CW) })
        val pad = (icon * 0.25f).toInt()
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)?.mutate()?.apply {
            if (theme.light) setTint(0xFFFFFFFF.toInt())
            setBounds(iconLeft.toInt() - pad, iconTop.toInt() - pad, (iconLeft + icon).toInt() + pad, (iconTop + icon).toInt() + pad); draw(c)
        }
        c.restore()

        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.text; textSize = 48f; typeface = sans }
        c.drawText("Ping AI", iconLeft + icon + 26f, iconTop + icon / 2f + 17f, name)
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.dim; textSize = 30f; typeface = sans
            textAlign = Paint.Align.RIGHT; letterSpacing = 0.12f
        }
        c.drawText(ctx.getString(R.string.share_tag), W - MARGIN, iconTop + icon / 2f + 11f, tag)
        return bmp
    }
}
