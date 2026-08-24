# AIのキモチ

**Version 0.2.0**

Transformer / LLM が文章を生成するまでの流れを、アニメーションと操作で体感する Android 学習アプリです。

## コンセプト

v0.2.0 では「技術用語を並べる」よりも「今なにが起きているかを直感で理解する」ことを優先しました。

初心者モードでは、LLMの流れを次の5段階に整理しています。

`文章を渡す → 文章を区切る → 数字にする → 関係を見る → 次を予想する`

Advanced モードでは、それぞれに対応する `Text Input / Tokenization / Embedding / Attention / Logits & Sampling` などの技術用語や内部値も表示します。

## v0.2.0 の主な変更

- 自由入力欄を廃止し、学習用の例文選択式へ変更
- 例文を4種類用意
  - 猫はソファで寝ている
  - 私は歯医者です
  - 犬がボールを追いかける
  - AIは文章を生成する
- 初心者向けの流れを10段階から5段階へ整理
- 本文・見出し・補足文のコントラストを全面改善
- 各画面に「次に何が起きる？」を表示
- Tokenの分割を視覚的なカードで表示
- Embeddingを教育用の点配置として可視化
- AttentionをToken選択＋線＋割合バーで表示
- Temperatureを操作しながら次Token候補の確率変化を確認可能
- Beginner / Advanced 切替
- PLAY / PAUSE / BACK / NEXT による自動学習フロー
- 完全オフライン

## 技術構成

- Kotlin
- Jetpack Compose
- Material 3
- Android minSdk 26
- targetSdk 35
- Java 17

## Toy Modelについて

このアプリは巨大LLMそのものを端末で動かすものではありません。

Token ID、Embedding、Attention Weight、Logits などには教育用の疑似値を含みます。概念の流れを理解するためのシミュレーションであり、実在モデルの内部値をそのまま再現するものではありません。

## ビルド

Android Studio でこのリポジトリを開き、`app` を実行してください。

CLI:

```bash
gradle :app:assembleDebug
```

生成先:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## APK

### v0.2.0 直接ダウンロード

[AI-no-kimochi v0.2.0 debug APK](https://raw.githubusercontent.com/IKEGAMI-99/AI-no-kimochi/main/dist/AI-no-kimochi-v0.2.0-debug.apk)

GitHub Actions の **Android APK** workflow が `main` への push ごとに APK をビルドし、`dist/AI-no-kimochi-v0.2.0-debug.apk` を更新します。

## Roadmap

### v0.3
- Attention Heatmap
- Q / K / V のより直感的な可視化
- LayerごとのEmbedding比較
- Top-K / Top-P
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
