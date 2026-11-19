package com.pingai.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.app.TimePickerDialog
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
import androidx.compose.ui.draw.alpha
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
import com.pingai.app.core.TipActions
import com.pingai.app.ui.common.QuizOptions
import com.pingai.app.ui.common.QuizGood
import com.pingai.app.ui.common.QuizBad
import com.pingai.app.ui.common.ResultLine
import com.pingai.app.ui.common.rememberQuizFlash
import com.pingai.app.ui.common.quizGlow
import com.pingai.app.ui.common.ThemeTile
import com.pingai.app.network.UpdateChecker
import com.pingai.app.widget.WidgetThemes
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.pingai.app.ui.common.Chip
import com.pingai.app.ui.common.SectionCard
import com.pingai.app.ui.common.TopicPicker
import com.pingai.app.ui.detail.DetailActivity
import com.pingai.app.core.APP_LANGUAGES
import com.pingai.app.core.CONTENT_LANGUAGES
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
fun MainScreen(onOpenSaved: () -> Unit = {}) {
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
    val stats by prefs.stats.collectAsState(initial = com.pingai.app.data.Stats())
    val saved by prefs.saved.collectAsState(initial = emptyList())
    val themeId by prefs.widgetTheme.collectAsState(initial = "dark")
    val quietOn by prefs.quietEnabled.collectAsState(initial = true)
    val quietStart by prefs.quietStartMin.collectAsState(initial = 23 * 60)
    val quietEnd by prefs.quietEndMin.collectAsState(initial = 7 * 60)
    val optionCount by prefs.optionCount.collectAsState(initial = 2)
    val updateVersion by prefs.updateVersion.collectAsState(initial = "")
    val updateUrl by prefs.updateUrl.collectAsState(initial = "")
    LaunchedEffect(Unit) { UpdateChecker.check(app, notify = false) }
    val cooldownUntil by TipService.cooldownUntil.collectAsState()
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cooldownUntil) {
        while (System.currentTimeMillis() < cooldownUntil) {
            nowTick = System.currentTimeMillis()
            delay(500)
        }
        nowTick = System.currentTimeMillis()
    }
    val waitSec = ((cooldownUntil - nowTick + 999) / 1000).coerceAtLeast(0).toInt()

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ping AI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
                Text(stringResource(R.string.main_subtitle), color = WinColors.TextDim)
            }
            Chip("♥ ${saved.size}", selected = false) { onOpenSaved() }
            Spacer(Modifier.width(8.dp))
            Chip("🔥 ${stats.effectiveStreak()}", selected = stats.effectiveStreak() > 0) { }
        }

        if (updateVersion.isNotBlank() && updateUrl.isNotBlank()) {
            val shape = RoundedCornerShape(14.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(WinColors.Accent.copy(alpha = 0.15f))
                    .border(1.dp, WinColors.Accent, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.update_available, updateVersion),
                    color = WinColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                }) { Text(stringResource(R.string.update_download)) }
            }
        }

        // Kahraman kart
        val heroShape = RoundedCornerShape(22.dp)
        val flash by rememberQuizFlash(active)
        val flashColor = if (active != null && active.answeredIdx == active.correctIdx) QuizGood else QuizBad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(Brush.linearGradient(listOf(WinColors.HeroStart, WinColors.HeroEnd)))
                .quizGlow(flash, flashColor)
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
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.widget_loading), color = WinColors.TextDim)
                }
                tip.isBlank() && lastError != null ->
                    Text(lastError.orEmpty(), color = WinColors.Error, fontSize = 17.sp)
                tip.isBlank() ->
                    Text(stringResource(R.string.hero_empty), color = WinColors.TextDim, fontSize = 17.sp)
                active != null && active.awaitingAnswer -> {
                    Text(active.question, color = WinColors.Text, fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.quiz_hint), color = WinColors.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    QuizOptions(active.options) { i ->
                        scope.launch {
                            val ok = TipActions.answer(app, active.id, i)
                            if (ok != null) WidgetUpdater.playFlash(app, ok)
                        }
                    }
                }
                else -> {
                    if (active != null) {
                        ResultLine(active)
                        if (active.hasQuiz) Spacer(Modifier.height(8.dp))
                    }
                    Text(tip, color = WinColors.Text, fontSize = 20.sp, lineHeight = 29.sp)
                }
            }
            if (tip.isNotBlank() && lastError != null && !loading) {
                Spacer(Modifier.height(8.dp))
                Text(lastError.orEmpty(), color = WinColors.Error, fontSize = 13.sp)
            }
            if (active != null && active.nextUpdateEpochMs > System.currentTimeMillis() && !loading && !active.awaitingAnswer) {
                Spacer(Modifier.height(10.dp))
                CountdownText(active.nextUpdateEpochMs)
            }
            Spacer(Modifier.height(16.dp))

            if (active != null && active.hasTip && !active.awaitingAnswer) {
                val isSaved = saved.any { it.id == active.tipId }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (isSaved) "♥ " + stringResource(R.string.btn_save) else "♡ " + stringResource(R.string.btn_save), selected = isSaved) {
                        scope.launch { TipActions.toggleSave(app, active.id); WidgetUpdater.updateAll(app) }
                    }
                    Chip("👍", selected = active.feedback == 1) {
                        scope.launch { TipActions.feedback(app, active.id, true); WidgetUpdater.updateAll(app) }
                    }
                    Chip("👎", selected = active.feedback == -1) {
                        scope.launch { TipActions.feedback(app, active.id, false); WidgetUpdater.updateAll(app) }
                    }
                    Chip("↗ " + stringResource(R.string.btn_share), selected = false) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, TipActions.shareText(active))
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canRefresh = isPro || active?.shortInfo.isNullOrBlank() || lastError != null
                Button(
                    enabled = !loading && canRefresh && waitSec == 0,
                    onClick = { TipService.launchRefresh(app, notify = false) }
                ) {
                    Text(
                        when {
                            !canRefresh -> stringResource(R.string.btn_refresh_locked)
                            waitSec > 0 -> stringResource(R.string.btn_wait, waitSec)
                            else -> stringResource(R.string.btn_refresh)
                        }
                    )
                }
                if (active != null && active.hasTip && !active.awaitingAnswer) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(context, DetailActivity::class.java)
                                .putExtra(DetailActivity.EXTRA_TOPIC_ID, active.id)
                        )
                    }) { Text(stringResource(R.string.btn_explain)) }
                }
            }
        }

        // İstatistikler
        SectionCard(stringResource(R.string.sec_stats)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("🔥 ${stats.effectiveStreak()}", stringResource(R.string.stat_streak))
                StatCell("🏆 ${stats.best}", stringResource(R.string.stat_best))
                StatCell("${stats.accuracyPercent}%", stringResource(R.string.stat_accuracy))
                StatCell("${stats.answered}", stringResource(R.string.stat_answered))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (stats.freezeAvailable()) R.string.freeze_ready else R.string.freeze_used),
                color = WinColors.TextDim, fontSize = 13.sp
            )
        }

        // Rozetler
        val earned = BADGES.count { it.earned(stats) }
        SectionCard(stringResource(R.string.sec_badges, earned, BADGES.size)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BADGES.forEach { b ->
                    val on = b.earned(stats)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(76.dp).alpha(if (on) 1f else 0.3f)
                    ) {
                        Text(b.emoji, fontSize = 28.sp)
                        Text(stringResource(b.nameRes), color = WinColors.Text, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
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
                            TipService.launchRefresh(app, notify = false, force = true)
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
                (listOf("system" to stringResource(R.string.lang_system)) + APP_LANGUAGES).forEach { (code, label) ->
                    Chip(label, selected = saved == code) {
                        if (saved != code) {
                            LocaleHelper.save(context, code)
                            (context as? Activity)?.recreate()
                        }
                    }
                }
            }
        }

        // Sessiz saatler
        SectionCard(stringResource(R.string.sec_quiet)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.quiet_desc), color = WinColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Switch(checked = quietOn, onCheckedChange = { scope.launch { prefs.setQuietEnabled(it) } })
            }
            if (quietOn) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.quiet_from), color = WinColors.TextDim, fontSize = 13.sp)
                    Chip("%02d:%02d".format(quietStart / 60, quietStart % 60), selected = false) {
                        TimePickerDialog(context, { _, h, m -> scope.launch { prefs.setQuietStartMin(h * 60 + m) } },
                            quietStart / 60, quietStart % 60, true).show()
                    }
                    Text(stringResource(R.string.quiet_to), color = WinColors.TextDim, fontSize = 13.sp)
                    Chip("%02d:%02d".format(quietEnd / 60, quietEnd % 60), selected = false) {
                        TimePickerDialog(context, { _, h, m -> scope.launch { prefs.setQuietEndMin(h * 60 + m) } },
                            quietEnd / 60, quietEnd % 60, true).show()
                    }
                }
            }
        }

        // Widget teması + şık sayısı
        SectionCard(stringResource(R.string.sec_theme)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetThemes.all.forEach { th ->
                    ThemeTile(
                        bg = Color(th.bg), button = Color(th.button), text = Color(th.text), accent = Color(th.accent),
                        label = stringResource(th.nameRes),
                        selected = themeId == th.id,
                        locked = th.pro && !isPro
                    ) {
                        scope.launch { prefs.setWidgetTheme(th.id); WidgetUpdater.updateAll(app) }
                    }
                }
            }
            if (!isPro) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.theme_pro_note), color = WinColors.TextDim, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.pref_choices), color = WinColors.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { n ->
                    val locked = n > 2 && !isPro
                    Chip(
                        label = "$n" + if (locked) " 🔒" else "",
                        selected = (if (isPro) optionCount else 2) == n,
                        enabled = !locked
                    ) { scope.launch { prefs.setOptionCount(n) } }
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
        Text(
            "Ping AI v${UpdateChecker.installedVersion(app)} · KCD",
            color = WinColors.TextDim.copy(alpha = 0.7f), fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = WinColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = WinColors.TextDim, fontSize = 12.sp)
    }
}
