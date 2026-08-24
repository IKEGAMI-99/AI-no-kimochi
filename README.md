# AIのキモチ

**Version 0.3.1**

Transformer / LLM が文章を生成するまでの内部処理を、操作しながら理解する Android 学習アプリです。

## 学習フロー

`文章 → Token → Embedding → Q/K/V → Attention → FFN → 次Token予測`

v0.3.1 では簡易/詳細モードの切り替えを廃止し、数式・Token ID・教育用ベクトル・Logitなどを常に表示する詳細学習モードへ一本化しました。

## v0.3.1 の主な変更

- Beginner / Advanced 切り替えを廃止し、詳細モードのみへ統一
- 各ステップの説明文を大幅に増量
- 「なぜこの処理が必要か」「次の処理とどうつながるか」を追加説明
- `次に何が起きる？` カードを各ステップ内容の一番下へ移動
- `statusBarsPadding()` と追加上マージンで、ステータスバーや画面上部UIとの重なりを改善
- navigation bar 側にも安全マージンを追加
- Token ID / Embeddingベクトル / Q・K・Vベクトル / Attention式 / FFN式 / Logit を常時表示
- PLAY / PAUSE は引き続き廃止、操作は BACK / NEXT のみ

## 主な学習内容

### Tokenization
文章をTokenへ分割し、Token IDとして扱う流れを表示します。

### Embedding
Tokenを意味的な関係を持つベクトルへ変換します。点とTokenラベルを同じ色で対応させ、近い例 `リンゴ ↔ 梨`、遠い例 `リンゴ ↔ バス` も表示します。

### Q / K / V
同じEmbeddingを別の学習済み行列で変換し、Query / Key / Value の3つの役割を作ることを図解します。

### Self-Attention
QueryとKeyからAttention Weightを作り、その重みでValueを混ぜる流れを表示します。自分自身へのAttentionも可視化します。

### FFN
Attentionで集めた情報をTokenごとに `Linear → Activation → Linear` で加工する流れを表示します。

### Logits / Sampling
次Token候補のLogit・確率分布・Temperature・Samplingを表示し、文章が1Tokenずつ生成される仕組みを説明します。

## 例文

- 猫はソファで寝ている
- 私は歯医者です
- 犬がボールを追いかける
- AIは文章を生成する

## 技術構成

- Kotlin
- Jetpack Compose
- Material 3
- Android minSdk 26
- targetSdk 35
- Java 17
- 完全オフライン

## Toy Modelについて

このアプリは巨大LLMそのものを端末で動かすものではありません。

Token ID、Embedding、Q/K/V、Attention Weight、FFN内部値、Logits などには教育用の疑似値を含みます。概念の流れを理解するためのシミュレーションであり、実在モデルの内部値をそのまま再現するものではありません。

## ビルド

```bash
gradle :app:assembleDebug
```

生成先:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## APK

### v0.3.1 直接ダウンロード

[AI-no-kimochi v0.3.1 debug APK](https://raw.githubusercontent.com/IKEGAMI-99/AI-no-kimochi/main/dist/AI-no-kimochi-v0.3.1-debug.apk)

GitHub Actions の **Android APK** workflow が `main` への push ごとに APK をビルドし、`dist/AI-no-kimochi-v0.3.1-debug.apk` を更新します。

## Roadmap

### v0.4
- Attention Heatmap
- LayerごとのEmbedding比較
- Top-K / Top-P
- Transformer Layer全体の反復可視化
- アニメーション演出の強化

### Future
- Embedding Playground
- Attention Playground
- Vector Arithmetic
- 実際の小型Transformerモデルとの接続
- 内部Activationの可視化
- クイズ / 学習モード

## License

未設定。公開・配布前にライセンスを決定してください。
