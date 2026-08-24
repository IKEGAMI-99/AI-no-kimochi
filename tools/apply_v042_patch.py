from pathlib import Path

source_path = Path("app/src/main/java/com/aikimochi/app/AiNoKimochiActivityV41.kt")
text = source_path.read_text(encoding="utf-8")

old = r'''@Composable private fun V41AttentionStage(tokens: List<V41Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
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
}'''

new = r'''@Composable private fun V41AttentionStage(tokens: List<V41Token>, selectedToken: Int, onSelected: (Int) -> Unit) {
    val weights = remember(tokens, selectedToken) { V41Engine.attention(tokens, selectedToken) }
    val selectedColor = v41TokenColor(selectedToken)
    Text("基準にするTokenをタップ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    V41TokenRow(tokens, selectedToken, onSelected)
    Spacer(Modifier.height(14.dp))

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("「${tokens[selectedToken].text}」が参考にする割合", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("上が参照先のKey、下がQuery側のTokenです。線を下から上へ追うと、どのToken同士が結ばれているか分かります。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(14.dp))

            Text("↑ 参照先Token（Key）", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                tokens.forEachIndexed { index, token ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(token.text, color = v41TokenColor(index), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(9.dp).clip(CircleShape).background(v41TokenColor(index)))
                    }
                }
            }

            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val count = tokens.size.coerceAtLeast(1)
                val xs = List(count) { i -> size.width * ((i + .5f) / count) }
                val topY = 10f
                val bottomY = size.height - 10f
                val startX = xs[selectedToken]

                weights.forEachIndexed { index, weight ->
                    val color = v41TokenColor(index)
                    drawLine(
                        color = color.copy(alpha = (.34f + weight * 1.8f).coerceIn(.34f, 1f)),
                        start = Offset(startX, bottomY),
                        end = Offset(xs[index], topY),
                        strokeWidth = 5f + weight * 22f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color, 8f, Offset(xs[index], topY))
                }

                drawCircle(selectedColor, 14f, Offset(startX, bottomY))
                drawCircle(
                    selectedColor.copy(alpha = .90f),
                    24f,
                    Offset(startX, bottomY),
                    style = Stroke(width = 4f + weights[selectedToken] * 10f)
                )
            }

            Row(Modifier.fillMaxWidth()) {
                tokens.forEachIndexed { index, token ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(v41TokenColor(index)))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            token.text,
                            color = if (index == selectedToken) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = if (index == selectedToken) FontWeight.Black else FontWeight.Bold,
                            maxLines = 1
                        )
                        if (index == selectedToken) {
                            Text("Query", color = selectedColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("↓ 基準Token（Query）", color = selectedColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            weights.forEachIndexed { index, value ->
                V41PercentageBar(
                    if (index == selectedToken) "${tokens[index].text}（自分自身）" else tokens[index].text,
                    value,
                    v41TokenColor(index)
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    V41InfoCard("代表式", "Attention(Q,K,V) = softmax(QKᵀ / √dₖ)V")
}'''

if old not in text:
    raise SystemExit("Target Attention block not found; refusing to patch an unexpected source version.")

source_path.write_text(text.replace(old, new), encoding="utf-8")

# Version bump.
gradle = Path("app/build.gradle.kts")
g = gradle.read_text(encoding="utf-8")
g = g.replace('versionCode = 7', 'versionCode = 8').replace('versionName = "0.4.1"', 'versionName = "0.4.2"')
gradle.write_text(g, encoding="utf-8")

# Keep the normal APK workflow on the new version after this one-time migration.
workflow = Path(".github/workflows/android.yml")
w = workflow.read_text(encoding="utf-8").replace("v0.4.1", "v0.4.2")
workflow.write_text(w, encoding="utf-8")

readme = Path("README.md")
r = readme.read_text(encoding="utf-8")
r = r.replace("**Version 0.4.1**", "**Version 0.4.2**")
marker = "## v0.4.1 の主な変更"
if marker in r and "## v0.4.2 の主な変更" not in r:
    r = r.replace(marker, "## v0.4.2 の主な変更\n\n- Attention図の上段に参照先Token（Key）、下段に基準Token（Query）を直接表示\n- 線の始点・終点とToken名を同じ色で対応\n- Self-Attentionの自己参照を `猫 → 猫` の縦線として可視化\n- 線を追うだけで、どのToken同士が結ばれているか分かる表示へ改善\n\n" + marker)
r = r.replace("v0.4.1", "v0.4.2")
readme.write_text(r, encoding="utf-8")

print("Applied v0.4.2 Attention label patch")
