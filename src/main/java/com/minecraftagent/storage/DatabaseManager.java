package com.minecraftagent.storage;

import com.minecraftagent.MinecraftAgentPlugin;

/**
 * データベース管理クラス
 */
public class DatabaseManager {
    
    private final MinecraftAgentPlugin plugin;
    
    public DatabaseManager(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * データベースを初期化
     */
    public void initialize() {
        // SQLite初期化（簡易実装）
        plugin.getPluginLogger().info("データベースを初期化しました");
    }
    
    /**
     * データベース接続を終了
     */
    public void close() {
        plugin.getPluginLogger().info("データベース接続を終了しました");
    }
}