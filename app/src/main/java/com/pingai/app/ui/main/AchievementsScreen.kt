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
fun AchievementsScreen(onBack: () -> Unit = {}) {
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
            Text(stringResource(R.string.ach_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WinColors.Text)
        }

        SectionCard(stringResource(R.string.sec_stats)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("🔥 ${stats.effectiveStreak()}", stringResource(R.string.stat_day_streak))
                StatCell("✅ ${stats.correctStreak}", stringResource(R.string.stat_correct_streak))
                StatCell("${stats.accuracyPercent}%", stringResource(R.string.stat_accuracy))
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("🏆 ${stats.best}", stringResource(R.string.stat_best_day))
                StatCell("💥 ${stats.bestCorrect}", stringResource(R.string.stat_best_correct))
                StatCell("${stats.answered}", stringResource(R.string.stat_answered))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (stats.freezeAvailable()) R.string.freeze_ready else R.string.freeze_used),
                color = WinColors.TextDim, fontSize = 13.sp
            )
        }

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
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = WinColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = WinColors.TextDim, fontSize = 12.sp)
    }
}
