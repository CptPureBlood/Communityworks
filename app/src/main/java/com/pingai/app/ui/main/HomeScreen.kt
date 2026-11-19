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
import com.pingai.app.data.Daily
import com.pingai.app.data.Limits
import com.pingai.app.ads.AdGate
import com.pingai.app.ads.RewardResult
import com.pingai.app.analytics.Track
import com.pingai.app.ui.common.AnimatedCounter
import com.pingai.app.ui.common.GoalRing
import com.pingai.app.ui.common.PingBackground
import com.pingai.app.ui.common.ShimmerLines
import com.pingai.app.ui.common.LoadingMessage
import androidx.compose.animation.animateContentSize
import com.pingai.app.data.Prefs
import com.pingai.app.data.RefreshInterval
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.data.Topic
import com.pingai.app.core.TipActions
import com.pingai.app.share.ShareCard
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

@Composable
private fun NextClock(targetMs: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(targetMs) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val left = ((targetMs - now) / 1000).coerceAtLeast(0)
    val text = if (targetMs <= now) "--:--" else "%02d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60)
    Text(text, color = WinColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun GoalCard(done: Int, goal: Int, nextMs: Long) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WinColors.Card.copy(alpha = 0.88f))
            .border(1.dp, WinColors.Stroke, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        GoalRing(done, goal)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.goal_title, done.coerceAtMost(goal), goal),
            color = WinColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.goal_next), color = WinColors.TextDim, fontSize = 11.sp)
            NextClock(nextMs)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSaved: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        c as Activity
    }
    val prefs = remember { Prefs(app) }
    val scope = rememberCoroutineScope()
    val daily by prefs.daily.collectAsState(initial = Daily())

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

    var showTopics by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    PingBackground(active?.tipId, Modifier.fillMaxSize())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

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

        // Konu çipi + seriler
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip((active?.name ?: stringResource(R.string.topic_pick)) + " ▾", selected = true, modifier = Modifier.weight(1f, fill = false)) {
                showTopics = true
            }
            Spacer(Modifier.weight(1f))
            Text("🔥", fontSize = 15.sp)
            Spacer(Modifier.width(3.dp))
            AnimatedCounter(stats.effectiveStreak(), WinColors.Text)
            Spacer(Modifier.width(10.dp))
            Text("✅", fontSize = 15.sp)
            Spacer(Modifier.width(3.dp))
            AnimatedCounter(stats.correctStreak, WinColors.Text)
        }

        GoalCard(daily.answered, Limits.DAILY_GOAL, active?.nextUpdateEpochMs ?: 0L)

        // Kahraman kart
        val heroShape = RoundedCornerShape(22.dp)
        val flash by rememberQuizFlash(active)
        val flashColor = if (active != null && active.answeredIdx == active.correctIdx) QuizGood else QuizBad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clip(heroShape)
                .background(Brush.linearGradient(listOf(WinColors.HeroStart.copy(alpha = 0.92f), WinColors.HeroEnd.copy(alpha = 0.92f))))
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
                loading -> Column {
                    ShimmerLines()
                    Spacer(Modifier.height(12.dp))
                    LoadingMessage(WinColors.TextDim, 14.sp)
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
                        AdGate.showThen(activity, "share", Track.category(app, active.name)) {
                            scope.launch { ShareCard.share(app, active.name, active.shortInfo) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val limit = if (isPro) Limits.PRO_DAILY else Limits.FREE_DAILY
            val limitReached = daily.used >= limit
            val freeNeedsCredit = !isPro && !active?.shortInfo.isNullOrBlank() && lastError == null
            val adsLeft = Limits.REWARD_ADS_PER_DAY - daily.adsWatched
            val catName = Track.category(app, active?.name.orEmpty())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    limitReached -> Button(enabled = false, onClick = {}) {
                        Text(stringResource(R.string.btn_limit_reached, limit))
                    }
                    freeNeedsCredit && daily.credits <= 0 -> Button(
                        enabled = !loading && adsLeft > 0,
                        onClick = {
                            AdGate.showRewarded(activity, catName) { r ->
                                when (r) {
                                    RewardResult.GRANTED -> scope.launch {
                                        if (prefs.grantRewardAd()) {
                                            Track.event(app, "ad_rewarded", "category" to catName)
                                            Toast.makeText(activity, R.string.reward_granted, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    RewardResult.NOT_READY -> Toast.makeText(activity, R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
                                    RewardResult.DISMISSED -> {}
                                }
                            }
                        }
                    ) {
                        Text(if (adsLeft > 0) stringResource(R.string.btn_watch_ad, Limits.CREDITS_PER_AD) else stringResource(R.string.btn_no_ad_left))
                    }
                    else -> Button(
                        enabled = !loading && waitSec == 0,
                        onClick = {
                            scope.launch {
                                if (!freeNeedsCredit || prefs.useCredit()) TipService.launchRefresh(app, notify = false)
                            }
                        }
                    ) {
                        Text(if (waitSec > 0) stringResource(R.string.btn_wait, waitSec) else stringResource(R.string.btn_refresh))
                    }
                }
                if (active != null && active.hasTip && !active.awaitingAnswer) {
                    OutlinedButton(onClick = {
                        AdGate.showThen(activity, "detail", catName) {
                            context.startActivity(
                                Intent(context, DetailActivity::class.java)
                                    .putExtra(DetailActivity.EXTRA_TOPIC_ID, active.id)
                            )
                        }
                    }) { Text(stringResource(R.string.btn_explain)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.daily_status, daily.used, limit) +
                    if (!isPro) "  ·  " + stringResource(R.string.credits_status, daily.credits) else "",
                color = WinColors.TextDim, fontSize = 12.sp
            )
        }

    }
    }

    if (showTopics) {
        ModalBottomSheet(
            onDismissRequest = { showTopics = false; editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = WinColors.Card
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
        Text(stringResource(R.string.sec_topics, topics.size, maxTopics), color = WinColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        run {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t ->
                    Chip(t.name, selected = t.id == active?.id) {
                        showTopics = false
                        if (t.id == active?.id) return@Chip
                        AdGate.showThen(activity, "topic_change", Track.category(app, t.name)) {
                            scope.launch {
                                prefs.setActiveTopicId(t.id)
                                WidgetUpdater.updateAll(app)
                            }
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
                        showTopics = false
                        Track.topicEvent(app, "topic_selected", name, "mode" to (mode ?: ""))
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

            }
        }
    }
}

