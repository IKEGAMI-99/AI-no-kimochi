package com.aikimochi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class AiNoKimochiActivityV21 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            V21Theme {
                V21App()
            }
        }
    }
}

private val V21Colors = darkColorScheme(
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

private val TokenColors = listOf(
    Color(0xFFFFC56D),
    Color(0xFF7CD6FF),
    Color(0xFFB7A7FF),
    Color(0xFF82E2A8),
    Color(0xFFFF9FC7),
    Color(0xFFFFA878)
)

private fun tokenColor(index: Int): Color = TokenColors[index % TokenColors.size]

@Composable
private fun V21Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = V21Colors, content = content)
}

private data class V21Example(val shortName: String, val text: String)

private enum class V21Stage(
    val title: String,
    val technical: String,
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
        "文章をTokenという小さな単位に分けます。AIは文章をTokenの列として扱います。",
        "次に、それぞれのTokenを『意味を持つ数字の位置』へ変換します。"
    ),
    EMBEDDING(
        "3. 数字にする",
        "Embedding",
        "Tokenをベクトルへ変換します。意味が近いTokenほど、Embedding空間でも近くなりやすいイメージです。",
        "次に、あるTokenが文章中のどのTokenをどれくらい参考にするかを調べます。"
    ),
    ATTENTION(
        "4. 関係を見る",
        "Self-Attention",
        "選んだTokenが、文章中の各Tokenをどれくらい参考にするかを計算します。自分自身も参照対象です。",
        "最後に、ここまでの情報から次に来そうなTokenを予想します。"
    ),
    PREDICT(
        "5. 次を予想する",
        "Logits / Sampling",
        "次に来そうなTokenへ点数をつけ、確率に変えて1つ選びます。",
        "選ばれたTokenを文章へ足し、同じ処理を繰り返して文章を生成します。"
    )
}

private data class V21Token(
    val text: String,
    val id: Int,
    val index: Int,
    val vector: List<Float>
)

private data class V21Candidate(val text: String, val logit: Float)

private object V21Engine {
    private val dictionary = listOf(
        "追いかける", "生成する", "ソファ", "歯医者", "ボール", "寝ている",
        "文章", "生成", "する", "猫", "犬", "AI", "私", "は", "が", "を", "で", "です"
    ).sortedByDescending { it.length }

    fun tokenize(text: String): List<V21Token> {
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
            V21Token(part, 100 + positive % 50000, index, vector)
        }
    }

    fun attention(tokens: List<V21Token>, queryIndex: Int): List<Float> {
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
            val nearby = 1f / (1f + abs(index - queryIndex))
            semantic + nearby + (token.id % 9) / 40f
        }
        val exps = raw.map { exp(it.toDouble()).toFloat() }
        val total = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / total }
    }

    fun candidates(example: V21Example): List<V21Candidate> = when (example.shortName) {
        "猫" -> listOf(
            V21Candidate("。", 2.8f), V21Candidate("よ", 1.8f), V21Candidate("ところ", 1.5f),
            V21Candidate("姿", 1.2f), V21Candidate("時間", 0.8f)
        )
        "自己紹介" -> listOf(
            V21Candidate("。", 2.9f), V21Candidate("が", 1.6f), V21Candidate("ので", 1.3f),
            V21Candidate("と", 1.0f), V21Candidate("！", 0.7f)
        )
        "犬" -> listOf(
            V21Candidate("。", 2.7f), V21Candidate("ため", 1.7f), V21Candidate("姿", 1.4f),
            V21Candidate("ように", 1.1f), V21Candidate("速く", 0.8f)
        )
        else -> listOf(
            V21Candidate("。", 2.6f), V21Candidate("ため", 1.8f), V21Candidate("ことで", 1.5f),
            V21Candidate("モデル", 1.2f), V21Candidate("仕組み", 0.9f)
        )
    }

    fun probabilities(example: V21Example, temperature: Float): List<Pair<V21Candidate, Float>> {
        val safe = temperature.coerceIn(0.1f, 2f)
        val candidates = candidates(example)
        val values = candidates.map { exp((it.logit / safe).toDouble()).toFloat() }
        val total = values.sum().coerceAtLeast(0.0001f)
        return candidates.zip(values.map { it / total })
    }
}

@Composable
private fun V21App() {
    val examples = remember {
        listOf(
            V21Example("猫", "猫はソファで寝ている"),
            V21Example("自己紹介", "私は歯医者です"),
            V21Example("犬", "犬がボールを追いかける"),
            V21Example("AI", "AIは文章を生成する")
        )
    }

    var exampleIndex by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(V21Stage.SENTENCE) }
    var advanced by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var selectedToken by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableFloatStateOf(1f) }
    var sampled by remember { mutableStateOf<String?>(null) }

    val example = examples[exampleIndex]
    val tokens = remember(exampleIndex) { V21Engine.tokenize(example.text) }
    if (selectedToken > tokens.lastIndex) selectedToken = 0

    LaunchedEffect(playing, stage, exampleIndex) {
        if (!playing) return@LaunchedEffect
        delay(1900)
        if (stage.ordinal < V21Stage.entries.lastIndex) {
            stage = V21Stage.entries[stage.ordinal + 1]
        } else {
            playing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06101E), Color(0xFF09172B), Color(0xFF06101E))))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        V21Header(advanced) { advanced = it }
        Spacer(Modifier.height(14.dp))
        V21ExamplePicker(examples, exampleIndex) { index ->
            exampleIndex = index
            stage = V21Stage.SENTENCE
            selectedToken = 0
            sampled = null
            playing = false
        }
        Spacer(Modifier.height(12.dp))
        V21StageRail(stage, advanced) {
            stage = it
            playing = false
        }
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            AnimatedContent(targetState = stage, label = "v21Stage") { current ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
                ) {
                    V21StageIntro(current, advanced)
                    Spacer(Modifier.height(18.dp))
                    when (current) {
                        V21Stage.SENTENCE -> V21SentenceStage(example)
                        V21Stage.TOKEN -> V21TokenStage(tokens, advanced)
                        V21Stage.EMBEDDING -> V21EmbeddingStage(tokens, advanced)
                        V21Stage.ATTENTION -> V21AttentionStage(tokens, selectedToken, { selectedToken = it }, advanced)
                        V21Stage.PREDICT -> V21PredictStage(
                            example,
                            temperature,
                            { temperature = it },
                            sampled,
                            {
                                val probabilities = V21Engine.probabilities(example, temperature)
                                val r = Random.nextFloat()
                                var sum = 0f
                                sampled = probabilities.firstOrNull { (_, p) ->
                                    sum += p
                                    r <= sum
                                }?.first?.text ?: probabilities.last().first.text
                            },
                            advanced
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        V21Navigation(stage, playing,
            onBack = {
                playing = false
                if (stage.ordinal > 0) stage = V21Stage.entries[stage.ordinal - 1]
            },
            onPlay = { playing = !playing },
            onNext = {
                playing = false
                if (stage.ordinal < V21Stage.entries.lastIndex) stage = V21Stage.entries[stage.ordinal + 1]
            }
        )
    }
}

@Composable
private fun V21Header(advanced: Boolean, onAdvancedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AIのキモチ", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("文章が生まれるまでを、触って理解する。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (advanced) "ADVANCED" else "BEGINNER", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Switch(checked = advanced, onCheckedChange = onAdvancedChange)
        }
    }
}

@Composable
private fun V21ExamplePicker(examples: List<V21Example>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text("例文を選ぶ", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEachIndexed { index, example ->
                val active = index == selected
                Card(
                    modifier = Modifier.clickable { onSelect(index) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                        Text(example.shortName, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(example.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V21StageRail(stage: V21Stage, advanced: Boolean, onStage: (V21Stage) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V21Stage.entries.forEach { item ->
            val active = item == stage
            Card(
                modifier = Modifier.clickable { onStage(item) },
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(item.title, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    if (advanced) Text(item.technical, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun V21StageIntro(stage: V21Stage, advanced: Boolean) {
    Text(stage.title.substringAfter(". "), color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
    if (advanced) Text(stage.technical, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(stage.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(12.dp))
    V21InfoCard("次に何が起きる？", stage.next)
}

@Composable
private fun V21SentenceStage(example: V21Example) {
    Text("いまAIに渡す文章", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V21BigSentence(example.text)
    Spacer(Modifier.height(14.dp))
    V21InfoCard("ポイント", "この時点では、まだ人間が読む普通の文章です。次の画面でAI向けの小さな単位へ分解します。")
}

@Composable
private fun V21TokenStage(tokens: List<V21Token>, advanced: Boolean) {
    Text("文章がパキッと分かれます", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V21TokenRow(tokens, null) {}
    Spacer(Modifier.height(14.dp))
    V21InfoCard("こうなりました", tokens.joinToString("  /  ") { it.text })
    if (advanced) {
        Spacer(Modifier.height(14.dp))
        Text("Token ID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        tokens.forEach { V21KeyValueRow("${it.index + 1}. ${it.text}", it.id.toString()) }
    }
}

@Composable
private fun V21EmbeddingStage(tokens: List<V21Token>, advanced: Boolean) {
    Text("どの点が、どのTokenなのか", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Text("点・ラベル・下のTokenカードは同じ色で対応しています。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)) {
                val w = maxWidth
                val h = maxHeight
                tokens.forEachIndexed { index, token ->
                    val px = (0.08f + (token.vector[0] + 1f) * 0.37f).coerceIn(0.04f, 0.78f)
                    val py = (0.06f + (token.vector[1] + 1f) * 0.35f).coerceIn(0.05f, 0.78f)
                    Row(
                        modifier = Modifier.offset(x = w * px, y = h * py),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(tokenColor(index)))
                        Spacer(Modifier.width(5.dp))
                        Text(token.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
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
    V21TokenRow(tokens, null) {}
    Spacer(Modifier.height(16.dp))
    V21EmbeddingDistanceCard()

    if (advanced) {
        Spacer(Modifier.height(14.dp))
        Text("この例文の教育用ベクトル", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        tokens.forEach {
            V21KeyValueRow(it.text, it.vector.joinToString(prefix = "[", postfix = "]") { v -> "%.2f".format(v) })
        }
    }
}

@Composable
private fun V21EmbeddingDistanceCard() {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("ベクトルの『近い・遠い』とは？", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("意味が近い単語", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            V21DistanceDiagram("リンゴ", "梨", near = true)
            Text("食べ物・果物という共通点が多いので、近い位置になりやすい。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("意味が遠い単語", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            V21DistanceDiagram("リンゴ", "バス", near = false)
            Text("意味や使われ方が大きく違うので、離れた位置になりやすい。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("※ 実際の距離はモデルや文脈によって変わります。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun V21DistanceDiagram(left: String, right: String, near: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            Text(left, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
        }
        Spacer(Modifier.width(if (near) 24.dp else 8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (near) 0.75f else 0.25f))
        )
        Spacer(Modifier.width(if (near) 24.dp else 8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(if (near) MaterialTheme.colorScheme.secondary else Color(0xFF82E2A8)))
            Text(right, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
        }
    }
    Text(if (near) "← 近い →" else "←──────── 遠い ────────→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun V21AttentionStage(tokens: List<V21Token>, selectedToken: Int, onSelectedToken: (Int) -> Unit, advanced: Boolean) {
    val weights = remember(tokens, selectedToken) { V21Engine.attention(tokens, selectedToken) }
    val primary = MaterialTheme.colorScheme.primary
    val selectedColor = tokenColor(selectedToken)

    Text("まず、気になるTokenをタップ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V21TokenRow(tokens, selectedToken, onSelectedToken)
    Spacer(Modifier.height(14.dp))

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "「${tokens[selectedToken].text}」は、どのTokenをどれくらい参考にしている？",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Self-Attentionなので、自分自身の「${tokens[selectedToken].text}」も参照します。下の『自分自身』の割合もAttention Weightの一部です。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(165.dp)) {
                val count = tokens.size.coerceAtLeast(1)
                val denominator = (count - 1).coerceAtLeast(1)
                val xs = List(count) { index -> size.width * (0.10f + 0.80f * index / denominator) }
                val bottomY = size.height * 0.78f
                val topY = size.height * 0.20f

                weights.forEachIndexed { index, weight ->
                    if (index != selectedToken) {
                        drawLine(
                            color = tokenColor(index).copy(alpha = (0.30f + weight * 1.7f).coerceIn(0.30f, 1f)),
                            start = Offset(xs[selectedToken], bottomY),
                            end = Offset(xs[index], topY),
                            strokeWidth = 5f + weight * 22f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                xs.forEachIndexed { index, x ->
                    drawCircle(
                        color = tokenColor(index),
                        radius = if (index == selectedToken) 18f else 13f,
                        center = Offset(x, bottomY)
                    )
                    if (index == selectedToken) {
                        drawCircle(
                            color = selectedColor.copy(alpha = 0.85f),
                            radius = 28f,
                            center = Offset(x, bottomY),
                            style = Stroke(width = 4f + weights[index] * 14f)
                        )
                    }
                }
            }
            Text(
                "○ 外側のリング = 自分自身への参照",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))
            weights.forEachIndexed { index, value ->
                V21PercentageBar(
                    label = if (index == selectedToken) "${tokens[index].text}（自分自身）" else tokens[index].text,
                    value = value,
                    color = tokenColor(index)
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (advanced) {
        Spacer(Modifier.height(12.dp))
        V21InfoCard(
            "Self-Attentionとして見る",
            "各TokenのQueryと、全Token（自分自身を含む）のKeyを比較してAttention Weightを作り、その重みでValueを混ぜます。この画面ではその関係を教育用に単純化しています。"
        )
    }
}

@Composable
private fun V21PredictStage(
    example: V21Example,
    temperature: Float,
    onTemperature: (Float) -> Unit,
    sampled: String?,
    onSample: () -> Unit,
    advanced: Boolean
) {
    val probabilities = remember(example, temperature) { V21Engine.probabilities(example, temperature) }
    Text("AIが考えている次の候補", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("Temperature  ${"%.1f".format(temperature)}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("低いほど無難、高いほど候補がばらけます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Slider(value = temperature, onValueChange = onTemperature, valueRange = 0.1f..2.0f)
            probabilities.forEach { (candidate, probability) ->
                V21CandidateBar(candidate, probability, advanced)
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("この確率から1つ選ぶ") }
        }
    }
    if (sampled != null) {
        Spacer(Modifier.height(14.dp))
        Text("選ばれたToken", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        V21BigSentence("${example.text}$sampled")
    }
}

@Composable
private fun V21BigSentence(text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 25.dp), contentAlignment = Alignment.Center) {
            Text("「$text」", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun V21InfoCard(title: String, text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun V21TokenRow(tokens: List<V21Token>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.forEachIndexed { index, token ->
            val active = selected == index
            val color = tokenColor(index)
            Card(
                modifier = if (selected != null) Modifier.clickable { onSelect(index) } else Modifier,
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (active) 0.30f else 0.15f))
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.height(3.dp))
                    Text(token.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun V21PercentageBar(label: String, value: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        Text("${(value * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(9.dp).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

@Composable
private fun V21CandidateBar(candidate: V21Candidate, probability: Float, advanced: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(candidate.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(
            if (advanced) "${(probability * 100).toInt()}%  logit ${"%.1f".format(candidate.logit)}" else "${(probability * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxWidth(probability.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
        )
    }
}

@Composable
private fun V21KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun V21Navigation(
    stage: V21Stage,
    playing: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack, enabled = stage.ordinal > 0, modifier = Modifier.weight(1f)) { Text("← BACK") }
        Button(onClick = onPlay, modifier = Modifier.weight(1f)) { Text(if (playing) "PAUSE" else "PLAY") }
        OutlinedButton(onClick = onNext, enabled = stage.ordinal < V21Stage.entries.lastIndex, modifier = Modifier.weight(1f)) { Text("NEXT →") }
    }
}
