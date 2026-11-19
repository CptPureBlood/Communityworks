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
import com.pingai.app.widget.WidgetTheme
import com.pingai.app.widget.WidgetThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** Bilgiyi, seçili widget temasının renkleriyle şık bir fotoğraf kartına çevirip paylaşır. */
object ShareCard {

    private const val W = 1080
    private const val H = 1350
    private const val MARGIN = 96f

    suspend fun share(context: Context, topic: String, info: String) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val theme = WidgetThemes.byId(prefs.widgetTheme.first(), prefs.isPro.first())
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

    private fun render(ctx: Context, theme: WidgetTheme, topic: String, info: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Arka plan: köşegen gradyan + sağ üstte vurgu rengi parıltısı
        paint.shader = LinearGradient(
            0f, 0f, W.toFloat(), H.toFloat(),
            theme.bg, ColorUtils.blendARGB(theme.bg, theme.accent, 0.22f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = RadialGradient(
            W * 0.9f, H * 0.08f, 760f,
            ColorUtils.setAlphaComponent(theme.accent, 90), ColorUtils.setAlphaComponent(theme.accent, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = RadialGradient(
            W * 0.05f, H * 0.98f, 620f,
            ColorUtils.setAlphaComponent(theme.accent, 55), ColorUtils.setAlphaComponent(theme.accent, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        // Konu "hap" etiketi
        val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            textSize = 44f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val label = topic.take(34)
        val pillW = pillText.measureText(label) + 72f
        val pill = RectF(MARGIN, 110f, MARGIN + pillW.coerceAtMost(W - 2 * MARGIN), 110f + 86f)
        paint.color = ColorUtils.setAlphaComponent(theme.accent, 48)
        c.drawRoundRect(pill, 43f, 43f, paint)
        c.drawText(label, pill.left + 36f, pill.centerY() + 15f, pillText)

        // Büyük, silik tırnak işareti
        val quote = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(theme.accent, 60)
            textSize = 520f
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        c.drawText("“", MARGIN - 20f, 640f, quote)

        // Bilgi metni: alana sığacak en büyük punto
        val areaTop = 300f
        val areaBottom = H - 330f
        val width = (W - 2 * MARGIN).toInt()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.text
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        fun build(sz: Float): StaticLayout {
            body.textSize = sz
            return StaticLayout.Builder.obtain(info, 0, info.length, body, width)
                .setLineSpacing(0f, 1.18f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        }
        var size = 68f
        var layout = build(size)
        while (layout.height > areaBottom - areaTop && size > 30f) {
            size -= 2f
            layout = build(size)
        }
        c.save()
        c.translate(MARGIN, areaTop + ((areaBottom - areaTop) - layout.height).coerceAtLeast(0f) / 2f)
        layout.draw(c)
        c.restore()

        // Vurgu çizgisi
        paint.color = theme.accent
        c.drawRoundRect(RectF(MARGIN, H - 290f, MARGIN + 120f, H - 280f), 5f, 5f, paint)

        // Alt bilgi: uygulama simgesi + ad + imza
        val icon = 130f
        val iconLeft = MARGIN
        val iconTop = H - 100f - icon
        c.save()
        val clip = Path().apply { addRoundRect(RectF(iconLeft, iconTop, iconLeft + icon, iconTop + icon), 32f, 32f, Path.Direction.CW) }
        c.clipPath(clip)
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_background)?.apply {
            setBounds(iconLeft.toInt(), iconTop.toInt(), (iconLeft + icon).toInt(), (iconTop + icon).toInt()); draw(c)
        }
        val pad = (icon * 0.25f).toInt()
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)?.apply {
            setBounds(iconLeft.toInt() - pad, iconTop.toInt() - pad, (iconLeft + icon).toInt() + pad, (iconTop + icon).toInt() + pad); draw(c)
        }
        c.restore()

        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.text; textSize = 56f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        c.drawText("Ping AI", iconLeft + icon + 32f, iconTop + icon / 2f + 4f, name)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.dim; textSize = 34f }
        c.drawText(ctx.getString(R.string.main_subtitle), iconLeft + icon + 32f, iconTop + icon / 2f + 52f, sub)

        val sign = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(theme.dim, 170); textSize = 34f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("KCD", W - MARGIN, iconTop + icon - 6f, sign)
        return bmp
    }
}
