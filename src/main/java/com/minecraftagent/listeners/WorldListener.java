package com.minecraftagent.listeners;

import com.minecraftagent.MinecraftAgentPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * ワールドイベントリスナー
 */
public class WorldListener implements Listener {
    
    private final MinecraftAgentPlugin plugin;
    
    public WorldListener(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // チャンク読み込み時の処理
    }
}