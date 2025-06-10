package com.minecraftagent.ai;

import com.minecraftagent.agent.MinecraftAgent;
import com.minecraftagent.behaviors.BaseBehavior;
import com.minecraftagent.behaviors.SurvivalBehavior;
import com.minecraftagent.behaviors.ResourceGatheringBehavior;
import com.minecraftagent.behaviors.ExplorationBehavior;
import com.minecraftagent.behaviors.BuildingBehavior;
import com.minecraftagent.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * エージェントの行動管理システム
 * 優先度ベースで行動を選択・実行
 */
public class BehaviorManager {
    
    private final MinecraftAgent agent;
    private final Logger logger;
    private final List<BaseBehavior> behaviors;
    
    private BaseBehavior currentBehavior;
    private long lastBehaviorCheck;
    
    public BehaviorManager(MinecraftAgent agent) {
        this.agent = agent;
        this.logger = agent.getLogger();
        this.behaviors = new ArrayList<>();
        this.lastBehaviorCheck = System.currentTimeMillis();
    }
    
    /**
     * 行動管理システムを初期化
     */
    public void initialize() {
        logger.debug("行動管理システムを初期化しています...");
        
        // 設定に基づいて行動を追加
        var config = agent.getPlugin().getConfigManager();
        
        // 生存行動
        if (config.isSurvivalEnabled()) {
            behaviors.add(new SurvivalBehavior(agent, config.getSurvivalPriority()));
        }
        
        // 資源収集行動
        if (config.isResourceGatheringEnabled()) {
            behaviors.add(new ResourceGatheringBehavior(agent, config.getResourceGatheringPriority()));
        }
        
        // 探索行動
        if (config.isExplorationEnabled()) {
            behaviors.add(new ExplorationBehavior(agent, config.getExplorationPriority()));
        }
        
        // 建築行動
        if (config.isBuildingEnabled()) {
            behaviors.add(new BuildingBehavior(agent, config.getBuildingPriority()));
        }
        
        // 全ての行動を初期化
        for (BaseBehavior behavior : behaviors) {
            try {
                behavior.initialize();
                logger.debug("行動を初期化しました: " + behavior.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("行動の初期化に失敗しました: " + behavior.getClass().getSimpleName(), e);
            }
        }
        
        logger.info("行動管理システムが初期化されました (" + behaviors.size() + "個の行動)");
    }
    
    /**
     * 行動管理システムを更新
     * 最適な行動を選択し実行
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        
        // 1秒に1回だけ行動選択をチェック
        if (currentTime - lastBehaviorCheck < 1000) {
            // 現在の行動を継続実行
            if (currentBehavior != null && currentBehavior.isActive()) {
                currentBehavior.update();
            }
            return;
        }
        
        lastBehaviorCheck = currentTime;
        
        // デバッグログ：BehaviorManagerが動作していることを確認
        logger.debug("BehaviorManager.update() 実行中 - 利用可能な行動数: " + behaviors.size());
        
        try {
            // 実行可能な行動を取得し、優先度でソート
            List<BaseBehavior> availableBehaviors = behaviors.stream()
                    .filter(behavior -> {
                        boolean canExecute = behavior.canExecute();
                        logger.debug("行動チェック: " + behavior.getClass().getSimpleName() + 
                                   " - 実行可能: " + canExecute);
                        return canExecute;
                    })
                    .sorted(Comparator.comparingInt(BaseBehavior::getPriority).reversed())
                    .toList();
            
            logger.debug("実行可能な行動数: " + availableBehaviors.size());
            
            if (availableBehaviors.isEmpty()) {
                // 実行可能な行動がない場合は待機
                logger.debug("実行可能な行動がありません - 待機中");
                if (currentBehavior != null) {
                    stopCurrentBehavior();
                }
                return;
            }
            
            // 最高優先度の行動を選択
            BaseBehavior selectedBehavior = availableBehaviors.get(0);
            logger.debug("選択された行動: " + selectedBehavior.getClass().getSimpleName() + 
                        " (優先度: " + selectedBehavior.getPriority() + ")");
            
            // 行動切り替えが必要かチェック
            if (currentBehavior != selectedBehavior) {
                switchBehavior(selectedBehavior);
            }
            
            // 現在の行動を実行
            if (currentBehavior != null && currentBehavior.isActive()) {
                currentBehavior.update();
            }
            
        } catch (Exception e) {
            logger.error("行動管理システムの更新中にエラーが発生しました", e);
        }
    }
    
    /**
     * 行動を切り替え
     */
    private void switchBehavior(BaseBehavior newBehavior) {
        // 現在の行動を停止
        if (currentBehavior != null) {
            stopCurrentBehavior();
        }
        
        // 新しい行動を開始
        try {
            newBehavior.start();
            this.currentBehavior = newBehavior;
            logger.debug("行動を切り替えました: " + newBehavior.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("行動の開始に失敗しました: " + newBehavior.getClass().getSimpleName(), e);
            this.currentBehavior = null;
        }
    }
    
    /**
     * 現在の行動を停止
     */
    private void stopCurrentBehavior() {
        if (currentBehavior != null) {
            try {
                currentBehavior.stop();
                logger.debug("行動を停止しました: " + currentBehavior.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("行動の停止に失敗しました: " + currentBehavior.getClass().getSimpleName(), e);
            } finally {
                currentBehavior = null;
            }
        }
    }
    
    /**
     * 特定の行動を強制実行
     */
    public void forceBehavior(Class<? extends BaseBehavior> behaviorClass) {
        BaseBehavior behavior = behaviors.stream()
                .filter(b -> b.getClass().equals(behaviorClass))
                .findFirst()
                .orElse(null);
        
        if (behavior != null) {
            switchBehavior(behavior);
            logger.info("行動を強制実行しました: " + behaviorClass.getSimpleName());
        } else {
            logger.warn("指定された行動が見つかりません: " + behaviorClass.getSimpleName());
        }
    }
    
    /**
     * 行動管理システムをシャットダウン
     */
    public void shutdown() {
        logger.debug("行動管理システムをシャットダウンしています...");
        
        // 現在の行動を停止
        stopCurrentBehavior();
        
        // 全ての行動をシャットダウン
        for (BaseBehavior behavior : behaviors) {
            try {
                behavior.shutdown();
            } catch (Exception e) {
                logger.error("行動のシャットダウンに失敗しました: " + behavior.getClass().getSimpleName(), e);
            }
        }
        
        behaviors.clear();
        logger.debug("行動管理システムがシャットダウンされました");
    }
    
    /**
     * 現在の行動を取得
     */
    public BaseBehavior getCurrentBehavior() {
        return currentBehavior;
    }
    
    /**
     * 行動リストを取得
     */
    public List<BaseBehavior> getBehaviors() {
        return new ArrayList<>(behaviors);
    }
    
    /**
     * 行動統計を取得
     */
    public String getStatistics() {
        int totalBehaviors = behaviors.size();
        int activeBehaviors = (int) behaviors.stream().filter(BaseBehavior::isActive).count();
        String currentBehaviorName = currentBehavior != null ? 
                currentBehavior.getClass().getSimpleName() : "なし";
        
        return String.format(
                "行動統計: 総数=%d, アクティブ=%d, 現在=%s",
                totalBehaviors, activeBehaviors, currentBehaviorName
        );
    }
}