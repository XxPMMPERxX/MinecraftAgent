package com.minecraftagent.listeners;

import com.minecraftagent.MinecraftAgentPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * プレイヤーイベントリスナー
 */
public class PlayerListener implements Listener {
    
    private final MinecraftAgentPlugin plugin;
    
    public PlayerListener(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // プレイヤー参加時の処理
    }
}