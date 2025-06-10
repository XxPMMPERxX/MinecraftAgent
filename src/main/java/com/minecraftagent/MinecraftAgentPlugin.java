package com.minecraftagent;

import org.bukkit.plugin.java.JavaPlugin;

public class MinecraftAgentPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        getLogger().info("MinecraftAgent plugin enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("MinecraftAgent plugin disabled!");
    }
}