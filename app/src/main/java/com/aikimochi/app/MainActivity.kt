package com.aikimochi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DarkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiNoKimochiTheme {
                TransformerLabApp()
            }
        }
    }
}

private val AppColors: DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AD8FF),
    secondary = Color(0xFFB8A7FF),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF070A12),
    surface = Color(0xFF101522),
    surfaceVariant = Color(0xFF171E2E),
    onBackground = Color(0xFFF2F6FF),
    onSurface = Color(0xFFF2F6FF)
)

@Composable
private fun AiNoKimochiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}

private enum class Stage(val title: String, val short: String) {
    TEXT("Text", "文章"),
    TOKEN("Tokenization", "Token"),
    EMBEDDING("Embedding", "Vector"),
    POSITION("Position", "位置"),
    QKV("Q / K / V", "QKV"),
    ATTENTION("Attention", "Attention"),
    FFN("Feed Forward Network", "FFN"),
    LAYERS("Transformer Layers", "Layers"),
    LOGITS("Logits", "Logits"),
    SAMPLING("Sampling", "Sampling")
}

private data class ToyToken(
    val text: String,
    val id: Int,
    val index: Int,
    val vector: List<Float>
)

private data class Candidate(val text: String, val logit: Float)

private object ToyEngine {
    private val dictionary = listOf(
        "ソファ", "寝て", "いる", "文章", "生成", "する", "猫", "犬", "虎", "車", "AI",
        "は", "が", "で", "を", "に", "と"
    ).sortedByDescending { it.length }

    fun tokenize(text: String): List<ToyToken> {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < text.length && parts.size < 16) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }
            val match = dictionary.firstOrNull { text.startsWith(it, i) }
            if (match != null) {
                parts += match
                i += match.length
                continue
            }
            val c = text[i]
            if (c.code < 128 && c.isLetterOrDigit()) {
                var j = i + 1
                while (j < text.length && text[j].code < 128 && text[j].isLetterOrDigit()) j++
                parts += text.substring(i, j)
                i = j
            } else {
                parts += c.toString()
                i++
            }
        }
        if (parts.isEmpty()) parts += "…"
        return parts.mapIndexed { index, part ->
            val seed = part.hashCode()
            val id = (seed.toLong().let { if (it < 0) -it else it } % 50000L).toInt() + 100
            val v = List(3) { dim ->
                val a = (seed * (dim + 3) * 0.00071) + index * 0.37
                sin(a).toFloat()
            }
            ToyToken(part, id, index, v)
        }
    }

    fun attention(tokens: List<ToyToken>, query: Int, head: Int): List<Float> {
        if (tokens.isEmpty()) return emptyList()
        val raw = tokens.mapIndexed { index, token ->
            val distance = 1f / (1f + kotlin.math.abs(index - query))
            val lexical = semanticBoost(tokens[query].text, token.text, head)
            val noise = ((token.id % (17 + head * 3)) / 40f)
            distance + lexical + noise
        }
        val exps = raw.map { exp(it.toDouble()).toFloat() }
        val sum = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / sum }
    }

    private fun semanticBoost(a: String, b: String, head: Int): Float {
        val pair = setOf(a, b)
        return when {
            pair.contains("猫") && (pair.contains("寝て") || pair.contains("いる")) -> if (head == 0) 1.5f else .6f
            pair.contains("ソファ") && pair.contains("で") -> if (head == 1) 1.4f else .4f
            pair.contains("猫") && pair.contains("ソファ") -> .8f
            a == b -> .45f
            else -> 0f
        }
    }

    fun candidates(): List<Candidate> = listOf(
        Candidate("寝て", 2.4f),
        Candidate("座って", 1.75f),
        Candidate("丸く", 1.4f),
        Candidate("遊んで", 1.05f),
        Candidate("静かに", .8f)
    )

    fun probabilities(temp: Float): List<Pair<Candidate, Float>> {
        val safeTemp = temp.coerceIn(.1f, 2f)
        val c = candidates()
        val exps = c.map { exp((it.logit / safeTemp).toDouble()).toFloat() }
        val sum = exps.sum()
        return c.zip(exps.map { it / sum })
    }
}

@Composable
private fun TransformerLabApp() {
    var input by remember { mutableStateOf("猫はソファで寝ている") }
    var analyzedText by remember { mutableStateOf(input) }
    var stage by remember { mutableStateOf(Stage.TEXT) }
    var advanced by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(1f) }
    var selectedToken by remember { mutableIntStateOf(0) }
    var head by remember { mutableIntStateOf(0) }
    var sampled by remember { mutableStateOf<String?>(null) }

    val tokens = remember(analyzedText) { ToyEngine.tokenize(analyzedText) }
    if (selectedToken > tokens.lastIndex) selectedToken = 0

    LaunchedEffect(isPlaying, stage) {
        if (!isPlaying) return@LaunchedEffect
        delay(1500)
        if (stage.ordinal < Stage.entries.lastIndex) {
            stage = Stage.entries[stage.ordinal + 1]
        } else {
            isPlaying = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080B14), Color(0xFF0A1020), Color(0xFF070A12))
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Header(advanced = advanced, onAdvancedChange = { advanced = it })
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("AIに入れる文章") },
            singleLine = true,
            trailingIcon = {
                Text(
                    "解析",
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            analyzedText = input.ifBlank { "猫はソファで寝ている" }
                            stage = Stage.TOKEN
                            sampled = null
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        )

        Spacer(Modifier.height(12.dp))
        StageRail(stage = stage, onStage = { stage = it; isPlaying = false })
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            AnimatedContent(
                targetState = stage,
                label = "stage",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) { current ->
                StageContent(
                    stage = current,
                    text = analyzedText,
                    tokens = tokens,
                    advanced = advanced,
                    selectedToken = selectedToken,
                    onSelectToken = { selectedToken = it },
                    head = head,
                    onHead = { head = it },
                    temperature = temperature,
                    onTemperature = { temperature = it },
                    sampled = sampled,
                    onSample = {
                        val probs = ToyEngine.probabilities(temperature)
                        val r = Random.nextFloat()
                        var acc = 0f
                        sampled = probs.firstOrNull { (_, p) ->
                            acc += p
                            r <= acc
                        }?.first?.text ?: probs.last().first.text
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PlayerControls(
            stage = stage,
            isPlaying = isPlaying,
            onPrev = {
                isPlaying = false
                if (stage.ordinal > 0) stage = Stage.entries[stage.ordinal - 1]
            },
            onPlay = { isPlaying = !isPlaying },
            onNext = {
                isPlaying = false
                if (stage.ordinal < Stage.entries.lastIndex) stage = Stage.entries[stage.ordinal + 1]
            }
        )
    }
}

@Composable
private fun Header(advanced: Boolean, onAdvancedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("AIのキモチ", fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("Transformerを、読まずに触って理解する。", color = Color(0xFF9BA7BC), fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (advanced) "ADVANCED" else "BEGINNER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Switch(checked = advanced, onCheckedChange = onAdvancedChange)
        }
    }
}

@Composable
private fun StageRail(stage: Stage, onStage: (Stage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Stage.entries.forEachIndexed { index, item ->
            val active = item == stage
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color(0xFF151B29))
                        .clickable { onStage(item) }
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text(
                        "${index + 1}. ${item.short}",
                        color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF9CA8BC),
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun StageContent(
    stage: Stage,
    text: String,
    tokens: List<ToyToken>,
    advanced: Boolean,
    selectedToken: Int,
    onSelectToken: (Int) -> Unit,
    head: Int,
    onHead: (Int) -> Unit,
    temperature: Float,
    onTemperature: (Float) -> Unit,
    sampled: String?,
    onSample: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(stage.title, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(stageDescription(stage, advanced), color = Color(0xFFA6B2C6), fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        when (stage) {
            Stage.TEXT -> TextStage(text)
            Stage.TOKEN -> TokenStage(tokens, advanced)
            Stage.EMBEDDING -> EmbeddingStage(tokens, advanced)
            Stage.POSITION -> PositionStage(tokens, advanced)
            Stage.QKV -> QkvStage(tokens, selectedToken, onSelectToken, advanced)
            Stage.ATTENTION -> AttentionStage(tokens, selectedToken, onSelectToken, head, onHead, advanced)
            Stage.FFN -> FfnStage(tokens, selectedToken, onSelectToken, advanced)
            Stage.LAYERS -> LayerStage(tokens)
            Stage.LOGITS -> LogitsStage(temperature, onTemperature, advanced)
            Stage.SAMPLING -> SamplingStage(temperature, sampled, onSample)
        }
    }
}

private fun stageDescription(stage: Stage, advanced: Boolean): String = when (stage) {
    Stage.TEXT -> "まず文章をモデルへ投入します。ここから先は、文字ではなく数値の世界です。"
    Stage.TOKEN -> if (advanced) "教育用Toy Tokenizerで文字列をToken IDへ変換します。" else "文章をAIが扱いやすい小さな単位に分けます。"
    Stage.EMBEDDING -> if (advanced) "Token IDを埋め込みベクトルへ写像。ここでは3次元へ縮約表示します。" else "Tokenを『意味の位置』を持つ数字の点へ変えます。"
    Stage.POSITION -> "同じTokenでも、文章中の順番を加えると表現が変わります。"
    Stage.QKV -> if (advanced) "XWq / XWk / XWv の3射影を観察します。" else "同じTokenから『探す・見つけられる・渡す情報』の3役を作ります。"
    Stage.ATTENTION -> if (advanced) "Softmax(QKᵀ/√d)V の重みを線の太さで可視化します。" else "選んだTokenが、ほかのTokenをどれだけ見ているかを表示します。"
    Stage.FFN -> "Attentionで集めた情報を、各Tokenごとにさらに加工します。"
    Stage.LAYERS -> "AttentionとFFNを何層も通り、Tokenの内部表現が少しずつ変わります。"
    Stage.LOGITS -> "次に来そうなTokenへ点数を付け、確率分布へ変換します。"
    Stage.SAMPLING -> "確率分布から実際の次Tokenを1つ選びます。文章生成はこのループの繰り返しです。"
}

@Composable
private fun TextStage(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Text("“$text”", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Text("↓", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        InfoPill("次のステップで文章がTokenに分かれます")
    }
}

@Composable
private fun TokenStage(tokens: List<ToyToken>, advanced: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tokens.forEach { token ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF171E2E))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Text("${token.index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text(token.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (advanced) Text("ID ${token.id}", color = Color(0xFF8F9BB0), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmbeddingStage(tokens: List<ToyToken>, advanced: Boolean) {
    Column {
        EmbeddingCanvas(tokens)
        Spacer(Modifier.height(12.dp))
        if (advanced && tokens.isNotEmpty()) {
            val t = tokens.first()
            Text("${t.text} → [${t.vector.joinToString { "%.2f".format(it) }}]", color = Color(0xFFAEB9CA), fontSize = 12.sp)
        } else {
            InfoPill("近い点ほど、Toy Model上では似た表現として扱われます")
        }
    }
}

@Composable
private fun EmbeddingCanvas(tokens: List<ToyToken>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0B101C))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF263148), radius = size.minDimension * .42f, center = center, style = Stroke(width = 1f))
            drawCircle(Color(0xFF1A2437), radius = size.minDimension * .25f, center = center, style = Stroke(width = 1f))
            tokens.forEachIndexed { i, token ->
                val x = ((token.vector[0] + 1f) / 2f) * (size.width * .78f) + size.width * .11f
                val y = ((token.vector[1] + 1f) / 2f) * (size.height * .72f) + size.height * .14f
                val radius = 10f + (token.vector[2] + 1f) * 5f
                drawCircle(Color(0xFF8AD8FF).copy(alpha = .15f), radius = radius * 2.2f, center = Offset(x, y))
                drawCircle(Color(0xFF8AD8FF), radius = radius, center = Offset(x, y))
            }
        }
        tokens.take(6).forEachIndexed { i, token ->
            Text(
                token.text,
                modifier = Modifier.padding(start = 10.dp, top = (10 + i * 22).dp),
                color = Color(0xFFD7E7F7),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PositionStage(tokens: List<ToyToken>, advanced: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tokens.forEachIndexed { index, token ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(token.text, modifier = Modifier.width(64.dp), fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .weight(1f)
                        .clip(CircleShape)
                        .background(Color(0xFF1A2334))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((index + 1f) / max(tokens.size, 1))
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(if (advanced) "pos=$index" else "${index + 1}番目", color = Color(0xFF9DAABD), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QkvStage(tokens: List<ToyToken>, selected: Int, onSelect: (Int) -> Unit, advanced: Boolean) {
    TokenSelector(tokens, selected, onSelect)
    Spacer(Modifier.height(18.dp))
    val token = tokens.getOrNull(selected) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QkvCard("Q", "Query", "何を探す？", Color(0xFF73D7FF), token, .91f, advanced, Modifier.weight(1f))
        QkvCard("K", "Key", "何を持つ？", Color(0xFFB29BFF), token, .72f, advanced, Modifier.weight(1f))
        QkvCard("V", "Value", "何を渡す？", Color(0xFFFFB26B), token, .58f, advanced, Modifier.weight(1f))
    }
}

@Composable
private fun QkvCard(label: String, name: String, hint: String, color: Color, token: ToyToken, scale: Float, advanced: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = .11f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(name, fontSize = 11.sp, color = Color(0xFFB7C2D3))
        Spacer(Modifier.height(10.dp))
        Text(hint, fontSize = 11.sp, textAlign = TextAlign.Center)
        if (advanced) {
            Spacer(Modifier.height(10.dp))
            Text(token.vector.joinToString(prefix = "[", postfix = "]") { "%.1f".format(it * scale) }, fontSize = 9.sp, color = color)
        }
    }
}

@Composable
private fun AttentionStage(tokens: List<ToyToken>, selected: Int, onSelect: (Int) -> Unit, head: Int, onHead: (Int) -> Unit, advanced: Boolean) {
    TokenSelector(tokens, selected, onSelect)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..1).forEach { h ->
            OutlinedButton(onClick = { onHead(h) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (head == h) MaterialTheme.colorScheme.primary else Color(0xFF9DA9BC))) {
                Text("HEAD ${h + 1}")
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    AttentionCanvas(tokens, selected, head)
    Spacer(Modifier.height(10.dp))
    val weights = ToyEngine.attention(tokens, selected, head)
    weights.forEachIndexed { index, value ->
        ProbabilityBar(tokens[index].text, value, if (advanced) "%.3f".format(value) else "${(value * 100).toInt()}%")
    }
}

@Composable
private fun AttentionCanvas(tokens: List<ToyToken>, selected: Int, head: Int) {
    val weights = ToyEngine.attention(tokens, selected, head)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B101C))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (tokens.isEmpty()) return@Canvas
            val spacing = size.width / (tokens.size + 1)
            val y1 = size.height * .28f
            val y2 = size.height * .72f
            val queryX = spacing * (selected + 1)
            weights.forEachIndexed { index, weight ->
                val targetX = spacing * (index + 1)
                drawLine(
                    color = Color(0xFF8AD8FF).copy(alpha = .18f + weight * .82f),
                    start = Offset(queryX, y1),
                    end = Offset(targetX, y2),
                    strokeWidth = 2f + weight * 20f,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(Color(0xFFFFFFFF), 8f, Offset(queryX, y1))
            tokens.indices.forEach { index ->
                drawCircle(Color(0xFFB8A7FF), 6f, Offset(spacing * (index + 1), y2))
            }
        }
        Text(tokens.getOrNull(selected)?.text ?: "", modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FfnStage(tokens: List<ToyToken>, selected: Int, onSelect: (Int) -> Unit, advanced: Boolean) {
    TokenSelector(tokens, selected, onSelect)
    Spacer(Modifier.height(22.dp))
    val labels = if (advanced) listOf("Hidden state", "Linear ↑", "GELU", "Linear ↓", "Residual") else listOf("Token", "広げる", "特徴を強調", "縮める", "更新完了")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 1 || index == 2) .9f else .64f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFC267).copy(alpha = .10f + index * .025f))
                    .padding(11.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, color = Color(0xFFFFC979), fontWeight = FontWeight.Bold) }
            if (index != labels.lastIndex) Text("↓", color = Color(0xFF748197))
        }
    }
}

@Composable
private fun LayerStage(tokens: List<ToyToken>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        (1..4).forEach { layer ->
            val progress by animateFloatAsState(targetValue = layer / 4f, animationSpec = tween(550), label = "layer")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF151D2B))
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LAYER $layer", fontWeight = FontWeight.Black, modifier = Modifier.width(86.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color(0xFF222D42))) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.width(10.dp))
                Text("Attn + FFN", fontSize = 10.sp, color = Color(0xFFA7B3C5))
            }
            if (layer < 4) Text("↓", color = Color(0xFF708097))
        }
        Spacer(Modifier.height(16.dp))
        InfoPill("${tokens.size}個のToken表現が、層を通るたび少しずつ書き換わります")
    }
}

@Composable
private fun LogitsStage(temperature: Float, onTemperature: (Float) -> Unit, advanced: Boolean) {
    Text("Temperature  ${"%.2f".format(temperature)}", fontWeight = FontWeight.Bold)
    Slider(value = temperature, onValueChange = onTemperature, valueRange = .1f..2f)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("堅実 0.1", color = Color(0xFF8E9BAE), fontSize = 11.sp)
        Text("自由 2.0", color = Color(0xFF8E9BAE), fontSize = 11.sp)
    }
    Spacer(Modifier.height(14.dp))
    ToyEngine.probabilities(temperature).forEach { (candidate, prob) ->
        ProbabilityBar(candidate.text, prob, if (advanced) "logit %.2f / p %.3f".format(candidate.logit, prob) else "${(prob * 100).toInt()}%")
    }
}

@Composable
private fun SamplingStage(temperature: Float, sampled: String?, onSample: () -> Unit) {
    val probs = ToyEngine.probabilities(temperature)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B101C)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val ring = size.minDimension * .38f
                probs.forEachIndexed { index, (_, p) ->
                    val angle = (2 * PI * index / probs.size) - PI / 2
                    val pos = Offset(center.x + cos(angle).toFloat() * ring, center.y + sin(angle).toFloat() * ring)
                    drawCircle(Color(0xFFFF8ED8).copy(alpha = .35f + p), radius = 8f + p * 34f, center = pos)
                }
                drawCircle(Color(0xFFFFF1FB), radius = 7f, center = center)
            }
            Text(sampled ?: "?", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSample) { Text(if (sampled == null) "NEXT TOKENを選ぶ" else "もう一度Sampling") }
        Spacer(Modifier.height(16.dp))
        if (sampled != null) {
            Text("選択されたToken", color = Color(0xFFA2AFC2), fontSize = 12.sp)
            Text(sampled, color = Color(0xFFFF9CDB), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("→ 文末へ追加され、Transformerへもう一度入力", color = Color(0xFFA2AFC2), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TokenSelector(tokens: List<ToyToken>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tokens.forEachIndexed { index, token ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (index == selected) MaterialTheme.colorScheme.primary.copy(alpha = .19f) else Color(0xFF171E2E))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 13.dp, vertical = 10.dp)
            ) {
                Text(token.text, color = if (index == selected) MaterialTheme.colorScheme.primary else Color(0xFFD3DBE8), fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ProbabilityBar(label: String, value: Float, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, modifier = Modifier.width(72.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Box(
            Modifier
                .weight(1f)
                .height(9.dp)
                .clip(CircleShape)
                .background(Color(0xFF20293A))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(9.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(detail, modifier = Modifier.width(92.dp), textAlign = TextAlign.End, fontSize = 10.sp, color = Color(0xFF96A3B7))
    }
}

@Composable
private fun InfoPill(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .09f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        color = Color(0xFFB9C8D9),
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PlayerControls(stage: Stage, isPlaying: Boolean, onPrev: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(onClick = onPrev, enabled = stage.ordinal > 0) { Text("← BACK") }
        Button(onClick = onPlay) { Text(if (isPlaying) "PAUSE" else "PLAY") }
        OutlinedButton(onClick = onNext, enabled = stage.ordinal < Stage.entries.lastIndex) { Text("NEXT →") }
    }
}
