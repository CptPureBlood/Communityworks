package com.pingai.app.ui.detail

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.pingai.app.R
import com.pingai.app.ads.AdManager
import com.pingai.app.core.LocaleHelper
import com.pingai.app.core.TipActions
import com.pingai.app.core.TipService
import com.pingai.app.data.Prefs
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.network.AiRepository
import com.pingai.app.ui.common.QuizBad
import com.pingai.app.ui.common.QuizGood
import com.pingai.app.ui.common.QuizOptions
import com.pingai.app.ui.common.ResultLine
import com.pingai.app.ui.common.rememberQuizFlash
import com.pingai.app.ui.common.quizGlow
import com.pingai.app.ui.theme.PingAiTheme
import com.pingai.app.ui.theme.WinColors
import com.pingai.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TOPIC_ID = "extra_topic_id"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val topicId = intent.getStringExtra(EXTRA_TOPIC_ID)
        setContent {
            PingAiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = WinColors.Bg) { DetailScreen(topicId) }
            }
        }
    }
}

@Composable
private fun DetailScreen(topicId: String?) {
    val context = LocalContext.current
    val app = context.applicationContext
    val prefs = remember { Prefs(app) }
    val scope = rememberCoroutineScope()

    val topics by prefs.topics.collectAsState(initial = emptyList())
    val topic = topics.firstOrNull { it.id == topicId } ?: topics.firstOrNull()
    val isPro by prefs.isPro.collectAsState(initial = true)

    var detail by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showAd by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    val flash by rememberQuizFlash(topic)
    val flashColor = if (topic != null && topic.answeredIdx == topic.correctIdx) QuizGood else QuizBad

    // Açıklama yalnızca soru cevaplandıktan sonra (ya da soru yoksa hemen) istenir.
    val ready = topic != null && topic.hasTip && !topic.awaitingAnswer
    LaunchedEffect(topic?.tipId, ready, attempt) {
        val t = topic ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        loading = true
        error = null
        detail = ""
        val provider = prefs.provider.first()
        val key = withContext(Dispatchers.IO) { SecureKeyStore.get(app).getKey(provider) }
        if (key.isNullOrBlank()) {
            error = context.getString(R.string.err_no_key, provider.displayLabel)
            loading = false
            return@LaunchedEffect
        }
        val lang = LocaleHelper.resolveContentLanguage(prefs.contentLanguage.first())
        try {
            val result = withTimeout(40_000) {
                AiRepository.detailed(provider, key, t.name, t.shortInfo, lang)
            }
            result.onSuccess { detail = it }.onFailure { error = TipService.errorText(context, it) }
        } catch (e: Exception) {
            error = TipService.errorText(context, e)
        }
        loading = false
    }

    LaunchedEffect(isPro) {
        if (!isPro && com.pingai.app.ads.AdGate.adsOn(context)) {
            delay(6000)
            showAd = com.pingai.app.ads.AdGate.adsOn(context)
        }
    }
    LaunchedEffect(topic?.id) {
        topic?.let { com.pingai.app.analytics.Track.topicEvent(app, "detail_opened", it.name) }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().quizGlow(flash, flashColor)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(topic?.name.orEmpty(), color = WinColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

            if (topic != null && topic.awaitingAnswer) {
                Text(topic.question, color = WinColors.Text, fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.quiz_hint), color = WinColors.TextDim, fontSize = 13.sp)
                QuizOptions(topic.options) { i ->
                    scope.launch {
                        val ok = TipActions.answer(app, topic.id, i)
                        if (ok != null) WidgetUpdater.playFlash(app, ok)
                    }
                }
            } else if (topic != null) {
                ResultLine(topic)
                if (topic.shortInfo.isNotBlank()) {
                    Text(
                        "“${topic.shortInfo}”",
                        color = WinColors.Text, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(WinColors.Card)
                            .padding(16.dp)
                    )
                }
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = WinColors.Accent, strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.detail_loading),
                            color = WinColors.TextDim,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    error != null -> {
                        Text(error.orEmpty(), color = WinColors.Error, fontSize = 16.sp)
                        Button(onClick = { attempt++ }) { Text(stringResource(R.string.detail_retry)) }
                    }
                    else -> Text(detail, color = WinColors.Text, fontSize = 17.sp, lineHeight = 26.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showAd) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdManager.BANNER_AD_UNIT_ID
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}
