# MinecraftAgent

完全自立型のMinecraft Java版エージェントプラグイン

## 概要

MinecraftAgentは、Bukkit/Spigotサーバー上で動作する完全自立型エージェントシステムです。AIエージェントが自律的に生存・探索・資源収集を行います。

## 機能

### 🤖 自律エージェントシステム
- 複数エージェントの同時管理
- リアルタイム行動決定
- 自動復活システム
- 設定可能な行動パラメータ

### 🧠 行動システム
- **生存行動**: 体力・食糧管理、危険回避
- **資源収集**: 木材、石材、鉱石の自動採取
- **探索行動**: 環境マッピングと新地域発見
- **戦闘システム**: 自動防御と敵対応

### ⚙️ 管理機能
- 設定ファイルによる詳細カスタマイズ
- データベース連携（SQLite/MySQL）
- 包括的なログシステム
- コマンドベースの管理インターフェース

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
# config.yml
agent:
  max_agents: 5
  default_behavior:
    survival:
      enabled: true
      priority: 100
      health_threshold: 10
    resource_gathering:
      enabled: true
      priority: 80
    exploration:
      enabled: true
      priority: 60
```

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

## ライセンス

MIT License

## サポート

バグ報告や機能要望は Issues でお知らせください。