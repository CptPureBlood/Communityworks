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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.imePadding
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
import com.pingai.app.ui.common.BADGES
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
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
            Chip("← " + stringResource(R.string.btn_back), selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
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
                    Chip(label, selected = effective == code) { scope.launch { prefs.setContentLanguage(code); com.pingai.app.analytics.Track.refreshLanguages(app) } }
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
                            com.pingai.app.analytics.Track.setUiLang(app, code)
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

        // Gizlilik ve reklamlar
        SectionCard(stringResource(R.string.sec_privacy)) {
            var analyticsOn by remember { mutableStateOf(com.pingai.app.analytics.Track.isOn(app)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_analytics), color = WinColors.Text)
                    Text(stringResource(R.string.pref_analytics_desc), color = WinColors.TextDim, fontSize = 12.sp)
                }
                Switch(checked = analyticsOn, onCheckedChange = {
                    analyticsOn = it
                    com.pingai.app.analytics.Track.setOn(app, it)
                })
            }
            if (!isPro) {
                Spacer(Modifier.height(14.dp))
                var adsOn by remember { mutableStateOf(com.pingai.app.ads.AdGate.userSwitch(app)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pref_test_ads), color = WinColors.Text)
                        Text(stringResource(R.string.pref_test_ads_desc), color = WinColors.TextDim, fontSize = 12.sp)
                    }
                    Switch(checked = adsOn, onCheckedChange = {
                        adsOn = it
                        com.pingai.app.ads.AdGate.setUserSwitch(app, it)
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            var showTerms by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showTerms = true }) { Text(stringResource(R.string.terms_open)) }
            if (showTerms) TermsDialog { showTerms = false }
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
