# Benchmark Harness 手順

## 目的

- `cold/warm` を分離する
- `baseline` と `candidate` を同一条件で比較する
- 各ケースを `n>=5` の中央値で比較する

`baseline` は「同一 modpack と同一実行条件で、本 mod の最適化機能を無効化した実行」を使う。計測機能そのものは有効のままにして、JSONL の形を揃える。

## ケース定義

- `startup_cold`: marker / foundation 出力がない状態から起動し、タイトル画面 first frame までを測る
- `startup_warm`: 同一 game dir を 1 回 priming したあとに再起動し、タイトル画面 first frame までを測る
- `resource_reload`: 同一 pack 構成のまま resource reload 全体を測る
- `world_join_existing`: 固定既存ワールド参加の `TTFCF` を主指標にし、参加後 30 秒の stall 件数と最大 frame time を補助指標にする

## 同一環境条件

比較対象に残す run は、`benchmark_run_start` の以下が一致していること。

- `preflightFingerprint`
- `configHash`
- `jvmArgsHash`
- `resourcePackFingerprint`
- `shaderEnabled=false`
- `worldId` (`world_join_existing` のみ)

温度条件が一致しない run は `benchmark_run_invalidated` として失格扱いになる。

## 実行手順

1. `baseline` 用ビルドで `.\\scripts\\Invoke-BenchmarkCase.ps1 -CaseId startup_cold -Variant baseline -RunIndex 1 -Launch` のように起動する
2. ケースに応じてタイトル画面表示、resource reload、既存ワールド参加を実施する
3. 各ケースを `baseline` と `candidate` それぞれ最低 5 回ずつ行う
4. `startup_warm` は同じ `game dir` を priming 後に再利用する
5. 実行後に `.\\scripts\\Summarize-BenchmarkResults.ps1` を実行して summary を生成する

## 出力

raw は各 run の `benchmark-events.jsonl` に保存される。summary は以下に出る。

- `artifacts/benchmarks/summary/benchmark-summary.json`
- `artifacts/benchmarks/summary/benchmark-summary.md`

正式判定は中央値を使う。平均値は扱わない。
