package com.minecraftagent.utils;

import com.minecraftagent.agent.MinecraftAgent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ブロック操作とインベントリ管理のユーティリティクラス
 */
public class BlockUtils {
    
    // エージェントのインベントリ（仮想インベントリ）
    private static final Map<String, Map<Material, Integer>> agentInventories = new HashMap<>();
    
    /**
     * エージェントのインベントリを取得（存在しない場合は作成）
     */
    private static Map<Material, Integer> getAgentInventory(MinecraftAgent agent) {
        String agentId = agent.getAgentId();
        return agentInventories.computeIfAbsent(agentId, k -> new HashMap<>());
    }
    
    /**
     * 指定範囲内の特定ブロックを検索
     */
    public static List<Block> findNearbyBlocks(Location center, int radius, Material... materials) {
        List<Block> foundBlocks = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getWorld().getBlockAt(
                        center.getBlockX() + x,
                        center.getBlockY() + y,
                        center.getBlockZ() + z
                    );
                    
                    for (Material material : materials) {
                        if (block.getType() == material) {
                            foundBlocks.add(block);
                        }
                    }
                }
            }
        }
        
        return foundBlocks;
    }
    
    /**
     * エージェントのインベントリにアイテムを追加
     */
    public static boolean addItemToInventory(MinecraftAgent agent, ItemStack item) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        Material material = item.getType();
        int amount = item.getAmount();
        
        inventory.merge(material, amount, Integer::sum);
        
        agent.getLogger().debug("アイテムを追加: " + material + " x" + amount + 
                               " (合計: " + inventory.get(material) + ")");
        return true;
    }
    
    /**
     * エージェントのインベントリからアイテムを削除
     */
    public static boolean removeItemFromInventory(MinecraftAgent agent, Material material, int amount) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        
        int currentAmount = inventory.getOrDefault(material, 0);
        if (currentAmount < amount) {
            return false; // 不足している
        }
        
        int newAmount = currentAmount - amount;
        if (newAmount == 0) {
            inventory.remove(material);
        } else {
            inventory.put(material, newAmount);
        }
        
        agent.getLogger().debug("アイテムを消費: " + material + " x" + amount + 
                               " (残り: " + newAmount + ")");
        return true;
    }
    
    /**
     * エージェントが指定アイテムを持っているかチェック
     */
    public static boolean hasBlockInInventory(MinecraftAgent agent, Material material, int requiredAmount) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        return inventory.getOrDefault(material, 0) >= requiredAmount;
    }
    
    /**
     * エージェントのインベントリの内容を取得
     */
    public static Map<Material, Integer> getInventoryContents(MinecraftAgent agent) {
        return new HashMap<>(getAgentInventory(agent));
    }
    
    /**
     * インベントリの統計情報を取得
     */
    public static String getInventoryStatistics(MinecraftAgent agent) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        
        if (inventory.isEmpty()) {
            return "インベントリは空です";
        }
        
        StringBuilder sb = new StringBuilder("インベントリ: ");
        inventory.forEach((material, amount) -> 
            sb.append(material.name()).append(" x").append(amount).append(", "));
        
        // 末尾のカンマを削除
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        
        return sb.toString();
    }
    
    /**
     * ブロックが設置可能かチェック
     */
    public static boolean canPlaceBlock(Location location) {
        Block block = location.getBlock();
        return block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA;
    }
    
    /**
     * ブロックが採掘可能かチェック
     */
    public static boolean canMineBlock(Block block) {
        Material type = block.getType();
        
        // 採掘不可能なブロック
        if (type.isAir() || type == Material.BEDROCK || type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        
        // プレイヤーの建築物は採掘しない（チェストなど）
        return type != Material.CHEST && 
               type != Material.FURNACE && 
               type != Material.CRAFTING_TABLE &&
               type != Material.ENCHANTING_TABLE;
    }
    
    /**
     * ブロックの硬度に基づく採掘時間を取得（秒）
     */
    public static double getMiningTime(Material material) {
        switch (material) {
            case STONE:
            case COBBLESTONE:
                return 2.0;
            case IRON_ORE:
            case GOLD_ORE:
                return 3.0;
            case DIAMOND_ORE:
                return 4.0;
            case OBSIDIAN:
                return 10.0;
            case DIRT:
            case SAND:
            case GRAVEL:
                return 0.5;
            case OAK_LOG:
            case BIRCH_LOG:
                return 1.5;
            default:
                return 1.0;
        }
    }
    
    /**
     * 材料からクラフト可能なアイテムを取得
     */
    public static List<Material> getCraftableItems(MinecraftAgent agent) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        List<Material> craftable = new ArrayList<>();
        
        // 基本的なクラフトレシピをチェック
        
        // 木材 -> 木の板
        if (inventory.getOrDefault(Material.OAK_LOG, 0) >= 1) {
            craftable.add(Material.OAK_PLANKS);
        }
        
        // 木の板 -> 棒
        if (inventory.getOrDefault(Material.OAK_PLANKS, 0) >= 2) {
            craftable.add(Material.STICK);
        }
        
        // 丸石 -> かまど
        if (inventory.getOrDefault(Material.COBBLESTONE, 0) >= 8) {
            craftable.add(Material.FURNACE);
        }
        
        // 木の板 -> 作業台
        if (inventory.getOrDefault(Material.OAK_PLANKS, 0) >= 4) {
            craftable.add(Material.CRAFTING_TABLE);
        }
        
        return craftable;
    }
    
    /**
     * アイテムをクラフト
     */
    public static boolean craftItem(MinecraftAgent agent, Material targetItem, int amount) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        
        switch (targetItem) {
            case OAK_PLANKS:
                if (removeItemFromInventory(agent, Material.OAK_LOG, 1)) {
                    addItemToInventory(agent, new ItemStack(Material.OAK_PLANKS, 4));
                    return true;
                }
                break;
                
            case STICK:
                if (removeItemFromInventory(agent, Material.OAK_PLANKS, 2)) {
                    addItemToInventory(agent, new ItemStack(Material.STICK, 4));
                    return true;
                }
                break;
                
            case CRAFTING_TABLE:
                if (removeItemFromInventory(agent, Material.OAK_PLANKS, 4)) {
                    addItemToInventory(agent, new ItemStack(Material.CRAFTING_TABLE, 1));
                    return true;
                }
                break;
                
            case FURNACE:
                if (removeItemFromInventory(agent, Material.COBBLESTONE, 8)) {
                    addItemToInventory(agent, new ItemStack(Material.FURNACE, 1));
                    return true;
                }
                break;
        }
        
        return false;
    }
    
    /**
     * 建築に適した材料を取得
     */
    public static List<Material> getBuildingMaterials(MinecraftAgent agent) {
        Map<Material, Integer> inventory = getAgentInventory(agent);
        List<Material> buildingMaterials = new ArrayList<>();
        
        Material[] materials = {
            Material.COBBLESTONE, Material.STONE, Material.OAK_PLANKS,
            Material.DIRT, Material.SAND, Material.GRAVEL,
            Material.BRICK, Material.STONE_BRICKS
        };
        
        for (Material material : materials) {
            if (inventory.getOrDefault(material, 0) > 0) {
                buildingMaterials.add(material);
            }
        }
        
        return buildingMaterials;
    }
    
    /**
     * ブロックを採掘
     */
    public static boolean mineBlock(MinecraftAgent agent, Block block) {
        if (!canMineBlock(block)) {
            agent.getLogger().debug("採掘不可能なブロック: " + block.getType() + " at " + block.getLocation());
            return false;
        }
        
        Material material = block.getType();
        Location blockLocation = block.getLocation();
        
        agent.getLogger().info("★ブロック採掘実行: " + material.name() + " at (" + 
                               blockLocation.getBlockX() + ", " + blockLocation.getBlockY() + ", " + blockLocation.getBlockZ() + ")");
        
        // ブロックを破壊してアイテムをインベントリに追加
        ItemStack drop = getBlockDrop(material);
        addItemToInventory(agent, drop);
        
        // ブロックをエアに変更
        block.setType(Material.AIR);
        
        agent.getLogger().info("★ブロック採掘完了: " + material.name() + " -> " + drop.getType() + " x" + drop.getAmount());
        return true;
    }
    
    /**
     * ブロックを設置
     */
    public static boolean placeBlock(MinecraftAgent agent, Location location, Material material) {
        if (!canPlaceBlock(location)) {
            return false;
        }
        
        // インベントリから材料を消費
        if (!removeItemFromInventory(agent, material, 1)) {
            return false;
        }
        
        // ブロックを設置
        Block block = location.getBlock();
        block.setType(material);
        
        agent.getLogger().debug("ブロックを設置しました: " + material.name());
        return true;
    }
    
    /**
     * ブロックを破壊した時のドロップアイテムを取得
     */
    private static ItemStack getBlockDrop(Material material) {
        switch (material) {
            case DIAMOND_ORE:
                return new ItemStack(Material.DIAMOND, 1);
            case IRON_ORE:
                return new ItemStack(Material.RAW_IRON, 1);
            case GOLD_ORE:
                return new ItemStack(Material.RAW_GOLD, 1);
            case COAL_ORE:
                return new ItemStack(Material.COAL, 1);
            case STONE:
                return new ItemStack(Material.COBBLESTONE, 1);
            case OAK_LOG:
                return new ItemStack(Material.OAK_LOG, 1);
            default:
                return new ItemStack(material, 1);
        }
    }
    
    /**
     * インベントリをクリア
     */
    public static void clearInventory(MinecraftAgent agent) {
        agentInventories.remove(agent.getAgentId());
        agent.getLogger().debug("インベントリをクリアしました");
    }
}