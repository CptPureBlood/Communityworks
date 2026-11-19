package com.pingai.app.widget

import android.graphics.Color
import androidx.annotation.StringRes
import com.pingai.app.R

data class WidgetTheme(
    val id: String,
    @StringRes val nameRes: Int,
    val bg: Int,
    val button: Int,
    val text: Int,
    val dim: Int,
    val accent: Int,
    val pro: Boolean
)

object WidgetThemes {
    val GOOD = Color.parseColor("#2EA043")
    val BAD = Color.parseColor("#E5484D")

    val all = listOf(
        WidgetTheme("dark", R.string.theme_dark, c("#202020"), c("#2F2F2F"), c("#FFFFFF"), c("#C5C5C5"), c("#60CDFF"), false),
        WidgetTheme("light", R.string.theme_light, c("#F3F3F3"), c("#E2E2E2"), c("#1B1B1B"), c("#5A5A5A"), c("#0067C0"), false),
        WidgetTheme("ocean", R.string.theme_ocean, c("#0B2A3F"), c("#14405E"), c("#EAF6FF"), c("#9CC3DA"), c("#4FD1FF"), true),
        WidgetTheme("sunset", R.string.theme_sunset, c("#3A1C2B"), c("#58293E"), c("#FFF1E8"), c("#E0B5A8"), c("#FF9E5E"), true),
        WidgetTheme("forest", R.string.theme_forest, c("#14281D"), c("#21402F"), c("#EAF7EC"), c("#A5C8AE"), c("#6EE7A0"), true),
        WidgetTheme("amoled", R.string.theme_amoled, c("#000000"), c("#1C1C1C"), c("#FFFFFF"), c("#B0B0B0"), c("#FFD166"), true)
    )

    fun byId(id: String?, isPro: Boolean): WidgetTheme {
        val t = all.firstOrNull { it.id == id } ?: all[0]
        return if (t.pro && !isPro) all[0] else t
    }

    private fun c(s: String) = Color.parseColor(s)
}
