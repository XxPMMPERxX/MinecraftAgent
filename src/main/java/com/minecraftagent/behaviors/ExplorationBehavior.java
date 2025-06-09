package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;

/**
 * 探索行動
 */
public class ExplorationBehavior extends BaseBehavior {
    
    public ExplorationBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
    }
    
    @Override
    public boolean canExecute() {
        return isAgentValid();
    }
    
    @Override
    protected void onUpdate() {
        // 探索ロジック
    }
}