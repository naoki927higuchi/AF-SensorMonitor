# AF-SensorMonitor

Android端末がOS経由で公開しているセンサーをすべて列挙し、値がリアルタイムで変化する様子を見るアプリ。スマホを傾ける・振る・回す・光を当てる・手で塞ぐ・磁石を近づけるなどしたときに、どのセンサーが反応したかが一目で分かることを目的にしている。精密な測定器ではない。

## 画面

- **メイン**：1センサー1行の計器盤。左の丸（活動インジケーター）が反応中に点灯し、行もうっすら光る。`● LIVE` をタップすると `HOLD`（全体を静止）。行をタップで詳細、歯車で表示設定。
- **詳細**：全チャンネルの値、直近20秒のグラフ（`PAUSE` / `RESUME`）、センサーのメタ情報。角度センサーは方位をPPI式の円（方向＝角度、外周＝現在、中心＝20秒前）で表示する。
- **表示設定（SENSORS）**：表示ON/OFF、`ALL`（全表示）、☰のドラッグで並べ替え、`RESET ORDER`（初期順序）、`EXPORT`（`JSON` / `CSV`）。

### 記号

| 記号 | 意味 |
|---|---|
| ● / ○ | 反応中 / 静止（値を受信中） |
| ― （`---`） | 購読中だが値がまだ来ていない（変化したときだけ届くセンサー） |
| ◇ `ARMED` | 発火待ちのトリガー型センサー |
| 🔒 `LOCKED` | 権限が必要（歩数系は詳細を開くと許可を求める。身体系は1.0.0では読まない） |
| × `N/A` | OSが購読を受け付けなかった |
| `w` | Wake-upセンサー |
| `+N` | メインに出していない値の個数（全値は詳細に表示） |

## Export形式

- JSON：`format = "af-sensormonitor-inventory"`、`formatVersion = 1`。`device`（製造元・モデル・Androidバージョン・画面サイズ等。シリアル番号やANDROID_IDは含めない）、`sensorCount`、`sensors[]`（Sensor APIのメタ情報と、Export時点の受信状況 `observed`）。
- CSV：1行1センサー、各行に端末の列を持つ（複数端末のCSVを1枚のシートに貼って比べられる）。UTF-8（BOM付き）。
- `observed.valuesLength` はイベントの値配列の長さ。未知のTYPEではOSが固定長（16等）の配列を渡すことがあり、意味のある値の個数とは限らない。

## 活動インジケーターの判定

センサーのチャンネルごとに、18ms以上離れたサンプル間の変化量を約0.1秒平均したもの（activity）と、静止時の変化量（noise）を学習する。activityが `max(noise, floor)` の5倍を超えたら反応とし、すぐ点灯して約1秒で消える。noiseは下がる方向には速く（0.6秒）、上がる方向には遅く（30秒）追従するので、手ぶれは反応にならず、振り続けても数十秒は反応が見える。`floor` は標準TYPEでは種類ごとの値、未知のTYPEでは分解能から決める。値が意味を持たないイベント型・トリガー型は受信自体を反応とし、近接は NEAR/FAR の切替、歩数計は増加を反応とする。

## ビルド

```powershell
.\Build-Android.ps1
```

単体テスト → Release APK（R8、署名、`apksigner verify`）→ `bin/Android/Release-<Version>/AF-SensorMonitor-<Version>.apk` と `.sha256`。署名鍵は初回に `.local/android-signing/` へ作られる（Git管理外）。JDKは Android Studio 同梱の jbr、SDKは `%LOCALAPPDATA%\Android\Sdk`（`local.properties` はGit管理外）。

規約は [AGENTS.md](AGENTS.md)、変更履歴は [HISTORY.md](HISTORY.md)。
