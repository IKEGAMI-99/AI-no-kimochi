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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class AiNoKimochiActivityV31 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V31Theme { V31App() } }
    }
}

private val V31Colors = darkColorScheme(
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

private val V31TokenColors = listOf(
    Color(0xFFFFC56D), Color(0xFF7CD6FF), Color(0xFFB7A7FF),
    Color(0xFF82E2A8), Color(0xFFFF9FC7), Color(0xFFFFA878)
)
private fun v31TokenColor(index: Int): Color = V31TokenColors[index % V31TokenColors.size]

@Composable
private fun V31Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = V31Colors, content = content)
}

private data class V31Example(val shortName: String, val text: String)
private data class V31Token(val text: String, val id: Int, val index: Int, val vector: List<Float>)
private data class V31Candidate(val text: String, val logit: Float)
private data class V31Qkv(val q: List<Float>, val k: List<Float>, val v: List<Float>)

private enum class V31Stage(
    val title: String,
    val technical: String,
    val explanation: String,
    val next: String
) {
    SENTENCE(
        "1. 文章",
        "Text Input",
        "まず、文章をAIへ入力します。この時点ではまだ人間が読む普通の文字列です。LLMは最終的にすべてを数値として計算するため、このあと文章を段階的に『計算できる形』へ変換していきます。ここで大切なのは、AIが最初から日本語の意味を文字のまま理解しているわけではない、という点です。",
        "次は文章をTokenという小さな単位へ分けます。"
    ),
    TOKEN(
        "2. Token",
        "Tokenization",
        "文章をTokenという小さな単位へ分割します。Tokenは必ずしも単語1個とは限らず、文字の一部や助詞、記号になることもあります。モデルはこのToken列を順番に処理します。TokenごとにIDが割り当てられ、ここから先は文字列そのものよりも数値IDやベクトルが主役になります。",
        "次は各TokenをEmbeddingベクトルへ変換します。"
    ),
    EMBEDDING(
        "3. Embedding",
        "Embedding",
        "Token IDだけでは『猫と犬は少し似ている』『リンゴと梨はかなり近い』といった意味上の関係を表しにくいため、各Tokenを多次元のベクトルへ変換します。このベクトル空間では、学習中の使われ方が似ているTokenほど近い方向や位置になりやすくなります。ただし実際の意味は1本の軸に対応するのではなく、多数の次元へ分散して表現されています。",
        "次は同じEmbeddingからQ・K・Vという3種類のベクトルを作ります。"
    ),
    QKV(
        "4. Q・K・V",
        "Query / Key / Value",
        "Attentionを計算するため、各TokenのEmbeddingを学習済みの行列で3方向へ変換します。Queryは『自分は何を探しているか』、Keyは『自分はどんな情報を持っているか』、Valueは『実際に相手へ渡す情報』に相当します。Q・K・Vは別々の単語ではなく、同じTokenを違う目的で見た3種類の表現です。",
        "次はQueryとKeyを比較してAttention Weightを作ります。"
    ),
    ATTENTION(
        "5. Attention",
        "Self-Attention",
        "あるTokenのQueryと、文章中のすべてのTokenのKeyを比較し、どのTokenをどれくらい参考にするかを決めます。この重みがAttention Weightです。Self-Attentionでは自分自身も参照対象です。得られた重みを使って各TokenのValueを混ぜることで、単独だったToken表現へ文章全体の文脈が入り込みます。Multi-Head Attentionでは、この計算を複数の視点で同時に行います。",
        "次は文脈を取り込んだ各TokenをFFNで個別に加工します。"
    ),
    FFN(
        "6. FFN",
        "Feed Forward Network",
        "Attentionが『他のTokenから必要な情報を集める処理』なら、FFNは『集めた情報を各Tokenの内部で加工する処理』です。同じFFNがすべてのTokenへ独立に適用され、一般的には一度高次元へ広げ、非線形なActivationを通し、元に近い次元へ戻します。ここで特徴の組み合わせを変換することで、モデルはより複雑なパターンを表現できるようになります。",
        "次は最終表現から語彙全体のLogitを計算し、次Tokenを選びます。"
    ),
    PREDICT(
        "7. 次Token予測",
        "Logits / Softmax / Sampling",
        "Transformerの層を通った最終表現から、語彙に含まれる各TokenへLogitという点数を付けます。Softmaxなどを使って確率分布へ変換し、その分布から次のTokenを選びます。Temperatureを変えると分布の尖り方が変わり、低いほど上位候補が選ばれやすく、高いほど候補がばらけます。選ばれたTokenを文章へ追加し、同じ処理を繰り返すことで文章が1Tokenずつ生成されます。",
        "選ばれたTokenを入力へ追加し、再びToken → Embedding → Transformerという処理を繰り返します。"
    )
}

private object V31Engine {
    private val dictionary = listOf(
        "追いかける", "生成する", "ソファ", "歯医者", "ボール", "寝ている",
        "文章", "生成", "する", "猫", "犬", "AI", "私", "は", "が", "を", "で", "です"
    ).sortedByDescending { it.length }

    fun tokenize(text: String): List<V31Token> {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < text.length && parts.size < 16) {
            if (text[i].isWhitespace()) { i++; continue }
            val match = dictionary.firstOrNull { text.startsWith(it, i) }
            if (match != null) { parts += match; i += match.length }
            else { parts += text[i].toString(); i++ }
        }
        if (parts.isEmpty()) parts += "…"
        return parts.mapIndexed { index, part ->
            val seed = part.hashCode()
            val positive = if (seed == Int.MIN_VALUE) 0 else abs(seed)
            val vector = List(3) { dim -> sin(seed * (dim + 2) * 0.00061 + index * 0.43).toFloat() }
            V31Token(part, 100 + positive % 50000, index, vector)
        }
    }

    fun qkv(token: V31Token): V31Qkv {
        val x = token.vector
        fun t(a: Float, b: Float, c: Float) = listOf(
            (x[0] * a + x[1] * b + x[2] * c).coerceIn(-1.5f, 1.5f),
            (x[0] * c - x[1] * a + x[2] * b).coerceIn(-1.5f, 1.5f),
            (x[0] * b + x[1] * c - x[2] * a).coerceIn(-1.5f, 1.5f)
        )
        return V31Qkv(
            t(0.78f, 0.24f, -0.16f),
            t(-0.18f, 0.83f, 0.31f),
            t(0.29f, -0.12f, 0.91f)
        )
    }

    fun attention(tokens: List<V31Token>, queryIndex: Int): List<Float> {
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
            semantic + 1f / (1f + abs(index - queryIndex)) + (token.id % 9) / 40f
        }
        val exps = raw.map { exp(it.toDouble()).toFloat() }
        val total = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / total }
    }

    fun ffnInput(token: V31Token): List<Float> = qkv(token).v
    fun ffnHidden(token: V31Token): List<Float> {
        val x = ffnInput(token)
        return listOf(
            maxOf(0f, x[0] * 1.20f + x[1] * 0.35f),
            maxOf(0f, x[1] * 1.10f - x[2] * 0.28f),
            maxOf(0f, x[2] * 1.25f + x[0] * 0.22f),
            maxOf(0f, (x[0] + x[1] + x[2]) * 0.55f),
            maxOf(0f, (x[0] - x[1]) * 0.72f),
            maxOf(0f, (x[2] - x[0]) * 0.68f)
        )
    }

    fun ffnOutput(token: V31Token): List<Float> {
        val h = ffnHidden(token)
        return listOf(
            (h[0] * 0.46f + h[2] * 0.31f - h[4] * 0.18f).coerceIn(-1.5f, 1.5f),
            (h[1] * 0.41f + h[3] * 0.29f + h[5] * 0.20f).coerceIn(-1.5f, 1.5f),
            (h[2] * 0.38f + h[4] * 0.27f - h[0] * 0.14f).coerceIn(-1.5f, 1.5f)
        )
    }

    fun candidates(example: V31Example): List<V31Candidate> = when (example.shortName) {
        "猫" -> listOf(V31Candidate("。",2.8f), V31Candidate("よ",1.8f), V31Candidate("ところ",1.5f), V31Candidate("姿",1.2f), V31Candidate("時間",0.8f))
        "自己紹介" -> listOf(V31Candidate("。",2.9f), V31Candidate("が",1.6f), V31Candidate("ので",1.3f), V31Candidate("と",1.0f), V31Candidate("！",0.7f))
        "犬" -> listOf(V31Candidate("。",2.7f), V31Candidate("ため",1.7f), V31Candidate("姿",1.4f), V31Candidate("ように",1.1f), V31Candidate("速く",0.8f))
        else -> listOf(V31Candidate("。",2.6f), V31Candidate("ため",1.8f), V31Candidate("ことで",1.5f), V31Candidate("モデル",1.2f), V31Candidate("仕組み",0.9f))
    }

    fun probabilities(example: V31Example, temperature: Float): List<Pair<V31Candidate, Float>> {
        val safe = temperature.coerceIn(0.1f, 2f)
        val candidates = candidates(example)
        val exps = candidates.map { exp((it.logit / safe).toDouble()).toFloat() }
        val total = exps.sum().coerceAtLeast(0.0001f)
        return candidates.zip(exps.map { it / total })
    }
}

@Composable
private fun V31App() {
    val examples = remember {
        listOf(
            V31Example("猫", "猫はソファで寝ている"),
            V31Example("自己紹介", "私は歯医者です"),
            V31Example("犬", "犬がボールを追いかける"),
            V31Example("AI", "AIは文章を生成する")
        )
    }
    var exampleIndex by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(V31Stage.SENTENCE) }
    var selectedToken by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableFloatStateOf(1f) }
    var sampled by remember { mutableStateOf<String?>(null) }

    val example = examples[exampleIndex]
    val tokens = remember(exampleIndex) { V31Engine.tokenize(example.text) }
    if (selectedToken > tokens.lastIndex) selectedToken = 0

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06101E), Color(0xFF09172B), Color(0xFF06101E))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)
    ) {
        V31Header()
        Spacer(Modifier.height(18.dp))
        V31ExamplePicker(examples, exampleIndex) {
            exampleIndex = it
            stage = V31Stage.SENTENCE
            selectedToken = 0
            sampled = null
        }
        Spacer(Modifier.height(12.dp))
        V31StageRail(stage) { stage = it }
        Spacer(Modifier.height(12.dp))

        Card(
            Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            AnimatedContent(targetState = stage, label = "v31Stage") { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    V31StageIntro(current)
                    Spacer(Modifier.height(20.dp))
                    when (current) {
                        V31Stage.SENTENCE -> V31SentenceStage(example)
                        V31Stage.TOKEN -> V31TokenStage(tokens)
                        V31Stage.EMBEDDING -> V31EmbeddingStage(tokens)
                        V31Stage.QKV -> V31QkvStage(tokens, selectedToken) { selectedToken = it }
                        V31Stage.ATTENTION -> V31AttentionStage(tokens, selectedToken) { selectedToken = it }
                        V31Stage.FFN -> V31FfnStage(tokens, selectedToken) { selectedToken = it }
                        V31Stage.PREDICT -> V31PredictStage(
                            example,
                            temperature,
                            { temperature = it },
                            sampled,
                            {
                                val probs = V31Engine.probabilities(example, temperature)
                                val r = Random.nextFloat()
                                var sum = 0f
                                sampled = probs.firstOrNull { (_, p) -> sum += p; r <= sum }?.first?.text
                                    ?: probs.last().first.text
                            }
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    V31NextCard(current.next)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        V31Navigation(
            stage,
            onBack = { if (stage.ordinal > 0) stage = V31Stage.entries[stage.ordinal - 1] },
            onNext = { if (stage.ordinal < V31Stage.entries.lastIndex) stage = V31Stage.entries[stage.ordinal + 1] }
        )
    }
}

@Composable
private fun V31Header() {
    Column(Modifier.fillMaxWidth()) {
        Text("AIのキモチ", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("文章が生まれるまでを、内部処理までじっくり理解する。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun V31ExamplePicker(examples: List<V31Example>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text("例文を選ぶ", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEachIndexed { index, e ->
                val active = index == selected
                Card(
                    Modifier.clickable { onSelect(index) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .20f) else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                        Text(e.shortName, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(e.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V31StageRail(stage: V31Stage, onStage: (V31Stage) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V31Stage.entries.forEach { item ->
            val active = item == stage
            Card(
                Modifier.clickable { onStage(item) },
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .20f) else MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(item.title, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    Text(item.technical, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun V31StageIntro(stage: V31Stage) {
    Text(stage.title.substringAfter(". "), color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
    Text(stage.technical, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(stage.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable
private fun V31SentenceStage(example: V31Example) {
    Text("いまAIに渡す文章", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V31BigSentence(example.text)
    Spacer(Modifier.height(14.dp))
    V31InfoCard("なぜ文字のままではダメ？", "ニューラルネットワークが直接計算できるのは数値です。そこで文章をTokenへ分け、Token ID、Embeddingという順に数値表現へ変換します。")
    Spacer(Modifier.height(12.dp))
    V31InfoCard("覚えておくポイント", "文章生成は完成文を一度に作るのではなく、次のTokenを1個予測する処理を何度も繰り返して進みます。")
}

@Composable
private fun V31TokenStage(tokens: List<V31Token>) {
    Text("文章をTokenへ分割", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V31TokenRow(tokens, null) {}
    Spacer(Modifier.height(14.dp))
    V31InfoCard("今回の分割", tokens.joinToString("  /  ") { it.text })
    Spacer(Modifier.height(14.dp))
    Text("Token ID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("モデル内部では各Tokenが整数IDに置き換わり、そのIDを使ってEmbedding表からベクトルを取り出します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(8.dp))
    tokens.forEach { V31KeyValueRow("${it.index + 1}. ${it.text}", it.id.toString()) }
}

@Composable
private fun V31EmbeddingStage(tokens: List<V31Token>) {
    Text("Tokenをベクトル空間へ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Text("点・ラベル・下のTokenカードは同じ色で対応しています。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)) {
                val w = maxWidth
                val h = maxHeight
                tokens.forEachIndexed { index, t ->
                    val px = (.08f + (t.vector[0] + 1f) * .37f).coerceIn(.04f, .78f)
                    val py = (.06f + (t.vector[1] + 1f) * .35f).coerceIn(.05f, .78f)
                    Row(Modifier.offset(x = w * px, y = h * py), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(v31TokenColor(index)))
                        Spacer(Modifier.width(5.dp))
                        Text(t.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("※ 本物のEmbeddingは数百〜数千次元です。ここでは見えるよう3次元相当に縮めた教育用表示です。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    V31TokenRow(tokens, null) {}
    Spacer(Modifier.height(16.dp))
    V31EmbeddingDistanceCard()
    Spacer(Modifier.height(14.dp))
    Text("この例文の教育用ベクトル", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("実モデルではもっと多くの次元を使います。ここでは仕組みを見るため3要素に縮めています。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Spacer(Modifier.height(7.dp))
    tokens.forEach { V31KeyValueRow(it.text, it.vector.joinToString(prefix = "[", postfix = "]") { v -> "%.2f".format(v) }) }
}

@Composable
private fun V31EmbeddingDistanceCard() {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("ベクトルの『近い・遠い』とは？", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("意味が近い例", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            V31DistanceDiagram("リンゴ", "梨", true)
            Text("どちらも果物で、似た文脈に現れやすいため、ベクトルも近い関係になりやすいです。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Text("意味が遠い例", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            V31DistanceDiagram("リンゴ", "バス", false)
            Text("意味や使われ方が大きく異なるため、Embedding空間でも離れた関係になりやすいです。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text("※ 距離はモデル・学習データ・文脈によって変化します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun V31DistanceDiagram(left: String, right: String, near: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            Text(left, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
        }
        Spacer(Modifier.width(if (near) 24.dp else 8.dp))
        Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (near) .75f else .25f)))
        Spacer(Modifier.width(if (near) 24.dp else 8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(if (near) MaterialTheme.colorScheme.secondary else Color(0xFF82E2A8)))
            Text(right, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
        }
    }
    Text(if (near) "← 近い →" else "←──────── 遠い ────────→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun V31QkvStage(tokens: List<V31Token>, selectedToken: Int, onSelectedToken: (Int) -> Unit) {
    val token = tokens[selectedToken]
    val qkv = remember(token) { V31Engine.qkv(token) }
    Text("どのTokenのQ・K・Vを見る？", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V31TokenRow(tokens, selectedToken, onSelectedToken)
    Spacer(Modifier.height(14.dp))
    V31InfoCard("Q・K・Vは3つの別単語ではない", "同じTokenのEmbeddingへ、それぞれ別の学習済み行列Wq・Wk・Wvを掛けて作った3種類のベクトルです。役割だけが異なります。")
    Spacer(Modifier.height(14.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("「${token.text}」のEmbedding", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            V31VectorPill(token.vector, v31TokenColor(selectedToken))
            Spacer(Modifier.height(10.dp))
            Text("↓ 3つの役割へ線形変換", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            V31QkvRoleCard("Q", "Query", "何を探している？", "このTokenが文章の中から、どんな情報を取り込みたいかを表します。", qkv.q, Color(0xFF62D6FF), "Q = XWq")
            Spacer(Modifier.height(10.dp))
            V31QkvRoleCard("K", "Key", "私はどんな情報？", "ほかのTokenのQueryと照合される特徴です。QとKの相性がAttentionの強さへつながります。", qkv.k, Color(0xFFB7A7FF), "K = XWk")
            Spacer(Modifier.height(10.dp))
            V31QkvRoleCard("V", "Value", "実際に渡す中身", "QとKで重要だと判断されたあと、実際に重み付きで足し合わせられる情報です。", qkv.v, Color(0xFFFFB36B), "V = XWv")
        }
    }
    Spacer(Modifier.height(14.dp))
    V31InfoCard("計算の流れ", "QとKは『誰をどれくらい見るか』を決めるために使い、Vは『見つけた相手から何を受け取るか』に使います。つまりQ/Kは検索、Vは中身という分担です。")
}

@Composable
private fun V31QkvRoleCard(letter: String, name: String, label: String, detail: String, vector: List<Float>, color: Color, formula: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    Text(letter, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("$name：$label", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(formula, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            V31VectorPill(vector, color)
        }
    }
}

@Composable
private fun V31VectorPill(vector: List<Float>, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(vector.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
    }
}

@Composable
private fun V31AttentionStage(tokens: List<V31Token>, selectedToken: Int, onSelectedToken: (Int) -> Unit) {
    val weights = remember(tokens, selectedToken) { V31Engine.attention(tokens, selectedToken) }
    val selectedColor = v31TokenColor(selectedToken)
    Text("どのTokenを基準に見る？", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V31TokenRow(tokens, selectedToken, onSelectedToken)
    Spacer(Modifier.height(14.dp))
    V31InfoCard("まずQとKを比較する", "選択TokenのQueryと、文章中のすべてのTokenのKeyを比較します。スコアをSoftmaxへ通すと、合計100%になるAttention Weightが得られます。")
    Spacer(Modifier.height(14.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("「${tokens[selectedToken].text}」は、どのTokenをどれくらい参考にする？", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Self-Attentionなので、自分自身も参照対象です。線が太いほど、そのTokenのValueが強く取り込まれるイメージです。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(165.dp)) {
                val count = tokens.size.coerceAtLeast(1)
                val denom = (count - 1).coerceAtLeast(1)
                val xs = List(count) { i -> size.width * (.10f + .80f * i / denom) }
                val bottom = size.height * .78f
                val top = size.height * .20f
                weights.forEachIndexed { index, w ->
                    if (index != selectedToken) {
                        drawLine(
                            color = v31TokenColor(index).copy(alpha = (.30f + w * 1.7f).coerceIn(.30f, 1f)),
                            start = Offset(xs[selectedToken], bottom),
                            end = Offset(xs[index], top),
                            strokeWidth = 5f + w * 22f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                xs.forEachIndexed { index, x ->
                    drawCircle(color = v31TokenColor(index), radius = if (index == selectedToken) 18f else 13f, center = Offset(x, bottom))
                    if (index == selectedToken) {
                        drawCircle(color = selectedColor.copy(alpha = .85f), radius = 28f, center = Offset(x, bottom), style = Stroke(width = 4f + weights[index] * 14f))
                    }
                }
            }
            Text("○ 外側のリング = 自分自身へのAttention", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            weights.forEachIndexed { index, value ->
                V31PercentageBar(if (index == selectedToken) "${tokens[index].text}（自分自身）" else tokens[index].text, value, v31TokenColor(index))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    V31InfoCard("数式で見る", "代表的には Attention(Q,K,V) = softmax(QKᵀ / √dₖ)V です。QKᵀで関連度を測り、Softmaxで重みにし、その重みでVを混ぜます。")
    Spacer(Modifier.height(12.dp))
    V31InfoCard("Multi-Head Attention", "実際のTransformerではQ/K/Vを複数組に分け、複数のHeadで同時にAttentionを計算します。Headごとに異なる関係を捉えられるため、文法・位置・意味など複数の手掛かりを並行して扱えます。")
}

@Composable
private fun V31FfnStage(tokens: List<V31Token>, selectedToken: Int, onSelectedToken: (Int) -> Unit) {
    val token = tokens[selectedToken]
    val input = remember(token) { V31Engine.ffnInput(token) }
    val hidden = remember(token) { V31Engine.ffnHidden(token) }
    val output = remember(token) { V31Engine.ffnOutput(token) }
    val color = v31TokenColor(selectedToken)
    Text("どのTokenのFFNを見る？", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V31TokenRow(tokens, selectedToken, onSelectedToken)
    Spacer(Modifier.height(14.dp))
    V31InfoCard("Attentionとの役割分担", "Attentionは他Tokenから情報を集めます。FFNはその結果を各Tokenごとに独立して加工します。同じFFNの重みが全Tokenへ共有されますが、入力が違うため出力もTokenごとに異なります。")
    Spacer(Modifier.height(14.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("「${token.text}」を1Tokenずつ加工", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            V31FfnBlock("入力", "Attention後の表現", input, color)
            Text("↓", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            V31FfnProcessBox("① 高次元へ広げる", "Linear / W₁", "特徴をより大きな内部空間へ写し、組み合わせを扱いやすくします。")
            Text("↓", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            V31FfnProcessBox("② 非線形変換", "Activation", "単なる線形変換だけでは表現できない複雑なパターンを作れるようにします。")
            Spacer(Modifier.height(8.dp))
            V31VectorPill(hidden, MaterialTheme.colorScheme.secondary)
            Text("↓", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            V31FfnProcessBox("③ 元の次元へ戻す", "Linear / W₂", "加工した特徴を次のTransformer層へ渡せる大きさへ戻します。")
            Text("↓", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            V31FfnBlock("出力", "加工されたToken表現", output, MaterialTheme.colorScheme.tertiary)
        }
    }
    Spacer(Modifier.height(14.dp))
    V31InfoCard("代表的な式", "FFN(x) = W₂ · activation(W₁x + b₁) + b₂ のように書けます。実際のLLMではGELUやSwiGLUなど、モデルによってActivationやゲート構造が異なります。")
    Spacer(Modifier.height(12.dp))
    V31InfoCard("なぜ何層も重ねる？", "Attention → FFNを1つのTransformer層として繰り返すたび、Token表現は少しずつ文脈に適したものへ変わります。大規模モデルではこの層を何十層、場合によってはそれ以上重ねます。")
}

@Composable
private fun V31FfnProcessBox(title: String, technical: String, description: String) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(technical, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun V31FfnBlock(title: String, subtitle: String, vector: List<Float>, color: Color) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .14f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = color, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(7.dp))
            V31VectorPill(vector, color)
        }
    }
}

@Composable
private fun V31PredictStage(example: V31Example, temperature: Float, onTemperature: (Float) -> Unit, sampled: String?, onSample: () -> Unit) {
    val probs = remember(example, temperature) { V31Engine.probabilities(example, temperature) }
    Text("語彙の候補へ確率をつける", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V31InfoCard("Logitとは？", "モデルが各Token候補へ付ける、生の点数です。Logitそのものは確率ではありません。Softmaxへ通すことで、候補全体の合計が100%になる確率分布へ変換できます。")
    Spacer(Modifier.height(12.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("Temperature  ${"%.1f".format(temperature)}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("低いほど上位候補へ集中し、高いほど分布が平らになります。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Slider(value = temperature, onValueChange = onTemperature, valueRange = .1f..2f)
            probs.forEach { (candidate, probability) ->
                V31CandidateBar(candidate, probability)
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("この確率分布から1Token選ぶ") }
        }
    }
    if (sampled != null) {
        Spacer(Modifier.height(14.dp))
        Text("選ばれたTokenを追加", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        V31BigSentence("${example.text}$sampled")
        Spacer(Modifier.height(12.dp))
        V31InfoCard("生成はここで終わらない", "追加されたTokenを含む新しい文章をもう一度モデルへ入れ、次のTokenを予測します。このループを繰り返して文章が伸びていきます。")
    }
}

@Composable
private fun V31NextCard(text: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .13f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("次に何が起きる？", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun V31BigSentence(text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 25.dp), contentAlignment = Alignment.Center) {
            Text("「$text」", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun V31InfoCard(title: String, text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun V31TokenRow(tokens: List<V31Token>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.forEachIndexed { index, token ->
            val active = selected == index
            val color = v31TokenColor(index)
            Card(
                modifier = if (selected != null) Modifier.clickable { onSelect(index) } else Modifier,
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (active) .30f else .15f))
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
private fun V31PercentageBar(label: String, value: Float, color: Color) {
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
private fun V31CandidateBar(candidate: V31Candidate, probability: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(candidate.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text("${(probability * 100).toInt()}%  logit ${"%.1f".format(candidate.logit)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth(probability.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(99.dp)).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))))
    }
}

@Composable
private fun V31KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun V31Navigation(stage: V31Stage, onBack: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack, enabled = stage.ordinal > 0, modifier = Modifier.weight(1f)) { Text("← BACK") }
        OutlinedButton(onClick = onNext, enabled = stage.ordinal < V31Stage.entries.lastIndex, modifier = Modifier.weight(1f)) { Text("NEXT →") }
    }
}
