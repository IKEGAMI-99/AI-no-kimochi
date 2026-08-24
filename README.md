# AIのキモチ

**Version 0.2.1**

Transformer / LLM が文章を生成するまでの流れを、アニメーションと操作で体感する Android 学習アプリです。

## コンセプト

初心者モードでは、LLMの流れを次の5段階に整理しています。

`文章を渡す → 文章を区切る → 数字にする → 関係を見る → 次を予想する`

Advanced モードでは、それぞれに対応する `Text Input / Tokenization / Embedding / Self-Attention / Logits & Sampling` などの技術用語や内部値も表示します。

## v0.2.1 の主な変更

- Embedding の各点に対応する Token ラベルを直接表示
- Embedding の点・ラベル・Tokenカードを同じ色で統一
- 「意味が近い / 遠い」ベクトルの具体例を追加
  - 近い例: リンゴ ↔ 梨
  - 遠い例: リンゴ ↔ バス
- Embedding の距離はモデルや文脈で変わることを注記
- Attention の説明を「どこを見る？」から「どのTokenをどれくらい参考にする？」へ改善
- Self-Attention が自分自身のTokenも参照することを明記
- 選択Token自身へのAttentionを外側リングで可視化
- Attention割合バーに「自分自身」と明記
- Attentionの線と割合バーをTokenごとの色に対応

## v0.2.0 から継続している機能

- 自由入力欄を廃止し、学習用の例文選択式
- 例文4種類
  - 猫はソファで寝ている
  - 私は歯医者です
  - 犬がボールを追いかける
  - AIは文章を生成する
- 初心者向けの5段階学習フロー
- Tokenの分割表示
- Embeddingの教育用可視化
- AttentionのToken選択・線・割合表示
- Temperature操作と次Token候補の確率表示
- Beginner / Advanced 切替
- PLAY / PAUSE / BACK / NEXT
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

### v0.2.1 直接ダウンロード

[AI-no-kimochi v0.2.1 debug APK](https://raw.githubusercontent.com/IKEGAMI-99/AI-no-kimochi/main/dist/AI-no-kimochi-v0.2.1-debug.apk)

GitHub Actions の **Android APK** workflow が `main` への push ごとに APK をビルドし、`dist/AI-no-kimochi-v0.2.1-debug.apk` を更新します。

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
