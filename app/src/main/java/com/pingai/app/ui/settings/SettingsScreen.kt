package com.pingai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingai.app.R
import com.pingai.app.data.*
import com.pingai.app.ui.onboarding.CONTENT_LANGUAGES
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.launch
import java.util.UUID

private const val MAX_TOPICS_FREE = 1
private const val MAX_TOPICS_PRO = 3
// Basit yerel Pro açma kodu (gerçek ödeme sistemi değil). İstersen değiştir.
private const val PRO_UNLOCK_CODE = "PINGAI-PRO"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val keyStore = remember { SecureKeyStore(context) }
    val scope = rememberCoroutineScope()
    fun s(id: Int) = context.getString(id)

    val provider by prefs.provider.collectAsState(initial = AiProvider.GEMINI)
    val topics by prefs.topics.collectAsState(initial = emptyList())
    val activeTopicId by prefs.activeTopicId.collectAsState(initial = null)
    val intervalMin by prefs.refreshIntervalMinutes.collectAsState(initial = RefreshInterval.H12.minutes)
    val notificationsEnabled by prefs.notificationsEnabled.collectAsState(initial = true)
    val isPro by prefs.isPro.collectAsState(initial = false)
    val contentLanguage by prefs.contentLanguage.collectAsState(initial = "system")

    var newTopic by remember { mutableStateOf("") }
    var proCode by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    LaunchedEffect(provider) { apiKeyInput = keyStore.getKey(provider) ?: "" }

    val maxTopics = if (isPro) MAX_TOPICS_PRO else MAX_TOPICS_FREE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(s(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Section(s(R.string.settings_provider))
        AiProvider.entries.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = provider == p,
                    enabled = isPro || p == AiProvider.GEMINI,
                    onClick = { scope.launch { prefs.setProvider(p) } }
                )
                Text(p.displayLabel + if (!isPro && p != AiProvider.GEMINI) " (Pro)" else "")
            }
        }
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("${provider.displayLabel} API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { keyStore.setKey(provider, apiKeyInput) },
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp)
        ) { Text("OK") }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Section("${s(R.string.settings_topics)} (${topics.size}/$maxTopics)")
        topics.forEach { topic ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = topic.id == activeTopicId,
                    onClick = { scope.launch { prefs.setActiveTopicId(topic.id) } }
                )
                Text(topic.name, modifier = Modifier.weight(1f))
                IconButton(
                    enabled = topics.size > 1,
                    onClick = {
                        scope.launch {
                            prefs.removeTopic(topic.id)
                            if (topic.id == activeTopicId) {
                                prefs.getTopicsOnce().firstOrNull()?.let { prefs.setActiveTopicId(it.id) }
                            }
                        }
                    }
                ) { Icon(Icons.Default.Delete, contentDescription = null) }
            }
        }
        if (topics.size < maxTopics) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTopic,
                    onValueChange = { newTopic = it },
                    label = { Text(s(R.string.settings_add_topic)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (newTopic.isNotBlank()) {
                        scope.launch {
                            val t = Topic(id = UUID.randomUUID().toString(), name = newTopic.trim())
                            prefs.upsertTopic(t)
                            if (activeTopicId == null) prefs.setActiveTopicId(t.id)
                            newTopic = ""
                            WorkScheduler.runNow(context)
                        }
                    }
                }) { Text("+") }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Section(s(R.string.settings_refresh_interval))
        listOf(
            RefreshInterval.H12 to R.string.interval_12h,
            RefreshInterval.H6 to R.string.interval_6h,
            RefreshInterval.H4 to R.string.interval_4h,
            RefreshInterval.H2 to R.string.interval_2h,
            RefreshInterval.H1 to R.string.interval_1h,
            RefreshInterval.M30 to R.string.interval_30m,
            RefreshInterval.M15 to R.string.interval_15m,
            RefreshInterval.M10 to R.string.interval_10m
        ).forEach { (interval, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = intervalMin == interval.minutes,
                    enabled = isPro || !interval.isPro,
                    onClick = {
                        scope.launch {
                            prefs.setRefreshIntervalMinutes(interval.minutes)
                            WorkScheduler.scheduleNext(context, interval.minutes)
                        }
                    }
                )
                Text(s(label) + if (!isPro && interval.isPro) " 🔒" else "")
            }
        }
        if (isPro) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { WorkScheduler.runNow(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(s(R.string.settings_manual_refresh))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(s(R.string.settings_notifications), modifier = Modifier.weight(1f))
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { scope.launch { prefs.setNotificationsEnabled(it) } }
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Section(s(R.string.settings_content_language))
        CONTENT_LANGUAGES.forEach { (code, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = contentLanguage == code,
                    onClick = { scope.launch { prefs.setContentLanguage(code) } }
                )
                Text(label)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        if (isPro) {
            Text("✓ " + s(R.string.settings_pro_active), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else {
            Section(s(R.string.settings_pro))
            Text(s(R.string.pro_upgrade_body))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = proCode,
                    onValueChange = { proCode = it },
                    label = { Text(s(R.string.settings_pro_code_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (proCode.trim() == PRO_UNLOCK_CODE) scope.launch { prefs.setPro(true) }
                }) { Text("OK") }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}
