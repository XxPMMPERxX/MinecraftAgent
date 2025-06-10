package com.minecraftagent;

import com.minecraftagent.agent.AgentManager;
import com.minecraftagent.commands.AgentCommand;
import com.minecraftagent.config.ConfigManager;
import com.minecraftagent.listeners.PlayerListener;
import com.minecraftagent.listeners.WorldListener;
import com.minecraftagent.storage.DatabaseManager;
import com.minecraftagent.utils.Logger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MinecraftAgent メインプラグインクラス
 * 完全自立型のMinecraft Javaエージェントシステム
 */
public class MinecraftAgentPlugin extends JavaPlugin {
    
    private static MinecraftAgentPlugin instance;
    
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private AgentManager agentManager;
    private Logger logger;
    
    @Override
    public void onEnable() {
        instance = this;
        
        try {
            // 設定マネージャー初期化（ロガーより先に）
            this.configManager = new ConfigManager(this);
            configManager.loadConfig();
            
            // ロガー初期化（設定読み込み後）
            this.logger = new Logger(this);
            logger.info("MinecraftAgent プラグインを開始しています...");
            logger.info("設定ファイルを読み込みました");
            
            // データベースマネージャー初期化
            this.databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            logger.info("データベースを初期化しました");
            
            // エージェントマネージャー初期化
            this.agentManager = new AgentManager(this);
            agentManager.initialize();
            logger.info("エージェントマネージャーを初期化しました");
            
            // コマンド登録
            getCommand("agent").setExecutor(new AgentCommand(this));
            logger.info("コマンドを登録しました");
            
            // イベントリスナー登録
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            getServer().getPluginManager().registerEvents(new WorldListener(this), this);
            logger.info("イベントリスナーを登録しました");
            
            logger.info("MinecraftAgent プラグインが正常に有効化されました");
            
        } catch (Exception e) {
            logger.error("プラグインの初期化中にエラーが発生しました: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        logger.info("MinecraftAgent プラグインを停止しています...");
        
        try {
            // エージェント停止
            if (agentManager != null) {
                agentManager.shutdown();
                logger.info("全てのエージェントを停止しました");
            }
            
            // データベース接続終了
            if (databaseManager != null) {
                databaseManager.close();
                logger.info("データベース接続を終了しました");
            }
            
            logger.info("MinecraftAgent プラグインが正常に無効化されました");
            
        } catch (Exception e) {
            logger.error("プラグインの停止中にエラーが発生しました: " + e.getMessage(), e);
        }
        
        instance = null;
    }
    
    /**
     * プラグインインスタンスを取得
     * @return プラグインインスタンス
     */
    public static MinecraftAgentPlugin getInstance() {
        return instance;
    }
    
    /**
     * 設定マネージャーを取得
     * @return 設定マネージャー
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * データベースマネージャーを取得
     * @return データベースマネージャー
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * エージェントマネージャーを取得
     * @return エージェントマネージャー
     */
    public AgentManager getAgentManager() {
        return agentManager;
    }
    
    /**
     * ロガーを取得
     * @return ロガー
     */
    public Logger getPluginLogger() {
        return logger;
    }
}