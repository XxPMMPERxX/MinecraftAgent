package com.minecraftagent.ai;

import com.minecraftagent.agent.MinecraftAgent;

/**
 * ワールド知識管理クラス
 */
public class WorldKnowledge {
    
    private final MinecraftAgent agent;
    
    public WorldKnowledge(MinecraftAgent agent) {
        this.agent = agent;
    }
    
    /**
     * ワールド知識を更新
     */
    public void update() {
        // 周囲の環境を分析・記録
    }
}