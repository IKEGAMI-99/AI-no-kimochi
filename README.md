# AIのキモチ

**Version 0.4.2**

Transformer / LLM が文章を生成するまでの内部処理を、横スワイプで1ページずつ追いながら理解する Android 学習アプリです。

## 学習フロー

`文章 → Token → Embedding → Q/K/V → Attention → FFN → 次Token予測 → AI用語辞典`

## v0.4.2 の主な変更

- Attention図の上段に参照先Token（Key）、下段に基準Token（Query）を直接表示
- 線の始点・終点とToken名を同じ色で対応
- Self-Attentionの自己参照を `猫 → 猫` の縦線として可視化
- 線を追うだけで、どのToken同士が結ばれているか分かる表示へ改善

## v0.4.1 の主な変更

- 次Token候補を5個から15個へ拡張
- Temperatureに加えて **Top-K** を追加
- **Top-P (Nucleus Sampling)** を追加
- Sampling処理を `Temperature → Top-K → Top-P → 再正規化 → Sampling` の順で可視化
- Top-K / Top-P で除外された候補は消さずにグレーアウト
- 除外候補へ `Top-Kで除外` / `Top-Pで除外` を表示
- 各候補に Temperature適用後の確率と、最終Sampling確率を表示
- 現在Sampling対象になっているToken数をリアルタイム表示
- AI用語辞典の推論・生成カテゴリに Logit / Sampling / Top-K / Top-P の説明を追加・強化

## 主な学習内容

### Tokenization
文章をTokenへ分割し、Token IDとして扱う流れを表示します。

### Embedding
Tokenを多次元ベクトルへ変換する考え方を可視化します。近い例 `リンゴ ↔ 梨`、遠い例 `リンゴ ↔ バス` も表示します。

### Q / K / V
同じEmbeddingを別の学習済み行列で変換し、Query / Key / Value の役割を作ることを図解します。

### Self-Attention
QueryとKeyからAttention Weightを作り、その重みでValueを混ぜる流れを表示します。上段に参照先Token、下段に基準Tokenを表示し、自分自身へのAttentionも縦線で可視化します。

### FFN
Attentionで集めた情報をTokenごとに加工する流れを表示します。

### Sampling
次Token候補のLogitをTemperatureで確率へ変換し、Top-KとTop-Pで候補集合を絞ったあと、残った候補だけを再正規化して次Tokenを選ぶ流れを表示します。

### AI用語辞典
AI関連用語をカテゴリ別に表示し、用語をタップすると詳細説明と関連語を確認できます。

## UI

- 教材用の固定例文 `猫はソファで寝ている`
- BACK / NEXT ボタンなし
- ページ移動は横スワイプ
- 上部ステップタブから任意ページへジャンプ可能
- 最終ページにカテゴリ別AI用語辞典

## 技術構成

- Kotlin
- Jetpack Compose
- Material 3
- Compose HorizontalPager
- Android minSdk 26
- targetSdk 35
- Java 17
- 完全オフライン

## Toy Modelについて

このアプリは巨大LLMそのものを端末で動かすものではありません。

Token ID、Embedding、Q/K/V、Attention Weight、FFN内部値、Logits、Sampling候補などには教育用の疑似値を含みます。概念の流れを理解するためのシミュレーションであり、実在モデルの内部値をそのまま再現するものではありません。

## ビルド

```bash
gradle :app:assembleDebug
```

生成先:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## APK

### v0.4.2 直接ダウンロード

[AI-no-kimochi v0.4.2 debug APK](https://raw.githubusercontent.com/IKEGAMI-99/AI-no-kimochi/main/dist/AI-no-kimochi-v0.4.2-debug.apk)

GitHub Actions の **Android APK** workflow が `main` への push ごとに APK をビルドし、`dist/AI-no-kimochi-v0.4.2-debug.apk` を更新します。

## Roadmap

### v0.4.x
- Attention Heatmap
- LayerごとのEmbedding比較
- 用語検索
- 用語お気に入り

### Future
- Transformer Layer全体の反復可視化
- Embedding Playground
- Attention Playground
- Vector Arithmetic
- 実際の小型Transformerモデルとの接続
- 内部Activationの可視化
- クイズ / 学習モード

## License

未設定。公開・配布前にライセンスを決定してください。
