# AIのキモチ

**Version 0.1.0**

Transformer / LLM が文章を生成するまでの流れを、アニメーションと操作で体感する Android 学習アプリです。

## コンセプト

文章を読むだけでは掴みにくい Transformer の内部処理を、次の順番で触って理解します。

`Text → Token → Embedding → Position → Q/K/V → Attention → FFN → Layers → Logits → Sampling`

この初版は巨大LLMそのものを端末で動かすのではなく、教育用の **Toy Transformer / Toy Tokenizer** を使います。表示される Token ID、Embedding、Attention Weight などは学習用の疑似値を含みますが、処理の流れと概念は実際の Transformer に沿っています。

## v0.1.0 の主な機能

- 任意文章の入力
- Toy Tokenizer による Token 分割
- Token ID 表示
- 3次元に縮約した Embedding 可視化
- Position 情報の可視化
- Query / Key / Value の役割表示
- 2 Head の Attention 切替
- Attention Weight のライン表示とバー表示
- FFN の処理フロー表示
- 4 Layer の Transformer 表示
- Temperature 0.1〜2.0 のリアルタイム操作
- Logits から Probability への変換表示
- 確率に基づく Next Token Sampling
- Beginner / Advanced モード
- PLAY / PAUSE / BACK / NEXT によるステップ再生
- ダーク / SF 風UI
- 完全オフライン

## 技術構成

- Kotlin
- Jetpack Compose
- Material 3
- Android minSdk 26
- targetSdk 35
- Java 17

## ビルド

Android Studio でこのリポジトリを開き、`app` を実行してください。

CLI では Gradle 8.9 を使って以下を実行できます。

```bash
gradle :app:assembleDebug
```

生成先：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## APK

GitHub Actions の **Android APK** workflow が `main` への push ごとに debug APK を生成し、Artifact `AI-no-kimochi-v0.1.0-debug` として保存します。

## 注意

v0.1.0 の数値は教育用シミュレーションです。実在モデルの内部値をそのまま表示するものではありません。将来バージョンでは小型モデル / ONNX Runtime / llama.cpp 等との接続を検討します。

## Roadmap

### v0.2
- Attention Heatmap
- Top-K / Top-P
- LayerごとのEmbedding比較
- アニメーション速度切替
- より滑らかなParticle / Glow表現

### v0.3
- Embedding Playground
- Attention Playground
- Vector Arithmetic
- 2文章比較モード

### Future
- 実際の小型Transformerモデルとの接続
- 内部Activationの可視化
- クイズ / 学習モード
- タブレットUI

## License

未設定。公開・配布前にライセンスを決定してください。
