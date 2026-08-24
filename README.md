# AIのキモチ

**Version 0.3.0**

Transformer / LLM が文章を生成するまでの流れを、アニメーションと操作で体感する Android 学習アプリです。

## コンセプト

初心者モードでも内部処理の流れが飛ばないよう、v0.3.0 では学習フローを7段階に整理しました。

`文章 → Token → Embedding → Q/K/V → Attention → FFN → 次Token予測`

Advanced モードでは数式や教育用ベクトルも表示します。

## v0.3.0 の主な変更

- Q / K / V を独立した学習ステップとして追加
  - Query = 「何を探している？」
  - Key = 「私はどんな情報？」
  - Value = 「実際に渡す中身」
- 同じEmbeddingからQ / K / Vへ3方向に変換されることを図解
- Advancedモードで `Q = XWq` / `K = XWk` / `V = XWv` と教育用ベクトルを表示
- Attention画面で「QとKを比較してWeightを作り、その重みでVを混ぜる」流れを明記
- FFN (Feed Forward Network) を独立した学習ステップとして追加
- FFNを `Linear → Activation → Linear` の流れで図解
- AttentionとFFNの役割の違いを明示
  - Attention = 他Tokenから情報を集める
  - FFN = Tokenごとに情報を加工する
- Advancedモードで代表的なFFN式を表示
- PLAY / PAUSE を廃止
- 下部ナビゲーションを BACK / NEXT のみに整理

## 既存機能

- 学習用の例文選択式
- 例文4種類
  - 猫はソファで寝ている
  - 私は歯医者です
  - 犬がボールを追いかける
  - AIは文章を生成する
- Token分割表示
- Embeddingの点にTokenラベルを直接表示
- Embeddingの点・ラベル・Tokenカードを同じ色で統一
- ベクトル距離の具体例
  - 近い例: リンゴ ↔ 梨
  - 遠い例: リンゴ ↔ バス
- Self-Attentionで自分自身も参照することを可視化
- Attention Weightの線・割合バー表示
- Temperature操作と次Token候補の確率表示
- Beginner / Advanced 切替
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

Token ID、Embedding、Q/K/V、Attention Weight、FFN内部値、Logits などには教育用の疑似値を含みます。概念の流れを理解するためのシミュレーションであり、実在モデルの内部値をそのまま再現するものではありません。

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

### v0.3.0 直接ダウンロード

[AI-no-kimochi v0.3.0 debug APK](https://raw.githubusercontent.com/IKEGAMI-99/AI-no-kimochi/main/dist/AI-no-kimochi-v0.3.0-debug.apk)

GitHub Actions の **Android APK** workflow が `main` への push ごとに APK をビルドし、`dist/AI-no-kimochi-v0.3.0-debug.apk` を更新します。

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
