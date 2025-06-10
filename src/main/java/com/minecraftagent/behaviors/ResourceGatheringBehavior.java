package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.BlockUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

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
    
    // 収集優先度の高い資源（地上優先）
    private final Material[] priorityResources = {
        Material.OAK_LOG,
        Material.STONE,
        Material.COBBLESTONE,
        Material.DIRT,
        Material.COAL_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.DIAMOND_ORE
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
            logger.info("ResourceGatheringBehavior: エージェントが無効");
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
        
        // 設定に基づく間隔でアクション
        int miningInterval = agent.getPlugin().getConfigManager().getConfig().getInt("agent.default_behavior.resource_gathering.mining_intervals", 1000);
        if (currentTime - lastGatherAction < miningInterval) {
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
            
            // ターゲットブロックをハイライト表示
            highlightTargetBlock(targetBlock);
            
            // エージェントからターゲットまでのロープ（線）を表示
            drawRopeToTarget(agentLocation, targetLocation);
            
            // 座標差分を計算
            updateCoordinateDifference(agentLocation, targetLocation);
            
            // 採掘可能な距離をチェック（シンプル版）
            double distance = agentLocation.distance(targetLocation);
            
            if (distance > 4.0) {
                // 遠すぎる場合は段階的に移動（テレポートを避ける）
                agent.getStatusDisplay().setAction("ターゲットに接近中");
                
                // より小さなステップで移動
                moveTowardsTargetGradually(entity, agentLocation, targetLocation);
                
            } else if (distance <= 4.0) {
                // 採掘可能距離内にいる場合は採掘開始
                startMining();
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
        int searchRadius = agent.getPlugin().getConfigManager().getConfig().getInt("agent.default_behavior.resource_gathering.search_radius", 20);
        List<Block> nearbyBlocks = BlockUtils.findNearbyBlocks(location, searchRadius, priorityResources);
        logger.debug("ResourceGatheringBehavior: " + searchRadius + "ブロック範囲で見つかった資源数=" + nearbyBlocks.size());
        return !nearbyBlocks.isEmpty();
    }
    
    /**
     * 新しいターゲットを探す（地上優先）
     */
    private void findNewTarget() {
        LivingEntity entity = agent.getEntity();
        Location location = entity.getLocation();
        
        // 地上レベル優先の検索
        int searchRadius = agent.getPlugin().getConfigManager().getConfig().getInt("agent.default_behavior.resource_gathering.search_radius", 15);
        
        for (Material material : priorityResources) {
            List<Block> blocks = BlockUtils.findNearbyBlocks(location, searchRadius, material);
            
            if (!blocks.isEmpty()) {
                // 地上に近いブロックを優先選択
                Block closest = blocks.stream()
                    .filter(BlockUtils::canMineBlock)
                    .filter(block -> {
                        double heightDiff = block.getLocation().getY() - location.getY();
                        return heightDiff >= -3.0; // 3ブロック以下の地下なら OK
                    })
                    .min((b1, b2) -> {
                        double dist1 = location.distance(b1.getLocation());
                        double dist2 = location.distance(b2.getLocation());
                        return Double.compare(dist1, dist2);
                    })
                    .orElse(null);
                
                if (closest != null) {
                    targetBlock = closest;
                    double distance = location.distance(closest.getLocation());
                    double heightDiff = closest.getLocation().getY() - location.getY();
                    
                    logger.debug("新しいターゲット発見: " + material + 
                               " (距離:" + String.format("%.1f", distance) + 
                               ", 高度差:" + String.format("%.1f", heightDiff) + ")");
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
     * ターゲットに向かって段階的に移動（テレポートを避ける）
     */
    private void moveTowardsTargetGradually(LivingEntity entity, Location current, Location target) {
        // 方向ベクトルを計算
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector());
        double distance = direction.length();
        
        if (distance < 0.5) return; // 十分近い場合は移動しない
        
        direction.normalize();
        
        // 非常に小さなステップで移動（0.3ブロック以下）
        double stepSize = Math.min(0.3, distance * 0.1); // 距離の10%、最大0.3ブロック
        Location nextLocation = current.clone().add(direction.multiply(stepSize));
        
        // Y座標の変更を最小限に抑制
        double targetY = target.getY();
        double currentY = current.getY();
        double heightDiff = targetY - currentY;
        
        // 高度差が小さい場合は現在の高さを維持
        if (Math.abs(heightDiff) < 3.0) {
            // 現在の高さ付近で安全な地面を探す
            nextLocation.setY(findSafeYNearCurrent(nextLocation, current));
        } else {
            // 大きな高度差がある場合は段階的に変更
            double yStep = Math.signum(heightDiff) * Math.min(0.5, Math.abs(heightDiff) * 0.1);
            nextLocation.setY(current.getY() + yStep);
        }
        
        // 移動先が安全かチェック
        if (isSafeLocationForMovement(nextLocation)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            nextLocation.setYaw(yaw);
            nextLocation.setPitch(0);
            
            // teleportで移動（小さなステップなので自然に見える）
            entity.teleport(nextLocation);
            
            logger.debug("段階的移動: " + String.format("(%.2f, %.2f, %.2f) → (%.2f, %.2f, %.2f), ステップ=%.2f",
                        current.getX(), current.getY(), current.getZ(),
                        nextLocation.getX(), nextLocation.getY(), nextLocation.getZ(),
                        stepSize));
        } else {
            logger.debug("段階的移動: 移動先が安全でないためスキップ");
        }
    }
    
    /**
     * 現在位置付近で安全なY座標を見つける
     */
    private double findSafeYNearCurrent(Location location, Location currentPosition) {
        World world = location.getWorld();
        if (world == null) return currentPosition.getY();
        
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int currentY = currentPosition.getBlockY();
        
        // 現在位置の±2ブロック範囲で安全な地面を探す
        for (int offset = 0; offset <= 2; offset++) {
            // まず下方向、次に上方向
            for (int direction : new int[]{-1, 1}) {
                int checkY = currentY + (direction * offset);
                if (checkY < world.getMinHeight() || checkY > world.getMaxHeight() - 2) continue;
                
                Block ground = world.getBlockAt(x, checkY, z);
                Block feet = world.getBlockAt(x, checkY + 1, z);
                Block head = world.getBlockAt(x, checkY + 2, z);
                
                // 地面が固体で、足元と頭上が空気
                if (ground.getType().isSolid() && 
                    !ground.getType().equals(Material.LAVA) &&
                    feet.getType().isAir() &&
                    head.getType().isAir()) {
                    return checkY + 1.0; // ブロックの上に立つ
                }
            }
        }
        
        // 見つからない場合は現在の高さを維持
        return currentPosition.getY();
    }
    
    /**
     * 移動用の安全性チェック（より緩和された条件）
     */
    private boolean isSafeLocationForMovement(Location location) {
        if (location == null || location.getWorld() == null) return false;
        
        World world = location.getWorld();
        Block feet = world.getBlockAt(location);
        Block head = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        
        // 足元と頭上が空気、または通過可能
        boolean feetSafe = feet.getType().isAir() || 
                          feet.getType() == Material.WATER ||
                          feet.getType() == Material.TALL_GRASS ||
                          feet.getType() == Material.GRASS;
        
        boolean headSafe = head.getType().isAir() ||
                          head.getType() == Material.WATER ||
                          head.getType() == Material.TALL_GRASS ||
                          head.getType() == Material.GRASS;
        
        // 危険なブロックは避ける
        boolean notDangerous = feet.getType() != Material.LAVA && 
                              feet.getType() != Material.FIRE &&
                              head.getType() != Material.LAVA &&
                              head.getType() != Material.FIRE;
        
        return feetSafe && headSafe && notDangerous;
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
     * 地下移動での安全性をチェック（通常より緩和された条件）
     */
    private boolean canMoveUnderground(Location location) {
        Block feet = location.getBlock();
        Block head = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        
        // 地下では溶岩と水を避ければOK（空気でなくても掘削できれば通れる）
        boolean feetSafe = feet.getType().isAir() || 
                          (!feet.getType().equals(Material.LAVA) && 
                           !feet.getType().equals(Material.WATER) && 
                           feet.getType() != Material.BEDROCK);
        
        boolean headSafe = head.getType().isAir() || 
                          (!head.getType().equals(Material.LAVA) && 
                           !head.getType().equals(Material.WATER) && 
                           head.getType() != Material.BEDROCK);
        
        return feetSafe && headSafe;
    }
    
    /**
     * 上向き移動での安全性をチェック（掘削・建築を前提とした条件）
     */
    private boolean canMoveUpward(Location location) {
        Block feet = location.getBlock();
        Block head = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        
        // 上向き移動では足場があるかチェック
        Block ground = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
        
        // 足場が固体で、足元と頭が通行可能（または掘削可能）
        boolean hasFooting = ground.getType().isSolid();
        
        boolean feetPassable = feet.getType().isAir() || 
                              BlockUtils.canMineBlock(feet) ||
                              (!feet.getType().equals(Material.LAVA) && 
                               !feet.getType().equals(Material.WATER));
        
        boolean headPassable = head.getType().isAir() || 
                              BlockUtils.canMineBlock(head) ||
                              (!head.getType().equals(Material.LAVA) && 
                               !head.getType().equals(Material.WATER));
        
        return hasFooting && feetPassable && headPassable;
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
        
        // 高度差による移動方法の選択
        double heightDiff = target.getY() - current.getY();
        
        if (heightDiff < -2.0) {
            // 地下向き移動：ターゲット方向のY座標をそのまま使用
            // nextStepのYはそのまま（direction.multiplyで計算済み）
        } else if (heightDiff > 2.0) {
            // 上向き移動：ターゲット方向のY座標をそのまま使用して登攀
            // nextStepのYはそのまま（direction.multiplyで計算済み）
        } else {
            // 水平移動：安全な地面レベルに調整
            nextStep.setY(getSafeGroundLevel(nextStep));
        }
        
        logger.debug("★移動ステップ: 現在Y=" + String.format("%.1f", current.getY()) + 
                    ", 次Y=" + String.format("%.1f", nextStep.getY()) + 
                    ", ターゲットY=" + String.format("%.1f", target.getY()) + 
                    ", 高度差=" + String.format("%.1f", heightDiff));
        
        // 移動予定パスと障害物を表示
        showMovementPath(current, nextStep);
        highlightMiningBlocks(nextStep);
        
        // まず障害物を採掘
        mineObstaclesInPath(entity, current, nextStep);
        
        // 移動先が安全かチェック（移動タイプに応じて判定）
        boolean canMove;
        if (heightDiff < -2.0) {
            // 地下移動：緩和された条件
            canMove = canMoveUnderground(nextStep);
        } else if (heightDiff > 2.0) {
            // 上向き移動：緩和された条件（掘削可能なら移動可能）
            canMove = canMoveUpward(nextStep);
        } else {
            // 水平移動：通常の条件
            canMove = isSafeLocation(nextStep);
        }
        
        if (canMove) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            nextStep.setYaw(yaw);
            nextStep.setPitch(0);
            
            moveEntityNaturally(entity, current, nextStep);
            logger.info("★移動ステップ実行: " + String.format("(%.1f, %.1f, %.1f) → (%.1f, %.1f, %.1f)", 
                       current.getX(), current.getY(), current.getZ(),
                       nextStep.getX(), nextStep.getY(), nextStep.getZ()));
        } else {
            // 移動できない場合は更に掘削
            logger.info("★移動先が安全でない - 追加掘削を実行");
            digBlocksAtLocation(nextStep);
            
            // 掘削後再度移動を試す
            boolean canMoveAfterDig;
            if (heightDiff < -2.0) {
                canMoveAfterDig = canMoveUnderground(nextStep);
            } else if (heightDiff > 2.0) {
                canMoveAfterDig = canMoveUpward(nextStep);
            } else {
                canMoveAfterDig = isSafeLocation(nextStep);
            }
            
            if (canMoveAfterDig) {
                float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
                nextStep.setYaw(yaw);
                nextStep.setPitch(0);
                moveEntityNaturally(entity, current, nextStep);
                logger.info("★追加掘削後移動成功");
            } else {
                // 代替経路を試す
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
        
        // より積極的に - 移動方向の前方1ブロックも予め掘削
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector()).normalize();
        Location aheadLocation = to.clone().add(direction.multiply(1.0));
        
        Block aheadFootBlock = aheadLocation.getBlock();
        Block aheadHeadBlock = aheadLocation.getWorld().getBlockAt(
            aheadLocation.getBlockX(), aheadLocation.getBlockY() + 1, aheadLocation.getBlockZ());
        
        if (aheadFootBlock.getType().isSolid() && BlockUtils.canMineBlock(aheadFootBlock)) {
            logger.info("★前方予掘削: " + aheadFootBlock.getType() + " at " + aheadFootBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, aheadFootBlock);
            if (success) {
                minedSomething = true;
                logger.info("★前方予掘削成功: " + aheadFootBlock.getType());
            }
        }
        
        if (aheadHeadBlock.getType().isSolid() && BlockUtils.canMineBlock(aheadHeadBlock)) {
            logger.info("★前方頭上予掘削: " + aheadHeadBlock.getType() + " at " + aheadHeadBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, aheadHeadBlock);
            if (success) {
                minedSomething = true;
                logger.info("★前方頭上予掘削成功: " + aheadHeadBlock.getType());
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
                
                moveEntityNaturally(entity, current, nextStep);
                return;
            } else {
                // 代替経路でも障害物がある場合は採掘を試す
                if (mineObstaclesInPath(entity, current, nextStep)) {
                    if (isSafeLocation(nextStep)) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-alternativeDirection.getX(), alternativeDirection.getZ()));
                        nextStep.setYaw(yaw);
                        nextStep.setPitch(0);
                        moveEntityNaturally(entity, current, nextStep);
                        return;
                    }
                }
            }
        }
        
        // どの方向も無理な場合は上に移動を試す（上の障害物も採掘）
        Location upStep = current.clone().add(0, 1, 0);
        if (isSafeLocation(upStep)) {
            moveEntityNaturally(entity, current, upStep);
        } else {
            // 上方向の障害物も採掘を試す
            if (mineObstaclesInPath(entity, current, upStep)) {
                if (isSafeLocation(upStep)) {
                    moveEntityNaturally(entity, current, upStep);
                }
            }
        }
    }
    
    
    /**
     * ターゲットまでのパスを作成（プレイヤーのように）
     */
    private void createPathToTarget(LivingEntity entity, Location from, Location target) {
        if (entity == null || from == null || target == null) return;
        
        double distance = from.distance(target);
        double heightDiff = target.getY() - from.getY();
        
        logger.info("★掘削ルート作成: 距離=" + String.format("%.2f", distance) + 
                   ", 高度差=" + String.format("%.2f", heightDiff));
        
        if (heightDiff < -2.0) {
            // 地下鉱石への場合は積極的な掘削モードに切り替え
            logger.info("★地下鉱石検出 - 積極的掘削モードを開始");
            
            // 真下の鉱石の場合は直接掘り下げる
            double horizontalDistance = Math.sqrt(Math.pow(target.getX() - from.getX(), 2) + 
                                                  Math.pow(target.getZ() - from.getZ(), 2));
            
            if (horizontalDistance < 2.0) {
                logger.info("★真下鉱石検出 - 階段状掘削を開始");
                digStaircaseDown(entity, from, target);
            } else {
                logger.info("★斜め下鉱石 - 階段状掘削を開始");
                digStaircaseToTarget(entity, from, target);
            }
        } else if (heightDiff > 2.0) {
            // 上向きターゲットの場合は登攀モードに切り替え
            logger.info("★上向きターゲット検出 - 登攀モードを開始");
            
            double horizontalDistance = Math.sqrt(Math.pow(target.getX() - from.getX(), 2) + 
                                                  Math.pow(target.getZ() - from.getZ(), 2));
            
            if (horizontalDistance < 2.0) {
                logger.info("★真上ターゲット検出 - 垂直登攀を開始");
                digVerticallyUp(entity, from, target);
            } else {
                logger.info("★斜め上ターゲット - 継続移動モードを開始");
                startContinuousMovement(target);
            }
        } else {
            // 通常の掘削（水平移動）
            digDirectToTarget(entity, from, target);
        }
    }
    
    /**
     * ターゲットに向かって直線的に掘削
     */
    private void digDirectToTarget(LivingEntity entity, Location from, Location target) {
        // ターゲットへの方向ベクトルを計算
        org.bukkit.util.Vector direction = target.toVector().subtract(from.toVector()).normalize();
        
        // より積極的に掘削するため、1-2ブロック先まで掘削
        for (double distance = 1.0; distance <= 2.0; distance += 1.0) {
            Location digTarget = from.clone().add(direction.multiply(distance));
            
            // 各距離での掘削実行
            boolean dugSomething = digBlocksAtLocation(digTarget);
            
            if (dugSomething) {
                logger.info("★連続掘削実行: 距離" + distance + "ブロック先を掘削");
            }
        }
        
        // 掘削後、より大きく前進（1.0ブロック）
        Location moveTarget = from.clone().add(direction.multiply(1.0));
        moveTarget.setY(getSafeGroundLevel(moveTarget));
        
        if (isSafeLocation(moveTarget)) {
            // 向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            moveTarget.setYaw(yaw);
            moveTarget.setPitch(0);
            
            entity.teleport(moveTarget);
            logger.info("★地下鉱石へ向かって掘削・前進: " + String.format("方向(%.2f, %.2f, %.2f)", 
                       direction.getX(), direction.getY(), direction.getZ()));
        } else {
            logger.info("★掘削後の移動先が安全でない - 追加掘削を実行");
            // 移動先が安全でない場合は更に掘削
            digBlocksAtLocation(moveTarget);
        }
    }
    
    /**
     * 指定位置のブロック群を掘削（足元、頭上など）
     */
    private boolean digBlocksAtLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        
        boolean dugSomething = false;
        
        logger.info("★掘削位置での掘削開始: " + String.format("(%.1f, %.1f, %.1f)", 
                   location.getX(), location.getY(), location.getZ()));
        
        // 足元のブロックを掘削
        Block footBlock = world.getBlockAt(location);
        if (footBlock.getType().isSolid() && footBlock.getType() != Material.BEDROCK) {
            logger.info("★足元ブロック掘削実行: " + footBlock.getType() + " at " + footBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, footBlock);
            logger.info("★足元ブロック掘削結果: " + (success ? "成功" : "失敗"));
            if (success) dugSomething = true;
        } else {
            logger.debug("足元ブロック掘削不要: " + footBlock.getType());
        }
        
        // 頭の高さのブロックを掘削
        Block headBlock = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        if (headBlock.getType().isSolid() && headBlock.getType() != Material.BEDROCK) {
            logger.info("★頭上ブロック掘削実行: " + headBlock.getType() + " at " + headBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, headBlock);
            logger.info("★頭上ブロック掘削結果: " + (success ? "成功" : "失敗"));
            if (success) dugSomething = true;
        } else {
            logger.debug("頭上ブロック掘削不要: " + headBlock.getType());
        }
        
        // 必要に応じて上のブロックも掘削
        Block upperBlock = world.getBlockAt(location.getBlockX(), location.getBlockY() + 2, location.getBlockZ());
        if (upperBlock.getType().isSolid() && upperBlock.getType() != Material.BEDROCK) {
            logger.info("★上部ブロック掘削実行: " + upperBlock.getType() + " at " + upperBlock.getLocation());
            boolean success = BlockUtils.mineBlock(agent, upperBlock);
            logger.info("★上部ブロック掘削結果: " + (success ? "成功" : "失敗"));
            if (success) dugSomething = true;
        } else {
            logger.debug("上部ブロック掘削不要: " + upperBlock.getType());
        }
        
        return dugSomething;
    }
    
    /**
     * 真下の鉱石への階段状掘削
     */
    private void digStaircaseDown(LivingEntity entity, Location from, Location target) {
        Location current = entity.getLocation();
        double targetY = target.getY();
        
        logger.info("★階段状掘削開始（真下）: 現在Y=" + String.format("%.1f", current.getY()) + 
                   ", ターゲットY=" + String.format("%.1f", targetY));
        
        // ターゲットに向かう方向を取得（少し前方に階段を作る）
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        
        // 前方1ブロック、下1ブロックの位置に階段を作る
        Location stepLocation = current.clone().add(direction.multiply(1.0)).subtract(0, 1, 0);
        
        // 階段の構築予定を表示
        showStaircasePlan(current, target);
        
        // 階段の各部分を掘削
        digStairStep(stepLocation);
        
        // 階段を下って移動
        if (canMoveToStairStep(stepLocation)) {
            // 移動パスを表示
            showMovementPath(current, stepLocation);
            
            moveEntityNaturally(entity, current, stepLocation);
            logger.info("★階段移動: Y " + String.format("%.1f", current.getY()) + 
                       " → " + String.format("%.1f", stepLocation.getY()));
        }
    }
    
    /**
     * 斜め下の鉱石への階段状掘削
     */
    private void digStaircaseToTarget(LivingEntity entity, Location from, Location target) {
        Location current = entity.getLocation();
        
        logger.info("★階段状掘削開始（斜め下）: 現在(" + String.format("%.1f, %.1f, %.1f", 
                   current.getX(), current.getY(), current.getZ()) + 
                   ") → ターゲット(" + String.format("%.1f, %.1f, %.1f", 
                   target.getX(), target.getY(), target.getZ()) + ")");
        
        // ターゲットに向かう方向を計算
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        
        // 階段の一段を作成（前方1ブロック、下1ブロック）
        Location stepLocation = current.clone().add(direction.multiply(1.0));
        
        // 高度差に応じて下降量を調整
        double heightDiff = target.getY() - current.getY();
        if (heightDiff < -1.0) {
            stepLocation.subtract(0, 1, 0); // 1ブロック下げる
        }
        
        // 階段の構築予定を表示
        showStaircasePlan(current, target);
        
        // 階段の各部分を掘削
        digStairStep(stepLocation);
        
        // 階段を移動
        if (canMoveToStairStep(stepLocation)) {
            // 移動パスを表示
            showMovementPath(current, stepLocation);
            
            moveEntityNaturally(entity, current, stepLocation);
            logger.info("★階段移動: (" + String.format("%.1f, %.1f, %.1f", 
                       current.getX(), current.getY(), current.getZ()) + 
                       ") → (" + String.format("%.1f, %.1f, %.1f", 
                       stepLocation.getX(), stepLocation.getY(), stepLocation.getZ()) + ")");
        }
    }
    
    /**
     * 階段の一段を掘削
     */
    private void digStairStep(Location stepLocation) {
        World world = stepLocation.getWorld();
        if (world == null) return;
        
        logger.info("★階段掘削: " + String.format("(%.1f, %.1f, %.1f)", 
                   stepLocation.getX(), stepLocation.getY(), stepLocation.getZ()));
        
        // 掘削対象をハイライト表示
        highlightMiningBlocks(stepLocation);
        
        // 足元のブロックを掘削
        Block stepBlock = world.getBlockAt(stepLocation);
        if (stepBlock.getType().isSolid() && BlockUtils.canMineBlock(stepBlock)) {
            logger.info("★階段足元掘削: " + stepBlock.getType());
            BlockUtils.mineBlock(agent, stepBlock);
        }
        
        // 頭の高さのブロックを掘削
        Block headBlock = world.getBlockAt(stepLocation.getBlockX(), stepLocation.getBlockY() + 1, stepLocation.getBlockZ());
        if (headBlock.getType().isSolid() && BlockUtils.canMineBlock(headBlock)) {
            logger.info("★階段頭上掘削: " + headBlock.getType());
            BlockUtils.mineBlock(agent, headBlock);
        }
        
        // 必要に応じて上部も掘削
        Block upperBlock = world.getBlockAt(stepLocation.getBlockX(), stepLocation.getBlockY() + 2, stepLocation.getBlockZ());
        if (upperBlock.getType().isSolid() && BlockUtils.canMineBlock(upperBlock)) {
            logger.info("★階段上部掘削: " + upperBlock.getType());
            BlockUtils.mineBlock(agent, upperBlock);
        }
    }
    
    /**
     * 階段の一段に移動可能かチェック
     */
    private boolean canMoveToStairStep(Location stepLocation) {
        World world = stepLocation.getWorld();
        if (world == null) return false;
        
        // 足元が空気または掘削済み
        Block stepBlock = world.getBlockAt(stepLocation);
        boolean feetClear = stepBlock.getType().isAir();
        
        // 頭の高さが空気または掘削済み
        Block headBlock = world.getBlockAt(stepLocation.getBlockX(), stepLocation.getBlockY() + 1, stepLocation.getBlockZ());
        boolean headClear = headBlock.getType().isAir();
        
        // 足場があるかチェック（1ブロック下）
        Block foundationBlock = world.getBlockAt(stepLocation.getBlockX(), stepLocation.getBlockY() - 1, stepLocation.getBlockZ());
        boolean hasFooting = foundationBlock.getType().isSolid() && foundationBlock.getType() != Material.LAVA;
        
        boolean canMove = feetClear && headClear && hasFooting;
        
        logger.debug("階段移動チェック: 足元=" + feetClear + ", 頭上=" + headClear + ", 足場=" + hasFooting + " → " + canMove);
        
        return canMove;
    }
    
    /**
     * エンティティを自然に移動（teleport以外の方法）
     */
    private void moveEntityNaturally(LivingEntity entity, Location from, Location to) {
        // 移動距離をチェック
        double distance = from.distance(to);
        
        if (distance > 3.0) {
            // 長距離の場合は段階的移動
            moveEntityInSteps(entity, from, to);
        } else {
            // 短距離の場合は物理移動を使用
            moveEntityPhysically(entity, from, to);
        }
    }
    
    /**
     * 物理演算による自然な移動
     */
    private void moveEntityPhysically(LivingEntity entity, Location from, Location to) {
        // 最終的な安全性チェック
        if (!isPathClearAndSafe(from, to)) {
            logger.warn("★移動経路が安全でない - teleportにフォールバック");
            entity.teleport(to);
            return;
        }
        
        // 方向ベクトルを計算
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        
        if (distance < 0.1) {
            return; // 移動不要
        }
        
        // 移動速度を調整（設定から取得）
        double movementSpeed = agent.getPlugin().getConfigManager().getConfig().getDouble("agent.default_behavior.resource_gathering.movement_speed", 0.21);
        direction.normalize().multiply(Math.min(distance, movementSpeed));
        
        // 向きを設定
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        Location newLocation = entity.getLocation();
        newLocation.setYaw(yaw);
        newLocation.setPitch(0);
        entity.teleport(newLocation); // 向きのみteleport
        
        // 物理移動実行
        try {
            entity.setVelocity(direction);
            logger.debug("★物理移動実行: 速度=" + String.format("(%.3f, %.3f, %.3f)", 
                        direction.getX(), direction.getY(), direction.getZ()));
        } catch (Exception e) {
            // 物理移動が失敗した場合はteleportにフォールバック
            logger.warn("★物理移動失敗 - teleportにフォールバック: " + e.getMessage());
            entity.teleport(to);
        }
    }
    
    /**
     * 段階的移動（長距離用）
     */
    private void moveEntityInSteps(LivingEntity entity, Location from, Location to) {
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector());
        double totalDistance = direction.length();
        
        // 0.3ブロックずつ段階的に移動（自然な歩幅）
        double stepSize = 0.3;
        int steps = (int) Math.ceil(totalDistance / stepSize);
        
        org.bukkit.util.Vector stepVector = direction.clone().normalize().multiply(stepSize);
        
        logger.debug("★段階的移動開始: " + steps + "ステップ");
        
        for (int i = 1; i <= steps; i++) {
            Location stepTarget = from.clone().add(stepVector.clone().multiply(i));
            
            // 最後のステップは正確な目標位置
            if (i == steps) {
                stepTarget = to.clone();
            }
            
            // 各ステップの安全性をチェック
            if (isPathClearAndSafe(entity.getLocation(), stepTarget)) {
                // 小さなteleportで滑らかに移動
                entity.teleport(stepTarget);
                
                // 少し遅延を入れて自然に見せる（20tick/秒 = 50ms/tick）
                try {
                    Thread.sleep(50); // 50ms遅延（1tick相当）
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                logger.warn("★段階的移動中断: ステップ" + i + "が安全でない");
                break;
            }
        }
    }
    
    /**
     * 移動経路が安全かつ障害物がないかチェック
     */
    private boolean isPathClearAndSafe(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || world != to.getWorld()) return false;
        
        // レイキャスティングで経路をチェック
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        
        if (distance < 0.1) return true;
        
        direction.normalize();
        
        // 0.2ブロック間隔でチェック
        for (double d = 0.2; d <= distance; d += 0.2) {
            Location checkPoint = from.clone().add(direction.clone().multiply(d));
            
            // 足元と頭の高さをチェック
            Block feetBlock = world.getBlockAt(checkPoint);
            Block headBlock = world.getBlockAt(checkPoint.getBlockX(), 
                                             checkPoint.getBlockY() + 1, 
                                             checkPoint.getBlockZ());
            
            // 障害物があるかチェック
            if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
                // 溶岩や危険物質は完全に回避
                if (feetBlock.getType() == Material.LAVA || 
                    headBlock.getType() == Material.LAVA ||
                    feetBlock.getType() == Material.WATER ||
                    headBlock.getType() == Material.WATER) {
                    return false;
                }
                
                // 掘削可能でない固体ブロックがあれば経路不可
                if (!BlockUtils.canMineBlock(feetBlock) && feetBlock.getType().isSolid()) {
                    return false;
                }
                if (!BlockUtils.canMineBlock(headBlock) && headBlock.getType().isSolid()) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 垂直に上に登攀する（真上のターゲット用）
     */
    private void digVerticallyUp(LivingEntity entity, Location from, Location target) {
        Location current = entity.getLocation();
        double targetY = target.getY();
        
        logger.info("★垂直登攀開始: 現在Y=" + String.format("%.1f", current.getY()) + 
                   ", ターゲットY=" + String.format("%.1f", targetY));
        
        // 1ブロック上を確認・掘削
        Location digTarget = current.clone().add(0, 1, 0);
        
        // 上のブロックを掘削（あれば）
        Block blockAbove = digTarget.getBlock();
        if (blockAbove.getType().isSolid() && BlockUtils.canMineBlock(blockAbove)) {
            logger.info("★垂直登攀掘削実行: " + blockAbove.getType() + " at " + blockAbove.getLocation());
            boolean success = BlockUtils.mineBlock(agent, blockAbove);
            if (success) {
                logger.info("★垂直登攀掘削成功: " + blockAbove.getType());
            }
        }
        
        // 足場を作って上に移動
        Location buildTarget = current.clone();
        Material buildMaterial = getBuildingMaterial();
        
        if (buildMaterial != null && BlockUtils.hasBlockInInventory(agent, buildMaterial, 1)) {
            // 足場を設置
            boolean built = BlockUtils.placeBlock(agent, buildTarget, buildMaterial);
            if (built) {
                logger.info("★足場設置成功: " + buildMaterial + " at " + buildTarget);
                
                // 上に移動
                Location newPos = current.clone().add(0, 1, 0);
                moveEntityNaturally(entity, current, newPos);
                logger.info("★垂直登攀移動: Y " + String.format("%.1f", current.getY()) + 
                           " → " + String.format("%.1f", newPos.getY()));
            }
        } else {
            logger.info("★足場材料不足 - 他の方法を試行");
            // 材料がない場合は継続移動モードにフォールバック
            startContinuousMovement(target);
        }
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
        // 座標差分を計算（エージェントから見たターゲットの方向）
        double deltaX = targetLocation.getX() - agentLocation.getX();
        double deltaY = targetLocation.getY() - agentLocation.getY();
        double deltaZ = targetLocation.getZ() - agentLocation.getZ();
        double distance = agentLocation.distance(targetLocation);
        
        // デバッグ用：実際の座標を確認
        logger.debug("座標確認 - エージェント:(" + String.format("%.1f,%.1f,%.1f", 
                    agentLocation.getX(), agentLocation.getY(), agentLocation.getZ()) + 
                    "), ターゲット:(" + String.format("%.1f,%.1f,%.1f", 
                    targetLocation.getX(), targetLocation.getY(), targetLocation.getZ()) + ")");
        
        // 方向を分かりやすく表示
        String directionX = deltaX > 0 ? "東" : deltaX < 0 ? "西" : "同X";
        String directionY = deltaY > 0 ? "上" : deltaY < 0 ? "下" : "同Y";
        String directionZ = deltaZ > 0 ? "南" : deltaZ < 0 ? "北" : "同Z";
        
        String coordDiffInfo = String.format("%s%.1f %s%.1f %s%.1f 距離:%.1f", 
                                            directionX, Math.abs(deltaX),
                                            directionY, Math.abs(deltaY), 
                                            directionZ, Math.abs(deltaZ),
                                            distance);
        
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
    
    // ==============================================
    // パーティクル表示システム
    // ==============================================
    
    /**
     * ターゲットブロックをハイライト表示
     */
    private void highlightTargetBlock(Block block) {
        if (block == null || block.getWorld() == null) return;
        
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        World world = block.getWorld();
        
        // ターゲット鉱石は発光エフェクト
        world.spawnParticle(Particle.ENCHANTMENT_TABLE, center, 10, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticle(Particle.CRIT, center, 8, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.FIREWORKS_SPARK, center, 3, 0.2, 0.2, 0.2, 0.02);
        
        // 金色の発光パーティクル
        world.spawnParticle(Particle.REDSTONE, center, 5, 0.3, 0.3, 0.3, 0,
                           new Particle.DustOptions(org.bukkit.Color.YELLOW, 2.0f));
        
        logger.debug("★ターゲットブロックをハイライト: " + block.getType() + " at " + center);
    }
    
    /**
     * エージェントからターゲットまでのロープ（線）を描画
     */
    private void drawRopeToTarget(Location agentLocation, Location targetLocation) {
        if (agentLocation == null || targetLocation == null || agentLocation.getWorld() != targetLocation.getWorld()) {
            return;
        }
        
        World world = agentLocation.getWorld();
        
        // エージェントの位置を少し上にずらして見やすくする
        Location startPoint = agentLocation.clone().add(0, 1.5, 0);
        Location endPoint = targetLocation.clone().add(0.5, 0.5, 0.5);
        
        // 2点間の距離と方向を計算
        org.bukkit.util.Vector direction = endPoint.toVector().subtract(startPoint.toVector());
        double distance = direction.length();
        
        if (distance < 0.1) return;
        
        direction.normalize();
        
        // ロープを描画するためのパーティクル間隔
        double particleSpacing = 0.3; // 0.3ブロック間隔
        int particleCount = (int) Math.ceil(distance / particleSpacing);
        
        // ロープの色（距離に応じて変化）
        org.bukkit.Color ropeColor;
        if (distance <= 4.0) {
            ropeColor = org.bukkit.Color.GREEN; // 採掘可能距離は緑
        } else if (distance <= 10.0) {
            ropeColor = org.bukkit.Color.YELLOW; // 中距離は黄色
        } else {
            ropeColor = org.bukkit.Color.RED; // 遠距離は赤
        }
        
        // パーティクルでロープを描画
        for (int i = 0; i <= particleCount; i++) {
            double t = (double) i / particleCount;
            Location ropePoint = startPoint.clone().add(direction.clone().multiply(distance * t));
            
            // メインのロープ線（発光パーティクル）
            world.spawnParticle(Particle.REDSTONE, ropePoint, 1, 0.0, 0.0, 0.0, 0,
                               new Particle.DustOptions(ropeColor, 1.5f));
            
            // 追加の視覚効果（少し発光させる）
            if (i % 3 == 0) { // 3つおきにより明るいパーティクル
                world.spawnParticle(Particle.GLOW, ropePoint, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        
        // ロープの両端に特別なマーカー
        // 開始点（エージェント側）
        world.spawnParticle(Particle.VILLAGER_HAPPY, startPoint, 3, 0.1, 0.1, 0.1, 0.02);
        world.spawnParticle(Particle.REDSTONE, startPoint, 3, 0.1, 0.1, 0.1, 0,
                           new Particle.DustOptions(org.bukkit.Color.BLUE, 2.0f));
        
        // 終了点（ターゲット側）
        world.spawnParticle(Particle.CRIT, endPoint, 3, 0.1, 0.1, 0.1, 0.02);
        world.spawnParticle(Particle.REDSTONE, endPoint, 3, 0.1, 0.1, 0.1, 0,
                           new Particle.DustOptions(org.bukkit.Color.ORANGE, 2.0f));
        
        logger.debug("★ロープ描画: 距離=" + String.format("%.1f", distance) + 
                    ", 色=" + ropeColor.toString() + ", パーティクル数=" + (particleCount + 1));
    }
    
    /**
     * 移動パスを表示
     */
    private void showMovementPath(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()) return;
        
        World world = from.getWorld();
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        
        if (distance < 0.1) return;
        
        direction.normalize();
        
        // 0.5ブロック間隔でパスを表示
        for (double d = 0.5; d <= distance; d += 0.5) {
            Location pathPoint = from.clone().add(direction.clone().multiply(d));
            
            // 発光する青色のパーティクルで移動パスを表示
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, pathPoint.add(0, 0.1, 0), 2, 0.1, 0.1, 0.1, 0.01);
            world.spawnParticle(Particle.GLOW, pathPoint, 3, 0.2, 0.2, 0.2, 0.02);
            
            // 青色の発光パーティクル
            world.spawnParticle(Particle.REDSTONE, pathPoint, 2, 0.1, 0.1, 0.1, 0,
                               new Particle.DustOptions(org.bukkit.Color.AQUA, 1.5f));
        }
        
        logger.debug("★移動パス表示: " + String.format("%.1f", distance) + "ブロック");
    }
    
    /**
     * 掘削対象ブロックをハイライト
     */
    private void highlightMiningBlocks(Location location) {
        if (location == null || location.getWorld() == null) return;
        
        World world = location.getWorld();
        
        // 足元ブロック
        Block footBlock = world.getBlockAt(location);
        if (footBlock.getType().isSolid() && BlockUtils.canMineBlock(footBlock)) {
            Location footCenter = footBlock.getLocation().add(0.5, 0.5, 0.5);
            // 発光する赤色エフェクトで掘削対象を表示
            world.spawnParticle(Particle.FLAME, footCenter, 5, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.LAVA, footCenter, 2, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.REDSTONE, footCenter, 4, 0.3, 0.3, 0.3, 0,
                               new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
        }
        
        // 頭上ブロック
        Block headBlock = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        if (headBlock.getType().isSolid() && BlockUtils.canMineBlock(headBlock)) {
            Location headCenter = headBlock.getLocation().add(0.5, 0.5, 0.5);
            // 発光する赤色エフェクトで掘削対象を表示
            world.spawnParticle(Particle.FLAME, headCenter, 5, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.LAVA, headCenter, 2, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.REDSTONE, headCenter, 4, 0.3, 0.3, 0.3, 0,
                               new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
        }
        
        // 上部ブロック
        Block upperBlock = world.getBlockAt(location.getBlockX(), location.getBlockY() + 2, location.getBlockZ());
        if (upperBlock.getType().isSolid() && BlockUtils.canMineBlock(upperBlock)) {
            Location upperCenter = upperBlock.getLocation().add(0.5, 0.5, 0.5);
            // 発光する赤色エフェクトで掘削対象を表示
            world.spawnParticle(Particle.FLAME, upperCenter, 5, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.LAVA, upperCenter, 2, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.REDSTONE, upperCenter, 4, 0.3, 0.3, 0.3, 0,
                               new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
        }
    }
    
    
    /**
     * 階段の構築予定を表示
     */
    private void showStaircasePlan(Location current, Location target) {
        if (current == null || target == null || current.getWorld() != target.getWorld()) return;
        
        World world = current.getWorld();
        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector()).normalize();
        
        // 階段の一段を表示
        Location stepLocation = current.clone().add(direction.multiply(1.0));
        
        // 高度差に応じて下降量を調整
        double heightDiff = target.getY() - current.getY();
        if (heightDiff < -1.0) {
            stepLocation.subtract(0, 1, 0);
        }
        
        // 発光する緑色のパーティクルで階段予定地を表示
        Location stepCenter = stepLocation.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.VILLAGER_HAPPY, stepCenter, 8, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticle(Particle.GLOW, stepCenter, 5, 0.2, 0.2, 0.2, 0.01);
        world.spawnParticle(Particle.REDSTONE, stepCenter, 6, 0.3, 0.3, 0.3, 0,
                           new Particle.DustOptions(org.bukkit.Color.GREEN, 2.0f));
        
        // 発光する階段の輪郭を表示
        for (int i = 0; i < 3; i++) {
            Location outlinePoint = stepLocation.clone().add(0, i, 0).add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.GLOW, outlinePoint, 2, 0.1, 0.1, 0.1, 0.01);
            world.spawnParticle(Particle.REDSTONE, outlinePoint, 2, 0.1, 0.1, 0.1, 0,
                               new Particle.DustOptions(org.bukkit.Color.LIME, 1.5f));
        }
        
        logger.debug("★階段構築予定表示: " + stepCenter);
    }
}