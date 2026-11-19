package com.pingai.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingai.app.R
import com.pingai.app.core.LocaleHelper
import com.pingai.app.core.TipService
import com.pingai.app.data.AiProvider
import com.pingai.app.data.Prefs
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.data.Topic
import com.pingai.app.ui.common.Chip
import com.pingai.app.ui.common.StepRow
import com.pingai.app.ui.common.TopicPicker
import com.pingai.app.ui.theme.WinColors
import com.pingai.app.widget.WidgetUpdater
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

val CONTENT_LANGUAGES = listOf(
    "tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var topic by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var lang by remember {
        mutableStateOf(LocaleHelper.deviceLanguage().takeIf { l -> CONTENT_LANGUAGES.any { it.first == l } } ?: "en")
    }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Ping AI", color = WinColors.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Text(
                    "━━",
                    color = if (i <= step) WinColors.Accent else WinColors.Stroke,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        when (step) {
            0 -> {
                Text(stringResource(R.string.onb_key_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.onb_intro), color = WinColors.TextDim)
                Spacer(Modifier.height(12.dp))
                StepRow(1, stringResource(R.string.onb_key_step1))
                StepRow(2, stringResource(R.string.onb_key_step2))
                StepRow(3, stringResource(R.string.onb_key_step3))
                StepRow(4, stringResource(R.string.onb_key_step4))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onb_key_free_note), color = WinColors.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.onb_open_studio)) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    label = { Text(stringResource(R.string.onb_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { step = 1 },
                    enabled = apiKey.length > 10,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.onb_next)) }
            }

            1 -> {
                Text(stringResource(R.string.onb_topic_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onb_topic_sub), color = WinColors.TextDim)
                Spacer(Modifier.height(18.dp))
                TopicPicker(topic) { topic = it }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { step = 0 }) { Text(stringResource(R.string.onb_back)) }
                    Button(
                        onClick = { step = 2 },
                        enabled = topic.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.onb_next)) }
                }
            }

            else -> {
                Text(stringResource(R.string.onb_lang_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onb_lang_sub), color = WinColors.TextDim)
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CONTENT_LANGUAGES.forEach { (code, label) ->
                        Chip(label, selected = lang == code) { lang = code }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { step = 1 }) { Text(stringResource(R.string.onb_back)) }
                    Button(
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            saving = true
                            val app = context.applicationContext
                            scope.launch {
                                val prefs = Prefs(app)
                                withContext(Dispatchers.IO) {
                                    SecureKeyStore.get(app).setKey(AiProvider.GEMINI, apiKey)
                                }
                                val t = Topic(id = UUID.randomUUID().toString(), name = topic.trim())
                                prefs.setProvider(AiProvider.GEMINI)
                                prefs.setContentLanguage(lang)
                                prefs.upsertTopic(t)
                                prefs.setActiveTopicId(t.id)
                                prefs.setRefreshIntervalMinutes(720)
                                prefs.setNotificationsEnabled(true)
                                prefs.setOnboardingDone(true)
                                TipService.launchRefresh(app, notify = false, force = true)
                                WidgetUpdater.updateAll(app)
                            }
                        }
                    ) { Text(stringResource(R.string.onb_finish)) }
                }
            }
        }
    }
}
