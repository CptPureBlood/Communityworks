package com.pingai.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.pingai.app.data.RefreshInterval
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.data.Topic
import com.pingai.app.ui.common.Chip
import com.pingai.app.ui.common.SectionCard
import com.pingai.app.ui.common.TopicPicker
import com.pingai.app.ui.detail.DetailActivity
import com.pingai.app.ui.onboarding.CONTENT_LANGUAGES
import com.pingai.app.ui.theme.WinColors
import com.pingai.app.widget.WidgetUpdater
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val PRO_UNLOCK_CODE = "PINGAI-PRO"

@Composable
private fun CountdownText(targetMs: Long, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(targetMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val left = ((targetMs - now) / 1000).coerceAtLeast(0)
    val h = left / 3600
    val m = (left % 3600) / 60
    val s = left % 60
    val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    Text(stringResource(R.string.widget_next_update, text), color = WinColors.TextDim, fontSize = 14.sp, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = context.applicationContext
    val prefs = remember { Prefs(app) }
    val scope = rememberCoroutineScope()

    val topics by prefs.topics.collectAsState(initial = emptyList())
    val activeId by prefs.activeTopicId.collectAsState(initial = null)
    val isPro by prefs.isPro.collectAsState(initial = false)
    val interval by prefs.refreshIntervalMinutes.collectAsState(initial = 720)
    val notif by prefs.notificationsEnabled.collectAsState(initial = true)
    val provider by prefs.provider.collectAsState(initial = AiProvider.GEMINI)
    val contentLang by prefs.contentLanguage.collectAsState(initial = "system")
    val fetchingSince by prefs.fetchingSince.collectAsState(initial = 0L)
    val lastError by prefs.lastError.collectAsState(initial = null)
    val status by TipService.status.collectAsState()

    val active = topics.firstOrNull { it.id == activeId } ?: topics.firstOrNull()
    val maxTopics = if (isPro) 3 else 1
    val loading = status.loading || (fetchingSince > 0L && System.currentTimeMillis() - fetchingSince < 60_000L)

    var editing by remember { mutableStateOf<String?>(null) } // "add" | "change" | null
    var topicDraft by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }
    var proCode by remember { mutableStateOf("") }
    LaunchedEffect(provider) {
        keyInput = withContext(Dispatchers.IO) { SecureKeyStore.get(app).getKey(provider) ?: "" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Ping AI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
            Text(stringResource(R.string.main_subtitle), color = WinColors.TextDim)
        }

        // Kahraman kart
        val heroShape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(Brush.linearGradient(listOf(WinColors.HeroStart, WinColors.HeroEnd)))
                .border(1.dp, WinColors.Stroke, heroShape)
                .padding(20.dp)
        ) {
            Text(
                active?.name ?: "",
                color = WinColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
            )
            Spacer(Modifier.height(10.dp))
            val tip = active?.shortInfo.orEmpty()
            when {
                loading && tip.isBlank() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = WinColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.height(0.dp).padding(horizontal = 6.dp))
                    Text(stringResource(R.string.widget_loading), color = WinColors.TextDim)
                }
                tip.isBlank() && lastError != null ->
                    Text(lastError.orEmpty(), color = WinColors.Error, fontSize = 17.sp)
                tip.isBlank() ->
                    Text(stringResource(R.string.hero_empty), color = WinColors.TextDim, fontSize = 17.sp)
                else -> Text(tip, color = WinColors.Text, fontSize = 20.sp, lineHeight = 29.sp)
            }
            if (tip.isNotBlank() && lastError != null && !loading) {
                Spacer(Modifier.height(8.dp))
                Text(lastError.orEmpty(), color = WinColors.Error, fontSize = 13.sp)
            }
            if (active != null && active.nextUpdateEpochMs > System.currentTimeMillis() && !loading) {
                Spacer(Modifier.height(10.dp))
                CountdownText(active.nextUpdateEpochMs)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canRefresh = isPro || active?.shortInfo.isNullOrBlank() || lastError != null
                Button(
                    enabled = !loading && (canRefresh || !isPro),
                    onClick = {
                        if (canRefresh) TipService.launchRefresh(app, notify = false)
                    }
                ) {
                    Text(
                        if (canRefresh) stringResource(R.string.btn_refresh)
                        else stringResource(R.string.btn_refresh_locked)
                    )
                }
                if (active != null && !active.shortInfo.isBlank()) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(context, DetailActivity::class.java)
                                .putExtra(DetailActivity.EXTRA_TOPIC_ID, active.id)
                        )
                    }) { Text(stringResource(R.string.btn_explain)) }
                }
            }
        }

        // Konular
        SectionCard(stringResource(R.string.sec_topics, topics.size, maxTopics)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t ->
                    Chip(t.name, selected = t.id == active?.id) {
                        scope.launch {
                            prefs.setActiveTopicId(t.id)
                            WidgetUpdater.updateAll(app)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (editing == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (topics.size < maxTopics) {
                        OutlinedButton(onClick = { topicDraft = ""; editing = "add" }) {
                            Text(stringResource(R.string.topic_add))
                        }
                    }
                    if (active != null) {
                        OutlinedButton(onClick = { topicDraft = active.name; editing = "change" }) {
                            Text(stringResource(R.string.topic_change))
                        }
                    }
                }
                if (active != null && topics.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            prefs.removeTopic(active.id)
                            prefs.getTopicsOnce().firstOrNull()?.let { prefs.setActiveTopicId(it.id) }
                            WidgetUpdater.updateAll(app)
                        }
                    }) { Text(stringResource(R.string.topic_remove)) }
                }
                if (!isPro) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.topic_free_note), color = WinColors.TextDim, fontSize = 13.sp)
                }
            } else {
                TopicPicker(topicDraft) { topicDraft = it }
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = topicDraft.isNotBlank(),
                    onClick = {
                        val mode = editing
                        val name = topicDraft.trim()
                        editing = null
                        scope.launch {
                            if (mode == "add") {
                                val t = Topic(id = UUID.randomUUID().toString(), name = name)
                                prefs.upsertTopic(t)
                                prefs.setActiveTopicId(t.id)
                            } else if (active != null) {
                                prefs.updateTopic(active.id) {
                                    it.copy(name = name, shortInfo = "", nextUpdateEpochMs = 0L)
                                }
                            }
                            WidgetUpdater.updateAll(app)
                            TipService.launchRefresh(app, notify = false)
                        }
                    }
                ) { Text(stringResource(R.string.topic_save)) }
            }
        }

        // Yenileme aralığı
        SectionCard(stringResource(R.string.sec_refresh)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RefreshInterval.entries.forEach { ri ->
                    val locked = ri.isPro && !isPro
                    Chip(
                        label = ri.label + if (locked) " 🔒" else "",
                        selected = interval == ri.minutes,
                        enabled = !locked
                    ) {
                        scope.launch {
                            prefs.setRefreshIntervalMinutes(ri.minutes)
                            val now = System.currentTimeMillis()
                            prefs.getTopicsOnce().forEach { t ->
                                if (t.lastUpdatedEpochMs > 0L) {
                                    prefs.updateTopic(t.id) { it.copy(nextUpdateEpochMs = now + ri.minutes * 60_000L) }
                                }
                            }
                            WorkScheduler.scheduleNext(app, ri.minutes, null)
                            WidgetUpdater.updateAll(app)
                        }
                    }
                }
            }
        }

        // Yapay zekâ
        SectionCard(stringResource(R.string.sec_ai)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    val locked = p != AiProvider.GEMINI && !isPro
                    Chip(
                        label = p.shortLabel + if (locked) " 🔒" else "",
                        selected = provider == p,
                        enabled = !locked
                    ) { scope.launch { prefs.setProvider(p) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it.trim() },
                label = { Text(stringResource(R.string.ai_key_label) + " · " + provider.shortLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val savedMsg = stringResource(R.string.ai_saved)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val p = provider
                    val k = keyInput
                    scope.launch {
                        withContext(Dispatchers.IO) { SecureKeyStore.get(app).setKey(p, k) }
                        Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.ai_save)) }
                if (provider == AiProvider.GEMINI) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                        )
                    }) { Text(stringResource(R.string.ai_get_key)) }
                }
            }
        }

        // Tercihler
        SectionCard(stringResource(R.string.sec_prefs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_notifications), color = WinColors.Text, modifier = Modifier.weight(1f))
                Switch(checked = notif, onCheckedChange = { scope.launch { prefs.setNotificationsEnabled(it) } })
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.pref_content_language), color = WinColors.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val effective = LocaleHelper.resolveContentLanguage(contentLang)
                CONTENT_LANGUAGES.forEach { (code, label) ->
                    Chip(label, selected = effective == code) { scope.launch { prefs.setContentLanguage(code) } }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.pref_app_language), color = WinColors.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val saved = LocaleHelper.saved(context)
                listOf(
                    "system" to stringResource(R.string.lang_system),
                    "en" to "English",
                    "tr" to "Türkçe"
                ).forEach { (code, label) ->
                    Chip(label, selected = saved == code) {
                        if (saved != code) {
                            LocaleHelper.save(context, code)
                            (context as? Activity)?.recreate()
                        }
                    }
                }
            }
        }

        // Pro
        SectionCard(stringResource(R.string.sec_pro)) {
            if (isPro) {
                Text("✓ " + stringResource(R.string.pro_active), color = WinColors.Accent, fontWeight = FontWeight.SemiBold)
            } else {
                Text(stringResource(R.string.pro_body), color = WinColors.TextDim)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proCode,
                        onValueChange = { proCode = it },
                        label = { Text(stringResource(R.string.pro_code_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        if (proCode.trim().equals(PRO_UNLOCK_CODE, ignoreCase = true)) {
                            scope.launch {
                                prefs.setPro(true)
                                WidgetUpdater.updateAll(app)
                            }
                        }
                    }) { Text(stringResource(R.string.pro_apply)) }
                }
            }
        }

        Text(stringResource(R.string.widget_howto), color = WinColors.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
    }
}
