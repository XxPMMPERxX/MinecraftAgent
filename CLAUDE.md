# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MinecraftAgent - 完全自立型のMinecraft Java版エージェントプラグイン。Bukkit/Spigotサーバー上で動作し、AIエージェントが自律的に行動します。

## Architecture

### Core Components
- `MinecraftAgentPlugin`: メインプラグインクラス
- `MinecraftAgent`: 個別エージェント実体
- `AgentManager`: 複数エージェント管理
- `BehaviorManager`: 行動選択・実行システム

### Behavior System
- `SurvivalBehavior`: 生存行動（体力・食糧・危険回避）
- `ResourceGatheringBehavior`: 資源収集行動
- `ExplorationBehavior`: 探索行動
- `BaseBehavior`: 全行動の基底クラス

### Support Systems
- `ConfigManager`: 設定ファイル管理
- `DatabaseManager`: データ永続化
- `Logger`: ログ管理
- `WorldKnowledge`: 環境認識

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

## Requirements

- Java 17+
- Maven 3.6+
- Bukkit/Spigot/Paper 1.20.1+
