package com.minecraftagent.behaviors;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.utils.Logger;

/**
 * 全ての行動の基底クラス
 */
public abstract class BaseBehavior {
    
    protected final MinecraftAgent agent;
    protected final Logger logger;
    protected final int priority;
    
    private boolean isActive;
    private boolean isInitialized;
    private long startTime;
    private long lastUpdate;
    
    public BaseBehavior(MinecraftAgent agent, int priority) {
        this.agent = agent;
        this.logger = agent.getLogger();
        this.priority = priority;
        this.isActive = false;
        this.isInitialized = false;
    }
    
    /**
     * 行動を初期化
     */
    public void initialize() {
        if (isInitialized) {
            return;
        }
        
        try {
            onInitialize();
            this.isInitialized = true;
            logger.debug(getClass().getSimpleName() + " を初期化しました");
        } catch (Exception e) {
            logger.error(getClass().getSimpleName() + " の初期化に失敗しました", e);
            throw e;
        }
    }
    
    /**
     * 行動を開始
     */
    public void start() {
        if (!isInitialized) {
            throw new IllegalStateException("行動が初期化されていません: " + getClass().getSimpleName());
        }
        
        if (isActive) {
            return;
        }
        
        try {
            this.isActive = true;
            this.startTime = System.currentTimeMillis();
            this.lastUpdate = startTime;
            
            onStart();
            logger.debug(getClass().getSimpleName() + " を開始しました");
        } catch (Exception e) {
            this.isActive = false;
            logger.error(getClass().getSimpleName() + " の開始に失敗しました", e);
            throw e;
        }
    }
    
    /**
     * 行動を更新
     */
    public void update() {
        if (!isActive || !canExecute()) {
            return;
        }
        
        try {
            this.lastUpdate = System.currentTimeMillis();
            onUpdate();
        } catch (Exception e) {
            logger.error(getClass().getSimpleName() + " の更新中にエラーが発生しました", e);
        }
    }
    
    /**
     * 行動を停止
     */
    public void stop() {
        if (!isActive) {
            return;
        }
        
        try {
            this.isActive = false;
            onStop();
            logger.debug(getClass().getSimpleName() + " を停止しました");
        } catch (Exception e) {
            logger.error(getClass().getSimpleName() + " の停止中にエラーが発生しました", e);
        }
    }
    
    /**
     * 行動をシャットダウン
     */
    public void shutdown() {
        if (isActive) {
            stop();
        }
        
        try {
            onShutdown();
            this.isInitialized = false;
            logger.debug(getClass().getSimpleName() + " をシャットダウンしました");
        } catch (Exception e) {
            logger.error(getClass().getSimpleName() + " のシャットダウン中にエラーが発生しました", e);
        }
    }
    
    /**
     * この行動が実行可能かどうかを判定
     * @return 実行可能な場合true
     */
    public abstract boolean canExecute();
    
    /**
     * 初期化時に呼ばれる処理（サブクラスでオーバーライド）
     */
    protected void onInitialize() {
        // デフォルトは何もしない
    }
    
    /**
     * 開始時に呼ばれる処理（サブクラスでオーバーライド）
     */
    protected void onStart() {
        // デフォルトは何もしない
    }
    
    /**
     * 更新時に呼ばれる処理（サブクラスで実装必須）
     */
    protected abstract void onUpdate();
    
    /**
     * 停止時に呼ばれる処理（サブクラスでオーバーライド）
     */
    protected void onStop() {
        // デフォルトは何もしない
    }
    
    /**
     * シャットダウン時に呼ばれる処理（サブクラスでオーバーライド）
     */
    protected void onShutdown() {
        // デフォルトは何もしない
    }
    
    // Getter methods
    public int getPriority() { return priority; }
    public boolean isActive() { return isActive; }
    public boolean isInitialized() { return isInitialized; }
    public long getStartTime() { return startTime; }
    public long getLastUpdate() { return lastUpdate; }
    
    /**
     * 実行時間を取得（ミリ秒）
     */
    public long getRunningTime() {
        return isActive ? System.currentTimeMillis() - startTime : 0;
    }
    
    /**
     * 最後の更新からの経過時間を取得（ミリ秒）
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdate;
    }
    
    /**
     * エージェントが有効かどうかをチェック
     */
    protected boolean isAgentValid() {
        return agent != null && 
               agent.isActive() && 
               agent.getEntity() != null && 
               !agent.getEntity().isDead();
    }
}