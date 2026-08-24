package com.aikimochi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class AiNoKimochiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiNoKimochiV2Theme {
                AiNoKimochiV2App()
            }
        }
    }
}

private val V2Colors = darkColorScheme(
    primary = Color(0xFF7CD6FF),
    secondary = Color(0xFFB7A7FF),
    tertiary = Color(0xFFFFC56D),
    background = Color(0xFF06101E),
    surface = Color(0xFF0D1A2B),
    surfaceVariant = Color(0xFF17263C),
    onBackground = Color(0xFFF5F9FF),
    onSurface = Color(0xFFF5F9FF),
    onSurfaceVariant = Color(0xFFC1CEE0)
)

@Composable
private fun AiNoKimochiV2Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = V2Colors, content = content)
}

private data class ExampleSentence(val shortName: String, val text: String)

private enum class LearnStage(
    val beginnerTitle: String,
    val technicalName: String,
    val explanation: String,
    val next: String
) {
    SENTENCE(
        "1. 文章を渡す",
        "Text Input",
        "まず、文章をAIに渡します。ここではまだ普通の文字です。",
        "次に、この文章をAIが扱いやすい小さな単位に分けます。"
    ),
    TOKEN(
        "2. 文章を区切る",
        "Tokenization",
        "文章をTokenという小さな単位に分けます。AIは文章を丸ごと読むのではなく、Tokenの列として扱います。",
        "次に、それぞれのTokenを数字の並びへ変換します。"
    ),
    EMBEDDING(
        "3. 数字にする",
        "Embedding",
        "Tokenを『意味を表す数字の位置』へ変換します。似た意味ほど、近い位置になりやすいイメージです。",
        "次に、単語どうしがどれくらい関係しているかを調べます。"
    ),
    ATTENTION(
        "4. 関係を見る",
        "Attention",
        "今見ているTokenが、ほかのTokenをどれくらい重要視するかを計算します。線が強いほど、強く参照しています。",
        "最後に、ここまでの情報から次に来そうなTokenを予想します。"
    ),
    PREDICT(
        "5. 次を予想する",
        "Logits / Sampling",
        "次に来そうなTokenへ点数をつけ、確率に変えて1つ選びます。選ばれたTokenを文章へ足し、また同じ処理を繰り返します。",
        "これが、LLMが1Tokenずつ文章を生成する基本のループです。"
    )
}

private data class ToyToken(
    val text: String,
    val id: Int,
    val index: Int,
    val vector: List<Float>
)

private data class Candidate(val text: String, val logit: Float)

private object V2ToyEngine {
    private val dictionary = listOf(
        "追いかける", "生成する", "ソファ", "歯医者", "ボール", "寝ている",
        "文章", "生成", "する", "猫", "犬", "AI", "私", "は", "が", "を", "で", "です"
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
            } else {
                parts += text[i].toString()
                i++
            }
        }
        if (parts.isEmpty()) parts += "…"

        return parts.mapIndexed { index, part ->
            val seed = part.hashCode()
            val positive = if (seed == Int.MIN_VALUE) 0 else abs(seed)
            val vector = List(3) { dim ->
                sin(seed * (dim + 2) * 0.00061 + index * 0.43).toFloat()
            }
            ToyToken(part, 100 + positive % 50000, index, vector)
        }
    }

    fun attention(tokens: List<ToyToken>, queryIndex: Int): List<Float> {
        if (tokens.isEmpty()) return emptyList()
        val query = tokens[queryIndex]
        val raw = tokens.mapIndexed { index, token ->
            val pair = setOf(query.text, token.text)
            val semantic = when {
                pair.contains("猫") && (pair.contains("寝ている") || pair.contains("ソファ")) -> 1.6f
                pair.contains("犬") && (pair.contains("ボール") || pair.contains("追いかける")) -> 1.5f
                pair.contains("私") && pair.contains("歯医者") -> 1.5f
                pair.contains("AI") && (pair.contains("文章") || pair.contains("生成する") || pair.contains("生成")) -> 1.5f
                query.text == token.text -> 0.7f
                else -> 0.15f
            }
            val nearby = 1.0f / (1f + abs(index - queryIndex))
            semantic + nearby + (token.id % 9) / 40f
        }
        val exps = raw.map { exp(it.toDouble()).toFloat() }
        val total = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / total }
    }

    fun candidates(example: ExampleSentence): List<Candidate> = when (example.shortName) {
        "猫" -> listOf(
            Candidate("。", 2.8f),
            Candidate("よ", 1.8f),
            Candidate("ところ", 1.5f),
            Candidate("姿", 1.2f),
            Candidate("時間", 0.8f)
        )
        "自己紹介" -> listOf(
            Candidate("。", 2.9f),
            Candidate("が", 1.6f),
            Candidate("ので", 1.3f),
            Candidate("と", 1.0f),
            Candidate("！", 0.7f)
        )
        "犬" -> listOf(
            Candidate("。", 2.7f),
            Candidate("ため", 1.7f),
            Candidate("姿", 1.4f),
            Candidate("ように", 1.1f),
            Candidate("速く", 0.8f)
        )
        else -> listOf(
            Candidate("。", 2.6f),
            Candidate("ため", 1.8f),
            Candidate("ことで", 1.5f),
            Candidate("モデル", 1.2f),
            Candidate("仕組み", 0.9f)
        )
    }

    fun probabilities(example: ExampleSentence, temperature: Float): List<Pair<Candidate, Float>> {
        val safe = temperature.coerceIn(0.1f, 2f)
        val candidates = candidates(example)
        val values = candidates.map { exp((it.logit / safe).toDouble()).toFloat() }
        val total = values.sum().coerceAtLeast(0.0001f)
        return candidates.zip(values.map { it / total })
    }
}

@Composable
private fun AiNoKimochiV2App() {
    val examples = remember {
        listOf(
            ExampleSentence("猫", "猫はソファで寝ている"),
            ExampleSentence("自己紹介", "私は歯医者です"),
            ExampleSentence("犬", "犬がボールを追いかける"),
            ExampleSentence("AI", "AIは文章を生成する")
        )
    }

    var exampleIndex by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(LearnStage.SENTENCE) }
    var advanced by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var selectedToken by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableFloatStateOf(1f) }
    var sampled by remember { mutableStateOf<String?>(null) }

    val example = examples[exampleIndex]
    val tokens = remember(exampleIndex) { V2ToyEngine.tokenize(examples[exampleIndex].text) }
    if (selectedToken > tokens.lastIndex) selectedToken = 0

    LaunchedEffect(playing, stage, exampleIndex) {
        if (!playing) return@LaunchedEffect
        delay(1900)
        if (stage.ordinal < LearnStage.entries.lastIndex) {
            stage = LearnStage.entries[stage.ordinal + 1]
        } else {
            playing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06101E), Color(0xFF09172B), Color(0xFF06101E))
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        V2Header(advanced = advanced, onAdvancedChange = { advanced = it })
        Spacer(Modifier.height(14.dp))
        ExamplePicker(
            examples = examples,
            selected = exampleIndex,
            onSelect = { index ->
                exampleIndex = index
                stage = LearnStage.SENTENCE
                selectedToken = 0
                sampled = null
                playing = false
            }
        )
        Spacer(Modifier.height(12.dp))
        StageRail(stage = stage, advanced = advanced) {
            stage = it
            playing = false
        }
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            AnimatedContent(targetState = stage, label = "learnStage") { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    StageIntro(current, advanced)
                    Spacer(Modifier.height(18.dp))
                    when (current) {
                        LearnStage.SENTENCE -> SentenceStage(example)
                        LearnStage.TOKEN -> TokenStage(tokens, advanced)
                        LearnStage.EMBEDDING -> EmbeddingStage(tokens, advanced)
                        LearnStage.ATTENTION -> AttentionStage(
                            tokens = tokens,
                            selectedToken = selectedToken,
                            onSelectedToken = { selectedToken = it },
                            advanced = advanced
                        )
                        LearnStage.PREDICT -> PredictStage(
                            example = example,
                            temperature = temperature,
                            onTemperature = { temperature = it },
                            sampled = sampled,
                            onSample = {
                                val probabilities = V2ToyEngine.probabilities(example, temperature)
                                val random = Random.nextFloat()
                                var accumulated = 0f
                                sampled = probabilities.firstOrNull { (_, probability) ->
                                    accumulated += probability
                                    random <= accumulated
                                }?.first?.text ?: probabilities.last().first.text
                            },
                            advanced = advanced
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        NavigationControls(
            stage = stage,
            playing = playing,
            onBack = {
                playing = false
                if (stage.ordinal > 0) stage = LearnStage.entries[stage.ordinal - 1]
            },
            onPlay = { playing = !playing },
            onNext = {
                playing = false
                if (stage.ordinal < LearnStage.entries.lastIndex) {
                    stage = LearnStage.entries[stage.ordinal + 1]
                }
            }
        )
    }
}

@Composable
private fun V2Header(advanced: Boolean, onAdvancedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "AIのキモチ",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "文章が生まれるまでを、触って理解する。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (advanced) "ADVANCED" else "BEGINNER",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(checked = advanced, onCheckedChange = onAdvancedChange)
        }
    }
}

@Composable
private fun ExamplePicker(
    examples: List<ExampleSentence>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            "例文を選ぶ",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            examples.forEachIndexed { index, example ->
                val active = index == selected
                Card(
                    modifier = Modifier.clickable { onSelect(index) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                        Text(
                            example.shortName,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            example.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRail(stage: LearnStage, advanced: Boolean, onStage: (LearnStage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LearnStage.entries.forEach { item ->
            val active = item == stage
            Card(
                modifier = Modifier.clickable { onStage(item) },
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(
                        item.beginnerTitle,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                    if (advanced) {
                        Text(
                            item.technicalName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageIntro(stage: LearnStage, advanced: Boolean) {
    Text(
        stage.beginnerTitle.substringAfter(". "),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 24.sp,
        fontWeight = FontWeight.Black
    )
    if (advanced) {
        Text(
            stage.technicalName,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        stage.explanation,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 21.sp
    )
    Spacer(Modifier.height(12.dp))
    InfoCard("次に何が起きる？", stage.next)
}

@Composable
private fun SentenceStage(example: ExampleSentence) {
    Text("いまAIに渡す文章", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    BigSentence(example.text)
    Spacer(Modifier.height(14.dp))
    InfoCard(
        "ポイント",
        "この時点では、まだ人間が読む普通の文章です。次の画面でAI向けの小さな単位へ分解します。"
    )
}

@Composable
private fun TokenStage(tokens: List<ToyToken>, advanced: Boolean) {
    Text("文章がパキッと分かれます", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    TokenRow(tokens, selected = null, onSelect = {})
    Spacer(Modifier.height(14.dp))
    InfoCard("こうなりました", tokens.joinToString("  /  ") { it.text })
    if (advanced) {
        Spacer(Modifier.height(14.dp))
        Text("Token ID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        tokens.forEach { token ->
            KeyValueRow("${token.index + 1}. ${token.text}", token.id.toString())
        }
    }
}

@Composable
private fun EmbeddingStage(tokens: List<ToyToken>, advanced: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Text("文字から『数字の位置』へ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                tokens.forEachIndexed { index, token ->
                    val x = size.width * (0.12f + (token.vector[0] + 1f) * 0.38f)
                    val y = size.height * (0.10f + (token.vector[1] + 1f) * 0.40f)
                    drawCircle(
                        color = if (index % 2 == 0) primary else secondary,
                        radius = 15f,
                        center = Offset(x, y)
                    )
                }
            }
            Text(
                "※ 本物のEmbeddingは数百〜数千次元。ここでは見えるよう3次元相当に縮めた教育用表示です。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    TokenRow(tokens, selected = null, onSelect = {})
    if (advanced) {
        Spacer(Modifier.height(14.dp))
        tokens.forEach { token ->
            KeyValueRow(
                token.text,
                token.vector.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }
            )
        }
    }
}

@Composable
private fun AttentionStage(
    tokens: List<ToyToken>,
    selectedToken: Int,
    onSelectedToken: (Int) -> Unit,
    advanced: Boolean
) {
    val weights = remember(tokens, selectedToken) { V2ToyEngine.attention(tokens, selectedToken) }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Text("まず、気になるTokenをタップ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    TokenRow(tokens, selected = selectedToken, onSelect = onSelectedToken)
    Spacer(Modifier.height(14.dp))

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "「${tokens[selectedToken].text}」は、どこを見ている？",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val count = tokens.size.coerceAtLeast(1)
                val denominator = (count - 1).coerceAtLeast(1)
                val xs = List(count) { index -> size.width * (0.10f + 0.80f * index / denominator) }
                val bottomY = size.height * 0.78f
                val topY = size.height * 0.20f

                weights.forEachIndexed { index, weight ->
                    if (index != selectedToken) {
                        drawLine(
                            color = primary.copy(alpha = (0.25f + weight * 1.8f).coerceIn(0.25f, 1f)),
                            start = Offset(xs[selectedToken], bottomY),
                            end = Offset(xs[index], topY),
                            strokeWidth = 5f + weight * 22f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                xs.forEachIndexed { index, x ->
                    drawCircle(
                        color = if (index == selectedToken) tertiary else primary,
                        radius = if (index == selectedToken) 18f else 13f,
                        center = Offset(x, bottomY)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            weights.forEachIndexed { index, value ->
                PercentageBar(tokens[index].text, value)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (advanced) {
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "Attentionとして見る",
            "実際のTransformerではQ・K・Vを使ってAttention Weightを計算します。ここでは関係の強さが直感で見えるよう単純化しています。"
        )
    }
}

@Composable
private fun PredictStage(
    example: ExampleSentence,
    temperature: Float,
    onTemperature: (Float) -> Unit,
    sampled: String?,
    onSample: () -> Unit,
    advanced: Boolean
) {
    val probabilities = remember(example, temperature) {
        V2ToyEngine.probabilities(example, temperature)
    }

    Text("AIが考えている次の候補", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Temperature  ${"%.1f".format(temperature)}",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "低いほど無難、高いほど候補がばらけます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Slider(value = temperature, onValueChange = onTemperature, valueRange = 0.1f..2.0f)
            probabilities.forEach { (candidate, probability) ->
                CandidateBar(candidate, probability, advanced)
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onSample, modifier = Modifier.fillMaxWidth()) {
                Text("この確率から1つ選ぶ")
            }
        }
    }

    if (sampled != null) {
        Spacer(Modifier.height(14.dp))
        Text("選ばれたToken", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        BigSentence("${example.text}$sampled")
    }
}

@Composable
private fun BigSentence(text: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 25.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "「$text」",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 25.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun TokenRow(tokens: List<ToyToken>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tokens.forEachIndexed { index, token ->
            val active = selected == index
            Card(
                modifier = if (selected != null) Modifier.clickable { onSelect(index) } else Modifier,
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (active) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.23f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(token.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PercentageBar(label: String, value: Float) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        Text("${(value * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(9.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun CandidateBar(candidate: Candidate, probability: Float, advanced: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(candidate.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(
            if (advanced) "${(probability * 100).toInt()}%  logit ${"%.1f".format(candidate.logit)}"
            else "${(probability * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(probability.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    )
                )
        )
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun NavigationControls(
    stage: LearnStage,
    playing: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onBack,
            enabled = stage.ordinal > 0,
            modifier = Modifier.weight(1f)
        ) {
            Text("← BACK")
        }
        Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Text(if (playing) "PAUSE" else "PLAY")
        }
        OutlinedButton(
            onClick = onNext,
            enabled = stage.ordinal < LearnStage.entries.lastIndex,
            modifier = Modifier.weight(1f)
        ) {
            Text("NEXT →")
        }
    }
}
