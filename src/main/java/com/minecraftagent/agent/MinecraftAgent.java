package com.minecraftagent.agent;

import com.minecraftagent.MinecraftAgentPlugin;
import com.minecraftagent.ai.BehaviorManager;
import com.minecraftagent.ai.WorldKnowledge;
import com.minecraftagent.display.AgentStatusDisplay;
import com.minecraftagent.utils.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private AgentStatusDisplay statusDisplay;
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
        this.statusDisplay = new AgentStatusDisplay(this);
        
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
            
            // ステータス表示開始
            statusDisplay.startDisplay();
            
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
        
        // ステータス表示停止
        if (statusDisplay != null) {
            statusDisplay.stopDisplay();
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
        
        // 安全なスポーン位置を確保
        Location safeLocation = findSafeSpawnLocation(homeLocation);
        
        // 村人エンティティを使用
        // 人間らしい見た目で攻撃的でない
        Entity spawnedEntity = world.spawnEntity(safeLocation, EntityType.VILLAGER);
        
        if (spawnedEntity instanceof LivingEntity) {
            this.entity = (LivingEntity) spawnedEntity;
            
            // エンティティ設定
            entity.setCustomName("§a[Agent] " + agentName);
            entity.setCustomNameVisible(true);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            // AIを部分的に制御（移動可能にするためsetAwareは無効化しない）
            if (entity instanceof org.bukkit.entity.Mob) {
                org.bukkit.entity.Mob mob = (org.bukkit.entity.Mob) entity;
                // mob.setAware(false); // コメントアウト：移動を可能にするため
                mob.setTarget(null); // ターゲットのみクリア
            }
            
            // 村人の場合の設定
            if (entity instanceof org.bukkit.entity.Villager) {
                org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                villager.setTarget(null); // ターゲット解除
            }
            
            // 初期ステータス更新
            updateStatus();
            
            logger.debug("エンティティをスポーンしました: " + agentName + " at " + safeLocation);
        } else {
            throw new IllegalStateException("有効なLivingEntityをスポーンできませんでした");
        }
    }
    
    /**
     * 安全なスポーン位置を探す
     */
    private Location findSafeSpawnLocation(Location original) {
        World world = original.getWorld();
        int baseX = original.getBlockX();
        int baseZ = original.getBlockZ();
        
        // 元の位置から検索開始
        for (int radius = 0; radius <= 10; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius || Math.abs(z) == radius || radius == 0) {
                        Location testLocation = new Location(world, baseX + x, 0, baseZ + z);
                        Location safeLocation = getSafeLocationAt(testLocation);
                        
                        if (safeLocation != null) {
                            return safeLocation;
                        }
                    }
                }
            }
        }
        
        // 最悪の場合は高い位置に設定
        return new Location(world, baseX, Math.max(original.getY(), 80), baseZ);
    }
    
    /**
     * 指定位置で安全なスポーン地点を取得
     */
    private Location getSafeLocationAt(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        // 上から下に向かって安全な場所を探す
        int maxY = Math.min(world.getHighestBlockYAt(x, z) + 5, world.getMaxHeight() - 1);
        
        for (int y = maxY; y > world.getMinHeight(); y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            
            // 地面が固体で、足元と頭上が空いている
            if (ground.getType().isSolid() && 
                (feet.getType().isAir() || !feet.getType().isSolid()) &&
                (head.getType().isAir() || !head.getType().isSolid())) {
                
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        
        return null; // 安全な場所が見つからない
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
                
            // 体力が非常に低い場合は緊急回復
            if (this.health <= 2.0 && this.health < entity.getMaxHealth()) {
                entity.setHealth(Math.min(entity.getMaxHealth(), this.health + 1.0));
                logger.debug("エージェント " + agentName + " の緊急体力回復を実行しました");
            }
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
    public AgentStatusDisplay getStatusDisplay() { return statusDisplay; }
    public MinecraftAgentPlugin getPlugin() { return plugin; }
    public Logger getLogger() { return logger; }
}