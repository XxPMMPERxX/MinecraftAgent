package com.minecraftagent.agent;

import com.minecraftagent.MinecraftAgentPlugin;
import com.minecraftagent.ai.BehaviorManager;
import com.minecraftagent.ai.WorldKnowledge;
import com.minecraftagent.utils.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * 自律型Minecraftエージェント
 */
public class MinecraftAgent {
    
    private final MinecraftAgentPlugin plugin;
    private final Logger logger;
    private final String agentId;
    private final String agentName;
    
    private LivingEntity entity;
    private Location homeLocation;
    private BehaviorManager behaviorManager;
    private WorldKnowledge worldKnowledge;
    private BukkitTask mainTask;
    
    private boolean isActive;
    private long lastUpdate;
    
    // エージェントの状態
    private AgentState state;
    private double health;
    private int foodLevel;
    
    public enum AgentState {
        SPAWNING,
        ACTIVE,
        IDLE,
        DEAD,
        DISABLED
    }
    
    public MinecraftAgent(MinecraftAgentPlugin plugin, String agentName, Location spawnLocation) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.agentId = UUID.randomUUID().toString();
        this.agentName = agentName;
        this.homeLocation = spawnLocation.clone();
        this.state = AgentState.SPAWNING;
        this.isActive = false;
        this.lastUpdate = System.currentTimeMillis();
        
        // サブシステム初期化
        this.worldKnowledge = new WorldKnowledge(this);
        this.behaviorManager = new BehaviorManager(this);
        
        logger.info("エージェント " + agentName + " を作成しました (ID: " + agentId + ")");
    }
    
    /**
     * エージェントを開始
     */
    public void start() {
        if (isActive) {
            logger.warn("エージェント " + agentName + " は既に開始されています");
            return;
        }
        
        try {
            // エンティティをスポーン
            spawnEntity();
            
            // 行動管理システム初期化
            behaviorManager.initialize();
            
            // メインタスク開始
            startMainTask();
            
            this.isActive = true;
            this.state = AgentState.ACTIVE;
            
            logger.info("エージェント " + agentName + " を開始しました");
            
        } catch (Exception e) {
            logger.error("エージェント " + agentName + " の開始に失敗しました", e);
            this.state = AgentState.DISABLED;
        }
    }
    
    /**
     * エージェントを停止
     */
    public void stop() {
        if (!isActive) {
            return;
        }
        
        this.isActive = false;
        this.state = AgentState.DISABLED;
        
        // メインタスク停止
        if (mainTask != null && !mainTask.isCancelled()) {
            mainTask.cancel();
        }
        
        // 行動管理システム停止
        if (behaviorManager != null) {
            behaviorManager.shutdown();
        }
        
        // エンティティ削除
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        
        logger.info("エージェント " + agentName + " を停止しました");
    }
    
    /**
     * エンティティをスポーン
     */
    private void spawnEntity() {
        World world = homeLocation.getWorld();
        if (world == null) {
            throw new IllegalStateException("スポーン地点のワールドが見つかりません");
        }
        
        // Villagerエンティティとしてスポーン（NPCの代替）
        Entity spawnedEntity = world.spawnEntity(homeLocation, EntityType.VILLAGER);
        
        if (spawnedEntity instanceof LivingEntity) {
            this.entity = (LivingEntity) spawnedEntity;
            
            // エンティティ設定
            entity.setCustomName("§a[Agent] " + agentName);
            entity.setCustomNameVisible(true);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            
            // 初期ステータス更新
            updateStatus();
            
            logger.debug("エンティティをスポーンしました: " + agentName);
        } else {
            throw new IllegalStateException("有効なLivingEntityをスポーンできませんでした");
        }
    }
    
    /**
     * メインタスクを開始
     */
    private void startMainTask() {
        int interval = plugin.getConfigManager().getTaskInterval();
        
        this.mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive || entity == null || entity.isDead()) {
                    handleDeath();
                    return;
                }
                
                try {
                    // ステータス更新
                    updateStatus();
                    
                    // ワールド知識更新
                    worldKnowledge.update();
                    
                    // 行動管理システム更新
                    behaviorManager.update();
                    
                    lastUpdate = System.currentTimeMillis();
                    
                } catch (Exception e) {
                    logger.error("エージェント " + agentName + " の更新中にエラーが発生しました", e);
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }
    
    /**
     * エージェントステータスを更新
     */
    private void updateStatus() {
        if (entity != null && !entity.isDead()) {
            this.health = entity.getHealth();
            this.foodLevel = (entity instanceof org.bukkit.entity.Player) ? 
                ((org.bukkit.entity.Player) entity).getFoodLevel() : 20;
        } else {
            this.health = 0;
            this.foodLevel = 0;
        }
    }
    
    /**
     * 死亡処理
     */
    private void handleDeath() {
        if (state != AgentState.DEAD) {
            logger.warn("エージェント " + agentName + " が死亡しました");
            this.state = AgentState.DEAD;
            
            // 復活処理をスケジュール
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isActive) {
                    respawn();
                }
            }, 100L); // 5秒後に復活
        }
    }
    
    /**
     * 復活処理
     */
    private void respawn() {
        try {
            logger.info("エージェント " + agentName + " を復活させています...");
            spawnEntity();
            this.state = AgentState.ACTIVE;
            logger.info("エージェント " + agentName + " が復活しました");
        } catch (Exception e) {
            logger.error("エージェント " + agentName + " の復活に失敗しました", e);
            this.state = AgentState.DISABLED;
        }
    }
    
    // Getter methods
    public String getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public LivingEntity getEntity() { return entity; }
    public Location getHomeLocation() { return homeLocation; }
    public AgentState getState() { return state; }
    public boolean isActive() { return isActive; }
    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public long getLastUpdate() { return lastUpdate; }
    
    public BehaviorManager getBehaviorManager() { return behaviorManager; }
    public WorldKnowledge getWorldKnowledge() { return worldKnowledge; }
    public MinecraftAgentPlugin getPlugin() { return plugin; }
    public Logger getLogger() { return logger; }
}