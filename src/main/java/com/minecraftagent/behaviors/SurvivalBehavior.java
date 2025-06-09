package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 生存行動 - 体力・食糧管理、危険回避
 */
public class SurvivalBehavior extends BaseBehavior {
    
    private final int healthThreshold;
    private final int foodThreshold;
    private final boolean autoDefendEnabled;
    private final int fleeHealthThreshold;
    
    private long lastFoodCheck;
    private long lastThreatCheck;
    private Entity currentThreat;
    
    public SurvivalBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
        
        var config = agent.getPlugin().getConfigManager();
        this.healthThreshold = config.getHealthThreshold();
        this.foodThreshold = config.getFoodThreshold();
        this.autoDefendEnabled = config.isAutoDefendEnabled();
        this.fleeHealthThreshold = config.getFleeHealthThreshold();
        
        this.lastFoodCheck = 0;
        this.lastThreatCheck = 0;
    }
    
    @Override
    public boolean canExecute() {
        if (!isAgentValid()) {
            return false;
        }
        
        LivingEntity entity = agent.getEntity();
        
        // 体力が低い
        if (entity.getHealth() < healthThreshold) {
            return true;
        }
        
        // 満腹度が低い（プレイヤーエンティティの場合）
        if (entity instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) entity;
            if (player.getFoodLevel() < foodThreshold) {
                return true;
            }
        }
        
        // 近くに敵がいる
        if (hasNearbyThreats()) {
            return true;
        }
        
        // 有害なポーション効果がある
        if (hasHarmfulPotionEffects()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    protected void onUpdate() {
        LivingEntity entity = agent.getEntity();
        
        // ステータス表示を更新
        agent.getStatusDisplay().setBehavior("SurvivalBehavior");
        
        // 1. 緊急回避（体力が非常に低い場合）
        if (entity.getHealth() <= fleeHealthThreshold) {
            agent.getStatusDisplay().setAction("緊急回避中");
            flee();
            return;
        }
        
        // 2. 脅威への対処
        if (handleThreats()) {
            return;
        }
        
        // 3. 体力回復
        if (entity.getHealth() < healthThreshold) {
            agent.getStatusDisplay().setAction("体力回復中");
            heal();
        }
        
        // 4. 食事
        if (needsFood()) {
            agent.getStatusDisplay().setAction("食事中");
            eat();
        }
        
        // 5. 有害効果の解除
        if (hasHarmfulPotionEffects()) {
            agent.getStatusDisplay().setAction("毒解除中");
            cureHarmfulEffects();
        }
    }
    
    /**
     * 近くの脅威をチェック
     */
    private boolean hasNearbyThreats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastThreatCheck < 2000) { // 2秒に1回チェック
            return currentThreat != null;
        }
        
        lastThreatCheck = currentTime;
        
        Location agentLoc = agent.getEntity().getLocation();
        int searchRadius = agent.getPlugin().getConfigManager().getEntitySearchRadius();
        
        List<Entity> nearbyEntities = agent.getEntity().getNearbyEntities(searchRadius, searchRadius, searchRadius);
        
        currentThreat = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Entity entity : nearbyEntities) {
            if (entity instanceof Monster) {
                Monster monster = (Monster) entity;
                if (monster.getTarget() == agent.getEntity()) {
                    double distance = agentLoc.distance(entity.getLocation());
                    if (distance < closestDistance) {
                        currentThreat = entity;
                        closestDistance = distance;
                    }
                }
            }
        }
        
        return currentThreat != null;
    }
    
    /**
     * 脅威への対処
     */
    private boolean handleThreats() {
        if (currentThreat == null || currentThreat.isDead()) {
            currentThreat = null;
            return false;
        }
        
        LivingEntity entity = agent.getEntity();
        double distance = entity.getLocation().distance(currentThreat.getLocation());
        
        // 非常に近い場合は逃走
        if (distance < 3.0 || entity.getHealth() <= fleeHealthThreshold) {
            agent.getStatusDisplay().setAction("逃走中");
            agent.getStatusDisplay().setTarget(currentThreat.getType().name());
            flee();
            return true;
        }
        
        // 自動防御が有効で、攻撃可能な場合
        if (autoDefendEnabled && distance < 8.0) {
            agent.getStatusDisplay().setAction("戦闘中");
            agent.getStatusDisplay().setTarget(currentThreat.getType().name());
            defend();
            return true;
        }
        
        return false;
    }
    
    /**
     * 逃走処理
     */
    private void flee() {
        Location agentLoc = agent.getEntity().getLocation();
        Location fleeTarget;
        
        if (currentThreat != null) {
            // 脅威から離れる方向に逃走
            Location threatLoc = currentThreat.getLocation();
            double dx = agentLoc.getX() - threatLoc.getX();
            double dz = agentLoc.getZ() - threatLoc.getZ();
            
            // 正規化して15ブロック先に設定
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 0) {
                dx = (dx / length) * 15;
                dz = (dz / length) * 15;
            }
            
            fleeTarget = agentLoc.clone().add(dx, 0, dz);
        } else {
            // ランダムな方向に逃走
            double angle = Math.random() * 2 * Math.PI;
            double dx = Math.cos(angle) * 15;
            double dz = Math.sin(angle) * 15;
            fleeTarget = agentLoc.clone().add(dx, 0, dz);
        }
        
        // 安全な高さを探す
        fleeTarget.setY(findSafeY(fleeTarget));
        
        // 移動を試行
        moveToLocation(fleeTarget);
        
        logger.info("エージェント " + agent.getAgentName() + " が逃走しています");
        agent.getStatusDisplay().setCustomStatus("🏃 逃走中");
    }
    
    /**
     * 防御処理
     */
    private void defend() {
        if (currentThreat == null) return;
        
        LivingEntity entity = agent.getEntity();
        
        // 武器を装備
        equipBestWeapon();
        
        // 攻撃範囲内なら攻撃
        double distance = entity.getLocation().distance(currentThreat.getLocation());
        if (distance < 4.0 && currentThreat instanceof LivingEntity) {
            // 攻撃処理（Bukkit APIの制限により簡易実装）
            if (currentThreat instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) currentThreat;
                target.damage(1.0, entity);
            }
        } else {
            // 攻撃範囲に近づく
            moveToLocation(currentThreat.getLocation());
        }
    }
    
    /**
     * 体力回復処理
     */
    private void heal() {
        // 回復アイテムを探して使用
        Material[] healingItems = {
            Material.GOLDEN_APPLE,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.MUSHROOM_STEW,
            Material.SUSPICIOUS_STEW
        };
        
        for (Material item : healingItems) {
            if (useItem(item)) {
                return;
            }
        }
        
        // 回復アイテムがない場合は安全な場所で待機
        if (!isInSafeLocation()) {
            findSafeLocation();
        }
    }
    
    /**
     * 食事が必要かチェック
     */
    private boolean needsFood() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFoodCheck < 5000) { // 5秒に1回チェック
            return false;
        }
        
        lastFoodCheck = currentTime;
        
        LivingEntity entity = agent.getEntity();
        if (entity instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) entity;
            return player.getFoodLevel() < foodThreshold;
        }
        
        return false;
    }
    
    /**
     * 食事処理
     */
    private void eat() {
        Material[] foodItems = {
            Material.COOKED_BEEF,
            Material.COOKED_PORKCHOP,
            Material.BREAD,
            Material.APPLE,
            Material.CARROT,
            Material.POTATO,
            Material.COOKED_CHICKEN,
            Material.COOKED_COD
        };
        
        for (Material food : foodItems) {
            if (useItem(food)) {
                logger.debug("エージェント " + agent.getAgentName() + " が食事をしました");
                agent.getStatusDisplay().setTarget(food.name());
                return;
            }
        }
        
        logger.warn("エージェント " + agent.getAgentName() + " の食料が不足しています");
    }
    
    /**
     * 有害なポーション効果があるかチェック
     */
    private boolean hasHarmfulPotionEffects() {
        LivingEntity entity = agent.getEntity();
        
        PotionEffectType[] harmfulEffects = {
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.SLOW,
            PotionEffectType.SLOW_DIGGING,
            PotionEffectType.CONFUSION,
            PotionEffectType.BLINDNESS
        };
        
        for (PotionEffectType effectType : harmfulEffects) {
            if (entity.hasPotionEffect(effectType)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 有害効果の治療
     */
    private void cureHarmfulEffects() {
        // ミルクバケツで効果を除去
        if (useItem(Material.MILK_BUCKET)) {
            logger.debug("エージェント " + agent.getAgentName() + " がポーション効果を除去しました");
            return;
        }
        
        // 特定の効果に対する対処
        LivingEntity entity = agent.getEntity();
        if (entity.hasPotionEffect(PotionEffectType.POISON) || 
            entity.hasPotionEffect(PotionEffectType.WITHER)) {
            // 毒・ウィザー効果の場合は回復アイテムを使用
            heal();
        }
    }
    
    // ユーティリティメソッド
    private boolean useItem(Material material) {
        // 簡易実装：実際にはインベントリ管理が必要
        return false;
    }
    
    private void equipBestWeapon() {
        // 簡易実装：実際には武器選択ロジックが必要
    }
    
    private void moveToLocation(Location target) {
        // 簡易実装：実際にはパスファインディングが必要
    }
    
    private boolean isInSafeLocation() {
        // 簡易実装：周囲に敵がいないかチェック
        return !hasNearbyThreats();
    }
    
    private void findSafeLocation() {
        // 簡易実装：安全な場所を探して移動
        Location homeLoc = agent.getHomeLocation();
        moveToLocation(homeLoc);
    }
    
    private int findSafeY(Location location) {
        // 簡易実装：安全な高さを探す
        return location.getBlockY();
    }
}