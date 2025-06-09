package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.BlockUtils;
import com.minecraftagent.utils.MovementUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 建築行動 - ブロックの設置、建築物の作成
 */
public class BuildingBehavior extends BaseBehavior {
    
    private BuildingProject currentProject;
    private final Random random;
    private long lastBuildAction;
    
    public BuildingBehavior(MinecraftAgent agent, int priority) {
        super(agent, priority);
        this.random = new Random();
        this.lastBuildAction = 0;
    }
    
    @Override
    public boolean canExecute() {
        if (!isAgentValid()) return false;
        
        // 建築材料を持っているかチェック
        return hasBuildingMaterials() || shouldGatherMaterials();
    }
    
    @Override
    protected void onStart() {
        logger.debug("建築行動を開始しました");
        
        // 新しい建築プロジェクトを生成
        if (currentProject == null) {
            generateBuildingProject();
        }
    }
    
    @Override
    protected void onUpdate() {
        LivingEntity entity = agent.getEntity();
        if (entity == null) return;
        
        // ステータス表示を更新
        agent.getStatusDisplay().setBehavior("BuildingBehavior");
        
        long currentTime = System.currentTimeMillis();
        
        // 1秒に1回だけ建築アクション
        if (currentTime - lastBuildAction < 1000) {
            return;
        }
        lastBuildAction = currentTime;
        
        if (currentProject == null) {
            agent.getStatusDisplay().setAction("建築計画作成");
            generateBuildingProject();
            return;
        }
        
        // 建築材料が不足している場合は収集
        if (!hasBuildingMaterials()) {
            agent.getStatusDisplay().setAction("材料収集中");
            gatherBuildingMaterials();
            return;
        }
        
        // 建築を実行
        if (currentProject != null) {
            agent.getStatusDisplay().setAction("建築中");
            agent.getStatusDisplay().setTarget(currentProject.getType().name());
        }
        executeBuildingProject();
    }
    
    @Override
    protected void onStop() {
        logger.debug("建築行動を停止しました");
    }
    
    /**
     * 建築材料を持っているかチェック
     */
    private boolean hasBuildingMaterials() {
        return BlockUtils.hasBlockInInventory(agent, Material.COBBLESTONE, 10) ||
               BlockUtils.hasBlockInInventory(agent, Material.STONE, 10) ||
               BlockUtils.hasBlockInInventory(agent, Material.DIRT, 10) ||
               BlockUtils.hasBlockInInventory(agent, Material.OAK_PLANKS, 10);
    }
    
    /**
     * 建築材料を収集すべきかチェック
     */
    private boolean shouldGatherMaterials() {
        // 周囲に収集可能な材料があるかチェック
        Location location = agent.getEntity().getLocation();
        return BlockUtils.findNearbyBlocks(location, 10, Material.STONE, Material.DIRT, Material.OAK_LOG).size() > 0;
    }
    
    /**
     * 建築プロジェクトを生成
     */
    private void generateBuildingProject() {
        LivingEntity entity = agent.getEntity();
        Location baseLocation = entity.getLocation().clone();
        
        // 平坦な場所を探す
        baseLocation = findFlatArea(baseLocation);
        
        // プロジェクトタイプをランダム選択
        BuildingType type = BuildingType.values()[random.nextInt(BuildingType.values().length)];
        
        currentProject = new BuildingProject(type, baseLocation);
        logger.info("新しい建築プロジェクト開始: " + type + " at " + baseLocation);
    }
    
    /**
     * 平坦な場所を探す
     */
    private Location findFlatArea(Location center) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = center.getBlockX() + random.nextInt(21) - 10; // -10 to +10
            int z = center.getBlockZ() + random.nextInt(21) - 10;
            
            Location testLocation = new Location(center.getWorld(), x, center.getY(), z);
            
            if (isFlatArea(testLocation, 5, 5)) {
                return testLocation;
            }
        }
        return center; // 見つからない場合は現在地
    }
    
    /**
     * 指定範囲が平坦かチェック
     */
    private boolean isFlatArea(Location center, int width, int depth) {
        int baseY = center.getBlockY();
        
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Block block = center.getWorld().getBlockAt(
                    center.getBlockX() + x,
                    baseY,
                    center.getBlockZ() + z
                );
                
                // 空気でない、または高低差が大きい場合はNG
                if (!block.getType().isAir() || 
                    Math.abs(block.getY() - baseY) > 1) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * 建築材料を収集
     */
    private void gatherBuildingMaterials() {
        Location location = agent.getEntity().getLocation();
        
        // 近くの建築材料を探す
        List<Block> nearbyBlocks = BlockUtils.findNearbyBlocks(location, 8, 
            Material.STONE, Material.DIRT, Material.OAK_LOG, Material.COBBLESTONE);
        
        if (!nearbyBlocks.isEmpty()) {
            Block targetBlock = nearbyBlocks.get(0);
            
            // ブロックに近づく
            if (location.distance(targetBlock.getLocation()) > 2.0) {
                moveTowards(targetBlock.getLocation());
            } else {
                // ブロックを採掘
                mineBlock(targetBlock);
            }
        }
    }
    
    /**
     * 建築プロジェクトを実行
     */
    private void executeBuildingProject() {
        if (currentProject.isCompleted()) {
            logger.info("建築プロジェクト完了: " + currentProject.getType());
            currentProject = null;
            return;
        }
        
        Location nextBlockLocation = currentProject.getNextBlockLocation();
        if (nextBlockLocation == null) {
            currentProject = null;
            return;
        }
        
        // 建築場所に自然に移動
        Location agentLocation = agent.getEntity().getLocation();
        if (agentLocation.distance(nextBlockLocation) > 3.0) {
            boolean reached = MovementUtils.walkTowards(agent.getEntity(), nextBlockLocation, 0.12);
            if (!reached) {
                // ジャンプが必要な場合
                if (MovementUtils.needsJump(agent.getEntity(), nextBlockLocation)) {
                    MovementUtils.performJump(agent.getEntity());
                }
                return; // まだ移動中
            }
        }
        
        // ブロックを設置
        Material material = selectBuildingMaterial();
        if (material != null) {
            placeBlock(nextBlockLocation, material);
            currentProject.markBlockPlaced(nextBlockLocation);
        }
    }
    
    /**
     * ブロックを採掘
     */
    private void mineBlock(Block block) {
        Material blockType = block.getType();
        
        // ブロックを破壊してアイテムとして追加
        block.setType(Material.AIR);
        BlockUtils.addItemToInventory(agent, new ItemStack(blockType, 1));
        
        logger.debug("ブロックを採掘しました: " + blockType);
    }
    
    /**
     * ブロックを設置
     */
    private void placeBlock(Location location, Material material) {
        Block block = location.getBlock();
        
        // インベントリから材料を消費
        if (BlockUtils.removeItemFromInventory(agent, material, 1)) {
            block.setType(material);
            logger.debug("ブロックを設置しました: " + material + " at " + location);
        }
    }
    
    /**
     * 建築材料を選択
     */
    private Material selectBuildingMaterial() {
        Material[] materials = {Material.COBBLESTONE, Material.STONE, Material.OAK_PLANKS, Material.DIRT};
        
        for (Material material : materials) {
            if (BlockUtils.hasBlockInInventory(agent, material, 1)) {
                return material;
            }
        }
        return null;
    }
    
    /**
     * 指定座標に安全に移動
     */
    private void moveTowards(Location target) {
        LivingEntity entity = agent.getEntity();
        Location current = entity.getLocation();
        
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
        
        // 足元と頭の位置が空気または非固体ブロックであることを確認
        return (feet.getType().isAir() || !feet.getType().isSolid()) &&
               (head.getType().isAir() || !head.getType().isSolid());
    }
    
    /**
     * 建築プロジェクトクラス
     */
    private static class BuildingProject {
        private final BuildingType type;
        private final Location baseLocation;
        private final List<Location> plannedBlocks;
        private final List<Location> placedBlocks;
        
        public BuildingProject(BuildingType type, Location baseLocation) {
            this.type = type;
            this.baseLocation = baseLocation;
            this.plannedBlocks = new ArrayList<>();
            this.placedBlocks = new ArrayList<>();
            
            generateBuildingPlan();
        }
        
        private void generateBuildingPlan() {
            switch (type) {
                case SIMPLE_HOUSE:
                    generateSimpleHouse();
                    break;
                case TOWER:
                    generateTower();
                    break;
                case BRIDGE:
                    generateBridge();
                    break;
                case WALL:
                    generateWall();
                    break;
            }
        }
        
        private void generateSimpleHouse() {
            // 5x5の簡単な家
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    for (int y = 0; y < 3; y++) {
                        // 壁部分のみ
                        if (x == 0 || x == 4 || z == 0 || z == 4 || y == 0 || y == 2) {
                            if (!(x == 2 && z == 0 && y == 1)) { // ドア部分は除外
                                plannedBlocks.add(baseLocation.clone().add(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        
        private void generateTower() {
            // 3x3の塔
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 3; x++) {
                    for (int z = 0; z < 3; z++) {
                        // 外周のみ
                        if (x == 0 || x == 2 || z == 0 || z == 2) {
                            plannedBlocks.add(baseLocation.clone().add(x, y, z));
                        }
                    }
                }
            }
        }
        
        private void generateBridge() {
            // 10ブロックの橋
            for (int x = 0; x < 10; x++) {
                plannedBlocks.add(baseLocation.clone().add(x, 0, 0));
                plannedBlocks.add(baseLocation.clone().add(x, 0, 2));
                // 手すり
                if (x % 2 == 0) {
                    plannedBlocks.add(baseLocation.clone().add(x, 1, -1));
                    plannedBlocks.add(baseLocation.clone().add(x, 1, 3));
                }
            }
        }
        
        private void generateWall() {
            // 15ブロックの壁
            for (int x = 0; x < 15; x++) {
                for (int y = 0; y < 4; y++) {
                    plannedBlocks.add(baseLocation.clone().add(x, y, 0));
                }
            }
        }
        
        public BuildingType getType() { return type; }
        
        public Location getNextBlockLocation() {
            for (Location location : plannedBlocks) {
                if (!placedBlocks.contains(location)) {
                    return location;
                }
            }
            return null;
        }
        
        public void markBlockPlaced(Location location) {
            placedBlocks.add(location);
        }
        
        public boolean isCompleted() {
            return placedBlocks.size() >= plannedBlocks.size();
        }
    }
    
    /**
     * 建築タイプ
     */
    private enum BuildingType {
        SIMPLE_HOUSE,
        TOWER,
        BRIDGE,
        WALL
    }
}