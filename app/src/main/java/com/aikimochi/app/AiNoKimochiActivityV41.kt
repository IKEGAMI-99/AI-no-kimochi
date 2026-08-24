package com.aikimochi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class AiNoKimochiActivityV41 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V41Theme { V41App() } }
    }
}

private val V41Colors = darkColorScheme(
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

private val V41TokenColors = listOf(
    Color(0xFFFFC56D), Color(0xFF7CD6FF), Color(0xFFB7A7FF),
    Color(0xFF82E2A8), Color(0xFFFF9FC7), Color(0xFFFFA878)
)

private fun v41TokenColor(index: Int): Color = V41TokenColors[index % V41TokenColors.size]

@Composable
private fun V41Theme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = V41Colors, content = content)

private data class V41Token(val text: String, val id: Int, val index: Int, val vector: List<Float>)
private data class V41Candidate(val text: String, val logit: Float)
private data class V41Qkv(val q: List<Float>, val k: List<Float>, val v: List<Float>)
private data class V41FilteredCandidate(
    val candidate: V41Candidate,
    val temperatureProbability: Float,
    val samplingProbability: Float,
    val enabled: Boolean,
    val reason: String
)
private data class V41GlossaryTerm(val name: String, val english: String, val summary: String, val detail: String, val related: String = "")
private data class V41GlossaryCategory(val title: String, val subtitle: String, val terms: List<V41GlossaryTerm>)

private enum class V41Page(val title: String, val technical: String, val explanation: String, val next: String) {
    SENTENCE(
        "1. 文章", "Text Input",
        "まず文章をAIへ入力します。この時点ではまだ人間が読む文字列です。LLM内部では、文字列をそのまま計算するのではなく、Token IDやEmbeddingなどの数値表現へ段階的に変換します。文章生成も完成文を一度に作るのではなく、次のTokenを1個ずつ予測する処理を繰り返します。",
        "文章をTokenという小さな単位へ分けます。"
    ),
    TOKEN(
        "2. Token", "Tokenization",
        "文章をTokenという処理単位へ分割します。Tokenは必ずしも単語1個ではなく、文字の一部、助詞、記号になることもあります。各Tokenには整数のToken IDが割り当てられ、モデルはそのIDからEmbedding表のベクトルを取り出します。",
        "各TokenをEmbeddingベクトルへ変換します。"
    ),
    EMBEDDING(
        "3. Embedding", "Embedding",
        "Token IDは単なる番号なので意味上の近さを表しにくいため、各Tokenを多次元ベクトルへ変換します。似た文脈で使われるTokenほど、ベクトル空間でも近い関係になりやすくなります。意味は1本の軸ではなく、多数の次元へ分散して表現されます。",
        "同じEmbeddingからQuery・Key・Valueを作ります。"
    ),
    QKV(
        "4. Q・K・V", "Query / Key / Value",
        "Attentionを計算するため、各TokenのEmbeddingをWq・Wk・Wvで3方向へ変換します。Queryは『どんな情報を探したいか』、Keyは『自分はどんな特徴を持つか』、Valueは『実際に渡す情報』です。3つは別の単語ではなく、同じTokenを異なる役割で表したベクトルです。",
        "QueryとKeyを比較してAttention Weightを作ります。"
    ),
    ATTENTION(
        "5. Attention", "Self-Attention",
        "あるTokenのQueryと文章中の全TokenのKeyを比較し、どのTokenをどれくらい参考にするかを決めます。Self-Attentionでは自分自身も参照します。そのWeightでValueを混ぜることで、Token表現へ周囲の文脈を取り込みます。",
        "文脈を取り込んだ各TokenをFFNで個別に加工します。"
    ),
    FFN(
        "6. FFN", "Feed Forward Network",
        "Attentionが他Tokenから情報を集める処理なら、FFNは集めた情報を各Token内部で加工する処理です。一般的には一度高次元へ広げ、非線形なActivationを通し、元に近い次元へ戻します。AttentionとFFNを何層も重ねることで表現が洗練されます。",
        "最終表現から語彙全体のLogitを計算し、Sampling候補を絞ります。"
    ),
    PREDICT(
        "7. 次Token予測", "Temperature / Top-K / Top-P / Sampling",
        "語彙の各Token候補へLogitを付け、Temperatureで確率分布を調整します。その後Top-Kで上位K個へ候補を絞り、Top-Pで累積確率が指定値に達する最小の候補集合へさらに絞ります。最後に残った候補だけを再正規化し、その確率から次TokenをSamplingします。",
        "ここまでが文章生成の1サイクルです。最後のページにはAI用語辞典があります。"
    ),
    GLOSSARY(
        "8. AI用語辞典", "AI Glossary",
        "AI・LLMで頻出する用語をカテゴリ別にまとめています。カテゴリを選び、用語カードをタップすると詳しい説明を確認できます。",
        ""
    )
}

private object V41Engine {
    private val dictionary = listOf("ソファ", "寝ている", "猫", "は", "で").sortedByDescending { it.length }

    fun tokenize(text: String): List<V41Token> {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < text.length && parts.size < 16) {
            val match = dictionary.firstOrNull { text.startsWith(it, i) }
            if (match != null) { parts += match; i += match.length } else { parts += text[i].toString(); i++ }
        }
        return parts.mapIndexed { index, part ->
            val seed = part.hashCode()
            val positive = if (seed == Int.MIN_VALUE) 0 else abs(seed)
            val vector = List(3) { dim -> sin(seed * (dim + 2) * 0.00061 + index * 0.43).toFloat() }
            V41Token(part, 100 + positive % 50000, index, vector)
        }
    }

    fun qkv(token: V41Token): V41Qkv {
        val x = token.vector
        fun t(a: Float, b: Float, c: Float) = listOf(
            (x[0] * a + x[1] * b + x[2] * c).coerceIn(-1.5f, 1.5f),
            (x[0] * c - x[1] * a + x[2] * b).coerceIn(-1.5f, 1.5f),
            (x[0] * b + x[1] * c - x[2] * a).coerceIn(-1.5f, 1.5f)
        )
        return V41Qkv(t(.78f, .24f, -.16f), t(-.18f, .83f, .31f), t(.29f, -.12f, .91f))
    }

    fun attention(tokens: List<V41Token>, queryIndex: Int): List<Float> {
        val query = tokens[queryIndex]
        val raw = tokens.mapIndexed { index, token ->
            val pair = setOf(query.text, token.text)
            val semantic = when {
                pair.contains("猫") && (pair.contains("寝ている") || pair.contains("ソファ")) -> 1.6f
                query.text == token.text -> .7f
                else -> .15f
            }
            semantic + 1f / (1f + abs(index - queryIndex)) + (token.id % 9) / 40f
        }
        val e = raw.map { exp(it.toDouble()).toFloat() }
        val sum = e.sum().coerceAtLeast(.0001f)
        return e.map { it / sum }
    }

    fun ffnInput(token: V41Token) = qkv(token).v
    fun ffnHidden(token: V41Token): List<Float> {
        val x = ffnInput(token)
        return listOf(
            maxOf(0f, x[0] * 1.2f + x[1] * .35f), maxOf(0f, x[1] * 1.1f - x[2] * .28f),
            maxOf(0f, x[2] * 1.25f + x[0] * .22f), maxOf(0f, (x[0] + x[1] + x[2]) * .55f),
            maxOf(0f, (x[0] - x[1]) * .72f), maxOf(0f, (x[2] - x[0]) * .68f)
        )
    }
    fun ffnOutput(token: V41Token): List<Float> {
        val h = ffnHidden(token)
        return listOf(
            (h[0] * .46f + h[2] * .31f - h[4] * .18f).coerceIn(-1.5f, 1.5f),
            (h[1] * .41f + h[3] * .29f + h[5] * .20f).coerceIn(-1.5f, 1.5f),
            (h[2] * .38f + h[4] * .27f - h[0] * .14f).coerceIn(-1.5f, 1.5f)
        )
    }

    fun candidates() = listOf(
        V41Candidate("。", 3.05f), V41Candidate("よ", 2.45f), V41Candidate("ね", 2.20f),
        V41Candidate("ところ", 1.98f), V41Candidate("姿", 1.82f), V41Candidate("時間", 1.65f),
        V41Candidate("だけ", 1.48f), V41Candidate("ようだ", 1.31f), V41Candidate("ので", 1.17f),
        V41Candidate("静かに", 1.02f), V41Candidate("らしい", .88f), V41Candidate("けれど", .73f),
        V41Candidate("今日", .56f), V41Candidate("部屋", .39f), V41Candidate("突然", .18f)
    )

    fun temperatureProbabilities(temperature: Float): List<Pair<V41Candidate, Float>> {
        val safe = temperature.coerceIn(.1f, 2f)
        val candidates = candidates()
        val e = candidates.map { exp((it.logit / safe).toDouble()).toFloat() }
        val sum = e.sum().coerceAtLeast(.0001f)
        return candidates.zip(e.map { it / sum }).sortedByDescending { it.second }
    }

    fun filteredDistribution(temperature: Float, topK: Int, topP: Float): List<V41FilteredCandidate> {
        val all = temperatureProbabilities(temperature)
        val k = topK.coerceIn(1, all.size)
        val topKItems = all.take(k)
        val topKNames = topKItems.map { it.first.text }.toSet()
        val topKSum = topKItems.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(.0001f)
        val topKNormalized = topKItems.map { it.first to (it.second / topKSum) }

        val nucleus = mutableSetOf<String>()
        var cumulative = 0f
        val p = topP.coerceIn(.1f, 1f)
        for ((candidate, probability) in topKNormalized) {
            if (nucleus.isEmpty() || cumulative < p) {
                nucleus += candidate.text
                cumulative += probability
            }
        }

        val activeBeforeRenorm = topKNormalized.filter { it.first.text in nucleus }
        val activeSum = activeBeforeRenorm.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(.0001f)
        val finalMap = activeBeforeRenorm.associate { it.first.text to (it.second / activeSum) }

        return all.map { (candidate, tempProbability) ->
            when {
                candidate.text !in topKNames -> V41FilteredCandidate(candidate, tempProbability, 0f, false, "Top-Kで除外")
                candidate.text !in nucleus -> V41FilteredCandidate(candidate, tempProbability, 0f, false, "Top-Pで除外")
                else -> V41FilteredCandidate(candidate, tempProbability, finalMap[candidate.text] ?: 0f, true, "Sampling対象")
            }
        }
    }
}

@Composable
private fun V41App() {
    val example = "猫はソファで寝ている"
    val tokens = remember { V41Engine.tokenize(example) }
    var selectedToken by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableFloatStateOf(1f) }
    var topK by remember { mutableIntStateOf(8) }
    var topP by remember { mutableFloatStateOf(.90f) }
    var sampled by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { V41Page.entries.size })
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06101E), Color(0xFF09172B), Color(0xFF06101E))))
            .statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        V41Header()
        Spacer(Modifier.height(12.dp))
        V41StageRail(pagerState.currentPage) { page -> scope.launch { pagerState.animateScrollToPage(page) } }
        Spacer(Modifier.height(10.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { pageIndex ->
            val page = V41Page.entries[pageIndex]
            Card(
                Modifier.fillMaxSize().padding(horizontal = 2.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    V41PageIntro(page)
                    Spacer(Modifier.height(20.dp))
                    when (page) {
                        V41Page.SENTENCE -> V41SentenceStage(example)
                        V41Page.TOKEN -> V41TokenStage(tokens)
                        V41Page.EMBEDDING -> V41EmbeddingStage(tokens)
                        V41Page.QKV -> V41QkvStage(tokens, selectedToken) { selectedToken = it }
                        V41Page.ATTENTION -> V41AttentionStage(tokens, selectedToken) { selectedToken = it }
                        V41Page.FFN -> V41FfnStage(tokens, selectedToken) { selectedToken = it }
                        V41Page.PREDICT -> V41PredictStage(
                            example, temperature, { temperature = it }, topK, { topK = it }, topP, { topP = it }, sampled
                        ) {
                            val active = V41Engine.filteredDistribution(temperature, topK, topP).filter { it.enabled }
                            val r = Random.nextFloat()
                            var cumulative = 0f
                            sampled = active.firstOrNull { item ->
                                cumulative += item.samplingProbability
                                r <= cumulative
                            }?.candidate?.text ?: active.lastOrNull()?.candidate?.text
                        }
                        V41Page.GLOSSARY -> V41GlossaryStage()
                    }
                    if (page != V41Page.GLOSSARY) {
                        Spacer(Modifier.height(22.dp))
                        V41NextCard(page.next)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("←  横へスワイプしてページ移動  →", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable private fun V41Header() {
    Column(Modifier.fillMaxWidth()) {
        Text("AIのキモチ", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Transformerの中を、1ページずつ横にたどる。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable private fun V41StageRail(currentPage: Int, onPage: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(currentPage) { state.animateScrollToItem(currentPage) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(V41Page.entries) { index, item ->
            val active = index == currentPage
            Card(
                Modifier.clickable { onPage(index) }, shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .20f) else MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
                    Text(item.title, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    Text(item.technical, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable private fun V41PageIntro(page: V41Page) {
    Text(page.title.substringAfter(". "), color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
    Text(page.technical, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(page.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable private fun V41SentenceStage(example: String) {
    Text("今回たどる例文", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp)); V41BigSentence(example); Spacer(Modifier.height(14.dp))
    V41InfoCard("生成の基本", "LLMは完成文を一度に生成せず、現在までのToken列から次のTokenを予測し、追加してまた予測する処理を繰り返します。")
}

@Composable private fun V41TokenStage(tokens: List<V41Token>) {
    Text("文章をTokenへ分割", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
    V41TokenRow(tokens, null) {}; Spacer(Modifier.height(14.dp)); V41InfoCard("今回の分割", tokens.joinToString("  /  ") { it.text })
    Spacer(Modifier.height(14.dp)); Text("Token ID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    tokens.forEach { V41KeyValueRow("${it.index + 1}. ${it.text}", it.id.toString()) }
}

@Composable private fun V41EmbeddingStage(tokens: List<V41Token>) {
    Text("Tokenをベクトル空間へ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)) {
                val w = maxWidth; val h = maxHeight
                tokens.forEachIndexed { index, t ->
                    val px = (.08f + (t.vector[0] + 1f) * .37f).coerceIn(.04f, .78f)
                    val py = (.06f + (t.vector[1] + 1f) * .35f).coerceIn(.05f, .78f)
                    Row(Modifier.offset(x = w * px, y = h * py), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(v41TokenColor(index))); Spacer(Modifier.width(5.dp))
                        Text(t.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("※ 実際のEmbeddingは数百〜数千次元。ここでは教育用に3要素へ縮めています。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(14.dp)); V41InfoCard("近い例", "リンゴ ↔ 梨：似た文脈で使われやすいため近い関係になりやすい。")
    Spacer(Modifier.height(10.dp)); V41InfoCard("遠い例", "リンゴ ↔ バス：意味や使われ方が違うため離れた関係になりやすい。")
}

@Composable private fun V41QkvStage(tokens: List<V41Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val token = tokens[selectedToken]; val qkv = remember(token) { V41Engine.qkv(token) }
    Text("Tokenを選んでQ・K・Vを見る", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); V41TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp)); V41QkvRole("Q", "Query", "何を探したい？", qkv.q, Color(0xFF62D6FF), "Q = XWq")
    Spacer(Modifier.height(10.dp)); V41QkvRole("K", "Key", "私はどんな特徴？", qkv.k, Color(0xFFB7A7FF), "K = XWk")
    Spacer(Modifier.height(10.dp)); V41QkvRole("V", "Value", "実際に渡す情報", qkv.v, Color(0xFFFFB36B), "V = XWv")
}

@Composable private fun V41QkvRole(letter: String, name: String, label: String, vector: List<Float>, color: Color, formula: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) { Text(letter, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(10.dp)); Column { Text("$name：$label", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Text(formula, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(9.dp)); V41VectorPill(vector, color)
        }
    }
}

@Composable private fun V41AttentionStage(tokens: List<V41Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val weights = remember(tokens, selectedToken) { V41Engine.attention(tokens, selectedToken) }
    val selectedColor = v41TokenColor(selectedToken)
    Text("基準にするTokenをタップ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); V41TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("「${tokens[selectedToken].text}」が参考にする割合", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(165.dp)) {
                val count = tokens.size; val denom = (count - 1).coerceAtLeast(1)
                val xs = List(count) { i -> size.width * (.10f + .80f * i / denom) }
                val bottom = size.height * .78f; val top = size.height * .20f
                weights.forEachIndexed { index, w -> if (index != selectedToken) drawLine(color = v41TokenColor(index).copy(alpha = (.30f + w * 1.7f).coerceIn(.30f, 1f)), start = Offset(xs[selectedToken], bottom), end = Offset(xs[index], top), strokeWidth = 5f + w * 22f, cap = StrokeCap.Round) }
                xs.forEachIndexed { index, x ->
                    drawCircle(v41TokenColor(index), if (index == selectedToken) 18f else 13f, Offset(x, bottom))
                    if (index == selectedToken) drawCircle(selectedColor.copy(alpha = .85f), 28f, Offset(x, bottom), style = Stroke(width = 4f + weights[index] * 14f))
                }
            }
            weights.forEachIndexed { index, value -> V41PercentageBar(if (index == selectedToken) "${tokens[index].text}（自分自身）" else tokens[index].text, value, v41TokenColor(index)); Spacer(Modifier.height(8.dp)) }
        }
    }
    Spacer(Modifier.height(14.dp)); V41InfoCard("代表式", "Attention(Q,K,V) = softmax(QKᵀ / √dₖ)V")
}

@Composable private fun V41FfnStage(tokens: List<V41Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val token = tokens[selectedToken]; val input = V41Engine.ffnInput(token); val hidden = V41Engine.ffnHidden(token); val output = V41Engine.ffnOutput(token)
    Text("Tokenを選んでFFNを見る", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); V41TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp)); V41FfnBlock("入力", "Attention後の表現", input, v41TokenColor(selectedToken))
    Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
    V41InfoCard("① Linear / W₁", "一度高次元へ広げ、特徴を組み合わせやすくします。")
    Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
    V41InfoCard("② Activation", "非線形処理で複雑なパターンを表現します。")
    Spacer(Modifier.height(8.dp)); V41VectorPill(hidden, MaterialTheme.colorScheme.secondary)
    Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
    V41InfoCard("③ Linear / W₂", "次の層へ渡せる次元へ戻します。")
    Spacer(Modifier.height(10.dp)); V41FfnBlock("出力", "加工されたToken表現", output, MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun V41PredictStage(
    example: String,
    temperature: Float,
    onTemperature: (Float) -> Unit,
    topK: Int,
    onTopK: (Int) -> Unit,
    topP: Float,
    onTopP: (Float) -> Unit,
    sampled: String?,
    onSample: () -> Unit
) {
    val distribution = remember(temperature, topK, topP) { V41Engine.filteredDistribution(temperature, topK, topP) }
    val activeCount = distribution.count { it.enabled }

    V41InfoCard("3段階で候補を絞る", "① Temperatureで確率の尖り方を変える → ② Top-Kで上位K個だけ残す → ③ Top-Pで累積確率pまでの候補だけ残す。最後に残った候補だけを合計100%へ再正規化してSamplingします。")
    Spacer(Modifier.height(14.dp))

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("Temperature  ${"%.1f".format(temperature)}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("低いほど上位へ集中、高いほど分布が平らになります。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Slider(value = temperature, onValueChange = onTemperature, valueRange = .1f..2f)

            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Top-K", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("上位 $topK / ${V41Engine.candidates().size} 個", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("確率順位の上位K個以外を候補から外します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Slider(
                value = topK.toFloat(),
                onValueChange = { onTopK(it.roundToInt().coerceIn(1, V41Engine.candidates().size)) },
                valueRange = 1f..V41Engine.candidates().size.toFloat(),
                steps = V41Engine.candidates().size - 2
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Top-P", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("${(topP * 100).roundToInt()}%", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Text("上位から確率を足し、累積確率がPに達する最小の候補集合だけを残します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            Slider(value = topP, onValueChange = onTopP, valueRange = .1f..1f, steps = 17)

            Spacer(Modifier.height(6.dp))
            V41InfoCard("現在のSampling対象", "$activeCount 個のTokenが残っています。グレーの候補は表示だけ残し、Samplingからは除外されています。")
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("候補Token（${distribution.size}個）", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Text("明るい候補だけがSampling対象。除外理由も表示します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Spacer(Modifier.height(9.dp))
    distribution.forEachIndexed { index, item ->
        V41SamplingCandidateCard(index + 1, item)
        Spacer(Modifier.height(8.dp))
    }

    Button(onClick = onSample, modifier = Modifier.fillMaxWidth(), enabled = activeCount > 0) { Text("残った候補から1Token選ぶ") }

    if (sampled != null) {
        Spacer(Modifier.height(14.dp)); Text("選ばれたToken", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp)); V41BigSentence("$example$sampled")
    }
}

@Composable private fun V41SamplingCandidateCard(rank: Int, item: V41FilteredCandidate) {
    val active = item.enabled
    val textColor = if (active) MaterialTheme.colorScheme.onSurface else Color(0xFF778394)
    val secondary = if (active) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF66717F)
    val barColor = if (active) MaterialTheme.colorScheme.primary else Color(0xFF5C6572)
    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF111C2B)),
        modifier = Modifier.fillMaxWidth().alpha(if (active) 1f else .72f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$rank", color = secondary, fontSize = 11.sp, modifier = Modifier.width(22.dp))
                    Text(item.candidate.text, color = textColor, fontWeight = FontWeight.Bold)
                }
                Text(item.reason, color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF7A8490), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temp後 ${(item.temperatureProbability * 100).let { "%.1f".format(it) }}%", color = secondary, fontSize = 10.sp)
                Text(if (active) "最終 ${(item.samplingProbability * 100).let { "%.1f".format(it) }}%" else "Sampling 0%", color = secondary, fontSize = 10.sp)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF0B1421))) {
                Box(Modifier.fillMaxWidth(item.temperatureProbability.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(99.dp)).background(barColor))
            }
        }
    }
}

@Composable private fun V41GlossaryStage() {
    val categories = remember { v41GlossaryCategories() }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var selectedTerm by remember { mutableStateOf<V41GlossaryTerm?>(null) }

    Text("カテゴリ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(categories) { index, category ->
            val active = index == categoryIndex
            Card(
                Modifier.clickable { categoryIndex = index }, shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceVariant)
            ) { Text(category.title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) }
        }
    }
    Spacer(Modifier.height(14.dp)); Text(categories[categoryIndex].title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
    Text(categories[categoryIndex].subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Spacer(Modifier.height(12.dp))
    categories[categoryIndex].terms.forEach { term ->
        Card(Modifier.fillMaxWidth().clickable { selectedTerm = term }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp)) {
                Text(term.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(term.english, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp)); Text(term.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(5.dp)); Text("タップして詳細 →", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(9.dp))
    }

    selectedTerm?.let { term ->
        AlertDialog(
            onDismissRequest = { selectedTerm = null },
            title = { Column { Text(term.name); Text(term.english, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) } },
            text = { Column { Text(term.detail, lineHeight = 21.sp); if (term.related.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text("関連：${term.related}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } } },
            confirmButton = { TextButton(onClick = { selectedTerm = null }) { Text("閉じる") } }
        )
    }
}

private fun v41GlossaryCategories() = listOf(
    V41GlossaryCategory("基礎概念", "AI全体の土台になる言葉。", listOf(
        V41GlossaryTerm("AI", "Artificial Intelligence", "人間の知的作業を機械で実現する技術の総称。", "認識・予測・生成・計画などをコンピュータで実現する広い概念です。", "機械学習、生成AI"),
        V41GlossaryTerm("LLM", "Large Language Model", "大量の文章から学習した大規模言語モデル。", "Token列から次Tokenを予測することで文章生成・要約・翻訳などを行います。", "Transformer、Token"),
        V41GlossaryTerm("生成AI", "Generative AI", "文章・画像・音声などを生成するAI。", "学習した統計的パターンを利用して新しいコンテンツを生成します。", "LLM、画像生成")
    )),
    V41GlossaryCategory("モデル内部", "Transformerの中で直接使われる仕組み。", listOf(
        V41GlossaryTerm("Token", "Token", "モデルが文章を扱う単位。", "単語・文字・記号などを表す単位で、生成時も基本的に1Tokenずつ出力します。", "Tokenizer"),
        V41GlossaryTerm("Embedding", "Embedding", "Tokenをベクトルへ変換する仕組み。", "意味や使われ方の関係を多次元ベクトルとして表現します。", "Vector"),
        V41GlossaryTerm("Attention", "Attention", "Token同士の関係へ重みを付ける仕組み。", "QueryとKeyから参照度を決め、その重みでValueを混ぜます。", "Q/K/V"),
        V41GlossaryTerm("FFN", "Feed Forward Network", "各Token内部の特徴を加工するネットワーク。", "Attention後の表現を各Tokenごとに独立して変換します。", "Transformer")
    )),
    V41GlossaryCategory("学習・調整", "モデルを作り、目的へ合わせる方法。", listOf(
        V41GlossaryTerm("Pretraining", "Pretraining", "大量データによる事前学習。", "一般的な言語能力やパターンを大規模データから学びます。", "Fine-tuning"),
        V41GlossaryTerm("Fine-tuning", "Fine-tuning", "学習済みモデルの追加調整。", "特定分野やタスクのデータでモデルを追加学習します。", "SFT、LoRA"),
        V41GlossaryTerm("RLHF", "Reinforcement Learning from Human Feedback", "人間の好みを利用する調整法。", "人間の評価を使い、望ましい応答へモデルを近づけます。", "Preference"),
        V41GlossaryTerm("LoRA", "Low-Rank Adaptation", "少量の追加パラメータで調整する手法。", "元の重みを大きく変えず効率良くFine-tuningできます。", "PEFT")
    )),
    V41GlossaryCategory("推論・生成", "モデルから文章を出すときの重要語。", listOf(
        V41GlossaryTerm("Temperature", "Temperature", "確率分布の尖り方を調整する値。", "低いほど上位候補へ集中し、高いほど候補がばらけます。", "Sampling"),
        V41GlossaryTerm("Top-K", "Top-K Sampling", "上位K個だけを候補に残す方法。", "Temperature適用後の確率順位で上位K個だけを残し、それ以外をSampling対象から除外します。", "Top-P"),
        V41GlossaryTerm("Top-P", "Nucleus Sampling", "累積確率Pまでの候補だけを残す方法。", "上位から確率を足し、指定した累積確率Pに達する最小の候補集合をSampling対象にします。候補数が状況に応じて変わるのがTop-Kとの違いです。", "Top-K、Temperature"),
        V41GlossaryTerm("Logit", "Logit", "Softmax前の生の点数。", "各Token候補へモデルが付けるスコアで、そのままでは確率ではありません。", "Softmax"),
        V41GlossaryTerm("Sampling", "Sampling", "確率分布から次Tokenを選ぶ処理。", "残った候補の確率に従って次のTokenを選択します。", "Temperature、Top-K、Top-P")
    )),
    V41GlossaryCategory("検索・知識", "外部情報をAIへ接続する仕組み。", listOf(
        V41GlossaryTerm("RAG", "Retrieval-Augmented Generation", "検索結果を参照して回答する構成。", "関連文書を検索し、その内容をモデルへ渡して回答を生成します。", "Vector DB"),
        V41GlossaryTerm("Vector DB", "Vector Database", "ベクトル類似検索を行うDB。", "Embeddingベクトルを保存し、質問に近い文書を検索します。", "Embedding Model"),
        V41GlossaryTerm("Chunking", "Chunking", "文書を検索単位へ分割する処理。", "長い文書を段落やToken数などで小さく分割します。", "RAG")
    )),
    V41GlossaryCategory("マルチモーダル", "文章以外の入力・出力を扱うAI。", listOf(
        V41GlossaryTerm("Multimodal", "Multimodal", "文章・画像・音声など複数形式を扱うこと。", "1つのモデルやシステムが複数の情報形式を統合して扱います。", "Vision Encoder"),
        V41GlossaryTerm("Vision Encoder", "Vision Encoder", "画像を特徴量へ変換する部分。", "画像をモデルが扱えるベクトル表現へ変換します。", "Multimodal")
    )),
    V41GlossaryCategory("性能・運用", "速度・容量・実行環境に関する用語。", listOf(
        V41GlossaryTerm("Latency", "Latency", "応答までにかかる遅延時間。", "入力してから出力が返るまでの時間です。", "Throughput"),
        V41GlossaryTerm("KV Cache", "KV Cache", "過去TokenのK/Vを再利用するキャッシュ。", "生成済みTokenのKey/Valueを保存して、次Token生成時の再計算を減らします。", "Attention"),
        V41GlossaryTerm("Quantization", "Quantization", "重みを低精度化して軽量化する方法。", "FP16からINT8/INT4などへ精度を落とし、メモリ使用量と計算量を減らします。", "Local LLM")
    )),
    V41GlossaryCategory("開発ツール", "AI開発でよく使われるツール。", listOf(
        V41GlossaryTerm("Hugging Face", "Hugging Face", "モデルやデータセットの共有基盤。", "AIモデル、データセット、推論ライブラリなどを公開・利用できます。"),
        V41GlossaryTerm("Ollama", "Ollama", "ローカルLLMを手軽に動かすツール。", "対応モデルをローカル環境で取得・実行・管理できます。"),
        V41GlossaryTerm("GitHub", "GitHub", "ソースコード管理・共有サービス。", "Gitリポジトリをホストし、IssuesやActionsなど開発機能も提供します。", "Git")
    )),
    V41GlossaryCategory("主要AI", "代表的な対話AIサービス。", listOf(
        V41GlossaryTerm("ChatGPT", "ChatGPT", "OpenAIの対話AIサービス。", "文章・画像・コードなどを扱う対話型AIサービスです。"),
        V41GlossaryTerm("Gemini", "Gemini", "GoogleのAIモデル・サービス群。", "Googleが開発するマルチモーダルAIモデル群です。"),
        V41GlossaryTerm("Claude", "Claude", "Anthropicの対話AI。", "Anthropicが開発する大規模言語モデル・対話サービスです。"),
        V41GlossaryTerm("Grok", "Grok", "xAIの対話AI。", "xAIが開発する大規模言語モデル・対話サービスです。")
    )),
    V41GlossaryCategory("オープンウェイト", "重みを取得・利用できる代表的モデル群。", listOf(
        V41GlossaryTerm("Llama", "Llama", "Metaのオープンウェイト系モデル。", "研究・開発・ローカル利用などで広く使われるモデル群です。"),
        V41GlossaryTerm("Qwen", "Qwen", "Alibaba系のオープンウェイトモデル群。", "多言語・コード・推論など多様なモデルが公開されています。"),
        V41GlossaryTerm("Mistral", "Mistral", "Mistral AIのモデル群。", "小型から高性能モデルまで複数のモデルが提供されています。"),
        V41GlossaryTerm("DeepSeek", "DeepSeek", "中国発のAIモデル群。", "推論・コードなどを含む複数のモデルが公開されています。")
    ))
)

@Composable private fun V41BigSentence(text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 25.dp), contentAlignment = Alignment.Center) {
            Text("「$text」", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun V41InfoCard(title: String, text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp)); Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable private fun V41NextCard(text: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .13f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("次に何が起きる？", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp)); Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable private fun V41TokenRow(tokens: List<V41Token>, selected: Int?, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(tokens) { index, token ->
            val active = selected == index; val color = v41TokenColor(index)
            Card(
                modifier = if (selected != null) Modifier.clickable { onSelect(index) } else Modifier,
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (active) .30f else .15f))
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.height(3.dp))
                    Text(token.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable private fun V41VectorPill(vector: List<Float>, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(vector.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
    }
}

@Composable private fun V41FfnBlock(title: String, subtitle: String, vector: List<Float>, color: Color) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .14f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = color, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(7.dp)); V41VectorPill(vector, color)
        }
    }
}

@Composable private fun V41PercentageBar(label: String, value: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp); Text("${(value * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(9.dp).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

@Composable private fun V41KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp); Spacer(Modifier.width(12.dp)); Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
}
