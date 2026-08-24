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
import kotlin.math.sin
import kotlin.random.Random

class AiNoKimochiActivityV40 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V40Theme { V40App() } }
    }
}

private val V40Colors = darkColorScheme(
    primary = Color(0xFF7CD6FF), secondary = Color(0xFFB7A7FF), tertiary = Color(0xFFFFC56D),
    background = Color(0xFF06101E), surface = Color(0xFF0D1A2B), surfaceVariant = Color(0xFF17263C),
    onBackground = Color(0xFFF5F9FF), onSurface = Color(0xFFF5F9FF), onSurfaceVariant = Color(0xFFC1CEE0)
)

private val V40TokenColors = listOf(
    Color(0xFFFFC56D), Color(0xFF7CD6FF), Color(0xFFB7A7FF),
    Color(0xFF82E2A8), Color(0xFFFF9FC7), Color(0xFFFFA878)
)
private fun v40TokenColor(index: Int): Color = V40TokenColors[index % V40TokenColors.size]

@Composable private fun V40Theme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = V40Colors, content = content)

private data class V40Example(val text: String)
private data class V40Token(val text: String, val id: Int, val index: Int, val vector: List<Float>)
private data class V40Candidate(val text: String, val logit: Float)
private data class V40Qkv(val q: List<Float>, val k: List<Float>, val v: List<Float>)
private data class V40GlossaryTerm(
    val name: String,
    val english: String,
    val summary: String,
    val detail: String,
    val related: String = ""
)
private data class V40GlossaryCategory(val title: String, val subtitle: String, val terms: List<V40GlossaryTerm>)

private enum class V40Page(val title: String, val technical: String, val explanation: String, val next: String) {
    SENTENCE(
        "1. 文章", "Text Input",
        "まず文章をAIへ入力します。この時点ではまだ人間が読む文字列です。LLMの内部では文字そのものを直接計算するのではなく、このあとToken IDやベクトルなどの数値表現へ段階的に変換していきます。文章生成も完成文を一度に作るのではなく、次のTokenを1個ずつ予測する処理の繰り返しです。",
        "文章をTokenという小さな単位へ分けます。"
    ),
    TOKEN(
        "2. Token", "Tokenization",
        "文章をTokenという処理単位へ分割します。Tokenは必ずしも単語1個ではなく、文字の一部、助詞、記号になることもあります。各Tokenには整数のToken IDが割り当てられ、モデルはIDの並びを使ってEmbedding表から対応するベクトルを取り出します。",
        "各TokenをEmbeddingベクトルへ変換します。"
    ),
    EMBEDDING(
        "3. Embedding", "Embedding",
        "Token IDは単なる番号なので、そのままでは意味上の近さを表しにくいものです。そこで各Tokenを多次元ベクトルへ変換します。学習中に似た文脈で使われるTokenは、ベクトル空間でも近い関係になりやすくなります。実際の意味は1本の軸に対応するのではなく、多数の次元へ分散して表現されています。",
        "同じEmbeddingからQuery・Key・Valueを作ります。"
    ),
    QKV(
        "4. Q・K・V", "Query / Key / Value",
        "Attentionを計算するため、各TokenのEmbeddingを学習済み行列Wq・Wk・Wvで3方向へ変換します。Queryは『どんな情報を探したいか』、Keyは『自分はどんな特徴を持つか』、Valueは『実際に渡す情報』です。3つは別々の単語ではなく、同じTokenを異なる役割で見た表現です。",
        "QueryとKeyを比較してAttention Weightを作ります。"
    ),
    ATTENTION(
        "5. Attention", "Self-Attention",
        "あるTokenのQueryと文章中の全TokenのKeyを比較し、どのTokenをどれくらい参考にするかを決めます。その割合がAttention Weightです。Self-Attentionでは自分自身も参照します。Weightを使ってValueを混ぜることで、単独だったToken表現へ周囲の文脈が取り込まれます。",
        "文脈を取り込んだ各TokenをFFNで個別に加工します。"
    ),
    FFN(
        "6. FFN", "Feed Forward Network",
        "Attentionが他Tokenから情報を集める処理なら、FFNは集めた情報を各Tokenの内部で加工する処理です。同じFFNが各Tokenへ独立に適用され、一般的には一度高次元へ広げ、非線形なActivationを通し、元に近い次元へ戻します。AttentionとFFNを何層も重ねることで表現が徐々に洗練されます。",
        "最終表現から語彙全体のLogitを計算し、次Tokenを選びます。"
    ),
    PREDICT(
        "7. 次Token予測", "Logits / Softmax / Sampling",
        "Transformerを通った最終表現から、語彙に含まれる各Token候補へLogitという生の点数を付けます。Softmaxなどで確率分布へ変換し、その分布から次Tokenを選びます。Temperatureを変えると分布の尖り方が変わります。選んだTokenを入力へ追加して同じ処理を繰り返し、文章が1Tokenずつ伸びます。",
        "ここまでが文章生成の1サイクルです。最後のページにはAI用語辞典があります。"
    ),
    GLOSSARY(
        "8. AI用語辞典", "AI Glossary",
        "AI・LLMを学ぶと頻繁に出てくる用語をカテゴリ別にまとめています。カテゴリを選び、気になる用語カードをタップすると詳しい説明を確認できます。モデル内部だけでなく、学習、RAG、マルチモーダル、性能・運用、主要ツールまで一通り俯瞰できます。",
        ""
    )
}

private object V40Engine {
    private val dictionary = listOf(
        "追いかける", "生成する", "ソファ", "歯医者", "ボール", "寝ている",
        "文章", "生成", "する", "猫", "犬", "AI", "私", "は", "が", "を", "で", "です"
    ).sortedByDescending { it.length }

    fun tokenize(text: String): List<V40Token> {
        val parts = mutableListOf<String>(); var i = 0
        while (i < text.length && parts.size < 16) {
            if (text[i].isWhitespace()) { i++; continue }
            val match = dictionary.firstOrNull { text.startsWith(it, i) }
            if (match != null) { parts += match; i += match.length } else { parts += text[i].toString(); i++ }
        }
        if (parts.isEmpty()) parts += "…"
        return parts.mapIndexed { index, part ->
            val seed = part.hashCode(); val positive = if (seed == Int.MIN_VALUE) 0 else abs(seed)
            val vector = List(3) { dim -> sin(seed * (dim + 2) * 0.00061 + index * 0.43).toFloat() }
            V40Token(part, 100 + positive % 50000, index, vector)
        }
    }

    fun qkv(token: V40Token): V40Qkv {
        val x = token.vector
        fun t(a: Float, b: Float, c: Float) = listOf(
            (x[0] * a + x[1] * b + x[2] * c).coerceIn(-1.5f, 1.5f),
            (x[0] * c - x[1] * a + x[2] * b).coerceIn(-1.5f, 1.5f),
            (x[0] * b + x[1] * c - x[2] * a).coerceIn(-1.5f, 1.5f)
        )
        return V40Qkv(t(0.78f, 0.24f, -0.16f), t(-0.18f, 0.83f, 0.31f), t(0.29f, -0.12f, 0.91f))
    }

    fun attention(tokens: List<V40Token>, queryIndex: Int): List<Float> {
        val query = tokens[queryIndex]
        val raw = tokens.mapIndexed { index, token ->
            val pair = setOf(query.text, token.text)
            val semantic = when {
                pair.contains("猫") && (pair.contains("寝ている") || pair.contains("ソファ")) -> 1.6f
                query.text == token.text -> 0.7f
                else -> 0.15f
            }
            semantic + 1f / (1f + abs(index - queryIndex)) + (token.id % 9) / 40f
        }
        val exps = raw.map { exp(it.toDouble()).toFloat() }; val total = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / total }
    }

    fun ffnInput(token: V40Token) = qkv(token).v
    fun ffnHidden(token: V40Token): List<Float> {
        val x = ffnInput(token)
        return listOf(
            maxOf(0f, x[0] * 1.20f + x[1] * 0.35f), maxOf(0f, x[1] * 1.10f - x[2] * 0.28f),
            maxOf(0f, x[2] * 1.25f + x[0] * 0.22f), maxOf(0f, (x[0] + x[1] + x[2]) * 0.55f),
            maxOf(0f, (x[0] - x[1]) * 0.72f), maxOf(0f, (x[2] - x[0]) * 0.68f)
        )
    }
    fun ffnOutput(token: V40Token): List<Float> {
        val h = ffnHidden(token)
        return listOf(
            (h[0] * 0.46f + h[2] * 0.31f - h[4] * 0.18f).coerceIn(-1.5f, 1.5f),
            (h[1] * 0.41f + h[3] * 0.29f + h[5] * 0.20f).coerceIn(-1.5f, 1.5f),
            (h[2] * 0.38f + h[4] * 0.27f - h[0] * 0.14f).coerceIn(-1.5f, 1.5f)
        )
    }

    fun candidates() = listOf(
        V40Candidate("。", 2.8f), V40Candidate("よ", 1.8f), V40Candidate("ところ", 1.5f),
        V40Candidate("姿", 1.2f), V40Candidate("時間", 0.8f)
    )
    fun probabilities(temperature: Float): List<Pair<V40Candidate, Float>> {
        val safe = temperature.coerceIn(0.1f, 2f); val c = candidates()
        val e = c.map { exp((it.logit / safe).toDouble()).toFloat() }; val sum = e.sum().coerceAtLeast(0.0001f)
        return c.zip(e.map { it / sum })
    }
}

@Composable
private fun V40App() {
    val example = remember { V40Example("猫はソファで寝ている") }
    val tokens = remember { V40Engine.tokenize(example.text) }
    var selectedToken by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableFloatStateOf(1f) }
    var sampled by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { V40Page.entries.size })
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06101E), Color(0xFF09172B), Color(0xFF06101E))))
            .statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        V40Header()
        Spacer(Modifier.height(12.dp))
        V40StageRail(pagerState.currentPage) { page -> scope.launch { pagerState.animateScrollToPage(page) } }
        Spacer(Modifier.height(10.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { pageIndex ->
            val page = V40Page.entries[pageIndex]
            Card(
                Modifier.fillMaxSize().padding(horizontal = 2.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    V40PageIntro(page)
                    Spacer(Modifier.height(20.dp))
                    when (page) {
                        V40Page.SENTENCE -> V40SentenceStage(example)
                        V40Page.TOKEN -> V40TokenStage(tokens)
                        V40Page.EMBEDDING -> V40EmbeddingStage(tokens)
                        V40Page.QKV -> V40QkvStage(tokens, selectedToken) { selectedToken = it }
                        V40Page.ATTENTION -> V40AttentionStage(tokens, selectedToken) { selectedToken = it }
                        V40Page.FFN -> V40FfnStage(tokens, selectedToken) { selectedToken = it }
                        V40Page.PREDICT -> V40PredictStage(example, temperature, { temperature = it }, sampled) {
                            val probs = V40Engine.probabilities(temperature); val r = Random.nextFloat(); var sum = 0f
                            sampled = probs.firstOrNull { (_, p) -> sum += p; r <= sum }?.first?.text ?: probs.last().first.text
                        }
                        V40Page.GLOSSARY -> V40GlossaryStage()
                    }
                    if (page != V40Page.GLOSSARY) {
                        Spacer(Modifier.height(22.dp)); V40NextCard(page.next)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("←  横へスワイプしてページ移動  →", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable private fun V40Header() {
    Column(Modifier.fillMaxWidth()) {
        Text("AIのキモチ", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Transformerの中を、1ページずつ横にたどる。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable private fun V40StageRail(currentPage: Int, onPage: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(currentPage) { state.animateScrollToItem(currentPage) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(V40Page.entries) { index, item ->
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

@Composable private fun V40PageIntro(page: V40Page) {
    Text(page.title.substringAfter(". "), color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
    Text(page.technical, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(page.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable private fun V40SentenceStage(example: V40Example) {
    Text("今回たどる例文", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp)); V40BigSentence(example.text); Spacer(Modifier.height(14.dp))
    V40InfoCard("なぜ文字のままではダメ？", "ニューラルネットワークが直接計算できるのは数値です。そこで文字列をTokenへ分け、Token ID、Embedding、Transformer内部表現という順に変換します。")
    Spacer(Modifier.height(12.dp)); V40InfoCard("生成の基本", "LLMは完成した文章全体を一度に吐き出しているわけではありません。現在までのToken列から次のTokenを予測し、それを追加してまた予測する、というループです。")
}

@Composable private fun V40TokenStage(tokens: List<V40Token>) {
    Text("文章をTokenへ分割", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
    V40TokenRow(tokens, null) {}; Spacer(Modifier.height(14.dp)); V40InfoCard("今回の分割", tokens.joinToString("  /  ") { it.text })
    Spacer(Modifier.height(14.dp)); Text("Token ID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("Token IDは意味そのものではなく、語彙表の何番目かを示す整数です。このIDからEmbedding表を引きます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(8.dp)); tokens.forEach { V40KeyValueRow("${it.index + 1}. ${it.text}", it.id.toString()) }
}

@Composable private fun V40EmbeddingStage(tokens: List<V40Token>) {
    Text("Tokenをベクトル空間へ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Text("点・ラベル・Tokenカードは同じ色で対応しています。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Spacer(Modifier.height(10.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)) {
                val w = maxWidth; val h = maxHeight
                tokens.forEachIndexed { index, t ->
                    val px = (.08f + (t.vector[0] + 1f) * .37f).coerceIn(.04f, .78f); val py = (.06f + (t.vector[1] + 1f) * .35f).coerceIn(.05f, .78f)
                    Row(Modifier.offset(x = w * px, y = h * py), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(v40TokenColor(index))); Spacer(Modifier.width(5.dp))
                        Text(t.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("※ 実際のEmbeddingは数百〜数千次元。ここでは教育用に3要素へ縮めています。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(14.dp)); V40EmbeddingDistanceCard(); Spacer(Modifier.height(14.dp))
    Text("教育用ベクトル", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp))
    tokens.forEach { V40KeyValueRow(it.text, it.vector.joinToString(prefix = "[", postfix = "]") { v -> "%.2f".format(v) }) }
}

@Composable private fun V40EmbeddingDistanceCard() {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("意味が近いほど、ベクトルも近くなりやすい", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp)); Text("近い例：リンゴ ↔ 梨", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            V40DistanceDiagram("リンゴ", "梨", true); Text("どちらも果物で似た文脈へ現れやすいため、近い関係になりやすいです。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp)); Text("遠い例：リンゴ ↔ バス", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            V40DistanceDiagram("リンゴ", "バス", false); Text("意味や使われ方が大きく違うため、離れた関係になりやすいです。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable private fun V40DistanceDiagram(left: String, right: String, near: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(if (near) 14.dp else 5.dp))
        Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (near) .8f else .25f)))
        Spacer(Modifier.width(if (near) 14.dp else 5.dp)); Text(right, color = MaterialTheme.colorScheme.onSurface)
    }
    Text(if (near) "← 近い →" else "←──────── 遠い ────────→", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
}

@Composable private fun V40QkvStage(tokens: List<V40Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val token = tokens[selectedToken]; val qkv = remember(token) { V40Engine.qkv(token) }
    Text("Tokenを選んでQ・K・Vを見る", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); V40TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp)); V40InfoCard("Q・K・Vは同じTokenから作る", "Embedding Xへ別々の学習済み行列Wq・Wk・Wvを掛け、3種類のベクトルへ変換します。Q/Kは参照先を決め、Vは実際に受け取る中身です。")
    Spacer(Modifier.height(14.dp)); V40QkvRole("Q", "Query", "何を探したい？", qkv.q, Color(0xFF62D6FF), "Q = XWq")
    Spacer(Modifier.height(10.dp)); V40QkvRole("K", "Key", "私はどんな特徴？", qkv.k, Color(0xFFB7A7FF), "K = XWk")
    Spacer(Modifier.height(10.dp)); V40QkvRole("V", "Value", "実際に渡す情報", qkv.v, Color(0xFFFFB36B), "V = XWv")
}

@Composable private fun V40QkvRole(letter: String, name: String, label: String, vector: List<Float>, color: Color, formula: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) { Text(letter, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(10.dp)); Column { Text("$name：$label", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Text(formula, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(9.dp)); V40VectorPill(vector, color)
        }
    }
}

@Composable private fun V40AttentionStage(tokens: List<V40Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val weights = remember(tokens, selectedToken) { V40Engine.attention(tokens, selectedToken) }; val selectedColor = v40TokenColor(selectedToken)
    Text("基準にするTokenをタップ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); V40TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp)); V40InfoCard("QとKからWeightを作る", "選択TokenのQueryと、全TokenのKeyを比較します。Softmax後の割合は合計100%になり、その割合でValueを混ぜます。Self-Attentionなので自分自身も参照対象です。")
    Spacer(Modifier.height(14.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("「${tokens[selectedToken].text}」が参考にする割合", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(165.dp)) {
                val count = tokens.size; val denom = (count - 1).coerceAtLeast(1); val xs = List(count) { i -> size.width * (.10f + .80f * i / denom) }; val bottom = size.height * .78f; val top = size.height * .20f
                weights.forEachIndexed { index, w -> if (index != selectedToken) drawLine(color = v40TokenColor(index).copy(alpha = (.30f + w * 1.7f).coerceIn(.30f, 1f)), start = Offset(xs[selectedToken], bottom), end = Offset(xs[index], top), strokeWidth = 5f + w * 22f, cap = StrokeCap.Round) }
                xs.forEachIndexed { index, x -> drawCircle(v40TokenColor(index), if (index == selectedToken) 18f else 13f, Offset(x, bottom)); if (index == selectedToken) drawCircle(selectedColor.copy(alpha = .85f), 28f, Offset(x, bottom), style = Stroke(width = 4f + weights[index] * 14f)) }
            }
            weights.forEachIndexed { index, value -> V40PercentageBar(if (index == selectedToken) "${tokens[index].text}（自分自身）" else tokens[index].text, value, v40TokenColor(index)); Spacer(Modifier.height(8.dp)) }
        }
    }
    Spacer(Modifier.height(14.dp)); V40InfoCard("代表式", "Attention(Q,K,V) = softmax(QKᵀ / √dₖ)V。QKᵀで関連度を測り、SoftmaxでWeightへ変換し、Vを重み付きで混ぜます。")
}

@Composable private fun V40FfnStage(tokens: List<V40Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val token = tokens[selectedToken]; val input = V40Engine.ffnInput(token); val hidden = V40Engine.ffnHidden(token); val output = V40Engine.ffnOutput(token)
    Text("Tokenを選んでFFNを見る", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); V40TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp)); V40InfoCard("Attentionとの違い", "Attentionは他Tokenから情報を集めます。FFNは集まった情報を各Tokenごとに独立して変換します。Token間の通信とToken内の加工を分担しているイメージです。")
    Spacer(Modifier.height(14.dp)); V40FfnBlock("入力", "Attention後の表現", input, v40TokenColor(selectedToken)); Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
    V40InfoCard("① Linear / W₁", "一度、より大きな内部次元へ広げて特徴の組み合わせを作りやすくします。")
    Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp); V40InfoCard("② Activation", "GELUやSwiGLUなどの非線形処理で、単純な線形変換だけでは表現できないパターンを扱います。")
    Spacer(Modifier.height(8.dp)); V40VectorPill(hidden, MaterialTheme.colorScheme.secondary); Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
    V40InfoCard("③ Linear / W₂", "次の層へ渡せる次元へ戻します。代表式は FFN(x) = W₂ · activation(W₁x + b₁) + b₂ です。")
    Spacer(Modifier.height(10.dp)); V40FfnBlock("出力", "加工されたToken表現", output, MaterialTheme.colorScheme.tertiary)
}

@Composable private fun V40PredictStage(example: V40Example, temperature: Float, onTemperature: (Float) -> Unit, sampled: String?, onSample: () -> Unit) {
    val probs = remember(temperature) { V40Engine.probabilities(temperature) }
    Text("語彙の候補へ点数と確率を付ける", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
    V40InfoCard("Logitとは？", "モデルが語彙中の各Token候補へ付ける生の点数です。確率ではありません。Softmaxなどで確率分布へ変換してから次Tokenを選びます。")
    Spacer(Modifier.height(12.dp)); Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("Temperature  ${"%.1f".format(temperature)}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("低いほど上位候補へ集中し、高いほど候補がばらけます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Slider(value = temperature, onValueChange = onTemperature, valueRange = .1f..2f)
            probs.forEach { (c, p) -> V40CandidateBar(c, p); Spacer(Modifier.height(10.dp)) }
            Button(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("確率分布から1Token選ぶ") }
        }
    }
    if (sampled != null) { Spacer(Modifier.height(14.dp)); Text("選ばれたTokenを追加", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); V40BigSentence("${example.text}$sampled") }
}

@Composable private fun V40GlossaryStage() {
    val categories = remember { v40GlossaryCategories() }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var selectedTerm by remember { mutableStateOf<V40GlossaryTerm?>(null) }

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
    Text(categories[categoryIndex].subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp); Spacer(Modifier.height(12.dp))
    categories[categoryIndex].terms.forEach { term ->
        Card(
            Modifier.fillMaxWidth().clickable { selectedTerm = term }, shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
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

private fun v40GlossaryCategories(): List<V40GlossaryCategory> = listOf(
    V40GlossaryCategory("基礎概念", "AI全体を理解するための土台になる用語。", listOf(
        V40GlossaryTerm("AI", "Artificial Intelligence", "人間の知的作業を機械で実現する技術の総称。", "認識・予測・生成・計画など、人間が知的だと考える処理をコンピュータで実現する広い概念です。機械学習や生成AIもAIの一部です。", "機械学習、生成AI"),
        V40GlossaryTerm("機械学習", "Machine Learning", "データから規則やパターンを学ぶ手法。", "人間がすべてのルールを手書きする代わりに、データから予測に役立つパターンやパラメータを学習します。", "深層学習、学習データ"),
        V40GlossaryTerm("深層学習", "Deep Learning", "多層ニューラルネットワークを使う機械学習。", "多数の層を持つニューラルネットワークで、画像・音声・文章などの複雑な特徴を段階的に学習します。LLMも深層学習の一種です。", "ニューラルネットワーク、Transformer"),
        V40GlossaryTerm("生成AI", "Generative AI", "文章・画像・音声など新しい内容を生成するAI。", "学習したデータの統計的なパターンを利用し、新しい文章・画像・音声・動画・コードなどを生成するAIの総称です。", "LLM、拡散モデル"),
        V40GlossaryTerm("LLM", "Large Language Model", "大量の文章から学習した大規模言語モデル。", "多数のパラメータを持ち、Token列から次Tokenを予測することで文章理解・生成・要約・翻訳・コード生成などを行うモデルです。", "Transformer、Token")
    )),
    V40GlossaryCategory("モデル内部", "TransformerやLLMの中で直接使われる仕組み。", listOf(
        V40GlossaryTerm("Tokenizer", "Tokenizer", "文章をTokenへ分割する処理。", "入力文字列をモデルの語彙表に対応するTokenへ分割し、Token ID列へ変換します。モデルごとに分割方法や語彙が異なります。", "Token、Vocabulary"),
        V40GlossaryTerm("Token", "Token", "モデルが文章を扱う最小単位。", "単語、単語の一部、文字、記号などを表す単位です。LLMは文章をTokenの列として処理し、生成時も基本的に1Tokenずつ出力します。", "Tokenizer、Token ID"),
        V40GlossaryTerm("Embedding", "Embedding", "Tokenを意味的なベクトルへ変換する仕組み。", "Token IDを多次元ベクトルへ写し、似た使われ方や意味を持つToken同士が近い関係になりやすい数値表現へ変えます。", "Vector、Embedding Model"),
        V40GlossaryTerm("Transformer", "Transformer", "Attentionを中心にした現代LLMの基本構造。", "Self-AttentionとFFNを中心に、Residual ConnectionやNormalizationなどを組み合わせたニューラルネットワーク構造です。多くのLLMの基盤です。", "Attention、FFN"),
        V40GlossaryTerm("Q / K / V", "Query / Key / Value", "Attentionで参照先と受け取る情報を決める3表現。", "QueryとKeyの相性からAttention Weightを求め、そのWeightを使ってValueを混ぜます。同じTokenの表現から別々の学習済み行列で作られます。", "Attention"),
        V40GlossaryTerm("Attention", "Attention", "Token同士の関係へ重みを付ける仕組み。", "各Tokenが他のTokenをどれくらい参照すべきかを計算し、その重みで情報を集約します。Self-Attentionでは自分自身も参照対象です。", "Q/K/V、Multi-Head Attention"),
        V40GlossaryTerm("FFN", "Feed Forward Network", "各Tokenの特徴を個別に加工するネットワーク。", "Attentionで集めた文脈情報をTokenごとに独立して変換します。一般に高次元へ広げ、非線形変換を行い、元の次元へ戻します。", "Activation、Transformer"),
        V40GlossaryTerm("Context Window", "Context Window", "一度に参照できるToken数の上限。", "モデルが1回の推論で入力と出力を合わせて保持・参照できるToken範囲です。長いほど多くの文脈を扱えますが計算量やメモリも増えます。", "Token、KV Cache"),
        V40GlossaryTerm("Parameter", "Parameter", "学習によって調整されるモデル内部の数値。", "ニューラルネットワークの重みやバイアスなど、学習で更新される値です。モデルサイズを○B parametersと表すときのBはbillionです。", "Weight、Training"),
        V40GlossaryTerm("MoE", "Mixture of Experts", "複数の専門ネットワークを選択して使う構造。", "入力ごとに一部のExpertだけを活性化し、総パラメータ数を増やしつつ1回の計算量を抑える設計です。", "Expert、Routing")
    )),
    V40GlossaryCategory("学習・調整", "モデルを作り、目的に合わせて調整する方法。", listOf(
        V40GlossaryTerm("Pretraining", "Pretraining", "大量データから基礎能力を学ぶ事前学習。", "大規模な文章・画像などから一般的なパターンを学習する段階です。LLMでは次Token予測などを大量に行い、基礎的な言語能力を形成します。", "Fine-tuning"),
        V40GlossaryTerm("Fine-tuning", "Fine-tuning", "学習済みモデルを追加データで調整すること。", "事前学習済みモデルへ特定分野やタスクのデータを追加学習し、振る舞いや知識を目的に合わせます。", "SFT、LoRA"),
        V40GlossaryTerm("SFT", "Supervised Fine-Tuning", "正解例を使う教師あり追加学習。", "質問と望ましい回答などの教師データでFine-tuningし、指示に従う能力や回答形式を整えます。", "Fine-tuning、RLHF"),
        V40GlossaryTerm("RLHF", "Reinforcement Learning from Human Feedback", "人間の好みを使って振る舞いを調整する方法。", "複数回答への人間の評価などを利用して、役立ちやすさや望ましい振る舞いにモデルを近づける学習手法です。", "Reward Model、Preference"),
        V40GlossaryTerm("LoRA", "Low-Rank Adaptation", "少ない追加パラメータで効率良く調整する手法。", "元モデルの重みを大きく変更せず、小さな低ランク行列を追加して学習します。計算資源と保存容量を抑えやすいのが特徴です。", "PEFT、Fine-tuning"),
        V40GlossaryTerm("Distillation", "Knowledge Distillation", "大きなモデルの能力を小さなモデルへ移す方法。", "Teacherモデルの出力や振る舞いをStudentモデルの学習に利用し、より小さいモデルへ能力を移す考え方です。", "Teacher、Student"),
        V40GlossaryTerm("Overfitting", "Overfitting", "学習データへ適応しすぎて汎化しない状態。", "訓練データでは高性能でも未知データで性能が落ちる現象です。データ量、正則化、学習回数などの調整が重要です。", "Generalization、Validation"),
        V40GlossaryTerm("Loss", "Loss", "モデルの予測誤差を表す指標。", "予測と正解のズレを数値化したもので、学習ではLossが小さくなる方向へパラメータを更新します。", "Gradient、Optimizer"),
        V40GlossaryTerm("Epoch", "Epoch", "学習データ全体を1周する単位。", "訓練データセットをモデルが一通り処理した回数を表します。多すぎるとOverfittingにつながる場合があります。", "Batch、Iteration")
    )),
    V40GlossaryCategory("推論・生成", "学習済みモデルへ指示し、出力を作るときの用語。", listOf(
        V40GlossaryTerm("Prompt", "Prompt", "モデルへ渡す入力や指示文。", "質問、命令、前提、例、出力形式などをまとめた入力です。モデルの出力はPromptの書き方や与える文脈で大きく変化します。", "System Prompt"),
        V40GlossaryTerm("System Prompt", "System Prompt", "会話全体の役割やルールを定める上位指示。", "モデルの役割、守るべきルール、口調、出力制約などを定めるために使われる指示です。一般のユーザー入力より上位に扱われる構成があります。", "Prompt"),
        V40GlossaryTerm("Inference", "Inference", "学習済みモデルを実際に動かして出力を得る処理。", "Trainingで重みを学んだあと、その重みを固定して入力から予測や生成を行う段階です。日常的にAIへ質問する処理は基本的にInferenceです。", "Training、Latency"),
        V40GlossaryTerm("Temperature", "Temperature", "出力確率のばらつきを調整する値。", "低くすると上位候補へ確率が集中しやすくなり、高くすると分布が平らになって多様な候補が選ばれやすくなります。", "Sampling、Softmax"),
        V40GlossaryTerm("Top-k", "Top-k Sampling", "上位k個の候補だけから選ぶSampling。", "確率の高いToken候補をk個に絞ってからサンプリングします。低確率の奇妙な候補を切り捨てやすくなります。", "Top-p、Sampling"),
        V40GlossaryTerm("Top-p", "Nucleus Sampling", "累積確率がpになる候補集合から選ぶSampling。", "候補数を固定せず、上位から確率を足して累積が指定値pに達するまでの候補だけを残します。", "Top-k、Temperature"),
        V40GlossaryTerm("Hallucination", "Hallucination", "もっともらしい誤情報を生成する現象。", "モデルが流暢さを保ったまま、事実ではない内容や存在しない引用・人物・仕様などを生成する問題です。RAGや検証工程で軽減を狙います。", "RAG、Grounding"),
        V40GlossaryTerm("Reasoning", "Reasoning", "複数段階の推論を行う能力。", "条件整理、計画、計算、比較などを複数ステップで処理して答えへ到達する能力を指します。", "Chain of Thought、Planning"),
        V40GlossaryTerm("Agent", "AI Agent", "目標に向けて複数の処理やツール利用を行う仕組み。", "モデルが状況を判断し、検索、コード実行、API利用などの行動を組み合わせてタスクを進めるシステムです。", "Tool Calling"),
        V40GlossaryTerm("Tool Calling", "Tool Calling", "外部ツールやAPIをモデルから呼び出す仕組み。", "検索、計算、データベース、カレンダー、コード実行などを外部ツールへ任せ、モデル単体ではできない処理を拡張します。", "Agent、Function Calling")
    )),
    V40GlossaryCategory("検索・知識", "外部情報や長期的な知識をAIへ接続する仕組み。", listOf(
        V40GlossaryTerm("RAG", "Retrieval-Augmented Generation", "検索した外部情報を参照して回答を生成する構成。", "質問に関連する文書を検索し、その内容をPromptへ追加してLLMに回答させます。モデルを再学習せず最新・社内情報を利用しやすくなります。", "Retrieval、Vector DB"),
        V40GlossaryTerm("Embedding Model", "Embedding Model", "検索用ベクトルを作るモデル。", "文章や画像をベクトルへ変換し、意味の近さを距離や類似度で比較できるようにします。RAGの検索部分でよく使われます。", "Embedding、Vector DB"),
        V40GlossaryTerm("Vector DB", "Vector Database", "ベクトルを保存し類似検索するデータベース。", "Embeddingベクトルと元文書を保存し、質問ベクトルに近いデータを高速に検索します。", "Cosine Similarity、RAG"),
        V40GlossaryTerm("Retrieval", "Retrieval", "関連する情報を検索して取り出す処理。", "ユーザーの質問に関連する文書チャンクなどを検索し、生成モデルへ渡す情報を選びます。", "RAG、Re-ranking"),
        V40GlossaryTerm("Chunking", "Chunking", "長い文書を検索しやすい小片へ分割する処理。", "文書を段落や一定Token数などの単位へ区切ります。Chunkが大きすぎても小さすぎても検索品質へ影響します。", "Retrieval、Context Window"),
        V40GlossaryTerm("Re-ranking", "Re-ranking", "検索候補をもう一度精密に並べ替える処理。", "最初のベクトル検索などで得た候補を、より精密なモデルで質問との関連度順に並べ直します。", "Retrieval、Reranker"),
        V40GlossaryTerm("Memory", "AI Memory", "会話履歴や知識を後で再利用する仕組み。", "過去の会話やユーザー設定などを保存し、必要なとき検索してPromptへ戻す構成です。モデル本体の重みへ直接記憶するのとは別物です。", "RAG、Vector DB")
    )),
    V40GlossaryCategory("マルチモーダル", "文章以外の画像・音声などを扱うAI。", listOf(
        V40GlossaryTerm("Multimodal", "Multimodal", "文章・画像・音声など複数形式を扱う能力。", "複数種類の入力や出力を1つのシステムで扱う考え方です。画像を見ながら会話したり、音声を理解して返答したりできます。", "Vision、Audio"),
        V40GlossaryTerm("Vision Encoder", "Vision Encoder", "画像をモデルが扱える特徴へ変換する部分。", "画像をパッチや特徴ベクトルへ変換し、言語モデルなどと接続できる表現へ変換します。", "Multimodal、Embedding"),
        V40GlossaryTerm("Speech-to-Text", "Speech-to-Text", "音声を文章へ変換する技術。", "マイクなどから得た音声波形を認識し、文字列へ変換します。音声アシスタントの入力部分などで使われます。", "ASR、Audio"),
        V40GlossaryTerm("Text-to-Image", "Text-to-Image", "文章から画像を生成する技術。", "Promptで指定した内容を元に画像を生成します。拡散モデルなどが代表的です。", "Diffusion、Prompt"),
        V40GlossaryTerm("Text-to-Speech", "Text-to-Speech", "文章を音声へ変換する技術。", "文字列から自然な発話音声を生成します。声質や話速、感情表現などを制御できるシステムもあります。", "TTS、Voice")
    )),
    V40GlossaryCategory("性能・運用", "AIを速く・軽く・安全に動かすための用語。", listOf(
        V40GlossaryTerm("Benchmark", "Benchmark", "モデル性能を比較する評価指標やテスト。", "数学、推論、コード、言語理解など特定能力を測る問題セットや測定方法です。単一Benchmarkだけで実利用性能を断定するのは危険です。", "Evaluation"),
        V40GlossaryTerm("Latency", "Latency", "入力から応答までの待ち時間。", "最初のTokenが返るまでの時間や、1回の推論全体にかかる遅延を指します。リアルタイム用途で重要です。", "TTFT、Throughput"),
        V40GlossaryTerm("Throughput", "Throughput", "一定時間に処理できる量。", "1秒あたりToken数や同時リクエスト処理量など、システム全体の処理能力を表します。", "Latency"),
        V40GlossaryTerm("GPU", "Graphics Processing Unit", "AI計算で広く使われる並列演算装置。", "行列演算を大量に並列実行する能力が高く、LLMの学習・推論で中心的に使われます。", "NPU、VRAM"),
        V40GlossaryTerm("NPU", "Neural Processing Unit", "ニューラルネットワーク向けに最適化された演算装置。", "スマートフォンやPCなどでAI処理を省電力・高速に行うための専用アクセラレータです。", "GPU、On-device AI"),
        V40GlossaryTerm("Quantization", "Quantization", "重みや計算の数値精度を下げて軽量化する技術。", "FP16からINT8やINT4などへ精度を下げ、メモリ使用量や計算量を削減します。性能が少し低下する場合があります。", "INT8、INT4"),
        V40GlossaryTerm("FP16 / INT8 / INT4", "Numeric Formats", "AI内部の数値を表す代表的な精度形式。", "FP16は16bit浮動小数点、INT8/INT4は8bit/4bit整数です。低bitほどモデルを軽量化しやすい一方、精度への影響も考慮します。", "Quantization"),
        V40GlossaryTerm("KV Cache", "Key-Value Cache", "生成済みTokenのK/Vを再利用するキャッシュ。", "Autoregressive生成で過去TokenのKeyとValueを毎回計算し直さず保存して再利用し、生成を高速化します。長文ほどメモリを多く使います。", "Attention、Context Window"),
        V40GlossaryTerm("Deployment", "Deployment", "モデルを実際のサービス環境へ配置すること。", "学習・評価したモデルをサーバー、クラウド、スマートフォンなどで利用できる状態にし、APIやアプリから呼び出せるようにします。", "Inference"),
        V40GlossaryTerm("Guardrail", "Guardrail", "危険・不適切な出力や操作を制御する仕組み。", "入力・出力フィルタ、権限制御、ツール利用制限、ポリシーチェックなどを組み合わせ、AIシステムの安全性を高めます。", "Safety、Moderation")
    )),
    V40GlossaryCategory("開発ツール", "AIモデルの実験・開発・ローカル実行でよく使うサービスやアプリ。", listOf(
        V40GlossaryTerm("Hugging Face", "Hugging Face", "モデルやデータセットを共有する主要プラットフォーム。", "オープンモデル、データセット、デモアプリなどを公開・取得でき、Transformersなどのライブラリも提供しています。", "Model Hub"),
        V40GlossaryTerm("GitHub", "GitHub", "コードやOSSを管理・共有するサービス。", "Gitリポジトリをホスティングし、Issue、Pull Request、Actionsなどで開発を管理できます。AI関連のOSSも多数公開されています。", "Git、GitHub Actions"),
        V40GlossaryTerm("Ollama", "Ollama", "ローカルLLMを手軽に実行・管理するツール。", "対応モデルを取得してPC上で動かし、CLIやローカルAPIから利用できます。", "Local LLM"),
        V40GlossaryTerm("LM Studio", "LM Studio", "GUIでローカルLLMを実行するアプリ。", "モデル検索、ダウンロード、チャット、ローカルAPIサーバーなどをGUIで扱えます。", "Local LLM"),
        V40GlossaryTerm("Google Colab", "Google Colab", "ブラウザでPythonやGPUを使えるノート環境。", "環境構築を抑えてJupyter Notebook形式のPythonコードを実行でき、AI学習や検証でよく使われます。", "Jupyter"),
        V40GlossaryTerm("Kaggle", "Kaggle", "データ分析・機械学習の競技とデータ共有サービス。", "公開データセット、Notebook、GPU環境、機械学習コンペなどを提供します。", "Dataset、Competition"),
        V40GlossaryTerm("Docker", "Docker", "実行環境をコンテナとしてまとめる技術。", "ライブラリや依存関係を含む環境をコンテナ化し、別のマシンでも同じ構成を再現しやすくします。", "Container"),
        V40GlossaryTerm("VS Code", "Visual Studio Code", "広く使われるコードエディタ。", "拡張機能、Git連携、ターミナルなどを備え、AI開発を含む多くのプログラミング用途で使われます。", "IDE、GitHub")
    )),
    V40GlossaryCategory("主要AI", "代表的な対話型AIサービス。", listOf(
        V40GlossaryTerm("ChatGPT", "ChatGPT", "OpenAIの対話型AIサービス。", "文章、画像、ファイル、コードなどを扱う対話型AIサービスです。利用できる機能やモデルはプラン・時期により変化します。", "OpenAI"),
        V40GlossaryTerm("Gemini", "Gemini", "GoogleのAIモデル・対話サービス群。", "Googleが開発するマルチモーダルAIモデル群および関連サービスです。", "Google"),
        V40GlossaryTerm("Claude", "Claude", "Anthropicの対話型AI。", "Anthropicが開発するLLMおよび対話サービスで、文章処理やコードなど幅広い用途に使われます。", "Anthropic"),
        V40GlossaryTerm("Grok", "Grok", "xAIの対話型AI。", "xAIが開発するAIモデル・対話サービスです。", "xAI"),
        V40GlossaryTerm("Copilot", "Microsoft Copilot", "MicrosoftのAIアシスタント群。", "Microsoft製品や開発環境などに組み込まれるAIアシスタントのブランドです。", "Microsoft")
    )),
    V40GlossaryCategory("オープンウェイト", "重みを取得してローカルや自前環境で動かせる代表的モデル群。", listOf(
        V40GlossaryTerm("Gemma", "Gemma", "Google系のオープンウェイトモデル群。", "Googleが公開する比較的扱いやすいオープンウェイトの言語モデル群です。ライセンス条件を確認して利用します。", "Google、Open Weight"),
        V40GlossaryTerm("Qwen", "Qwen", "Alibaba系のオープンウェイトモデル群。", "AlibabaのQwenチームが開発する言語・マルチモーダルモデル群で、多言語やコードなど幅広いモデルがあります。", "Alibaba"),
        V40GlossaryTerm("Llama", "Llama", "Metaの代表的なオープンウェイトモデル群。", "Metaが公開するLLM系列で、研究・開発・ローカル実行などで広く利用されています。", "Meta"),
        V40GlossaryTerm("Mistral", "Mistral", "Mistral AIのモデル群。", "Mistral AIが開発する言語モデル群で、オープンウェイトとして公開されるモデルと商用提供モデルがあります。", "Mistral AI"),
        V40GlossaryTerm("DeepSeek", "DeepSeek", "DeepSeekが開発するモデル群。", "中国のDeepSeekが開発する言語・推論・コードなどのモデル群です。公開形態やライセンスはモデルごとに確認が必要です。", "Open Weight")
    ))
)

@Composable private fun V40NextCard(text: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .13f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("次に何が起きる？", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp)); Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp)); Text("横へスワイプして続ける →", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun V40BigSentence(text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 25.dp), contentAlignment = Alignment.Center) { Text("「$text」", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }
    }
}

@Composable private fun V40InfoCard(title: String, text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(13.dp)) { Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.height(4.dp)); Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 20.sp) }
    }
}

@Composable private fun V40TokenRow(tokens: List<V40Token>, selected: Int?, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(tokens) { index, token ->
            val active = selected == index; val color = v40TokenColor(index)
            Card(modifier = if (selected != null) Modifier.clickable { onSelect(index) } else Modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (active) .30f else .15f))) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.height(3.dp)); Text(token.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
            }
        }
    }
}

@Composable private fun V40VectorPill(vector: List<Float>, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text(vector.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp) }
}

@Composable private fun V40FfnBlock(title: String, subtitle: String, vector: List<Float>, color: Color) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .14f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = color, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp); Spacer(Modifier.height(7.dp)); V40VectorPill(vector, color) }
    }
}

@Composable private fun V40PercentageBar(label: String, value: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp); Text("${(value * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)) { Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(9.dp).clip(RoundedCornerShape(99.dp)).background(color)) }
}

@Composable private fun V40CandidateBar(candidate: V40Candidate, probability: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(candidate.text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Text("${(probability * 100).toInt()}%  logit ${"%.1f".format(candidate.logit)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
    Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)) { Box(Modifier.fillMaxWidth(probability.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(99.dp)).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) }
}

@Composable private fun V40KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp); Spacer(Modifier.width(12.dp)); Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, textAlign = TextAlign.End) }
    Spacer(Modifier.height(6.dp))
}
