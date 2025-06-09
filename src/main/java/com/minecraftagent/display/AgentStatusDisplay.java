package com.minecraftagent.display;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.behaviors.BaseBehavior;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * エージェントの状況をリアルタイム表示するクラス
 */
public class AgentStatusDisplay {
    
    private final MinecraftAgent agent;
    private BukkitTask displayTask;
    private String currentStatus;
    private String currentAction;
    private String currentTarget;
    private long lastUpdate;
    
    // 行動の日本語表示マップ
    private static final Map<String, String> BEHAVIOR_NAMES = new HashMap<>();
    static {
        BEHAVIOR_NAMES.put("SurvivalBehavior", "🔥 生存");
        BEHAVIOR_NAMES.put("ResourceGatheringBehavior", "⛏️ 採掘");
        BEHAVIOR_NAMES.put("BuildingBehavior", "🏗️ 建築");
        BEHAVIOR_NAMES.put("ExplorationBehavior", "🧭 探索");
    }
    
    public AgentStatusDisplay(MinecraftAgent agent) {
        this.agent = agent;
        this.currentStatus = "待機中";
        this.currentAction = "";
        this.currentTarget = "";
        this.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * ステータス表示を開始
     */
    public void startDisplay() {
        if (displayTask != null && !displayTask.isCancelled()) {
            displayTask.cancel();
        }
        
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateDisplay();
            }
        }.runTaskTimer(agent.getPlugin(), 0L, 20L); // 1秒ごとに更新
    }
    
    /**
     * ステータス表示を停止
     */
    public void stopDisplay() {
        if (displayTask != null && !displayTask.isCancelled()) {
            displayTask.cancel();
        }
    }
    
    /**
     * 現在の行動を設定
     */
    public void setBehavior(String behaviorName) {
        String displayName = BEHAVIOR_NAMES.getOrDefault(behaviorName, behaviorName);
        this.currentStatus = displayName;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * 現在のアクションを設定
     */
    public void setAction(String action) {
        this.currentAction = action;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * 現在のターゲットを設定
     */
    public void setTarget(String target) {
        this.currentTarget = target;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * カスタムステータスメッセージを設定
     */
    public void setCustomStatus(String status) {
        this.currentStatus = status;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * 表示を更新
     */
    private void updateDisplay() {
        LivingEntity entity = agent.getEntity();
        if (entity == null || entity.isDead()) {
            return;
        }
        
        // エンティティ名を更新
        updateEntityName(entity);
        
        // 近くのプレイヤーにアクションバーメッセージを送信
        sendActionBarToNearbyPlayers(entity);
    }
    
    /**
     * エンティティの名前を更新
     */
    private void updateEntityName(LivingEntity entity) {
        String baseName = "§a[Agent] " + agent.getAgentName();
        String statusLine = "";
        
        if (!currentStatus.isEmpty()) {
            statusLine = "§7" + currentStatus;
        }
        
        if (!currentAction.isEmpty()) {
            if (!statusLine.isEmpty()) {
                statusLine += " §8| ";
            }
            statusLine += "§e" + currentAction;
        }
        
        String fullName = baseName;
        if (!statusLine.isEmpty()) {
            fullName += "\n" + statusLine;
        }
        
        entity.setCustomName(fullName);
        entity.setCustomNameVisible(true);
    }
    
    /**
     * 近くのプレイヤーにアクションバーメッセージを送信
     */
    private void sendActionBarToNearbyPlayers(LivingEntity entity) {
        if (currentTarget.isEmpty()) {
            return;
        }
        
        String message = buildActionBarMessage();
        
        // 半径20ブロック以内のプレイヤーに送信
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(entity.getWorld()) && 
                player.getLocation().distance(entity.getLocation()) <= 20) {
                
                // チャットにメッセージを送信（ActionBarの代替）
                player.sendMessage("[エージェント] " + message);
            }
        }
    }
    
    /**
     * アクションバーメッセージを構築
     */
    private String buildActionBarMessage() {
        StringBuilder message = new StringBuilder();
        
        message.append("§a").append(agent.getAgentName()).append(" §8| ");
        message.append("§7").append(currentStatus);
        
        if (!currentAction.isEmpty()) {
            message.append(" §8- §e").append(currentAction);
        }
        
        if (!currentTarget.isEmpty()) {
            message.append(" §8→ §f").append(currentTarget);
        }
        
        return message.toString();
    }
    
    /**
     * 詳細ステータスをチャットに送信
     */
    public void sendDetailedStatus(Player player) {
        player.sendMessage("§6=== " + agent.getAgentName() + " 詳細ステータス ===");
        player.sendMessage("§7現在の行動: §f" + currentStatus);
        
        if (!currentAction.isEmpty()) {
            player.sendMessage("§7実行中: §e" + currentAction);
        }
        
        if (!currentTarget.isEmpty()) {
            player.sendMessage("§7ターゲット: §f" + currentTarget);
        }
        
        // エージェントの状態情報
        player.sendMessage("§7状態: §f" + agent.getState());
        player.sendMessage("§7体力: §c" + String.format("%.1f", agent.getHealth()) + "/20");
        player.sendMessage("§7満腹度: §6" + agent.getFoodLevel() + "/20");
        
        // 現在の行動情報
        BaseBehavior currentBehavior = agent.getBehaviorManager().getCurrentBehavior();
        if (currentBehavior != null) {
            player.sendMessage("§7優先度: §b" + currentBehavior.getPriority());
            player.sendMessage("§7アクティブ: §a" + (currentBehavior.isActive() ? "はい" : "いいえ"));
        }
        
        // 位置情報
        LivingEntity entity = agent.getEntity();
        if (entity != null) {
            org.bukkit.Location loc = entity.getLocation();
            player.sendMessage("§7位置: §f" + 
                String.format("X:%.1f Y:%.1f Z:%.1f", loc.getX(), loc.getY(), loc.getZ()));
        }
        
        // 最終更新時間
        long timeSinceUpdate = (System.currentTimeMillis() - lastUpdate) / 1000;
        player.sendMessage("§7最終更新: §f" + timeSinceUpdate + "秒前");
    }
    
    /**
     * ステータス履歴をチャットに送信
     */
    public void sendStatusHistory(Player player) {
        // 簡単な履歴表示（実装可能なら詳細化）
        player.sendMessage("§6=== " + agent.getAgentName() + " 行動履歴 ===");
        player.sendMessage("§7- エージェント作成");
        player.sendMessage("§7- 行動システム初期化");
        player.sendMessage("§7- " + currentStatus + " 開始");
        
        if (!currentAction.isEmpty()) {
            player.sendMessage("§7- " + currentAction + " 実行");
        }
    }
    
    /**
     * 緊急メッセージを送信
     */
    public void sendEmergencyMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(agent.getEntity().getWorld())) {
                player.sendMessage("§c[緊急] §e" + agent.getAgentName() + "§r: " + message);
            }
        }
    }
    
    /**
     * 成功メッセージを送信
     */
    public void sendSuccessMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(agent.getEntity().getWorld())) {
                player.sendMessage("§a[成功] §e" + agent.getAgentName() + "§r: " + message);
            }
        }
    }
    
    /**
     * 進行状況メッセージを送信
     */
    public void sendProgressMessage(String action, int progress, int total) {
        String progressBar = createProgressBar(progress, total, 10);
        String message = String.format("§e%s§r: %s §7(%d/%d)", 
                                       agent.getAgentName(), progressBar, progress, total);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(agent.getEntity().getWorld()) && 
                player.getLocation().distance(agent.getEntity().getLocation()) <= 30) {
                player.sendMessage("[進行状況] " + message);
            }
        }
    }
    
    /**
     * プログレスバーを作成
     */
    private String createProgressBar(int progress, int total, int length) {
        if (total <= 0) return "§7[----------]";
        
        int filled = (int) ((double) progress / total * length);
        StringBuilder bar = new StringBuilder("§a[");
        
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("§a█");
            } else {
                bar.append("§7-");
            }
        }
        
        bar.append("§a]");
        return bar.toString();
    }
}