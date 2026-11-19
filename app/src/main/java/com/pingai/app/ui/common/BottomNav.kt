package com.pingai.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingai.app.R
import com.pingai.app.ui.theme.WinColors

private const val HOME = "m3 11 9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"
private const val BOOKMARK = "M7 3h10a2 2 0 0 1 2 2v15.2a.6.6 0 0 1-.95.5L12.5 16.2a.9.9 0 0 0-1 0L5.95 20.7a.6.6 0 0 1-.95-.5V5a2 2 0 0 1 2-2z"
private const val AWARD = "M12 3a6 6 0 1 0 0 12a6 6 0 1 0 0-12z M8.5 14.5 7 22l5-3 5 3-1.5-7.5"
private const val GEAR = "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"

@Composable
private fun NavIcon(data: String, color: Color, filled: Boolean) {
    val path: Path = remember(data) {
        androidx.core.graphics.PathParser.createPathFromPathData(data)!!.asComposePath()
    }
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            if (filled) drawPath(path, color, style = Fill)
            drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun RowScope.NavItem(selected: Boolean, label: String, data: String, filled: Boolean, onClick: () -> Unit) {
    val color = if (selected) WinColors.Accent else WinColors.NavIdle
    Column(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NavIcon(data, color, filled && selected)
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

/** Alt gezinme çubuğu: ana sayfa, kaydedilen (yer imi), rozetler, ayarlar (çark). */
@Composable
fun BottomNav(current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WinColors.Bg)
            .drawBehind { drawLine(WinColors.Stroke, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        NavItem(current == "home", stringResource(R.string.nav_home), HOME, true) { onSelect("home") }
        NavItem(current == "saved", stringResource(R.string.nav_saved), BOOKMARK, false) { onSelect("saved") }
        NavItem(current == "ach", stringResource(R.string.nav_badges), AWARD, false) { onSelect("ach") }
        NavItem(current == "settings", stringResource(R.string.nav_settings), GEAR, false) { onSelect("settings") }
    }
}
