package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.MovementUtils;

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
    
    // 継続的な逃走のための状態管理
    private boolean isFleeing;
    private long fleeStartTime;
    private int fleeStepCount;
    private static final int FLEE_STEP_INTERVAL_MS = 150; // 150ms毎に逃走ステップ実行
    
    public SurvivalBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
        
        var config = agent.getPlugin().getConfigManager();
        this.healthThreshold = config.getHealthThreshold();
        this.foodThreshold = config.getFoodThreshold();
        this.autoDefendEnabled = config.isAutoDefendEnabled();
        this.fleeHealthThreshold = config.getFleeHealthThreshold();
        
        this.lastFoodCheck = 0;
        this.lastThreatCheck = 0;
        this.isFleeing = false;
        this.fleeStepCount = 0;
    }
    
    @Override
    public boolean canExecute() {
        if (!isAgentValid()) {
            logger.debug("SurvivalBehavior: エージェントが無効");
            return false;
        }
        
        LivingEntity entity = agent.getEntity();
        
        // 体力が低い
        if (entity.getHealth() < healthThreshold) {
            logger.debug("SurvivalBehavior: 体力が低い " + entity.getHealth() + " < " + healthThreshold);
            return true;
        }
        
        // 満腹度が低い（プレイヤーエンティティの場合）
        if (entity instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) entity;
            if (player.getFoodLevel() < foodThreshold) {
                logger.debug("SurvivalBehavior: 満腹度が低い " + player.getFoodLevel() + " < " + foodThreshold);
                return true;
            }
        }
        
        // 近くに敵がいる
        if (hasNearbyThreats()) {
            logger.debug("SurvivalBehavior: 近くに脅威あり");
            return true;
        }
        
        // 有害なポーション効果がある
        if (hasHarmfulPotionEffects()) {
            logger.debug("SurvivalBehavior: 有害効果あり");
            return true;
        }
        
        logger.debug("SurvivalBehavior: 実行条件なし");
        return false;
    }
    
    @Override
    protected void onUpdate() {
        LivingEntity entity = agent.getEntity();
        
        // ステータス表示を更新
        agent.getStatusDisplay().setBehavior("SurvivalBehavior");
        
        long currentTime = System.currentTimeMillis();
        
        // 継続的な逃走処理を最初にチェック
        if (isFleeing) {
            processContinuousFlee(entity, currentTime);
            return; // 逃走中は他の処理をスキップ
        }
        
        // 1. 緊急回避（体力が非常に低い場合）
        if (entity.getHealth() <= fleeHealthThreshold) {
            agent.getStatusDisplay().setAction("緊急回避中");
            startContinuousFlee();
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
            startContinuousFlee();
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
     * 継続的な逃走を開始
     */
    private void startContinuousFlee() {
        isFleeing = true;
        fleeStartTime = System.currentTimeMillis();
        fleeStepCount = 0;
        logger.debug("継続的逃走を開始");
    }
    
    /**
     * 継続的な逃走処理（複数tickにわたって実行）
     */
    private void processContinuousFlee(LivingEntity entity, long currentTime) {
        // 逃走ステップのタイミングをチェック
        if (currentTime - fleeStartTime < fleeStepCount * FLEE_STEP_INTERVAL_MS) {
            return; // まだ次のステップの時間ではない
        }
        
        // 脅威がなくなった、または安全な距離に達した場合は逃走終了
        if (currentThreat == null || currentThreat.isDead() || 
            entity.getLocation().distance(currentThreat.getLocation()) > 15.0) {
            isFleeing = false;
            logger.debug("逃走終了");
            return;
        }
        
        // 1ステップ逃走実行
        performFleeStep(entity);
        fleeStepCount++;
        
        // 最大逃走時間を超えた場合は中止
        if (currentTime - fleeStartTime > 15000) { // 15秒でタイムアウト
            isFleeing = false;
            logger.debug("逃走タイムアウト");
        }
        
        // ログを減らしてスパムを防止
        if (currentTime - lastThreatCheck > 5000) { // 5秒に1回のみログ出力
            logger.info("エージェント " + agent.getAgentName() + " が逃走しています");
            lastThreatCheck = currentTime;
        }
        agent.getStatusDisplay().setCustomStatus("🏃 逃走中");
    }
    
    /**
     * 1回の逃走ステップを実行
     */
    private void performFleeStep(LivingEntity entity) {
        Location agentLoc = entity.getLocation();
        
        // 逃走方向を計算
        org.bukkit.util.Vector fleeDirection = calculateFleeDirection(agentLoc);
        
        // 小さなステップで自然に移動（一度に1ブロックずつ）
        Location nextStep = agentLoc.clone().add(fleeDirection.multiply(1.0));
        
        // 安全な高さに調整
        nextStep.setY(findSafeY(nextStep));
        
        // 障害物をチェックして安全な場合のみ移動
        if (isSafeToMoveTo(nextStep)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-fleeDirection.getX(), fleeDirection.getZ()));
            nextStep.setYaw(yaw);
            nextStep.setPitch(0);
            
            entity.teleport(nextStep);
        } else {
            // 障害物がある場合は別の方向を試す
            tryAlternativeFleeStep(entity, agentLoc);
        }
    }
    
    /**
     * 代替逃走ステップを試す
     */
    private void tryAlternativeFleeStep(LivingEntity entity, Location agentLoc) {
        // 90度回転した方向を試す
        double[] angles = {Math.PI/4, -Math.PI/4, Math.PI/2, -Math.PI/2}; // 45度、右、左
        
        for (double angleOffset : angles) {
            org.bukkit.util.Vector baseDirection = calculateFleeDirection(agentLoc);
            double baseAngle = Math.atan2(baseDirection.getZ(), baseDirection.getX());
            double newAngle = baseAngle + angleOffset;
            
            org.bukkit.util.Vector alternativeDirection = new org.bukkit.util.Vector(
                Math.cos(newAngle), 0, Math.sin(newAngle)
            );
            
            Location nextStep = agentLoc.clone().add(alternativeDirection.multiply(1.0));
            nextStep.setY(findSafeY(nextStep));
            
            if (isSafeToMoveTo(nextStep)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-alternativeDirection.getX(), alternativeDirection.getZ()));
                nextStep.setYaw(yaw);
                nextStep.setPitch(0);
                
                entity.teleport(nextStep);
                return;
            }
        }
        
        // どの方向も駄目な場合は上に移動を試す
        Location upStep = agentLoc.clone().add(0, 1, 0);
        if (isSafeToMoveTo(upStep)) {
            entity.teleport(upStep);
        }
    }
    
    /**
     * 逃走処理（自然な移動）
     */
    private void flee() {
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        Location agentLoc = entity.getLocation();
        
        // 逃走方向を計算
        org.bukkit.util.Vector fleeDirection = calculateFleeDirection(agentLoc);
        
        // 小さなステップで自然に移動（一度に2ブロックずつ）
        Location nextStep = agentLoc.clone().add(fleeDirection.multiply(2.0));
        
        // 安全な高さに調整
        nextStep.setY(findSafeY(nextStep));
        
        // 障害物をチェックして安全な場合のみ移動
        if (isSafeToMoveTo(nextStep)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-fleeDirection.getX(), fleeDirection.getZ()));
            nextStep.setYaw(yaw);
            nextStep.setPitch(0);
            
            entity.teleport(nextStep);
        } else {
            // 障害物がある場合は另の方向を試す
            tryAlternativeFleeDirection(entity, agentLoc);
        }
        
        // ログを減らしてスパムを防止
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastThreatCheck > 5000) { // 5秒に1回のみログ出力
            logger.info("エージェント " + agent.getAgentName() + " が逃走しています");
            lastThreatCheck = currentTime;
        }
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
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        // 直接体力を回復（テスト用）
        double currentHealth = entity.getHealth();
        double maxHealth = entity.getMaxHealth();
        
        if (currentHealth < maxHealth) {
            double newHealth = Math.min(maxHealth, currentHealth + 2.0); // 2ハート回復
            entity.setHealth(newHealth);
            logger.debug("エージェント " + agent.getAgentName() + " の体力を回復しました: " + newHealth + "/" + maxHealth);
        }
        
        // 安全な場所で待機
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
    
    /**
     * 逃走方向を計算
     */
    private org.bukkit.util.Vector calculateFleeDirection(Location agentLoc) {
        if (currentThreat != null && !currentThreat.isDead()) {
            // 脅威から離れる方向に逃走
            Location threatLoc = currentThreat.getLocation();
            org.bukkit.util.Vector fleeDirection = agentLoc.toVector().subtract(threatLoc.toVector()).normalize();
            
            // 方向が無効な場合はランダムな方向
            if (fleeDirection.length() < 0.1) {
                double angle = Math.random() * 2 * Math.PI;
                fleeDirection = new org.bukkit.util.Vector(Math.cos(angle), 0, Math.sin(angle));
            }
            
            return fleeDirection;
        } else {
            // 脅威がない場合はホームに向かう
            Location homeLoc = agent.getHomeLocation();
            return homeLoc.toVector().subtract(agentLoc.toVector()).normalize();
        }
    }
    
    /**
     * 代替の逃走方向を試す
     */
    private void tryAlternativeFleeDirection(LivingEntity entity, Location agentLoc) {
        // 90度回転した方向を試す
        double[] angles = {Math.PI/2, -Math.PI/2, Math.PI}; // 右、左、後ろ
        
        for (double angleOffset : angles) {
            org.bukkit.util.Vector baseDirection = calculateFleeDirection(agentLoc);
            double baseAngle = Math.atan2(baseDirection.getZ(), baseDirection.getX());
            double newAngle = baseAngle + angleOffset;
            
            org.bukkit.util.Vector alternativeDirection = new org.bukkit.util.Vector(
                Math.cos(newAngle), 0, Math.sin(newAngle)
            );
            
            Location nextStep = agentLoc.clone().add(alternativeDirection.multiply(1.5));
            nextStep.setY(findSafeY(nextStep));
            
            if (isSafeToMoveTo(nextStep)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-alternativeDirection.getX(), alternativeDirection.getZ()));
                nextStep.setYaw(yaw);
                nextStep.setPitch(0);
                
                entity.teleport(nextStep);
                return;
            }
        }
        
        // どの方向も駄目な場合は上に移動を試す
        Location upStep = agentLoc.clone().add(0, 1, 0);
        if (isSafeToMoveTo(upStep)) {
            entity.teleport(upStep);
        }
    }
    
    /**
     * 移動先が安全かチェック
     */
    private boolean isSafeToMoveTo(Location location) {
        if (location == null || location.getWorld() == null) return false;
        
        org.bukkit.World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        // 世界の範囲内かチェック
        if (y < world.getMinHeight() || y > world.getMaxHeight() - 2) {
            return false;
        }
        
        // 足元と頭上のブロックをチェック
        org.bukkit.block.Block feet = world.getBlockAt(x, y, z);
        org.bukkit.block.Block head = world.getBlockAt(x, y + 1, z);
        org.bukkit.block.Block ground = world.getBlockAt(x, y - 1, z);
        
        // 足元と頭上が空気で、地面が固体
        boolean feetClear = feet.getType().isAir() || !feet.getType().isSolid();
        boolean headClear = head.getType().isAir() || !head.getType().isSolid();
        boolean groundSolid = ground.getType().isSolid();
        
        // 危険なブロックを回避
        boolean notDangerous = feet.getType() != Material.LAVA && 
                              feet.getType() != Material.FIRE &&
                              head.getType() != Material.LAVA &&
                              head.getType() != Material.FIRE &&
                              ground.getType() != Material.LAVA;
        
        return feetClear && headClear && groundSolid && notDangerous;
    }
    
    private void moveToLocation(Location target) {
        LivingEntity entity = agent.getEntity();
        if (entity == null || target == null) return;
        
        Location current = entity.getLocation();
        double distance = current.distance(target);
        
        // 近い場合は到達済みとみなす
        if (distance < 1.0) {
            return;
        }
        
        // 自然な移動（小さなステップで）
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        Location nextStep = current.clone().add(direction.multiply(1.5)); // 1.5ブロックずつ移動
        
        // 安全な高さに調整
        nextStep.setY(findSafeY(nextStep));
        
        // 向きを設定
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        nextStep.setYaw(yaw);
        nextStep.setPitch(0);
        
        // 安全な場合のみ移動
        if (isSafeToMoveTo(nextStep)) {
            entity.teleport(nextStep);
        }
        
        logger.debug("エージェント " + agent.getAgentName() + " を移動させました: " + nextStep);
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
        // 安全な高さを探す
        org.bukkit.World world = location.getWorld();
        if (world == null) return location.getBlockY();
        
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = Math.max(location.getBlockY(), world.getMinHeight());
        int maxY = Math.min(world.getMaxHeight() - 1, startY + 20);
        
        // 上から下に向かって安全な場所を探す
        for (int y = maxY; y >= startY; y--) {
            org.bukkit.block.Block ground = world.getBlockAt(x, y, z);
            org.bukkit.block.Block feet = world.getBlockAt(x, y + 1, z);
            org.bukkit.block.Block head = world.getBlockAt(x, y + 2, z);
            
            if (ground.getType().isSolid() && 
                (feet.getType().isAir() || !feet.getType().isSolid()) &&
                (head.getType().isAir() || !head.getType().isSolid())) {
                return y + 1;
            }
        }
        
        return Math.max(location.getBlockY(), world.getHighestBlockYAt(x, z) + 1);
    }
}