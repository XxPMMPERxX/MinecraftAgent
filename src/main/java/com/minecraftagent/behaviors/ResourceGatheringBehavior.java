package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.BlockUtils;
import com.minecraftagent.utils.MovementUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 資源収集行動 - 戦略的なアイテム収集
 */
public class ResourceGatheringBehavior extends BaseBehavior {
    
    private Block targetBlock;
    private long lastGatherAction;
    private long miningStartTime;
    private boolean isMining;
    
    // 継続的な移動のための状態管理
    private boolean isMovingToTarget;
    private Location movementDestination;
    private long movementStartTime;
    private int movementStepCount;
    private static final int MOVEMENT_STEP_INTERVAL_MS = 200; // 200ms毎に移動ステップ実行
    
    // 収集優先度の高い資源
    private final Material[] priorityResources = {
        Material.DIAMOND_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.COAL_ORE,
        Material.OAK_LOG,
        Material.STONE,
        Material.COBBLESTONE,
        Material.DIRT
    };
    
    public ResourceGatheringBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
        this.lastGatherAction = 0;
        this.isMining = false;
        this.isMovingToTarget = false;
        this.movementStepCount = 0;
    }
    
    @Override
    public boolean canExecute() {
        if (!isAgentValid()) {
            logger.debug("ResourceGatheringBehavior: エージェントが無効");
            return false;
        }
        
        boolean inventoryFull = isInventoryFull();
        boolean hasResources = hasNeededResources();
        
        logger.debug("ResourceGatheringBehavior: インベントリ満杯=" + inventoryFull + ", 必要資源あり=" + hasResources);
        
        // インベントリが満杯でない場合、または必要な資源がある場合
        boolean canExecute = !inventoryFull || hasResources;
        logger.debug("ResourceGatheringBehavior: 実行可能=" + canExecute);
        
        return canExecute;
    }
    
    @Override
    protected void onStart() {
        logger.debug("資源収集行動を開始しました");
        targetBlock = null;
        isMining = false;
        isMovingToTarget = false;
        movementStepCount = 0;
    }
    
    @Override
    protected void onUpdate() {
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        // ステータス表示を更新
        agent.getStatusDisplay().setBehavior("ResourceGatheringBehavior");
        
        long currentTime = System.currentTimeMillis();
        
        // 継続的な移動処理を最初にチェック
        if (isMovingToTarget && movementDestination != null) {
            // 移動中もターゲットとの差分を表示
            if (targetBlock != null) {
                updateCoordinateDifference(entity.getLocation(), targetBlock.getLocation());
                
                // 移動中でも5ブロック以内に到達したら掘削開始の判定を行う
                double distance = entity.getLocation().distance(targetBlock.getLocation());
                if (distance <= 5.0) {
                    // 継続移動を停止して掘削処理に移行
                    isMovingToTarget = false;
                    movementDestination = null;
                    logger.info("★継続移動を停止して掘削処理に移行: 距離=" + String.format("%.1f", distance));
                    // 掘削処理は次のupdateサイクルで実行される
                    return;
                }
            }
            processContinuousMovement(entity, currentTime);
            return; // 移動中は他の処理をスキップ
        }
        
        // 採掘中の場合
        if (isMining && targetBlock != null) {
            agent.getStatusDisplay().setAction("採掘中");
            // 採掘中も座標差分を表示
            updateCoordinateDifference(entity.getLocation(), targetBlock.getLocation());
            processMining(currentTime);
            return;
        }
        
        // 1秒に1回だけアクション
        if (currentTime - lastGatherAction < 1000) {
            return;
        }
        lastGatherAction = currentTime;
        
        // ターゲットブロックが無効になった場合は新しいものを探す
        if (targetBlock == null || !isValidTarget(targetBlock)) {
            agent.getStatusDisplay().setAction("資源探索中");
            findNewTarget();
        }
        
        // プレイヤーのような自然な採掘行動
        if (targetBlock != null) {
            Location agentLocation = entity.getLocation();
            Location targetLocation = targetBlock.getLocation();
            
            // 座標差分を計算
            updateCoordinateDifference(agentLocation, targetLocation);
            
            // 採掘可能な距離をチェック（直線距離で5ブロック以内）
            double distance = agentLocation.distance(targetLocation);
            
            if (distance > 5.0) {
                // 遠すぎる場合は継続的移動を開始
                agent.getStatusDisplay().setAction("ターゲットに接近中");
                startContinuousMovement(targetLocation);
            } else if (canMineFromPosition(agentLocation, targetLocation)) {
                // 採掘可能な位置にいる場合は採掘開始
                startMining();
            } else {
                // 採掘不可能な位置にいる場合はプレイヤーのように道を作る
                agent.getStatusDisplay().setAction("地下鉱石への掘削中");
                logger.info("★掘削ルート構築開始: " + targetBlock.getType() + " at " + targetLocation);
                logger.info("★エージェント位置: " + agentLocation);
                logger.info("★距離: " + String.format("%.2f", distance));
                createPathToTarget(entity, agentLocation, targetLocation);
            }
        }
    }
    
    @Override
    protected void onStop() {
        logger.debug("資源収集行動を停止しました");
        isMining = false;
        targetBlock = null;
        isMovingToTarget = false;
        movementDestination = null;
        movementStepCount = 0;
    }
    
    /**
     * インベントリが満杯かチェック
     */
    private boolean isInventoryFull() {
        Map<Material, Integer> inventory = BlockUtils.getInventoryContents(agent);
        int totalItems = inventory.values().stream().mapToInt(Integer::intValue).sum();
        return totalItems > 200; // 仮の制限値
    }
    
    /**
     * 必要な資源があるかチェック
     */
    private boolean hasNeededResources() {
        Location location = agent.getEntity().getLocation();
        List<Block> nearbyBlocks = BlockUtils.findNearbyBlocks(location, 20, priorityResources); // 10→20ブロックに拡大
        logger.debug("ResourceGatheringBehavior: 20ブロック範囲で見つかった資源数=" + nearbyBlocks.size());
        return !nearbyBlocks.isEmpty();
    }
    
    /**
     * 新しいターゲットを探す
     */
    private void findNewTarget() {
        LivingEntity entity = agent.getEntity();
        Location location = entity.getLocation();
        
        // 優先度順に資源を探す（地下鉱石も含めて範囲を拡大）
        for (Material material : priorityResources) {
            // 地下鉱石のために検索範囲を拡大（20ブロック）
            List<Block> blocks = BlockUtils.findNearbyBlocks(location, 20, material);
            
            if (!blocks.isEmpty()) {
                // 最も近いブロックを選択
                Block closest = blocks.stream()
                    .filter(BlockUtils::canMineBlock)
                    .min((b1, b2) -> Double.compare(
                        location.distance(b1.getLocation()),
                        location.distance(b2.getLocation())
                    ))
                    .orElse(null);
                
                if (closest != null) {
                    targetBlock = closest;
                    double distance = location.distance(closest.getLocation());
                    double heightDiff = closest.getLocation().getY() - location.getY();
                    
                    logger.debug("新しいターゲットを発見: " + material + 
                               " at " + closest.getLocation() + 
                               " (距離:" + String.format("%.1f", distance) + 
                               ", 高度差:" + String.format("%.1f", heightDiff) + ")");
                    
                    // 地下鉱石の場合は特別なログ出力
                    if (heightDiff < -2) {
                        logger.info("地下鉱石を発見: " + material + " - 掘削が必要");
                    }
                    return;
                }
            }
        }
        
        targetBlock = null;
    }
    
    /**
     * ターゲットが有効かチェック
     */
    private boolean isValidTarget(Block block) {
        return block != null && 
               !block.getType().isAir() && 
               BlockUtils.canMineBlock(block);
    }
    
    /**
     * ターゲットに向かって安全に移動
     */
    private void moveTowardsTarget() {
        LivingEntity entity = agent.getEntity();
        Location current = entity.getLocation();
        Location target = targetBlock.getLocation().add(0.5, 0, 0.5); // ブロックの中央
        
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        Location newLocation = current.clone().add(direction.multiply(0.3)); // より小さな移動距離
        
        // 安全な高さに調整
        newLocation.setY(getSafeGroundLevel(newLocation));
        
        // 移動先が安全かチェック
        if (isSafeLocation(newLocation)) {
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            newLocation.setYaw(yaw);
            
            entity.teleport(newLocation);
        }
    }
    
    /**
     * 安全な地面の高さを取得
     */
    private double getSafeGroundLevel(Location location) {
        int startY = Math.max(location.getBlockY() + 10, location.getWorld().getHighestBlockYAt(location));
        
        for (int y = startY; y > 0; y--) {
            Block block = location.getWorld().getBlockAt(location.getBlockX(), y, location.getBlockZ());
            Block above = location.getWorld().getBlockAt(location.getBlockX(), y + 1, location.getBlockZ());
            Block above2 = location.getWorld().getBlockAt(location.getBlockX(), y + 2, location.getBlockZ());
            
            if (block.getType().isSolid() && 
                (above.getType().isAir() || !above.getType().isSolid()) &&
                (above2.getType().isAir() || !above2.getType().isSolid())) {
                return y + 1.0;
            }
        }
        
        return Math.max(location.getY(), 64.0);
    }
    
    /**
     * 移動先が安全かチェック
     */
    private boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        
        return (feet.getType().isAir() || !feet.getType().isSolid()) &&
               (head.getType().isAir() || !head.getType().isSolid());
    }
    
    
    /**
     * 指定位置からターゲットを採掘できるかチェック
     */
    private boolean canMineFromPosition(Location from, Location target) {
        double distance = from.distance(target);
        
        // 採掘可能距離をチェック
        if (distance > 5.0) {
            return false;
        }
        
        // 直接採掘可能（間に障害物がない）かチェック
        return hasDirectMiningPath(from, target);
    }
    
    /**
     * 直接的な採掘パスがあるかチェック
     */
    private boolean hasDirectMiningPath(Location from, Location target) {
        World world = from.getWorld();
        if (world == null) return false;
        
        // レイキャストで間のブロックをチェック
        org.bukkit.util.Vector direction = target.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(target);
        
        // 0.5ブロック間隔でチェック
        for (double d = 0.5; d < distance; d += 0.5) {
            Location checkPoint = from.clone().add(direction.clone().multiply(d));
            Block blockInPath = world.getBlockAt(checkPoint);
            
            // 固体ブロックがある場合は直接採掘できない
            if (blockInPath.getType().isSolid() && 
                blockInPath.getType() != Material.BEDROCK &&
                !blockInPath.equals(targetBlock)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 継続的な移動を開始
     */
    private void startContinuousMovement(Location destination) {
        isMovingToTarget = true;
        movementDestination = destination.clone();
        movementStartTime = System.currentTimeMillis();
        movementStepCount = 0;
        logger.debug("継続的移動を開始: " + destination);
    }
    
    /**
     * 継続的な移動処理（複数tickにわたって実行）
     */
    private void processContinuousMovement(LivingEntity entity, long currentTime) {
        if (movementDestination == null) {
            isMovingToTarget = false;
            return;
        }
        
        // 移動ステップのタイミングをチェック
        if (currentTime - movementStartTime < movementStepCount * MOVEMENT_STEP_INTERVAL_MS) {
            return; // まだ次のステップの時間ではない
        }
        
        Location current = entity.getLocation();
        double distance = current.distance(movementDestination);
        
        // 目的地に到達した場合
        if (distance < 2.0) {
            isMovingToTarget = false;
            movementDestination = null;
            logger.debug("目的地に到達しました");
            return;
        }
        
        // 1ステップ移動実行
        performMovementStep(entity, current, movementDestination);
        movementStepCount++;
        
        // 最大移動時間を超えた場合は中止
        if (currentTime - movementStartTime > 30000) { // 30秒でタイムアウト
            isMovingToTarget = false;
            movementDestination = null;
            logger.debug("移動タイムアウト");
        }
    }
    
    /**
     * 1回の移動ステップを実行
     */
    private void performMovementStep(LivingEntity entity, Location current, Location target) {
        // 方向を計算（小さなステップで自然に移動）
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        Location nextStep = current.clone().add(direction.multiply(0.8)); // 0.8ブロックずつ移動
        
        // 安全な高さに調整
        nextStep.setY(getSafeGroundLevel(nextStep));
        
        // 移動先が安全かチェック
        if (isSafeLocation(nextStep)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            nextStep.setYaw(yaw);
            nextStep.setPitch(0);
            
            entity.teleport(nextStep);
        } else {
            // 障害物がある場合は採掘して道を作る
            if (mineObstaclesInPath(entity, current, nextStep)) {
                // 採掘成功後に移動を試す
                if (isSafeLocation(nextStep)) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
                    nextStep.setYaw(yaw);
                    nextStep.setPitch(0);
                    entity.teleport(nextStep);
                } else {
                    // まだ障害物がある場合は代替経路を試す
                    tryAlternativeMovementStep(entity, current, target);
                }
            } else {
                // 採掘できない場合は代替経路を試す
                tryAlternativeMovementStep(entity, current, target);
            }
        }
    }
    
    /**
     * 移動経路上の障害物を採掘
     */
    private boolean mineObstaclesInPath(LivingEntity entity, Location from, Location to) {
        boolean minedSomething = false;
        
        // 足元と頭の高さのブロックをチェック
        Block footBlock = to.getBlock();
        Block headBlock = to.getWorld().getBlockAt(to.getBlockX(), to.getBlockY() + 1, to.getBlockZ());
        
        // 足元のブロックが障害物の場合は採掘
        if (footBlock.getType().isSolid() && BlockUtils.canMineBlock(footBlock)) {
            logger.info("★移動経路の障害物を採掘: " + footBlock.getType() + " at " + footBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, footBlock);
            if (success) {
                minedSomething = true;
                logger.info("★移動経路採掘成功: " + footBlock.getType());
            }
        }
        
        // 頭の高さのブロックが障害物の場合は採掘
        if (headBlock.getType().isSolid() && BlockUtils.canMineBlock(headBlock)) {
            logger.info("★移動経路の頭上障害物を採掘: " + headBlock.getType() + " at " + headBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, headBlock);
            if (success) {
                minedSomething = true;
                logger.info("★移動経路頭上採掘成功: " + headBlock.getType());
            }
        }
        
        return minedSomething;
    }
    
    /**
     * 代替移動ステップを試す
     */
    private void tryAlternativeMovementStep(LivingEntity entity, Location current, Location target) {
        double[] angles = {Math.PI/4, -Math.PI/4, Math.PI/2, -Math.PI/2}; // 45度、右、左
        
        org.bukkit.util.Vector baseDirection = target.toVector().subtract(current.toVector()).normalize();
        double baseAngle = Math.atan2(baseDirection.getZ(), baseDirection.getX());
        
        for (double angleOffset : angles) {
            double newAngle = baseAngle + angleOffset;
            org.bukkit.util.Vector alternativeDirection = new org.bukkit.util.Vector(
                Math.cos(newAngle), 0, Math.sin(newAngle)
            );
            
            Location nextStep = current.clone().add(alternativeDirection.multiply(0.8));
            nextStep.setY(getSafeGroundLevel(nextStep));
            
            if (isSafeLocation(nextStep)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-alternativeDirection.getX(), alternativeDirection.getZ()));
                nextStep.setYaw(yaw);
                nextStep.setPitch(0);
                
                entity.teleport(nextStep);
                return;
            } else {
                // 代替経路でも障害物がある場合は採掘を試す
                if (mineObstaclesInPath(entity, current, nextStep)) {
                    if (isSafeLocation(nextStep)) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-alternativeDirection.getX(), alternativeDirection.getZ()));
                        nextStep.setYaw(yaw);
                        nextStep.setPitch(0);
                        entity.teleport(nextStep);
                        return;
                    }
                }
            }
        }
        
        // どの方向も無理な場合は上に移動を試す（上の障害物も採掘）
        Location upStep = current.clone().add(0, 1, 0);
        if (isSafeLocation(upStep)) {
            entity.teleport(upStep);
        } else {
            // 上方向の障害物も採掘を試す
            if (mineObstaclesInPath(entity, current, upStep)) {
                if (isSafeLocation(upStep)) {
                    entity.teleport(upStep);
                }
            }
        }
    }
    
    /**
     * ターゲットに向かって自然に移動（プレイヤーのような小さなステップで）
     */
    private void moveTowardsTarget(LivingEntity entity, Location target) {
        if (entity == null || target == null) return;
        
        Location current = entity.getLocation();
        double distance = current.distance(target);
        
        if (distance < 2.0) {
            return; // 十分近い
        }
        
        // 方向を計算（小さなステップで自然に移動）
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        Location nextStep = current.clone().add(direction.multiply(1.0)); // 1ブロックずつ移動
        
        // 安全な高さに調整
        nextStep.setY(getSafeGroundLevel(nextStep));
        
        // 移動先が安全かチェック
        if (isSafeLocation(nextStep)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            nextStep.setYaw(yaw);
            nextStep.setPitch(0);
            
            entity.teleport(nextStep);
        } else {
            // 障害物がある場合は別の経路を試す
            tryAlternativePathToTarget(entity, current, target);
        }
    }
    
    /**
     * 代替経路を試す（障害物回避）
     */
    private void tryAlternativePathToTarget(LivingEntity entity, Location current, Location target) {
        // 90度回転した方向を試す
        double[] angles = {Math.PI/4, -Math.PI/4, Math.PI/2, -Math.PI/2}; // 45度、右、左
        
        org.bukkit.util.Vector baseDirection = target.toVector().subtract(current.toVector()).normalize();
        double baseAngle = Math.atan2(baseDirection.getZ(), baseDirection.getX());
        
        for (double angleOffset : angles) {
            double newAngle = baseAngle + angleOffset;
            org.bukkit.util.Vector alternativeDirection = new org.bukkit.util.Vector(
                Math.cos(newAngle), 0, Math.sin(newAngle)
            );
            
            Location nextStep = current.clone().add(alternativeDirection.multiply(1.0));
            nextStep.setY(getSafeGroundLevel(nextStep));
            
            if (isSafeLocation(nextStep)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-alternativeDirection.getX(), alternativeDirection.getZ()));
                nextStep.setYaw(yaw);
                nextStep.setPitch(0);
                
                entity.teleport(nextStep);
                return;
            }
        }
        
        // すべての方向が無理な場合は上に移動を試す
        Location upStep = current.clone().add(0, 1, 0);
        if (isSafeLocation(upStep)) {
            entity.teleport(upStep);
        }
    }
    
    /**
     * ターゲットまでのパスを作成（プレイヤーのように）
     */
    private void createPathToTarget(LivingEntity entity, Location from, Location target) {
        if (entity == null || from == null || target == null) return;
        
        // ターゲットまでの直線的な掘削を試行
        digDirectToTarget(entity, from, target);
    }
    
    /**
     * ターゲットに向かって直線的に掘削
     */
    private void digDirectToTarget(LivingEntity entity, Location from, Location target) {
        // ターゲットへの方向ベクトルを計算
        org.bukkit.util.Vector direction = target.toVector().subtract(from.toVector()).normalize();
        
        // 1ブロック先の位置を掘削目標とする
        Location digTarget = from.clone().add(direction.multiply(1.0));
        
        // 足元、頭の高さ、必要に応じて上のブロックも掘削
        digBlocksAtLocation(digTarget);
        
        // 掘削後、少し前進
        Location moveTarget = from.clone().add(direction.multiply(0.5));
        moveTarget.setY(getSafeGroundLevel(moveTarget));
        
        if (isSafeLocation(moveTarget)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            moveTarget.setYaw(yaw);
            moveTarget.setPitch(0);
            
            entity.teleport(moveTarget);
            logger.debug("地下鉱石へ向かって掘削・前進: " + direction);
        }
    }
    
    /**
     * 指定位置のブロック群を掘削（足元、頭上など）
     */
    private void digBlocksAtLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        logger.info("★掘削位置での掘削開始: " + String.format("(%.1f, %.1f, %.1f)", 
                   location.getX(), location.getY(), location.getZ()));
        
        // 足元のブロックを掘削
        Block footBlock = world.getBlockAt(location);
        if (footBlock.getType().isSolid() && footBlock.getType() != Material.BEDROCK) {
            logger.info("★足元ブロック掘削実行: " + footBlock.getType() + " at " + footBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, footBlock);
            logger.info("★足元ブロック掘削結果: " + (success ? "成功" : "失敗"));
        } else {
            logger.debug("足元ブロック掘削不要: " + footBlock.getType());
        }
        
        // 頭の高さのブロックを掘削
        Block headBlock = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        if (headBlock.getType().isSolid() && headBlock.getType() != Material.BEDROCK) {
            logger.info("★頭上ブロック掘削実行: " + headBlock.getType() + " at " + headBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, headBlock);
            logger.info("★頭上ブロック掘削結果: " + (success ? "成功" : "失敗"));
        } else {
            logger.debug("頭上ブロック掘削不要: " + headBlock.getType());
        }
        
        // 必要に応じて上のブロックも掘削
        Block upperBlock = world.getBlockAt(location.getBlockX(), location.getBlockY() + 2, location.getBlockZ());
        if (upperBlock.getType().isSolid() && upperBlock.getType() != Material.BEDROCK) {
            logger.info("★上部ブロック掘削実行: " + upperBlock.getType() + " at " + upperBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, upperBlock);
            logger.info("★上部ブロック掘削結果: " + (success ? "成功" : "失敗"));
        } else {
            logger.debug("上部ブロック掘削不要: " + upperBlock.getType());
        }
    }
    
    /**
     * 下に掘り下げる（段階的に）
     */
    private void digDownToTarget(LivingEntity entity, Location from, Location target) {
        Location current = entity.getLocation();
        Location digTarget = current.clone().subtract(0, 1, 0);
        
        Block blockToRemove = digTarget.getBlock();
        if (blockToRemove.getType().isSolid() && blockToRemove.getType() != Material.BEDROCK) {
            // ブロックを破壊
            BlockUtils.mineBlock(agent, blockToRemove);
            
            // 安全な場合のみ少しずつ下に移動
            if (isSafeToMoveDown(current)) {
                Location newPos = current.clone().subtract(0, 0.5, 0); // 0.5ブロックずつ下に
                newPos.setY(getSafeGroundLevel(newPos));
                entity.teleport(newPos);
                logger.debug("エージェント " + agent.getAgentName() + " が下に掘り進みました");
            }
        }
    }
    
    /**
     * 上に足場を作って登る（段階的に）
     */
    private void buildUpToTarget(LivingEntity entity, Location from, Location target) {
        Location current = entity.getLocation();
        Location buildTarget = current.clone().add(0, 1, 0);
        
        Block blockToPlace = buildTarget.getBlock();
        if (blockToPlace.getType().isAir()) {
            // ブロックを設置
            Material buildMaterial = getBuildingMaterial();
            if (buildMaterial != null && BlockUtils.hasBlockInInventory(agent, buildMaterial, 1)) {
                BlockUtils.placeBlock(agent, blockToPlace.getLocation(), buildMaterial);
                
                // 少しずつ上に移動
                Location newPos = current.clone().add(0, 0.5, 0); // 0.5ブロックずつ上に
                newPos.setY(getSafeGroundLevel(newPos));
                entity.teleport(newPos);
                logger.debug("エージェント " + agent.getAgentName() + " が上に足場を作りました");
            }
        }
    }
    
    /**
     * 横に掘り進む（段階的に）
     */
    private void digHorizontalToTarget(LivingEntity entity, Location from, Location target) {
        Location current = entity.getLocation();
        
        // ターゲットの方向を計算（小さなステップで）
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        Location digTarget = current.clone().add(direction.multiply(0.5)); // 0.5ブロックずつ進む
        
        Block blockToRemove = digTarget.getBlock();
        if (blockToRemove.getType().isSolid() && blockToRemove.getType() != Material.BEDROCK) {
            // ブロックを破壊
            BlockUtils.mineBlock(agent, blockToRemove);
            
            // 頭上のブロックもチェック
            Block headBlock = digTarget.clone().add(0, 1, 0).getBlock();
            if (headBlock.getType().isSolid() && headBlock.getType() != Material.BEDROCK) {
                BlockUtils.mineBlock(agent, headBlock);
            }
        }
        
        // 安全な場合のみ前に移動
        if (isSafeLocation(digTarget)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            digTarget.setYaw(yaw);
            digTarget.setPitch(0);
            
            entity.teleport(digTarget);
            logger.debug("エージェント " + agent.getAgentName() + " が横に掘り進みました");
        }
    }
    
    /**
     * 下に移動しても安全かチェック
     */
    private boolean isSafeToMoveDown(Location location) {
        Location below = location.clone().subtract(0, 2, 0); // 2ブロック下をチェック
        Block belowBlock = below.getBlock();
        
        // 溶岩や空気の上には移動しない
        return belowBlock.getType().isSolid() && 
               belowBlock.getType() != Material.LAVA && 
               belowBlock.getType() != Material.WATER;
    }
    
    /**
     * 建築用のマテリアルを取得
     */
    private Material getBuildingMaterial() {
        Material[] buildingMaterials = {
            Material.COBBLESTONE,
            Material.STONE,
            Material.DIRT,
            Material.OAK_PLANKS
        };
        
        for (Material material : buildingMaterials) {
            if (BlockUtils.hasBlockInInventory(agent, material, 1)) {
                return material;
            }
        }
        
        return null;
    }
    
    /**
     * 採掘を開始
     */
    private void startMining() {
        if (targetBlock != null && BlockUtils.canMineBlock(targetBlock)) {
            isMining = true;
            miningStartTime = System.currentTimeMillis();
            logger.debug("採掘開始: " + targetBlock.getType());
        }
    }
    
    /**
     * 採掘処理
     */
    private void processMining(long currentTime) {
        if (targetBlock == null || !isValidTarget(targetBlock)) {
            isMining = false;
            return;
        }
        
        Material material = targetBlock.getType();
        double requiredTime = BlockUtils.getMiningTime(material) * 1000; // ミリ秒に変換
        
        if (currentTime - miningStartTime >= requiredTime) {
            // 採掘完了
            completeMining();
        } else {
            // 採掘中のエフェクト（パーティクルなど）を追加可能
            showMiningProgress();
        }
    }
    
    /**
     * 採掘完了処理
     */
    private void completeMining() {
        if (targetBlock == null) return;
        
        Material material = targetBlock.getType();
        
        // ドロップアイテムを決定
        ItemStack drop = getBlockDrop(material);
        
        // ブロックを破壊
        targetBlock.setType(Material.AIR);
        
        // インベントリに追加
        BlockUtils.addItemToInventory(agent, drop);
        
        logger.info("採掘完了: " + material + " -> " + drop.getType() + " x" + drop.getAmount());
        
        // 次のターゲットを探すためにリセット
        isMining = false;
        targetBlock = null;
        
        // インベントリ統計をログ出力
        logger.debug(BlockUtils.getInventoryStatistics(agent));
    }
    
    /**
     * ブロックのドロップアイテムを取得
     */
    private ItemStack getBlockDrop(Material blockType) {
        switch (blockType) {
            case STONE:
                return new ItemStack(Material.COBBLESTONE, 1);
            case DIAMOND_ORE:
                return new ItemStack(Material.DIAMOND, 1);
            case IRON_ORE:
                return new ItemStack(Material.RAW_IRON, 1);
            case GOLD_ORE:
                return new ItemStack(Material.RAW_GOLD, 1);
            case COAL_ORE:
                return new ItemStack(Material.COAL, 1);
            case OAK_LOG:
                return new ItemStack(Material.OAK_LOG, 1);
            default:
                return new ItemStack(blockType, 1);
        }
    }
    
    /**
     * 採掘進行状況を表示
     */
    private void showMiningProgress() {
        // パーティクルエフェクトやサウンドを追加可能
        // 現在は何もしない
    }
    
    /**
     * エージェントとターゲットの座標差分をスコアボードとログに更新
     */
    private void updateCoordinateDifference(Location agentLocation, Location targetLocation) {
        // 座標差分を計算
        double deltaX = targetLocation.getX() - agentLocation.getX();
        double deltaY = targetLocation.getY() - agentLocation.getY();
        double deltaZ = targetLocation.getZ() - agentLocation.getZ();
        double distance = agentLocation.distance(targetLocation);
        
        // フォーマットした文字列を作成
        String coordDiffInfo = String.format("ΔX:%.1f ΔY:%.1f ΔZ:%.1f 距離:%.1f", 
                                            deltaX, deltaY, deltaZ, distance);
        
        // スコアボードに表示
        agent.getStatusDisplay().setTarget(String.format("%s (%s)", 
                                                        targetBlock.getType().name(), 
                                                        coordDiffInfo));
        
        // デバッグログに出力
        logger.debug("採掘ターゲット座標差分 - " + 
                    String.format("エージェント位置:(%.1f, %.1f, %.1f), " +
                                "ターゲット位置:(%.1f, %.1f, %.1f), " +
                                "差分:(ΔX=%.1f, ΔY=%.1f, ΔZ=%.1f), 距離:%.1f",
                                agentLocation.getX(), agentLocation.getY(), agentLocation.getZ(),
                                targetLocation.getX(), targetLocation.getY(), targetLocation.getZ(),
                                deltaX, deltaY, deltaZ, distance));
    }
    
    /**
     * 収集統計を取得
     */
    public String getGatheringStatistics() {
        Map<Material, Integer> inventory = BlockUtils.getInventoryContents(agent);
        int totalItems = inventory.values().stream().mapToInt(Integer::intValue).sum();
        
        return String.format("収集統計: 総アイテム数=%d, 種類=%d, 採掘中=%s", 
                           totalItems, inventory.size(), isMining ? "はい" : "いいえ");
    }
}