package com.minecraftagent.commands;

import com.minecraftagent.MinecraftAgentPlugin;
import com.minecraftagent.agent.MinecraftAgent;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * エージェント管理コマンド
 */
public class AgentCommand implements CommandExecutor {
    
    private final MinecraftAgentPlugin plugin;
    
    public AgentCommand(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "spawn":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用方法: /agent spawn <エージェント名>");
                    return true;
                }
                spawnAgent(player, args[1]);
                break;
                
            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用方法: /agent remove <エージェント名>");
                    return true;
                }
                removeAgent(player, args[1]);
                break;
                
            case "list":
                listAgents(player);
                break;
                
            case "status":
                showStatus(player);
                break;
                
            default:
                showHelp(player);
                break;
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== MinecraftAgent コマンド ===");
        player.sendMessage(ChatColor.YELLOW + "/agent spawn <名前> - エージェントをスポーン");
        player.sendMessage(ChatColor.YELLOW + "/agent remove <名前> - エージェントを削除");
        player.sendMessage(ChatColor.YELLOW + "/agent list - エージェント一覧");
        player.sendMessage(ChatColor.YELLOW + "/agent status - システム状態");
    }
    
    private void spawnAgent(Player player, String agentName) {
        MinecraftAgent agent = plugin.getAgentManager().createAgent(agentName, player.getLocation());
        if (agent != null) {
            player.sendMessage(ChatColor.GREEN + "エージェント '" + agentName + "' をスポーンしました");
        } else {
            player.sendMessage(ChatColor.RED + "エージェントのスポーンに失敗しました");
        }
    }
    
    private void removeAgent(Player player, String agentName) {
        if (plugin.getAgentManager().removeAgent(agentName)) {
            player.sendMessage(ChatColor.GREEN + "エージェント '" + agentName + "' を削除しました");
        } else {
            player.sendMessage(ChatColor.RED + "エージェント '" + agentName + "' が見つかりません");
        }
    }
    
    private void listAgents(Player player) {
        var agents = plugin.getAgentManager().getAllAgents();
        if (agents.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "アクティブなエージェントはありません");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== エージェント一覧 ===");
        for (MinecraftAgent agent : agents) {
            String status = agent.isActive() ? ChatColor.GREEN + "アクティブ" : ChatColor.RED + "非アクティブ";
            player.sendMessage(ChatColor.YELLOW + agent.getAgentName() + " - " + status);
        }
    }
    
    private void showStatus(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== システム状態 ===");
        player.sendMessage(ChatColor.YELLOW + plugin.getAgentManager().getStatistics());
    }
}