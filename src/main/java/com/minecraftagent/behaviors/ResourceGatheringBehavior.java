package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;

/**
 * 資源収集行動
 */
public class ResourceGatheringBehavior extends BaseBehavior {
    
    public ResourceGatheringBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
    }
    
    @Override
    public boolean canExecute() {
        return isAgentValid();
    }
    
    @Override
    protected void onUpdate() {
        // 資源収集ロジック
    }
}