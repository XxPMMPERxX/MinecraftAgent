package com.minecraftagent.utils;

import com.minecraftagent.MinecraftAgentPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ログ管理クラス
 */
public class Logger {
    
    private final MinecraftAgentPlugin plugin;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File logFile;
    private final boolean fileLogging;
    private final boolean consoleLogging;
    private final LogLevel level;
    
    public enum LogLevel {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);
        
        private final int value;
        
        LogLevel(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    public Logger(MinecraftAgentPlugin plugin) {
        this.plugin = plugin;
        
        // 設定から読み込み（初期化時は仮の値を使用）
        this.fileLogging = true;
        this.consoleLogging = true;
        this.level = LogLevel.INFO;
        
        // ログファイル設定
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        this.logFile = new File(logDir, "agent.log");
    }
    
    /**
     * DEBUGレベルでログ出力
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }
    
    /**
     * INFOレベルでログ出力
     */
    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }
    
    /**
     * WARNレベルでログ出力
     */
    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }
    
    /**
     * ERRORレベルでログ出力
     */
    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }
    
    /**
     * ERRORレベルでログ出力（例外付き）
     */
    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }
    
    /**
     * ログ出力メイン処理
     */
    private void log(LogLevel logLevel, String message, Throwable throwable) {
        if (logLevel.getValue() < this.level.getValue()) {
            return;
        }
        
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = String.format("[%s] [%s] %s", timestamp, logLevel.name(), message);
        
        // コンソール出力
        if (consoleLogging) {
            switch (logLevel) {
                case DEBUG:
                case INFO:
                    Bukkit.getLogger().info("[MinecraftAgent] " + message);
                    break;
                case WARN:
                    Bukkit.getLogger().warning("[MinecraftAgent] " + message);
                    break;
                case ERROR:
                    Bukkit.getLogger().severe("[MinecraftAgent] " + message);
                    if (throwable != null) {
                        throwable.printStackTrace();
                    }
                    break;
            }
        }
        
        // ファイル出力
        if (fileLogging) {
            writeToFile(logMessage, throwable);
        }
    }
    
    /**
     * ファイルにログを書き込み
     */
    private void writeToFile(String message, Throwable throwable) {
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            
            pw.println(message);
            
            if (throwable != null) {
                throwable.printStackTrace(pw);
            }
            
        } catch (IOException e) {
            Bukkit.getLogger().severe("[MinecraftAgent] ログファイルの書き込みに失敗しました: " + e.getMessage());
        }
    }
}