# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MinecraftAgent - 完全自立型のMinecraft Java版エージェントプラグイン。村人エンティティベースのAIエージェントが自律的に行動します。

## Architecture

### Core Components
- `MinecraftAgentPlugin`: メインプラグインクラス
- `MinecraftAgent`: 個別エージェント実体（村人エンティティベース）
- `AgentManager`: 複数エージェント管理
- `BehaviorManager`: 優先度ベース行動選択・実行システム

### Enhanced Behavior System
- `SurvivalBehavior`: 生存行動（優先度100 - 最高）
- `ResourceGatheringBehavior`: 資源収集行動（優先度80 - 地上優先）
- `ExplorationBehavior`: 探索行動（優先度60 - 最低）
- `BuildingBehavior`: 建築行動（優先度70）
- `BaseBehavior`: 全行動の基底クラス

### Enhanced Movement & Mining System
- `MovementUtils`: 段階的移動とY座標最適化
- 地上レベル優先移動（現在位置±3ブロック範囲）
- 小さなステップ移動（0.3ブロック以下）
- 視覚的フィードバック（ロープ線、パーティクル効果）

### Support Systems
- `ConfigManager`: 設定ファイル管理
- `DatabaseManager`: データ永続化
- `Logger`: ログ管理
- `WorldKnowledge`: 環境認識
- `AgentStatusDisplay`: リアルタイム状態表示

## Development Commands

```bash
# プロジェクトのビルド
mvn clean package

# テストの実行
mvn test

# 依存関係の確認
mvn dependency:tree
```

## Configuration

`src/main/resources/config.yml`でエージェントの行動パラメータを設定可能。

### Key Configuration Changes Made
- `resource_gathering.search_radius`: 20 → 15（地上優先のため縮小）
- 地上優先採掘ロジック（priorityResources配列の順序変更）
- 移動最適化設定（movement_speed: 0.21）

## Recent Major Improvements

### 移動システム改善 (MinecraftAgent.java:163-187, MovementUtils.java)
- 村人AI無効化（職業・取引無効、ターゲットクリア）
- 段階的移動システム（0.3ブロック以下のステップ）
- 地上レベル優先移動（findSuitableY method）
- 定期的AI抑制（suppressVillagerAI method）

### 採掘システム改善 (ResourceGatheringBehavior.java)
- 地上優先ターゲット選択（findNewTarget method）
- 視覚的フィードバック（drawRopeToTarget method）
- 座標差分表示改善（東西南北表示）
- 段階的移動統合（moveTowardsTargetGradually method）

### 問題修正履歴
1. **移動めちゃめちゃ問題** → MovementUtilsのシンプル化
2. **木の上テレポート問題** → 地上レベル優先移動
3. **村人通常動作問題** → AI無効化システム
4. **掘削位置問題** → 地上優先ロジック
5. **座標差分表示問題** → 方向表示改善

## Requirements

- Java 17+
- Maven 3.6+
- Bukkit/Spigot/Paper 1.20.1+

## Important Technical Notes

### AI Control System
- Village AI disabled but movement preserved
- Regular AI suppression in main task loop
- Target clearing and pathfinding interruption
- Maintains teleport and physics capabilities

### Movement Optimization
- Ground level priority (±3 blocks from current position)
- Gradual movement steps (max 0.3 blocks per step)
- Height change minimization for natural movement
- Safety checks for lava, fire, and fall damage

### Visual Feedback System
- Rope lines with distance-based colors (green/yellow/red)
- Particle highlights for targets and mining blocks
- Coordinate difference display in human-readable format
- Real-time status updates via scoreboard
