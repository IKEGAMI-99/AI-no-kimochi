# AIのキモチ

**Version 0.4.0**

Transformer / LLM が文章を生成するまでの内部処理を、横スワイプで1ページずつ追いながら理解する Android 学習アプリです。

## 学習フロー

`文章 → Token → Embedding → Q/K/V → Attention → FFN → 次Token予測 → AI用語辞典`

## v0.4.0 の主な変更

- 例文選択UIを廃止し、教材用の固定例文 `猫はソファで寝ている` に統一
- BACK / NEXT ボタンを廃止
- ページ移動を横スワイプ式へ変更
- 上部ステップタブから任意ページへのジャンプも可能
- 表示領域を拡大し、解説コンテンツへ使える縦スペースを増加
- 最終ページに **AI用語辞典** を追加
- 用語辞典はカテゴリ選択 → 用語カード → タップで詳細表示
- 用語カテゴリ:
  - 基礎概念
  - モデル内部
  - 学習・調整
  - 推論・生成
  - 検索・知識
  - マルチモーダル
  - 性能・運用
  - 開発ツール
  - 主要AI
  - オープンウェイト
- RAG / Vector DB / LoRA / RLHF / KV Cache / Quantization / Tool Calling など主要用語を収録

## 主な学習内容

### Tokenization
文章をTokenへ分割し、Token IDとして扱う流れを表示します。

### Embedding
Tokenを多次元ベクトルへ変換する考え方を可視化します。近い例 `リンゴ ↔ 梨`、遠い例 `リンゴ ↔ バス` も表示します。

### Q / K / V
同じEmbeddingを別の学習済み行列で変換し、Query / Key / Value の役割を作ることを図解します。

### Self-Attention
QueryとKeyからAttention Weightを作り、その重みでValueを混ぜる流れを表示します。自分自身へのAttentionも可視化します。

### FFN
Attentionで集めた情報をTokenごとに加工する流れを表示します。

### Logits / Sampling
Logit、Softmax、Temperature、Samplingを使って次Tokenが決まる流れを表示します。

### AI用語辞典
AI関連用語をカテゴリ別に表示し、用語をタップすると詳細説明と関連語を確認できます。

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

### v0.4.0 直接ダウンロード

[AI-no-kimochi v0.4.0 debug APK](https://raw.githubusercontent.com/IKEGAMI-99/AI-no-kimochi/main/dist/AI-no-kimochi-v0.4.0-debug.apk)

GitHub Actions の **Android APK** workflow が `main` への push ごとに APK をビルドし、`dist/AI-no-kimochi-v0.4.0-debug.apk` を更新します。

## Roadmap

### v0.4.x
- Attention Heatmap
- LayerごとのEmbedding比較
- Top-K / Top-P
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
