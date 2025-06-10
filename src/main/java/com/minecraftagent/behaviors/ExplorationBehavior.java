package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.MovementUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * 探索行動 - ランダムウォークと興味深いブロックの発見
 */
public class ExplorationBehavior extends BaseBehavior {
    
    private Location targetLocation;
    private long lastDirectionChange;
    private int stuckCounter;
    private Location lastPosition;
    private final Random random;
    
    public ExplorationBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
        this.random = new Random();
        this.lastDirectionChange = 0;
        this.stuckCounter = 0;
    }
    
    @Override
    public boolean canExecute() {
        if (!isAgentValid()) {
            logger.debug("ExplorationBehavior: エージェントが無効");
            return false;
        }
        
        // 他の行動がより重要な場合は探索を停止
        // 常に最低優先度として動作させる
        logger.debug("ExplorationBehavior: 実行可能");
        return true;
    }
    
    @Override
    protected void onStart() {
        logger.debug("探索行動を開始しました");
        generateNewTarget();
    }
    
    @Override
    protected void onUpdate() {
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        // ステータス表示を更新
        agent.getStatusDisplay().setBehavior("ExplorationBehavior");
        
        Location currentLocation = entity.getLocation();
        
        // スタック検出
        checkIfStuck(currentLocation);
        
        // 新しいターゲットが必要かチェック
        if (needsNewTarget(currentLocation)) {
            agent.getStatusDisplay().setAction("目標地点探索");
            generateNewTarget();
        }
        
        // ターゲットに向かって自然に移動
        if (targetLocation != null) {
            agent.getStatusDisplay().setAction("移動中");
            agent.getStatusDisplay().setTarget(String.format("(%d, %d, %d)", 
                targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ()));
            
            // シンプルで確実な移動ロジック
            double distance = currentLocation.distance(targetLocation);
            if (distance < 2.0) {
                generateNewTarget(); // 目標に到達したら新しいターゲットを生成
            } else {
                moveTowardsTargetSimple(entity, currentLocation);
            }
        }
        
        // 興味深いブロックをチェック
        exploreNearbyBlocks(currentLocation);
    }
    
    @Override
    protected void onStop() {
        logger.debug("探索行動を停止しました");
        targetLocation = null;
    }
    
    /**
     * スタック状態をチェック
     */
    private void checkIfStuck(Location currentLocation) {
        if (lastPosition != null && 
            lastPosition.distance(currentLocation) < 0.3) { // より敏感に検出
            stuckCounter++;
            if (stuckCounter > 5) { // より早く反応
                // スタックしている場合は新しいターゲットを生成
                agent.getStatusDisplay().setAction("スタック回避");
                logger.debug("スタック検出 - 新しいターゲットを生成");
                generateNewTarget();
                stuckCounter = 0;
            }
        } else {
            stuckCounter = 0;
        }
        lastPosition = currentLocation.clone();
    }
    
    /**
     * 新しいターゲットが必要かチェック
     */
    private boolean needsNewTarget(Location currentLocation) {
        if (targetLocation == null) return true;
        
        // ターゲットに近づいた場合
        if (currentLocation.distance(targetLocation) < 3.0) return true;
        
        // 10秒ごとに方向転換（より長い探索時間）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDirectionChange > 10000) {
            lastDirectionChange = currentTime;
            return true;
        }
        
        return false;
    }
    
    /**
     * 新しいターゲット位置を生成
     */
    private void generateNewTarget() {
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        Location currentLocation = entity.getLocation();
        
        // ランダムな方向と距離でターゲットを生成（短い距離で確実に）
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = 5 + random.nextDouble() * 10; // 5-15ブロック（短距離）
        
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        
        targetLocation = currentLocation.clone().add(offsetX, 0, offsetZ);
        
        // 地面の高さに調整
        targetLocation.setY(getGroundLevel(targetLocation));
        
        logger.debug("新しい探索ターゲット: " + 
                     String.format("(%.1f, %.1f, %.1f)", 
                                   targetLocation.getX(), 
                                   targetLocation.getY(), 
                                   targetLocation.getZ()));
    }
    
    /**
     * ターゲットに向かってシンプルに移動（ぐるぐる回らない）
     */
    private void moveTowardsTargetSimple(LivingEntity entity, Location currentLocation) {
        if (targetLocation == null) return;
        
        Vector direction = targetLocation.toVector().subtract(currentLocation.toVector());
        double distance = direction.length();
        
        if (distance > 0.5) {
            direction.normalize();
            
            // 直線的な移動で、小さなステップ
            Location newLocation = currentLocation.clone();
            double moveDistance = Math.min(1.0, distance); // 1ブロックずつ移動
            newLocation.add(direction.multiply(moveDistance));
            
            // 安全な地面の高さに調整
            newLocation.setY(getGroundLevel(newLocation));
            
            // エンティティの向きを設定（移動方向に向ける）
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            newLocation.setYaw(yaw);
            newLocation.setPitch(0);
            
            // 移動先が安全かチェック
            if (isSafeToMoveTo(newLocation)) {
                entity.teleport(newLocation);
            } else {
                // 移動できない場合は新しいターゲットを生成
                generateNewTarget();
            }
        }
    }
    
    /**
     * 移動先が安全かチェック
     */
    private boolean isSafeToMoveTo(Location location) {
        if (location == null || location.getWorld() == null) return false;
        
        Block feet = location.getBlock();
        Block head = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        Block ground = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
        
        // 足元と頭上が空気で、地面が固体
        boolean feetClear = feet.getType().isAir() || !feet.getType().isSolid();
        boolean headClear = head.getType().isAir() || !head.getType().isSolid();
        boolean groundSolid = ground.getType().isSolid();
        
        // 危険なブロックを回避
        boolean notDangerous = feet.getType() != Material.LAVA && 
                              feet.getType() != Material.FIRE &&
                              head.getType() != Material.LAVA &&
                              head.getType() != Material.FIRE;
        
        return feetClear && headClear && groundSolid && notDangerous;
    }
    
    /**
     * ターゲットに向かって移動（旧版 - 使用しない）
     */
    private void moveTowardsTarget(LivingEntity entity, Location currentLocation) {
        Vector direction = targetLocation.toVector().subtract(currentLocation.toVector());
        double distance = direction.length();
        
        if (distance > 0.5) {
            direction.normalize();
            
            // より自然な移動のため、teleportを使用
            Location newLocation = currentLocation.clone();
            
            // 移動距離を制限（1ブロック以下）
            double moveDistance = Math.min(0.3, distance);
            newLocation.add(direction.multiply(moveDistance));
            
            // 地面の高さに調整
            newLocation.setY(getGroundLevel(newLocation));
            
            // エンティティの向きを設定
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            newLocation.setYaw(yaw);
            newLocation.setPitch(0);
            
            // テレポートで移動
            entity.teleport(newLocation);
        }
    }
    
    /**
     * 安全な地面の高さを取得
     */
    private double getGroundLevel(Location location) {
        int startY = Math.max(location.getBlockY() + 10, location.getWorld().getHighestBlockYAt(location)); // 上から探索開始
        
        for (int y = startY; y > 0; y--) {
            Block block = location.getWorld().getBlockAt(location.getBlockX(), y, location.getBlockZ());
            Block above = location.getWorld().getBlockAt(location.getBlockX(), y + 1, location.getBlockZ());
            Block above2 = location.getWorld().getBlockAt(location.getBlockX(), y + 2, location.getBlockZ());
            
            // 固体ブロックで、上の2ブロックが空気の場合
            if (block.getType().isSolid() && 
                (above.getType().isAir() || !above.getType().isSolid()) &&
                (above2.getType().isAir() || !above2.getType().isSolid())) {
                return y + 1.0; // ブロックの上に立つ
            }
        }
        
        // 見つからない場合は元の高さ、または世界の海面レベルを使用
        return Math.max(location.getY(), 64.0);
    }
    
    /**
     * 近くの興味深いブロックを探索
     */
    private void exploreNearbyBlocks(Location currentLocation) {
        int range = 5;
        for (int x = -range; x <= range; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = currentLocation.getWorld().getBlockAt(
                        currentLocation.getBlockX() + x,
                        currentLocation.getBlockY() + y,
                        currentLocation.getBlockZ() + z
                    );
                    
                    if (isInterestingBlock(block.getType())) {
                        logger.debug("興味深いブロックを発見: " + block.getType() + 
                                   " at " + block.getLocation());
                    }
                }
            }
        }
    }
    
    /**
     * 興味深いブロックかチェック
     */
    private boolean isInterestingBlock(Material material) {
        return material == Material.DIAMOND_ORE ||
               material == Material.IRON_ORE ||
               material == Material.GOLD_ORE ||
               material == Material.COAL_ORE ||
               material == Material.CHEST ||
               material == Material.SPAWNER;
    }
}