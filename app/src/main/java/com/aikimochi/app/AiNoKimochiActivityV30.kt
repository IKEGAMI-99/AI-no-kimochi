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

class AiNoKimochiActivityV30 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V30Theme { V30App() } }
    }
}

private val V30Colors = darkColorScheme(
    primary = Color(0xFF7CD6FF), secondary = Color(0xFFB7A7FF), tertiary = Color(0xFFFFC56D),
    background = Color(0xFF06101E), surface = Color(0xFF0D1A2B), surfaceVariant = Color(0xFF17263C),
    onBackground = Color(0xFFF5F9FF), onSurface = Color(0xFFF5F9FF), onSurfaceVariant = Color(0xFFC1CEE0)
)

private val V30TokenColors = listOf(
    Color(0xFFFFC56D), Color(0xFF7CD6FF), Color(0xFFB7A7FF),
    Color(0xFF82E2A8), Color(0xFFFF9FC7), Color(0xFFFFA878)
)
private fun v30TokenColor(index: Int): Color = V30TokenColors[index % V30TokenColors.size]

@Composable private fun V30Theme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = V30Colors, content = content) }

private data class V30Example(val shortName: String, val text: String)
private data class V30Token(val text: String, val id: Int, val index: Int, val vector: List<Float>)
private data class V30Candidate(val text: String, val logit: Float)
private data class V30Qkv(val q: List<Float>, val k: List<Float>, val v: List<Float>)

private enum class V30Stage(val title: String, val technical: String, val explanation: String, val next: String) {
    SENTENCE("1. 文章", "Text Input", "まず文章をAIに渡します。ここではまだ、人間が読む普通の文字です。", "次に、文章をAIが扱いやすい小さな単位へ分けます。"),
    TOKEN("2. Token", "Tokenization", "文章をTokenという小さな単位へ分けます。AIは文章をTokenの列として扱います。", "次に、それぞれのTokenを意味を持つ数字のベクトルへ変換します。"),
    EMBEDDING("3. Embedding", "Embedding", "Tokenをベクトルへ変換します。意味や使われ方が似たTokenは、空間でも近くなりやすいイメージです。", "次に、同じベクトルからQ・K・Vという3種類の役割を作ります。"),
    QKV("4. Q・K・V", "Query / Key / Value", "各Tokenのベクトルを3方向へ変換します。Qは『何を探す？』、Kは『私は何者？』、Vは『渡す情報』という役割です。", "次にQとKを比較して、どのTokenをどれくらい参考にするか決めます。"),
    ATTENTION("5. Attention", "Self-Attention", "選んだTokenのQと、全TokenのKを比較してAttention Weightを作ります。その重みでVを混ぜ、文脈を取り込みます。", "次に、Attentionで集めた情報をTokenごとにFFNで加工します。"),
    FFN("6. FFN", "Feed Forward Network", "Attentionで集めた情報を、Tokenごとに小さなニューラルネットワークへ通して加工します。ここではToken同士を混ぜず、1つずつ処理します。", "最後に、加工された情報から次に来そうなTokenを予想します。"),
    PREDICT("7. 次を予想", "Logits / Sampling", "次に来そうなTokenへ点数をつけ、確率に変えて1つ選びます。", "選ばれたTokenを文章へ足し、同じ処理を繰り返して文章を生成します。")
}

private object V30Engine {
    private val dictionary = listOf("追いかける", "生成する", "ソファ", "歯医者", "ボール", "寝ている", "文章", "生成", "する", "猫", "犬", "AI", "私", "は", "が", "を", "で", "です").sortedByDescending { it.length }

    fun tokenize(text: String): List<V30Token> {
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
            V30Token(part, 100 + positive % 50000, index, vector)
        }
    }

    fun qkv(token: V30Token): V30Qkv {
        val x = token.vector
        fun t(a: Float, b: Float, c: Float) = listOf(
            (x[0] * a + x[1] * b + x[2] * c).coerceIn(-1.5f, 1.5f),
            (x[0] * c - x[1] * a + x[2] * b).coerceIn(-1.5f, 1.5f),
            (x[0] * b + x[1] * c - x[2] * a).coerceIn(-1.5f, 1.5f)
        )
        return V30Qkv(t(0.78f, 0.24f, -0.16f), t(-0.18f, 0.83f, 0.31f), t(0.29f, -0.12f, 0.91f))
    }

    fun attention(tokens: List<V30Token>, queryIndex: Int): List<Float> {
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
            semantic + 1f / (1f + abs(index - queryIndex)) + (token.id % 9) / 40f
        }
        val exps = raw.map { exp(it.toDouble()).toFloat() }; val total = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / total }
    }

    fun ffnInput(token: V30Token): List<Float> = qkv(token).v
    fun ffnHidden(token: V30Token): List<Float> {
        val x = ffnInput(token)
        return listOf(
            maxOf(0f, x[0] * 1.20f + x[1] * 0.35f), maxOf(0f, x[1] * 1.10f - x[2] * 0.28f),
            maxOf(0f, x[2] * 1.25f + x[0] * 0.22f), maxOf(0f, (x[0] + x[1] + x[2]) * 0.55f),
            maxOf(0f, (x[0] - x[1]) * 0.72f), maxOf(0f, (x[2] - x[0]) * 0.68f)
        )
    }
    fun ffnOutput(token: V30Token): List<Float> {
        val h = ffnHidden(token)
        return listOf(
            (h[0] * 0.46f + h[2] * 0.31f - h[4] * 0.18f).coerceIn(-1.5f, 1.5f),
            (h[1] * 0.41f + h[3] * 0.29f + h[5] * 0.20f).coerceIn(-1.5f, 1.5f),
            (h[2] * 0.38f + h[4] * 0.27f - h[0] * 0.14f).coerceIn(-1.5f, 1.5f)
        )
    }

    fun candidates(example: V30Example): List<V30Candidate> = when (example.shortName) {
        "猫" -> listOf(V30Candidate("。",2.8f),V30Candidate("よ",1.8f),V30Candidate("ところ",1.5f),V30Candidate("姿",1.2f),V30Candidate("時間",0.8f))
        "自己紹介" -> listOf(V30Candidate("。",2.9f),V30Candidate("が",1.6f),V30Candidate("ので",1.3f),V30Candidate("と",1.0f),V30Candidate("！",0.7f))
        "犬" -> listOf(V30Candidate("。",2.7f),V30Candidate("ため",1.7f),V30Candidate("姿",1.4f),V30Candidate("ように",1.1f),V30Candidate("速く",0.8f))
        else -> listOf(V30Candidate("。",2.6f),V30Candidate("ため",1.8f),V30Candidate("ことで",1.5f),V30Candidate("モデル",1.2f),V30Candidate("仕組み",0.9f))
    }
    fun probabilities(example: V30Example, temperature: Float): List<Pair<V30Candidate, Float>> {
        val safe = temperature.coerceIn(0.1f,2f); val c = candidates(example); val e = c.map { exp((it.logit/safe).toDouble()).toFloat() }; val sum = e.sum().coerceAtLeast(0.0001f)
        return c.zip(e.map { it/sum })
    }
}

@Composable private fun V30App() {
    val examples = remember { listOf(V30Example("猫","猫はソファで寝ている"), V30Example("自己紹介","私は歯医者です"), V30Example("犬","犬がボールを追いかける"), V30Example("AI","AIは文章を生成する")) }
    var exampleIndex by remember { mutableIntStateOf(0) }; var stage by remember { mutableStateOf(V30Stage.SENTENCE) }; var advanced by remember { mutableStateOf(false) }; var selectedToken by remember { mutableIntStateOf(0) }; var temperature by remember { mutableFloatStateOf(1f) }; var sampled by remember { mutableStateOf<String?>(null) }
    val example = examples[exampleIndex]; val tokens = remember(exampleIndex) { V30Engine.tokenize(example.text) }; if (selectedToken > tokens.lastIndex) selectedToken = 0

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF06101E),Color(0xFF09172B),Color(0xFF06101E)))).padding(horizontal=16.dp,vertical=14.dp)) {
        V30Header(advanced){advanced=it}; Spacer(Modifier.height(14.dp))
        V30ExamplePicker(examples,exampleIndex){ exampleIndex=it; stage=V30Stage.SENTENCE; selectedToken=0; sampled=null }
        Spacer(Modifier.height(12.dp)); V30StageRail(stage,advanced){stage=it}; Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().weight(1f), shape=RoundedCornerShape(26.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            AnimatedContent(targetState=stage,label="v30Stage") { current ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    V30StageIntro(current,advanced); Spacer(Modifier.height(18.dp))
                    when(current) {
                        V30Stage.SENTENCE -> V30SentenceStage(example)
                        V30Stage.TOKEN -> V30TokenStage(tokens,advanced)
                        V30Stage.EMBEDDING -> V30EmbeddingStage(tokens,advanced)
                        V30Stage.QKV -> V30QkvStage(tokens,selectedToken,{selectedToken=it},advanced)
                        V30Stage.ATTENTION -> V30AttentionStage(tokens,selectedToken,{selectedToken=it},advanced)
                        V30Stage.FFN -> V30FfnStage(tokens,selectedToken,{selectedToken=it},advanced)
                        V30Stage.PREDICT -> V30PredictStage(example,temperature,{temperature=it},sampled,{
                            val probs=V30Engine.probabilities(example,temperature); val r=Random.nextFloat(); var sum=0f
                            sampled=probs.firstOrNull{(_,p)->sum+=p; r<=sum}?.first?.text ?: probs.last().first.text
                        },advanced)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp)); V30Navigation(stage,{if(stage.ordinal>0)stage=V30Stage.entries[stage.ordinal-1]},{if(stage.ordinal<V30Stage.entries.lastIndex)stage=V30Stage.entries[stage.ordinal+1]})
    }
}

@Composable private fun V30Header(advanced:Boolean,onAdvancedChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){ Column(Modifier.weight(1f)){ Text("AIのキモチ",color=MaterialTheme.colorScheme.onBackground,fontSize=28.sp,fontWeight=FontWeight.Black); Text("文章が生まれるまでを、触って理解する。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}; Column(horizontalAlignment=Alignment.End){Text(if(advanced)"ADVANCED" else "BEGINNER",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp,fontWeight=FontWeight.Bold);Switch(checked=advanced,onCheckedChange=onAdvancedChange)} }
}
@Composable private fun V30ExamplePicker(examples:List<V30Example>,selected:Int,onSelect:(Int)->Unit){Column{Text("例文を選ぶ",color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.Bold,fontSize=13.sp);Spacer(Modifier.height(7.dp));Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){examples.forEachIndexed{index,e->val active=index==selected;Card(Modifier.clickable{onSelect(index)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(active)MaterialTheme.colorScheme.primary.copy(alpha=.20f) else MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(horizontal=13.dp,vertical=9.dp)){Text(e.shortName,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(e.text,color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp)}}}}}}
@Composable private fun V30StageRail(stage:V30Stage,advanced:Boolean,onStage:(V30Stage)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){V30Stage.entries.forEach{item->val active=item==stage;Card(Modifier.clickable{onStage(item)},shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=if(active)MaterialTheme.colorScheme.primary.copy(alpha=.20f) else MaterialTheme.colorScheme.surface)){Column(Modifier.padding(horizontal=13.dp,vertical=9.dp)){Text(item.title,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium);if(advanced)Text(item.technical,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=9.sp)}}}}}
@Composable private fun V30StageIntro(stage:V30Stage,advanced:Boolean){Text(stage.title.substringAfter(". "),color=MaterialTheme.colorScheme.onSurface,fontSize=24.sp,fontWeight=FontWeight.Black);if(advanced)Text(stage.technical,color=MaterialTheme.colorScheme.primary,fontSize=13.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(stage.explanation,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp);Spacer(Modifier.height(12.dp));V30InfoCard("次に何が起きる？",stage.next)}
@Composable private fun V30SentenceStage(example:V30Example){Text("いまAIに渡す文章",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));V30BigSentence(example.text);Spacer(Modifier.height(14.dp));V30InfoCard("ポイント","この時点では、まだ人間が読む普通の文章です。次の画面でAI向けの小さな単位へ分解します。")}
@Composable private fun V30TokenStage(tokens:List<V30Token>,advanced:Boolean){Text("文章がパキッと分かれます",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));V30TokenRow(tokens,null){};Spacer(Modifier.height(14.dp));V30InfoCard("こうなりました",tokens.joinToString("  /  "){it.text});if(advanced){Spacer(Modifier.height(14.dp));Text("Token ID",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);tokens.forEach{V30KeyValueRow("${it.index+1}. ${it.text}",it.id.toString())}}}

@Composable private fun V30EmbeddingStage(tokens:List<V30Token>,advanced:Boolean){
    Text("どの点が、どのTokenなのか",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text("点・ラベル・下のTokenカードは同じ色で対応しています。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Spacer(Modifier.height(10.dp))
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp)){BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)){val w=maxWidth;val h=maxHeight;tokens.forEachIndexed{index,t->val px=(.08f+(t.vector[0]+1f)*.37f).coerceIn(.04f,.78f);val py=(.06f+(t.vector[1]+1f)*.35f).coerceIn(.05f,.78f);Row(Modifier.offset(x=w*px,y=h*py),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(12.dp).clip(CircleShape).background(v30TokenColor(index)));Spacer(Modifier.width(5.dp));Text(t.text,color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp,fontWeight=FontWeight.Bold)}}};Text("※ 本物のEmbeddingは数百〜数千次元。ここでは見えるよう3次元相当に縮めた教育用表示です。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp,lineHeight=16.sp)}}
    Spacer(Modifier.height(12.dp));V30TokenRow(tokens,null){};Spacer(Modifier.height(16.dp));V30EmbeddingDistanceCard();if(advanced){Spacer(Modifier.height(14.dp));Text("この例文の教育用ベクトル",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);tokens.forEach{V30KeyValueRow(it.text,it.vector.joinToString(prefix="[",postfix="]"){v->"%.2f".format(v)})}}
}
@Composable private fun V30EmbeddingDistanceCard(){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp)){Text("ベクトルの『近い・遠い』とは？",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text("意味が近い単語",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);V30DistanceDiagram("リンゴ","梨",true);Text("食べ物・果物という共通点が多いので、近い位置になりやすい。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Spacer(Modifier.height(14.dp));Text("意味が遠い単語",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);V30DistanceDiagram("リンゴ","バス",false);Text("意味や使われ方が大きく違うので、離れた位置になりやすい。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Spacer(Modifier.height(8.dp));Text("※ 実際の距離はモデルや文脈によって変わります。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp)}}}
@Composable private fun V30DistanceDiagram(left:String,right:String,near:Boolean){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary));Text(left,color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp)};Spacer(Modifier.width(if(near)24.dp else 8.dp));Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=if(near).75f else .25f)));Spacer(Modifier.width(if(near)24.dp else 8.dp));Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(13.dp).clip(CircleShape).background(if(near)MaterialTheme.colorScheme.secondary else Color(0xFF82E2A8)));Text(right,color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp)}};Text(if(near)"← 近い →" else "←──────── 遠い ────────→",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)}

@Composable private fun V30QkvStage(tokens:List<V30Token>,selectedToken:Int,onSelectedToken:(Int)->Unit,advanced:Boolean){
    val token=tokens[selectedToken];val qkv=remember(token){V30Engine.qkv(token)};Text("どのTokenのQ・K・Vを見る？",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));V30TokenRow(tokens,selectedToken,onSelectedToken);Spacer(Modifier.height(14.dp));V30InfoCard("まず大事なこと","Q・K・Vは別々の単語ではありません。同じTokenのEmbeddingを、学習された3種類の変換で別のベクトルへ写したものです。");Spacer(Modifier.height(14.dp))
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("「${token.text}」のEmbedding",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));V30VectorPill(token.vector,v30TokenColor(selectedToken));Spacer(Modifier.height(10.dp));Text("↓ 3つの役割へ変換",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Spacer(Modifier.height(12.dp));V30QkvRoleCard("Q","Query","何を探している？","このTokenが、文の中でどんな情報を探したいかを表すベクトル。",qkv.q,Color(0xFF62D6FF),advanced,"Q = XWq");Spacer(Modifier.height(10.dp));V30QkvRoleCard("K","Key","私はどんな情報？","ほかのQueryから照合されるための『特徴ラベル』のようなベクトル。",qkv.k,Color(0xFFB7A7FF),advanced,"K = XWk");Spacer(Modifier.height(10.dp));V30QkvRoleCard("V","Value","実際に渡す中身","Attentionで重要だと判断されたとき、実際に次の表現へ混ぜ込まれる情報。",qkv.v,Color(0xFFFFB36B),advanced,"V = XWv")}}
    Spacer(Modifier.height(14.dp));V30InfoCard("このあとAttentionで何をする？","選んだTokenのQを、文章中のすべてのTokenのKと比べます。相性が良いKほどAttention Weightが大きくなり、その重みでVを混ぜます。")
}
@Composable private fun V30QkvRoleCard(letter:String,name:String,beginner:String,detail:String,vector:List<Float>,color:Color,advanced:Boolean,formula:String){Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.fillMaxWidth().padding(13.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha=.22f)),contentAlignment=Alignment.Center){Text(letter,color=color,fontWeight=FontWeight.Black,fontSize=20.sp)};Spacer(Modifier.width(10.dp));Column{Text("$name：$beginner",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);if(advanced)Text(formula,color=color,fontSize=11.sp,fontWeight=FontWeight.Bold)}};Spacer(Modifier.height(7.dp));Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,lineHeight=18.sp);if(advanced){Spacer(Modifier.height(8.dp));V30VectorPill(vector,color)}}}}
@Composable private fun V30VectorPill(vector:List<Float>,color:Color){Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha=.14f)).padding(horizontal=12.dp,vertical=7.dp)){Text(vector.joinToString(prefix="[",postfix="]"){"%.2f".format(it)},color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp)}}

@Composable private fun V30AttentionStage(tokens:List<V30Token>,selectedToken:Int,onSelectedToken:(Int)->Unit,advanced:Boolean){
    val weights=remember(tokens,selectedToken){V30Engine.attention(tokens,selectedToken)};val selectedColor=v30TokenColor(selectedToken);Text("まず、気になるTokenをタップ",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));V30TokenRow(tokens,selectedToken,onSelectedToken);Spacer(Modifier.height(14.dp))
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp)){Text("「${tokens[selectedToken].text}」は、どのTokenをどれくらい参考にしている？",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text("Self-Attentionなので、自分自身の「${tokens[selectedToken].text}」も参照します。QとKの相性から、この割合が作られます。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,lineHeight=18.sp);Spacer(Modifier.height(10.dp));Canvas(Modifier.fillMaxWidth().height(165.dp)){val count=tokens.size.coerceAtLeast(1);val denom=(count-1).coerceAtLeast(1);val xs=List(count){i->size.width*(.10f+.80f*i/denom)};val bottom=size.height*.78f;val top=size.height*.20f;weights.forEachIndexed{index,w->if(index!=selectedToken)drawLine(color=v30TokenColor(index).copy(alpha=(.30f+w*1.7f).coerceIn(.30f,1f)),start=Offset(xs[selectedToken],bottom),end=Offset(xs[index],top),strokeWidth=5f+w*22f,cap=StrokeCap.Round)};xs.forEachIndexed{index,x->drawCircle(color=v30TokenColor(index),radius=if(index==selectedToken)18f else 13f,center=Offset(x,bottom));if(index==selectedToken)drawCircle(color=selectedColor.copy(alpha=.85f),radius=28f,center=Offset(x,bottom),style=Stroke(width=4f+weights[index]*14f))}};Text("○ 外側のリング = 自分自身への参照",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);Spacer(Modifier.height(10.dp));weights.forEachIndexed{index,value->V30PercentageBar(if(index==selectedToken)"${tokens[index].text}（自分自身）" else tokens[index].text,value,v30TokenColor(index));Spacer(Modifier.height(8.dp))}}}
    if(advanced){Spacer(Modifier.height(12.dp));V30InfoCard("Attentionの計算","概念的には QKᵀ を計算し、スケーリングとSoftmaxでAttention Weightへ変換します。その重みでVを足し合わせ、文脈を含んだ新しい表現を作ります。")}
}

@Composable private fun V30FfnStage(tokens:List<V30Token>,selectedToken:Int,onSelectedToken:(Int)->Unit,advanced:Boolean){
    val token=tokens[selectedToken];val input=remember(token){V30Engine.ffnInput(token)};val hidden=remember(token){V30Engine.ffnHidden(token)};val output=remember(token){V30Engine.ffnOutput(token)};val color=v30TokenColor(selectedToken)
    Text("どのTokenのFFNを見る？",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));V30TokenRow(tokens,selectedToken,onSelectedToken);Spacer(Modifier.height(14.dp));V30InfoCard("Attentionとの違い","Attentionは『ほかのTokenから情報を集める』処理。FFNは『集めた情報を、そのTokenの中で加工する』処理です。FFN中はToken同士を直接混ぜません。");Spacer(Modifier.height(14.dp))
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("「${token.text}」を1Tokenずつ加工",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));V30FfnBlock("入力","Attention後の情報",input,color,advanced);Text("↓",color=MaterialTheme.colorScheme.primary,fontSize=24.sp);V30FfnProcessBox("① 広げる","Linear","少ない特徴を、より大きな内部空間へ展開する");Text("↓",color=MaterialTheme.colorScheme.primary,fontSize=24.sp);V30FfnProcessBox("② 選別する","Activation","重要な特徴を強くし、不要なものを弱める");if(advanced){Spacer(Modifier.height(8.dp));V30VectorPill(hidden,MaterialTheme.colorScheme.secondary)};Text("↓",color=MaterialTheme.colorScheme.primary,fontSize=24.sp);V30FfnProcessBox("③ 戻す","Linear","次の層が扱える大きさへ圧縮する");Text("↓",color=MaterialTheme.colorScheme.primary,fontSize=24.sp);V30FfnBlock("出力","加工されたToken表現",output,MaterialTheme.colorScheme.tertiary,advanced)}};Spacer(Modifier.height(14.dp));V30InfoCard("Transformerではこれを何度も繰り返す","実際のモデルでは Attention → FFN を1層として何十層も重ねます。層を進むたびに、各Tokenの表現が文脈に合わせて少しずつ変わります。");if(advanced){Spacer(Modifier.height(12.dp));V30InfoCard("数式で見る","代表的な形は FFN(x) = W₂ · activation(W₁x + b₁) + b₂ です。実際のLLMでは活性化関数やゲート構造がモデルごとに異なります。")}
}
@Composable private fun V30FfnProcessBox(title:String,technical:String,description:String){Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.fillMaxWidth().padding(12.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text(technical,color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(4.dp));Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}}}
@Composable private fun V30FfnBlock(title:String,subtitle:String,vector:List<Float>,color:Color,advanced:Boolean){Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=color.copy(alpha=.14f))){Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=color,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);if(advanced){Spacer(Modifier.height(7.dp));V30VectorPill(vector,color)}}}}

@Composable private fun V30PredictStage(example:V30Example,temperature:Float,onTemperature:(Float)->Unit,sampled:String?,onSample:()->Unit,advanced:Boolean){val probs=remember(example,temperature){V30Engine.probabilities(example,temperature)};Text("AIが考えている次の候補",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp)){Text("Temperature  ${"%.1f".format(temperature)}",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text("低いほど無難、高いほど候補がばらけます。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);Slider(value=temperature,onValueChange=onTemperature,valueRange=.1f..2f);probs.forEach{(c,p)->V30CandidateBar(c,p,advanced);Spacer(Modifier.height(10.dp))};Button(onClick=onSample,modifier=Modifier.fillMaxWidth()){Text("この確率から1つ選ぶ")}}};if(sampled!=null){Spacer(Modifier.height(14.dp));Text("選ばれたToken",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));V30BigSentence("${example.text}$sampled")}}
@Composable private fun V30BigSentence(text:String){Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Box(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=25.dp),contentAlignment=Alignment.Center){Text("「$text」",color=MaterialTheme.colorScheme.onSurface,fontSize=25.sp,lineHeight=34.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center)}}}
@Composable private fun V30InfoCard(title:String,text:String){Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(13.dp)){Text(title,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=12.sp);Spacer(Modifier.height(4.dp));Text(text,color=MaterialTheme.colorScheme.onSurface,fontSize=13.sp,lineHeight=19.sp)}}}
@Composable private fun V30TokenRow(tokens:List<V30Token>,selected:Int?,onSelect:(Int)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){tokens.forEachIndexed{index,t->val active=selected==index;val color=v30TokenColor(index);Card(modifier=if(selected!=null)Modifier.clickable{onSelect(index)} else Modifier,shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=color.copy(alpha=if(active).30f else .15f))){Column(Modifier.padding(horizontal=12.dp,vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(7.dp).clip(CircleShape).background(color));Spacer(Modifier.height(3.dp));Text(t.text,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text("${index+1}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp)}}}}}
@Composable private fun V30PercentageBar(label:String,value:Float,color:Color){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp);Text("${(value*100).toInt()}%",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)};Spacer(Modifier.height(4.dp));Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)){Box(Modifier.fillMaxWidth(value.coerceIn(0f,1f)).height(9.dp).clip(RoundedCornerShape(99.dp)).background(color))}}
@Composable private fun V30CandidateBar(candidate:V30Candidate,probability:Float,advanced:Boolean){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(candidate.text,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text(if(advanced)"${(probability*100).toInt()}%  logit ${"%.1f".format(candidate.logit)}" else "${(probability*100).toInt()}%",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)};Spacer(Modifier.height(4.dp));Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)){Box(Modifier.fillMaxWidth(probability.coerceIn(0f,1f)).height(10.dp).clip(RoundedCornerShape(99.dp)).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.secondary))))}}
@Composable private fun V30KeyValueRow(key:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(key,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);Spacer(Modifier.width(12.dp));Text(value,color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp,textAlign=TextAlign.End)};Spacer(Modifier.height(6.dp))}
@Composable private fun V30Navigation(stage:V30Stage,onBack:()->Unit,onNext:()->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick=onBack,enabled=stage.ordinal>0,modifier=Modifier.weight(1f)){Text("← BACK")};OutlinedButton(onClick=onNext,enabled=stage.ordinal<V30Stage.entries.lastIndex,modifier=Modifier.weight(1f)){Text("NEXT →")}}}
