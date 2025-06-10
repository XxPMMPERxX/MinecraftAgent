package com.minecraftagent.config;

import com.minecraftagent.MinecraftAgentPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 設定管理クラス
 */
public class ConfigManager {
    
    private final MinecraftAgentPlugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 設定ファイルを読み込み
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
    
    /**
     * 最大エージェント数を取得
     */
    public int getMaxAgents() {
        return config.getInt("agent.max_agents", 10);
    }
    
    /**
     * 生存行動が有効かどうか
     */
    public boolean isSurvivalEnabled() {
        return config.getBoolean("agent.default_behavior.survival.enabled", true);
    }
    
    /**
     * 生存行動の優先度を取得
     */
    public int getSurvivalPriority() {
        return config.getInt("agent.default_behavior.survival.priority", 100);
    }
    
    /**
     * 体力閾値を取得
     */
    public int getHealthThreshold() {
        return config.getInt("agent.default_behavior.survival.health_threshold", 10);
    }
    
    /**
     * 満腹度閾値を取得
     */
    public int getFoodThreshold() {
        return config.getInt("agent.default_behavior.survival.food_threshold", 10);
    }
    
    /**
     * 資源収集行動が有効かどうか
     */
    public boolean isResourceGatheringEnabled() {
        return config.getBoolean("agent.default_behavior.resource_gathering.enabled", true);
    }
    
    /**
     * 資源収集行動の優先度を取得
     */
    public int getResourceGatheringPriority() {
        return config.getInt("agent.default_behavior.resource_gathering.priority", 80);
    }
    
    /**
     * 探索行動が有効かどうか
     */
    public boolean isExplorationEnabled() {
        return config.getBoolean("agent.default_behavior.exploration.enabled", true);
    }
    
    /**
     * 探索行動の優先度を取得
     */
    public int getExplorationPriority() {
        return config.getInt("agent.default_behavior.exploration.priority", 60);
    }
    
    /**
     * 探索半径を取得
     */
    public int getExplorationRadius() {
        return config.getInt("agent.default_behavior.exploration.radius", 100);
    }
    
    /**
     * 探索間隔を取得（秒）
     */
    public int getExplorationInterval() {
        return config.getInt("agent.default_behavior.exploration.interval_seconds", 30);
    }
    
    /**
     * 建築行動が有効かどうか
     */
    public boolean isBuildingEnabled() {
        return config.getBoolean("agent.default_behavior.building.enabled", true);
    }
    
    /**
     * 建築行動の優先度を取得
     */
    public int getBuildingPriority() {
        return config.getInt("agent.default_behavior.building.priority", 70);
    }
    
    /**
     * 戦闘が有効かどうか
     */
    public boolean isCombatEnabled() {
        return config.getBoolean("agent.combat.enabled", true);
    }
    
    /**
     * 自動防御が有効かどうか
     */
    public boolean isAutoDefendEnabled() {
        return config.getBoolean("agent.combat.auto_defend", true);
    }
    
    /**
     * 逃走体力閾値を取得
     */
    public int getFleeHealthThreshold() {
        return config.getInt("agent.combat.flee_health", 5);
    }
    
    /**
     * モブ攻撃が有効かどうか
     */
    public boolean isAttackMobsEnabled() {
        return config.getBoolean("agent.combat.attack_mobs", true);
    }
    
    /**
     * プレイヤー攻撃が有効かどうか
     */
    public boolean isAttackPlayersEnabled() {
        return config.getBoolean("agent.combat.attack_players", false);
    }
    
    /**
     * タスク実行間隔を取得（ティック）
     */
    public int getTaskInterval() {
        return config.getInt("performance.task_interval", 20);
    }
    
    /**
     * チャンク読み込み範囲を取得
     */
    public int getChunkRadius() {
        return config.getInt("performance.chunk_radius", 3);
    }
    
    /**
     * エンティティ検索範囲を取得
     */
    public int getEntitySearchRadius() {
        return config.getInt("performance.entity_search_radius", 16);
    }
    
    /**
     * データベースタイプを取得
     */
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }
    
    /**
     * SQLiteファイルパスを取得
     */
    public String getSQLiteFile() {
        return config.getString("database.sqlite.file", "plugins/MinecraftAgent/data.db");
    }
    
    /**
     * MySQLホストを取得
     */
    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }
    
    /**
     * MySQLポートを取得
     */
    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }
    
    /**
     * MySQLデータベース名を取得
     */
    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "minecraft_agent");
    }
    
    /**
     * MySQLユーザー名を取得
     */
    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }
    
    /**
     * MySQLパスワードを取得
     */
    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }
    
    /**
     * ログレベルを取得
     */
    public String getLogLevel() {
        return config.getString("logging.level", "INFO");
    }
    
    /**
     * ファイルログが有効かどうか
     */
    public boolean isFileLoggingEnabled() {
        return config.getBoolean("logging.file", true);
    }
    
    /**
     * コンソールログが有効かどうか
     */
    public boolean isConsoleLoggingEnabled() {
        return config.getBoolean("logging.console", true);
    }
}