package com.pingai.app.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingai.app.R
import com.pingai.app.data.Prefs
import com.pingai.app.ui.common.Chip
import com.pingai.app.ui.theme.WinColors
import com.pingai.app.widget.WidgetUpdater
import kotlinx.coroutines.launch

@Composable
fun SavedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val prefs = remember { Prefs(app) }
    val saved by prefs.saved.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Chip("← " + stringResource(R.string.btn_back), selected = false) { onBack() }
            Spacer(Modifier.padding(start = 12.dp))
            Text(
                stringResource(R.string.saved_title),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WinColors.Text
            )
        }
        Spacer(Modifier.height(14.dp))
        if (saved.isEmpty()) {
            Text(stringResource(R.string.saved_empty), color = WinColors.TextDim)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(saved, key = { it.id }) { tip ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(WinColors.Card)
                            .border(1.dp, WinColors.Stroke, shape)
                            .padding(16.dp)
                    ) {
                        Text(tip.topic, color = WinColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (tip.question.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(tip.question, color = WinColors.TextDim, fontSize = 14.sp)
                            if (tip.answer.isNotBlank()) {
                                Text(
                                    stringResource(R.string.saved_answer, tip.answer),
                                    color = WinColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(tip.info, color = WinColors.Text, fontSize = 17.sp, lineHeight = 25.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("↗ " + stringResource(R.string.btn_share), selected = false) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "📚 ${tip.topic}\n\n${tip.info}\n\n— Ping AI")
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                            Chip("✕ " + stringResource(R.string.saved_remove), selected = false) {
                                scope.launch { prefs.removeSaved(tip.id); WidgetUpdater.updateAll(app) }
                            }
                        }
                    }
                }
            }
        }
    }
}
