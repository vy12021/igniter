package io.github.trojan_gfw.igniter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.trojan_gfw.igniter.servers.data.ServerListDataManager;

/**
 * 自动模式管理器
 * 负责自动切换服务器、定时测试延迟、监控连接状态
 */
public class AutoModeManager {
    private static final String TAG = "AutoModeManager";
    private static final String PREF_NAME = "auto_mode_prefs";
    private static final String KEY_AUTO_MODE_ENABLED = "auto_mode_enabled";
    private static final String KEY_LAST_SELECTED_SERVER = "last_selected_server";
    
    // 定时测试间隔：30分钟
    private static final long TEST_INTERVAL_MINUTES = 30;
    // 连接超时检测间隔：30秒
    private static final long CONNECTION_CHECK_INTERVAL_SECONDS = 30;
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;
    
    private static AutoModeManager instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final Handler mainHandler;
    private final ScheduledExecutorService scheduler;
    
    private boolean autoModeEnabled = false;
    private String currentServerId = null;
    private int retryCount = 0;
    private ScheduledFuture<?> testSchedule;
    private ScheduledFuture<?> connectionCheckSchedule;
    private AutoModeListener listener;
    
    private AutoModeManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // 加载保存的状态
        loadState();
    }
    
    public static synchronized AutoModeManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoModeManager(context);
        }
        return instance;
    }
    
    public static synchronized AutoModeManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AutoModeManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    /**
     * 设置自动模式监听器
     */
    public void setListener(AutoModeListener listener) {
        this.listener = listener;
    }
    
    /**
     * 启用/禁用自动模式
     */
    public void setAutoModeEnabled(boolean enabled) {
        if (this.autoModeEnabled == enabled) {
            return;
        }
        
        this.autoModeEnabled = enabled;
        saveState();
        
        if (enabled) {
            startAutoMode();
        } else {
            stopAutoMode();
        }
        
        LogHelper.i(TAG, "Auto mode " + (enabled ? "enabled" : "disabled"));
        
        if (listener != null) {
            mainHandler.post(() -> listener.onAutoModeStateChanged(enabled));
        }
    }
    
    /**
     * 检查自动模式是否启用
     */
    public boolean isAutoModeEnabled() {
        return autoModeEnabled;
    }
    
    /**
     * 获取当前选中的服务器ID
     */
    @Nullable
    public String getCurrentServerId() {
        return currentServerId;
    }
    
    /**
     * 设置当前服务器（用户手动选择时调用）
     */
    public void setCurrentServer(@NonNull String serverId) {
        this.currentServerId = serverId;
        this.retryCount = 0; // 重置重试计数
        saveState();
        
        LogHelper.i(TAG, "Current server set to: " + serverId);
        
        if (listener != null) {
            mainHandler.post(() -> listener.onServerChanged(serverId));
        }
    }
    
    /**
     * 获取当前服务器的测试结果
     */
    @Nullable
    public TestResult getCurrentServerTestResult() {
        if (currentServerId == null) {
            return null;
        }
        return TestResultManager.getInstance().getTestResult(currentServerId);
    }
    
    /**
     * 报告连接失败（由ProxyService调用）
     */
    public void reportConnectionFailure() {
        if (!autoModeEnabled) {
            return;
        }
        
        LogHelper.w(TAG, "Connection failure reported for server: " + currentServerId);
        
        retryCount++;
        if (retryCount >= MAX_RETRY_COUNT) {
            // 达到最大重试次数，切换到下一个可用服务器
            switchToNextAvailableServer();
        } else {
            // 重试当前服务器
            LogHelper.i(TAG, "Retrying connection, attempt " + retryCount + "/" + MAX_RETRY_COUNT);
            if (listener != null) {
                mainHandler.post(() -> listener.onRetryConnection(currentServerId, retryCount));
            }
        }
    }
    
    /**
     * 报告连接成功（由ProxyService调用）
     */
    public void reportConnectionSuccess() {
        if (!autoModeEnabled) {
            return;
        }
        
        LogHelper.i(TAG, "Connection success reported for server: " + currentServerId);
        retryCount = 0; // 重置重试计数
        
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionSuccess(currentServerId));
        }
    }
    
    /**
     * 启动自动模式
     */
    private void startAutoMode() {
        // 启动定时测试
        startPeriodicTesting();
        
        // 启动连接状态检查
        startConnectionMonitoring();
        
        // 如果没有当前服务器，选择最佳服务器
        if (currentServerId == null) {
            selectBestServer();
        }
    }
    
    /**
     * 停止自动模式
     */
    private void stopAutoMode() {
        // 停止定时任务
        if (testSchedule != null) {
            testSchedule.cancel(false);
            testSchedule = null;
        }
        
        if (connectionCheckSchedule != null) {
            connectionCheckSchedule.cancel(false);
            connectionCheckSchedule = null;
        }
        
        LogHelper.i(TAG, "Auto mode stopped");
    }
    
    /**
     * 启动定时测试
     */
    private void startPeriodicTesting() {
        testSchedule = scheduler.scheduleAtFixedRate(
            this::performPeriodicTest,
            0, // 立即开始第一次测试
            TEST_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        LogHelper.i(TAG, "Periodic testing started (interval: " + TEST_INTERVAL_MINUTES + " minutes)");
    }
    
    /**
     * 启动连接监控
     */
    private void startConnectionMonitoring() {
        connectionCheckSchedule = scheduler.scheduleAtFixedRate(
            this::checkConnectionStatus,
            CONNECTION_CHECK_INTERVAL_SECONDS,
            CONNECTION_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        LogHelper.i(TAG, "Connection monitoring started (interval: " + CONNECTION_CHECK_INTERVAL_SECONDS + " seconds)");
    }
    
    /**
     * 执行定期测试
     */
    private void performPeriodicTest() {
        try {
            LogHelper.i(TAG, "Starting periodic server testing...");
            
            ServerListDataManager dataManager = new ServerListDataManager(
                Globals.getTrojanConfigListPath(), false, "", 0L);
            List<TrojanConfig> servers = dataManager.loadServerConfigList();
            
            if (servers.isEmpty()) {
                LogHelper.w(TAG, "No servers available for testing");
                return;
            }
            
            // 测试所有服务器
            for (TrojanConfig server : servers) {
                testServer(server);
            }
            
            LogHelper.i(TAG, "Periodic testing completed for " + servers.size() + " servers");
            
        } catch (Exception e) {
            LogHelper.e(TAG, "Error during periodic testing: " + e.getMessage());
        }
    }
    
    /**
     * 测试单个服务器
     */
    private void testServer(TrojanConfig server) {
        // 使用现有的ping测试功能
        ServerListDataManager dataManager = new ServerListDataManager(
            Globals.getTrojanConfigListPath(), false, "", 0L);
        
        dataManager.pingTrojanConfigServer(server, new ServerListDataManager.PingCallback() {
            @Override
            public void onSuccess(TrojanConfig config, com.stealthcopter.networktools.ping.PingStats pingStats) {
                float avgTime = pingStats.getAverageTimeTaken();
                TestResultManager.getInstance().saveTestResult(
                    config.getIdentifier(), true, (long)avgTime, "");
                
                LogHelper.d(TAG, "Server " + config.getIdentifier() + " test result: " + avgTime + "ms");
                
                // 如果这是当前服务器，通知监听器更新显示
                if (config.getIdentifier().equals(currentServerId) && listener != null) {
                    mainHandler.post(() -> listener.onServerTestCompleted(config.getIdentifier(), true, (long)avgTime));
                }
            }
            
            @Override
            public void onFailed(TrojanConfig config) {
                TestResultManager.getInstance().saveTestResult(
                    config.getIdentifier(), false, 0, "Ping failed");
                
                LogHelper.d(TAG, "Server " + config.getIdentifier() + " test failed");
                
                // 如果这是当前服务器，通知监听器更新显示
                if (config.getIdentifier().equals(currentServerId) && listener != null) {
                    mainHandler.post(() -> listener.onServerTestCompleted(config.getIdentifier(), false, 0));
                }
            }
        });
    }
    
    /**
     * 检查连接状态
     */
    private void checkConnectionStatus() {
        // 这里可以添加连接状态检查逻辑
        // 例如检查VPN是否仍然连接，网络是否可达等
        LogHelper.d(TAG, "Checking connection status...");
    }
    
    /**
     * 切换到下一个可用服务器
     */
    private void switchToNextAvailableServer() {
        try {
            String nextServer = findBestAvailableServer();
            if (nextServer != null && !nextServer.equals(currentServerId)) {
                LogHelper.i(TAG, "Switching from " + currentServerId + " to " + nextServer);
                
                String previousServer = currentServerId;
                setCurrentServer(nextServer);
                
                if (listener != null) {
                    mainHandler.post(() -> listener.onServerSwitched(previousServer, nextServer));
                }
            } else {
                LogHelper.w(TAG, "No alternative server available");
                if (listener != null) {
                    mainHandler.post(() -> listener.onNoAlternativeServer());
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error switching server: " + e.getMessage());
        }
    }
    
    /**
     * 选择最佳服务器
     */
    private void selectBestServer() {
        String bestServer = findBestAvailableServer();
        if (bestServer != null) {
            setCurrentServer(bestServer);
        }
    }
    
    /**
     * 查找最佳可用服务器
     */
    @Nullable
    private String findBestAvailableServer() {
        try {
            ServerListDataManager dataManager = new ServerListDataManager(
                Globals.getTrojanConfigListPath(), false, "", 0L);
            List<TrojanConfig> servers = dataManager.loadServerConfigList();
            
            TrojanConfig bestServer = null;
            long bestDelay = Long.MAX_VALUE;
            
            for (TrojanConfig server : servers) {
                // 跳过当前失败的服务器
                if (server.getIdentifier().equals(currentServerId)) {
                    continue;
                }
                
                TestResult testResult = TestResultManager.getInstance().getTestResult(server.getIdentifier());
                if (testResult != null && testResult.isConnected() && testResult.isValid()) {
                    if (testResult.getDelay() < bestDelay) {
                        bestDelay = testResult.getDelay();
                        bestServer = server;
                    }
                }
            }
            
            return bestServer != null ? bestServer.getIdentifier() : null;
            
        } catch (Exception e) {
            LogHelper.e(TAG, "Error finding best server: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 保存状态到SharedPreferences
     */
    private void saveState() {
        preferences.edit()
            .putBoolean(KEY_AUTO_MODE_ENABLED, autoModeEnabled)
            .putString(KEY_LAST_SELECTED_SERVER, currentServerId)
            .apply();
    }
    
    /**
     * 从SharedPreferences加载状态
     */
    private void loadState() {
        autoModeEnabled = preferences.getBoolean(KEY_AUTO_MODE_ENABLED, false);
        currentServerId = preferences.getString(KEY_LAST_SELECTED_SERVER, null);
        
        LogHelper.i(TAG, "Loaded state: autoMode=" + autoModeEnabled + ", currentServer=" + currentServerId);
    }
    
    /**
     * 清理资源
     */
    public void destroy() {
        stopAutoMode();
        scheduler.shutdown();
    }
    
    /**
     * 自动模式监听器接口
     */
    public interface AutoModeListener {
        /**
         * 自动模式状态改变
         */
        void onAutoModeStateChanged(boolean enabled);
        
        /**
         * 服务器改变
         */
        void onServerChanged(String serverId);
        
        /**
         * 服务器切换
         */
        void onServerSwitched(String fromServerId, String toServerId);
        
        /**
         * 连接成功
         */
        void onConnectionSuccess(String serverId);
        
        /**
         * 重试连接
         */
        void onRetryConnection(String serverId, int retryCount);
        
        /**
         * 没有可用的替代服务器
         */
        void onNoAlternativeServer();
        
        /**
         * 服务器开始测试
         */
        void onServerTesting(String serverId);
        
        /**
         * 服务器测试完成
         */
        void onServerTestCompleted(String serverId, boolean success, long delay);
    }
}