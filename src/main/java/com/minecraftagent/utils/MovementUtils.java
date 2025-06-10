package com.minecraftagent.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * 自然な移動のためのユーティリティクラス
 */
public class MovementUtils {
    
    /**
     * 自然な歩行移動を実行（シンプル版）
     */
    public static boolean walkTowards(LivingEntity entity, Location target, double speed) {
        Location current = entity.getLocation();
        double distance = current.distance(target);
        
        if (distance < 1.0) {
            return true; // 到達済み
        }
        
        // 方向ベクトルを計算
        Vector direction = target.toVector().subtract(current.toVector()).normalize();
        
        // 小さなステップで移動（現在位置を基準）
        double moveDistance = Math.min(speed * 0.5, distance); // より小さなステップ
        Location newLocation = current.clone().add(direction.multiply(moveDistance));
        
        // 現在位置を基準とした適切なY座標を設定
        newLocation.setY(findSuitableY(newLocation, current));
        
        // 向きを設定
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        newLocation.setYaw(yaw);
        newLocation.setPitch(0);
        
        // 移動先が安全かチェック
        if (isSafeLocation(newLocation)) {
            entity.teleport(newLocation);
            return false; // まだ移動中
        }
        
        return false; // 移動できない
    }
    
    /**
     * 現在のエージェント位置を基準とした適切なY座標を見つける
     */
    public static double findSuitableY(Location location, Location currentPosition) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        // 現在のエージェント位置を基準に探索範囲を決定
        int agentY = currentPosition.getBlockY();
        int searchStartY = Math.max(agentY + 3, agentY); // 現在位置から上に3ブロックまで
        int searchEndY = Math.max(agentY - 5, world.getMinHeight()); // 現在位置から下に5ブロックまで
        
        // まず現在の高さ付近で安全な場所を探す
        for (int y = searchStartY; y >= searchEndY; y--) {
            if (isSafeGroundSpot(world, x, y, z)) {
                return y + 1.0; // ブロックの上に立つ
            }
        }
        
        // 見つからない場合は現在のY座標を維持
        return currentPosition.getY();
    }
    
    /**
     * 地上レベルの安全なY座標を見つける（探索用）
     */
    private static double findGroundLevel(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        // 地表から少し上から探索開始
        int maxY = world.getHighestBlockYAt(x, z) + 5;
        
        // 上から下に探索して、安全な地面を見つける
        for (int y = maxY; y > world.getMinHeight(); y--) {
            if (isSafeGroundSpot(world, x, y, z)) {
                return y + 1.0; // ブロックの上に立つ
            }
        }
        
        // 見つからない場合は元のY座標（地上レベル優先）
        return Math.max(location.getY(), 64.0);
    }
    
    /**
     * 安全な地面かチェック（シンプル版）
     */
    private static boolean isSafeGroundSpot(World world, int x, int y, int z) {
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        
        // 地面が固体で、足元と頭上が空気
        return ground.getType().isSolid() && 
               !ground.getType().equals(Material.LAVA) &&
               feet.getType().isAir() &&
               head.getType().isAir();
    }
    
    /**
     * 指定座標が安全な立ち位置かチェック
     */
    private static boolean isSafeSpot(World world, int x, int y, int z) {
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        
        return ground.getType().isSolid() && 
               !ground.getType().equals(Material.LAVA) &&
               (feet.getType().isAir() || isPassable(feet.getType())) &&
               (head.getType().isAir() || isPassable(head.getType()));
    }
    
    /**
     * 移動先が安全かチェック（シンプル版）
     */
    private static boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = location.getWorld().getBlockAt(
            location.getBlockX(), 
            location.getBlockY() + 1, 
            location.getBlockZ()
        );
        Block ground = location.getWorld().getBlockAt(
            location.getBlockX(), 
            location.getBlockY() - 1, 
            location.getBlockZ()
        );
        
        // 足元と頭上が空気で、地面が固体
        return feet.getType().isAir() && 
               head.getType().isAir() && 
               ground.getType().isSolid() &&
               ground.getType() != Material.LAVA;
    }
    
    /**
     * ブロックが通過可能かチェック
     */
    private static boolean isPassable(Material material) {
        return material.isAir() || 
               material == Material.WATER ||
               material == Material.TALL_GRASS ||
               material == Material.GRASS ||
               material == Material.FERN ||
               material == Material.LARGE_FERN ||
               material == Material.DEAD_BUSH ||
               material == Material.DANDELION ||
               material == Material.POPPY ||
               material == Material.SNOW;
    }
    
    /**
     * パス検索（簡単なA*アルゴリズム風）
     */
    public static Location findPath(Location start, Location target, int maxSteps) {
        if (start.getWorld() != target.getWorld()) {
            return null;
        }
        
        double distance = start.distance(target);
        if (distance < 1.0) {
            return target;
        }
        
        // 直線で行けるかチェック
        if (hasDirectPath(start, target)) {
            Vector direction = target.toVector().subtract(start.toVector()).normalize();
            Location nextStep = start.clone().add(direction.multiply(0.5));
            nextStep.setY(findGroundLevel(nextStep));
            return nextStep;
        }
        
        // 障害物を回避する簡単なロジック
        return findAlternatePath(start, target);
    }
    
    /**
     * 直線パスが可能かチェック
     */
    private static boolean hasDirectPath(Location start, Location target) {
        Vector direction = target.toVector().subtract(start.toVector()).normalize();
        double distance = start.distance(target);
        
        for (double d = 0.5; d < distance; d += 0.5) {
            Location checkPoint = start.clone().add(direction.multiply(d));
            checkPoint.setY(findGroundLevel(checkPoint));
            
            if (!isSafeLocation(checkPoint)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 代替パスを探す
     */
    private static Location findAlternatePath(Location start, Location target) {
        Vector direction = target.toVector().subtract(start.toVector()).normalize();
        
        // 左右に少し迂回するパスを試す
        Vector[] alternatives = {
            direction.clone().rotateAroundY(Math.toRadians(30)), // 30度右
            direction.clone().rotateAroundY(Math.toRadians(-30)), // 30度左
            direction.clone().rotateAroundY(Math.toRadians(60)), // 60度右
            direction.clone().rotateAroundY(Math.toRadians(-60)) // 60度左
        };
        
        for (Vector alt : alternatives) {
            Location nextStep = start.clone().add(alt.multiply(0.8));
            nextStep.setY(findGroundLevel(nextStep));
            
            if (isSafeLocation(nextStep)) {
                return nextStep;
            }
        }
        
        // 最後の手段：真上に移動
        Location upStep = start.clone();
        upStep.setY(start.getY() + 1);
        if (isSafeLocation(upStep)) {
            return upStep;
        }
        
        return start; // 移動できない
    }
    
    /**
     * ジャンプが必要かチェック
     */
    public static boolean needsJump(LivingEntity entity, Location target) {
        Location current = entity.getLocation();
        
        // 高低差が1ブロック以上ある場合
        double heightDiff = target.getY() - current.getY();
        
        if (heightDiff > 0.5 && heightDiff <= 1.5) {
            // 前方1ブロックが固体かチェック
            Vector direction = target.toVector().subtract(current.toVector()).normalize();
            Location checkLocation = current.clone().add(direction.multiply(1.0));
            Block frontBlock = checkLocation.getBlock();
            
            return frontBlock.getType().isSolid();
        }
        
        return false;
    }
    
    /**
     * ジャンプを実行
     */
    public static void performJump(LivingEntity entity) {
        Vector jumpVelocity = entity.getVelocity();
        jumpVelocity.setY(0.5); // ジャンプ力
        entity.setVelocity(jumpVelocity);
    }
    
    /**
     * 移動統計を取得
     */
    public static String getMovementStatistics(LivingEntity entity, Location target) {
        Location current = entity.getLocation();
        double distance = current.distance(target);
        double heightDiff = target.getY() - current.getY();
        
        return String.format("Distance: %.2f, Height diff: %.2f, Speed: %.2f", 
                           distance, heightDiff, entity.getVelocity().length());
    }
}