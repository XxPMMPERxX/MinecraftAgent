# MinecraftAgent

完全自立型のMinecraft Java版エージェントプラグイン

## 概要

MinecraftAgentは、Bukkit/Spigotサーバー上で動作する完全自立型エージェントシステムです。村人エンティティをベースとしたAIエージェントが自律的に生存・探索・資源収集を行います。

## 主要機能

### 🤖 自律エージェントシステム
- **村人ベースのエージェント**: 自然な見た目で人間らしい動作
- **AI制御システム**: 村人の標準AIを無効化し、カスタムAIで完全制御
- **複数エージェント管理**: 最大10体のエージェントを同時運用
- **自動復活システム**: 死亡時の自動復活（5秒後）
- **リアルタイム状態監視**: ヘルス、位置、行動状況の継続監視

### 🧠 高度な行動システム
- **優先度ベース意思決定**: 複数の行動から最適なものを自動選択
- **生存行動 (優先度100)**: 体力・食糧管理、危険回避
- **資源収集行動 (優先度80)**: 地上優先の効率的な採掘・伐採
- **探索行動 (優先度60)**: ランダムウォークによる環境探索
- **建築行動 (優先度70)**: 基本的な建築・設置作業

### 🎯 改良された移動・採掘システム
- **段階的移動**: 小さなステップによる自然な移動
- **地上優先採掘**: 地下鉱石よりも地上資源を優先
- **視覚的フィードバック**: ターゲットまでのロープ線表示
- **座標差分表示**: 方向と距離の直感的な表示（東西南北）
- **パーティクル効果**: ターゲット、移動経路、掘削箇所のハイライト

## 要件

- Minecraft Java Edition 1.20.1
- Bukkit/Spigot/Paper サーバー
- Java 17以降
- Maven 3.6以降（ビルド時）

## インストール

### 1. プラグインのビルド
```bash
mvn clean package
```

### 2. プラグインのデプロイ
生成された `target/minecraft-agent-1.0.0.jar` をサーバーの `plugins` フォルダにコピー

### 3. サーバー起動
サーバーを起動すると自動的に設定ファイルが生成されます
```bash
docker compose run --service-ports mc
```

### 4. 設定（オプション）
`plugins/MinecraftAgent/config.yml` を編集してエージェントの行動をカスタマイズ

## 使用方法

### エージェント管理コマンド

```bash
# エージェントをスポーン
/agent spawn <エージェント名>

# エージェントを削除
/agent remove <エージェント名>

# エージェント一覧表示
/agent list

# システム状態確認
/agent status
```

### 設定例

```yaml
# config.yml - 完全な設定例
agent:
  max_agents: 10
  default_behavior:
    # 生存行動 - 最高優先度
    survival:
      enabled: true
      priority: 100
      health_threshold: 10
      food_threshold: 10
    
    # 資源収集行動 - 地上優先
    resource_gathering:
      enabled: true
      priority: 80
      search_radius: 15        # 地上優先のため縮小
      movement_speed: 0.21
      mining_intervals: 1000
    
    # 探索行動 - 最低優先度
    exploration:
      enabled: true
      priority: 60
      radius: 100
      interval_seconds: 30
    
    # 建築行動
    building:
      enabled: true
      priority: 70

# パフォーマンス設定
performance:
  task_interval: 20          # 20tick間隔でタスク実行
  chunk_radius: 3
  entity_search_radius: 16
```

## エージェントの特徴

### 視覚的フィードバック
- **ロープ線**: エージェントからターゲットまでの直線を表示
  - 🟢 緑: 4ブロック以内（採掘可能距離）
  - 🟡 黄: 4-10ブロック（中距離）
  - 🔴 赤: 10ブロック以上（遠距離）
- **ターゲットハイライト**: 金色のパーティクル効果
- **座標差分**: `東5.2 下2.1 南3.4 距離:6.8` 形式の分かりやすい表示

### AI制御システム
- **村人AI無効化**: 標準の村人行動（取引、職業など）を完全停止
- **カスタムAI制御**: BehaviorManagerによる完全制御
- **移動機能保持**: teleportと物理移動は維持
- **定期的抑制**: 村人の自動行動を継続的に抑制

### 移動最適化
- **段階的移動**: 0.3ブロック以下の小さなステップで自然な移動
- **地上レベル優先**: 現在位置±3ブロック範囲での移動
- **安全性チェック**: 溶岩・火・落下を回避
- **高度変化最小**: Y軸の急激な変化を防止

## 権限

- `minecraftagent.admin` - 管理者権限（全コマンド使用可能）
- `minecraftagent.user` - 一般ユーザー権限（基本コマンド使用可能）

## 開発

### プロジェクト構造
```
src/main/java/com/minecraftagent/
├── MinecraftAgentPlugin.java     # メインプラグインクラス
├── agent/                        # エージェント管理
├── ai/                          # AI・行動管理
├── behaviors/                    # 行動パターン
├── commands/                     # コマンド処理
├── config/                       # 設定管理
├── listeners/                    # イベントリスナー
├── storage/                      # データ永続化
└── utils/                        # ユーティリティ
```

### カスタム行動の追加

1. `BaseBehavior` を継承した新しいクラスを作成
2. `BehaviorManager` に行動を登録
3. 設定ファイルに行動パラメータを追加

## トラブルシューティング

### エージェントが動かない場合
1. **ログ確認**: サーバーログで以下を確認
   - `BehaviorManager.update() 実行中` - BehaviorManagerが動作中
   - `isAgentValid: all checks passed` - エージェントが有効
   - `村人設定完了（移動可能）` - 村人AI制御成功

2. **設定確認**: `config.yml`の設定値を確認
   - 各行動の`enabled: true`設定
   - 適切な優先度設定
   - search_radiusなどのパラメータ

3. **権限確認**: プレイヤーに適切な権限が付与されているか

### よくある問題
- **エージェントが木の上にテレポートする** → 修正済み（地上レベル優先移動）
- **村人の通常動作をしている** → 修正済み（AI制御システム）
- **移動がめちゃめちゃ** → 修正済み（段階的移動システム）
- **掘る場所がおかしい** → 修正済み（地上優先採掘）

### デバッグモード
ログレベルを`DEBUG`に設定すると詳細な動作ログが出力されます：
```yaml
logging:
  level: "DEBUG"
```

## ライセンス

MIT License

## サポート

バグ報告や機能要望は Issues でお知らせください。
