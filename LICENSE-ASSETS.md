# ライセンス（コード以外の素材・例外） / Licensing of assets and exceptions

このリポジトリは、対象ごとに条件が異なります。  
This repository uses different terms for different kinds of files.

| 対象 / What | 条件 / Terms |
|---|---|
| ソースコード（`app/src/`、`Tools/`、`*.ps1`、Gradleのビルド設定など）<br>Source code | [MIT License](LICENSE)（Copyright (c) 2026 af） |
| 作者が作成したコード以外の素材（下記の除外を除く）<br>Author's non-code assets (except those excluded below) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.ja)（表示：af / afさんのAIラボ） |
| ブランド素材（除外）<br>Brand assets (excluded) | **許諾しません。** Not licensed. |
| 第三者のソフトウェア・素材<br>Third-party software and assets | 元のライセンスのとおり / Their own licenses |

## CC BY 4.0 の対象 / Covered by CC BY 4.0

- README などの文書

表示の例 / Attribution example: `AF-SensorMonitor by af (afさんのAIラボ), CC BY 4.0`

## 許諾しない：ブランド素材 / Not licensed: brand assets

複製・改変したものを公開・配布する場合は、次を差し替えてください。本家と取り違えられないようにするためです。  
If you publish or distribute a copy or a modified version, replace the following so that it is not mistaken for the original.

- 名称：「AF-SensorMonitor」「afさんのAIラボ」「AF-AI LAB」 / Names
- アプリアイコン：`app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`、`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` が参照する画像
- マスコット（クロさん）：`app/src/main/res/drawable-nodpi/kuro_normal.png`、`kuro_quiet.png`、`kuro_reacting.png`
- 素材の原本：`Reference/UserProvided/ユーザー提供リソース.png`、`Reference/UserProvided/透過版.png`
- `Tools/make_assets.py` は MIT ですが、このスクリプトで上記の原本から切り出した画像はブランド素材のままです。

## 第三者 / Third party

- AndroidX・Jetpack Compose（Apache-2.0）、sh.calvin.reorderable（Apache-2.0）：ビルド時にGradleが取得します。リポジトリには含みません。
- Gradle Wrapper（`gradle/wrapper/gradle-wrapper.jar`、`gradlew`、`gradlew.bat`）：Apache-2.0。
- Android SDK・Android Studio：それぞれの利用規約に従います。

## 補足 / Notes

- 上記は作者の方針を示したもので、法的助言ではありません。
- Release APK は配布していません。ソースから各自でビルドしてください。
