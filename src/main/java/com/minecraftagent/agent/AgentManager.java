package com.minecraftagent.agent;

import com.minecraftagent.MinecraftAgentPlugin;
import com.minecraftagent.utils.Logger;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * エージェント管理クラス
 */
public class AgentManager {
    
    private final MinecraftAgentPlugin plugin;
    private final Logger logger;
    private final Map<String, MinecraftAgent> agents;
    private final int maxAgents;
    
    public AgentManager(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.agents = new ConcurrentHashMap<>();
        this.maxAgents = plugin.getConfigManager().getMaxAgents();
    }
    
    /**
     * エージェントマネージャーを初期化
     */
    public void initialize() {
        logger.info("エージェントマネージャーを初期化しました (最大エージェント数: " + maxAgents + ")");
    }
    
    /**
     * 新しいエージェントを作成・開始
     * @param agentName エージェント名
     * @param spawnLocation スポーン地点
     * @return 作成されたエージェント、失敗時はnull
     */
    public MinecraftAgent createAgent(String agentName, Location spawnLocation) {
        // エージェント数制限チェック
        if (agents.size() >= maxAgents) {
            logger.warn("最大エージェント数に達しているため、新しいエージェントを作成できません");
            return null;
        }
        
        // 名前重複チェック
        if (agents.containsKey(agentName)) {
            logger.warn("エージェント名 '" + agentName + "' は既に使用されています");
            return null;
        }
        
        try {
            // エージェント作成
            MinecraftAgent agent = new MinecraftAgent(plugin, agentName, spawnLocation);
            agents.put(agentName, agent);
            
            // エージェント開始
            agent.start();
            
            logger.info("エージェント '" + agentName + "' を作成・開始しました");
            return agent;
            
        } catch (Exception e) {
            logger.error("エージェント '" + agentName + "' の作成に失敗しました", e);
            return null;
        }
    }
    
    /**
     * エージェントを取得
     * @param agentName エージェント名
     * @return エージェント、存在しない場合はnull
     */
    public MinecraftAgent getAgent(String agentName) {
        return agents.get(agentName);
    }
    
    /**
     * 全てのエージェントを取得
     * @return エージェントのコレクション
     */
    public Collection<MinecraftAgent> getAllAgents() {
        return agents.values();
    }
    
    /**
     * アクティブなエージェント数を取得
     * @return アクティブなエージェント数
     */
    public int getActiveAgentCount() {
        return (int) agents.values().stream()
                .filter(MinecraftAgent::isActive)
                .count();
    }
    
    /**
     * エージェントを停止・削除
     * @param agentName エージェント名
     * @return 成功時true
     */
    public boolean removeAgent(String agentName) {
        MinecraftAgent agent = agents.get(agentName);
        if (agent == null) {
            logger.warn("エージェント '" + agentName + "' が見つかりません");
            return false;
        }
        
        try {
            // エージェント停止
            agent.stop();
            
            // マップから削除
            agents.remove(agentName);
            
            logger.info("エージェント '" + agentName + "' を削除しました");
            return true;
            
        } catch (Exception e) {
            logger.error("エージェント '" + agentName + "' の削除に失敗しました", e);
            return false;
        }
    }
    
    /**
     * 全てのエージェントを停止
     */
    public void stopAllAgents() {
        logger.info("全てのエージェントを停止しています...");
        
        for (MinecraftAgent agent : agents.values()) {
            try {
                agent.stop();
            } catch (Exception e) {
                logger.error("エージェント '" + agent.getAgentName() + "' の停止に失敗しました", e);
            }
        }
        
        logger.info("全てのエージェントが停止されました");
    }
    
    /**
     * エージェントマネージャーをシャットダウン
     */
    public void shutdown() {
        stopAllAgents();
        agents.clear();
        logger.info("エージェントマネージャーをシャットダウンしました");
    }
    
    /**
     * エージェントが存在するかチェック
     * @param agentName エージェント名
     * @return 存在する場合true
     */
    public boolean hasAgent(String agentName) {
        return agents.containsKey(agentName);
    }
    
    /**
     * エージェント統計情報を取得
     * @return 統計情報の文字列
     */
    public String getStatistics() {
        int total = agents.size();
        int active = getActiveAgentCount();
        int dead = (int) agents.values().stream()
                .filter(agent -> agent.getState() == MinecraftAgent.AgentState.DEAD)
                .count();
        int disabled = (int) agents.values().stream()
                .filter(agent -> agent.getState() == MinecraftAgent.AgentState.DISABLED)
                .count();
        
        return String.format(
            "エージェント統計: 総数=%d, アクティブ=%d, 死亡=%d, 無効=%d, 最大=%d",
            total, active, dead, disabled, maxAgents
        );
    }
}