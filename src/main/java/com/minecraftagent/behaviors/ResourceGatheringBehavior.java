package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.BlockUtils;
import com.minecraftagent.utils.MovementUtils;

import org.bukkit.Location;
import org.bukkit.Material;
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
    }
    
    @Override
    public boolean canExecute() {
        if (!isAgentValid()) return false;
        
        // インベントリが満杯でない場合、または必要な資源がある場合
        return !isInventoryFull() || hasNeededResources();
    }
    
    @Override
    protected void onStart() {
        logger.debug("資源収集行動を開始しました");
        targetBlock = null;
        isMining = false;
    }
    
    @Override
    protected void onUpdate() {
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        // ステータス表示を更新
        agent.getStatusDisplay().setBehavior("ResourceGatheringBehavior");
        
        long currentTime = System.currentTimeMillis();
        
        // 採掘中の場合
        if (isMining && targetBlock != null) {
            agent.getStatusDisplay().setAction("採掘中");
            agent.getStatusDisplay().setTarget(targetBlock.getType().name());
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
        
        // ターゲットに向かって自然に移動または採掘開始
        if (targetBlock != null) {
            Location agentLocation = entity.getLocation();
            double distance = agentLocation.distance(targetBlock.getLocation());
            
            if (distance > 2.0) {
                agent.getStatusDisplay().setAction("移動中");
                agent.getStatusDisplay().setTarget(targetBlock.getType().name());
                boolean reached = MovementUtils.walkTowards(entity, targetBlock.getLocation().add(0.5, 0, 0.5), 0.1);
                if (!reached) {
                    // ジャンプが必要な場合
                    if (MovementUtils.needsJump(entity, targetBlock.getLocation())) {
                        MovementUtils.performJump(entity);
                    }
                }
            } else {
                startMining();
            }
        }
    }
    
    @Override
    protected void onStop() {
        logger.debug("資源収集行動を停止しました");
        isMining = false;
        targetBlock = null;
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
        List<Block> nearbyBlocks = BlockUtils.findNearbyBlocks(location, 10, priorityResources);
        return !nearbyBlocks.isEmpty();
    }
    
    /**
     * 新しいターゲットを探す
     */
    private void findNewTarget() {
        LivingEntity entity = agent.getEntity();
        Location location = entity.getLocation();
        
        // 優先度順に資源を探す
        for (Material material : priorityResources) {
            List<Block> blocks = BlockUtils.findNearbyBlocks(location, 15, material);
            
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
                    logger.debug("新しいターゲットを発見: " + material + " at " + closest.getLocation());
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
     * 収集統計を取得
     */
    public String getGatheringStatistics() {
        Map<Material, Integer> inventory = BlockUtils.getInventoryContents(agent);
        int totalItems = inventory.values().stream().mapToInt(Integer::intValue).sum();
        
        return String.format("収集統計: 総アイテム数=%d, 種類=%d, 採掘中=%s", 
                           totalItems, inventory.size(), isMining ? "はい" : "いいえ");
    }
}