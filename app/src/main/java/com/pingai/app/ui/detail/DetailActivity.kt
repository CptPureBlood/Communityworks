package com.pingai.app.ui.detail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.pingai.app.R
import com.pingai.app.ads.AdManager
import com.pingai.app.data.Prefs
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.network.AiRepository
import com.pingai.app.ui.theme.PingAiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Locale

class DetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TOPIC_ID = "extra_topic_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val topicId = intent.getStringExtra(EXTRA_TOPIC_ID)
        setContent {
            PingAiTheme {
                Surface(modifier = Modifier.fillMaxSize()) { DetailScreen(topicId) }
            }
        }
    }
}

@Composable
private fun DetailScreen(topicId: String?) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val keyStore = remember { SecureKeyStore(context) }
    val repository = remember { AiRepository() }

    var topicName by remember { mutableStateOf("") }
    var detailText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isPro by remember { mutableStateOf(true) }
    var showAd by remember { mutableStateOf(false) }

    LaunchedEffect(topicId) {
        isPro = prefs.isPro.first()
        val topics = prefs.getTopicsOnce()
        val topic = topics.firstOrNull { it.id == topicId } ?: topics.firstOrNull()
        if (topic != null) {
            topicName = topic.name
            val provider = prefs.provider.first()
            val apiKey = keyStore.getKey(provider)
            val langRaw = prefs.contentLanguage.first()
            val lang = if (langRaw == "system") Locale.getDefault().language else langRaw
            detailText = if (apiKey.isNullOrBlank()) {
                context.getString(R.string.widget_error)
            } else {
                repository.fetchDetailedExplanation(provider, apiKey, topic.name, topic.shortInfo, lang)
                    .getOrElse { context.getString(R.string.widget_error) }
            }
        }
        isLoading = false
    }

    // Free kullanıcıda ekran açıldıktan 6 sn sonra alt banner reklamı.
    LaunchedEffect(isPro) {
        if (!isPro) {
            delay(6000)
            showAd = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(topicName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(context.getString(R.string.detail_loading))
                }
            } else {
                Text(detailText, style = MaterialTheme.typography.bodyLarge)
            }
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
