package com.pingai.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingai.app.R
import com.pingai.app.ui.theme.WinColors

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "chip")
    Text(
        text = label,
        color = when {
            selected -> WinColors.Accent
            enabled -> WinColors.Text
            else -> WinColors.TextDim.copy(alpha = 0.5f)
        },
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) WinColors.Accent.copy(alpha = 0.16f) else WinColors.Card)
            .border(BorderStroke(1.dp, if (selected) WinColors.Accent else WinColors.Stroke), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WinColors.Card)
            .border(BorderStroke(1.dp, WinColors.Stroke), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WinColors.Text)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            number.toString(),
            color = WinColors.Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(WinColors.Accent.copy(alpha = 0.16f))
                .padding(top = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(text, color = WinColors.Text, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
    }
}

/** Kategori çipleri + serbest metin + "istediğin kadar ayrıntılı yaz" ipucu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(value: String, onValueChange: (String) -> Unit) {
    val cats = listOf(
        "📜" to R.string.cat_history,
        "⚖️" to R.string.cat_law,
        "✨" to R.string.cat_fun_facts,
        "🔬" to R.string.cat_science,
        "🌍" to R.string.cat_geography,
        "💻" to R.string.cat_technology,
        "🩺" to R.string.cat_health,
        "🗣️" to R.string.cat_languages,
        "⚓" to R.string.cat_maritime,
        "💼" to R.string.cat_business,
        "🚀" to R.string.cat_space,
        "🧠" to R.string.cat_psychology
    )
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            cats.forEach { (emoji, res) ->
                val name = stringResource(res)
                Chip(label = "$emoji $name", selected = value.trim() == name) { onValueChange(name) }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.topic_field_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.topic_hint_specific),
            color = WinColors.TextDim,
            fontSize = 13.sp,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------------------------------------------------------- Quiz bileşenleri

/** Cevap verildiğinde 1 → 0 sönen parlama değeri. İlk açılışta (zaten cevaplıysa) parlamaz. */
@Composable
fun rememberQuizFlash(topic: com.pingai.app.data.Topic?): androidx.compose.runtime.State<Float> {
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val answered = topic?.answeredIdx ?: -1
    var prev by androidx.compose.runtime.remember(topic?.tipId) {
        androidx.compose.runtime.mutableIntStateOf(answered)
    }
    androidx.compose.runtime.LaunchedEffect(topic?.tipId, answered) {
        if (prev < 0 && answered >= 0) {
            anim.snapTo(1f)
            anim.animateTo(0f, androidx.compose.animation.core.tween(1400))
        }
        prev = answered
    }
    return anim.asState()
}

val QuizGood = Color(0xFF2EA043)
val QuizBad = Color(0xFFE5484D)

@Composable
fun QuizOptions(options: List<String>, onAnswer: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            OptionButton(i, label) { onAnswer(i) }
        }
    }
}

/** Sırayla beliren, basınca hafifçe küçülen şık düğmesi. */
@Composable
private fun OptionButton(index: Int, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "press")
    var shown by remember(label) { mutableStateOf(false) }
    LaunchedEffect(label) { delay(70L * index); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(280), label = "in")
    val lift by animateFloatAsState(if (shown) 0f else 14f, tween(280), label = "lift")
    Text(
        text = label,
        color = WinColors.Text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha; translationY = lift }
            .clip(shape)
            .background(Color(0x33FFFFFF))
            .border(BorderStroke(1.dp, Color(0x44FFFFFF)), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    )
}

/** Yüklenirken metin satırlarını andıran, nabız gibi atan iskelet. */
@Composable
fun ShimmerLines() {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.18f, 0.5f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1f, 0.92f, 0.6f).forEach { w ->
            Box(
                Modifier.fillMaxWidth(w).height(16.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = a))
            )
        }
    }
}

/** Sayı değişince aşağıdan yukarı kayarak değişen sayaç. */
@Composable
fun AnimatedCounter(value: Int, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 15.sp) {
    AnimatedContent(
        targetState = value,
        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
        label = "counter"
    ) { v -> Text(v.toString(), color = color, fontWeight = FontWeight.SemiBold, fontSize = fontSize) }
}

/** Günlük hedef halkası: bugün cevaplanan soru / hedef. */
@Composable
fun GoalRing(done: Int, goal: Int) {
    val prog by animateFloatAsState((done.toFloat() / goal).coerceIn(0f, 1f), tween(700), label = "goal")
    val complete = done >= goal
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val inset = 2.dp.toPx()
            val sz = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Color(0x33FFFFFF), 0f, 360f, false, tl, sz, style = st)
            drawArc(if (complete) QuizGood else WinColors.Accent, -90f, 360f * prog, false, tl, sz, style = st)
        }
        Text(if (complete) "✓" else done.toString(), color = WinColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ResultLine(topic: com.pingai.app.data.Topic) {
    if (!topic.hasQuiz || topic.answeredIdx < 0) return
    val ok = topic.answeredIdx == topic.correctIdx
    Text(
        text = if (ok) stringResource(R.string.quiz_correct)
        else stringResource(R.string.quiz_wrong, topic.options.getOrNull(topic.correctIdx).orEmpty()),
        color = if (ok) QuizGood else QuizBad,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

/** Kenarlardan ortaya doğru saydamlaşan parlama (flash: 1 → 0). */
fun Modifier.quizGlow(flash: Float, color: Color): Modifier = this.drawBehind {
    if (flash > 0.01f) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                0f to Color.Transparent,
                0.45f to Color.Transparent,
                1f to color.copy(alpha = (flash * 0.9f).coerceIn(0f, 1f)),
                radius = size.maxDimension * 0.62f
            )
        )
    }
}

/** Widget teması için küçük örnek kart: arka plan, vurgu rengi, metin satırları ve şık düğmeleri. */
@Composable
fun ThemeTile(
    bg: Color, button: Color, text: Color, accent: Color,
    label: String, selected: Boolean, locked: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = !locked, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .size(width = 104.dp, height = 78.dp)
                .clip(shape)
                .background(bg)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) WinColors.Accent else WinColors.Stroke), shape)
                .padding(9.dp)
        ) {
            Box(Modifier.size(width = 34.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(text.copy(alpha = 0.75f)))
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(text.copy(alpha = 0.45f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(11.dp).clip(RoundedCornerShape(5.dp)).background(button))
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(11.dp).clip(RoundedCornerShape(5.dp)).background(button))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label + if (locked) " 🔒" else "",
            color = if (selected) WinColors.Accent else if (locked) WinColors.TextDim.copy(alpha = 0.6f) else WinColors.Text,
            fontSize = 13.sp
        )
    }
}

/**
 * Soru gelene kadar gösterilen esprili mesajlar: her biri 2 sn kalır, sonuna 0,33 sn'de bir nokta eklenir.
 * 20 sn'yi aşarsa sırayla "easter egg" mesajlarına geçer.
 */
@Composable
fun LoadingMessage(color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    val msgs = androidx.compose.ui.res.stringArrayResource(R.array.loading_msgs)
    val eggs = androidx.compose.ui.res.stringArrayResource(R.array.loading_eggs)
    val order = remember(msgs.size) { msgs.indices.shuffled() }
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtime()
        while (true) {
            delay(110)
            elapsed = android.os.SystemClock.elapsedRealtime() - start
        }
    }
    val base = if (elapsed >= 20_000L) {
        eggs[((elapsed - 20_000L) / 2_000L).toInt().coerceAtMost(eggs.size - 1)]
    } else {
        msgs[order[(elapsed / 2_000L).toInt() % order.size]]
    }
    val dots = ((elapsed % 2_000L) / 333L % 4L).toInt()
    Text(base + ".".repeat(dots), color = color, fontSize = fontSize)
}
