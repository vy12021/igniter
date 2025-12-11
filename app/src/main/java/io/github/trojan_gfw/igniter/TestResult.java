package io.github.trojan_gfw.igniter;

import androidx.annotation.NonNull;

/**
 * 服务器测试结果数据类
 */
public class TestResult {
    // 测试结果有效期：30分钟（与自动模式测试间隔保持一致）
    public static final long RESULT_VALIDITY_DURATION = 30 * 60 * 1000; // 30分钟
    
    private String serverIdentifier; // 服务器标识符 (host:port)
    private long testTime;          // 测试时间戳
    private boolean connected;      // 是否连接成功
    private long delay;            // 延迟时间(ms)
    private String error;          // 错误信息

    public TestResult() {
    }

    public TestResult(String serverIdentifier, long testTime, boolean connected, long delay, String error) {
        this.serverIdentifier = serverIdentifier;
        this.testTime = testTime;
        this.connected = connected;
        this.delay = delay;
        this.error = error;
    }

    public String getServerIdentifier() {
        return serverIdentifier;
    }

    public void setServerIdentifier(String serverIdentifier) {
        this.serverIdentifier = serverIdentifier;
    }

    public long getTestTime() {
        return testTime;
    }

    public void setTestTime(long testTime) {
        this.testTime = testTime;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * 获取格式化的测试结果用于显示
     * @deprecated Use getFormattedResult(Context) for internationalization
     */
    @Deprecated
    public String getFormattedResult() {
        if (testTime == 0) {
            return "Not tested";
        }
        
        if (connected) {
            return delay + "ms";
        } else {
            return "Connection failed";
        }
    }
    
    /**
     * 获取格式化的测试结果用于显示（国际化版本）
     */
    public String getFormattedResult(android.content.Context context) {
        if (testTime == 0) {
            return context.getString(R.string.not_tested);
        }
        
        if (connected) {
            return delay + "ms";
        } else {
            return context.getString(R.string.connection_failed_text);
        }
    }

    /**
     * 检查测试结果是否有效且不太旧（超过30分钟）
     */
    public boolean isValid() {
        if (testTime == 0) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        return (currentTime - testTime) < RESULT_VALIDITY_DURATION;
    }

    /**
     * 获取测试结果的状态描述
     * @deprecated Use getStatusDescription(Context) for internationalization
     */
    @Deprecated
    public String getStatusDescription() {
        if (testTime == 0) {
            return "Not tested";
        }
        
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - testTime;
        
        if (timeDiff > RESULT_VALIDITY_DURATION) { // 超过30分钟
            return "Result expired";
        }
        
        if (connected) {
            if (delay < 100) {
                return "Good connection";
            } else if (delay < 500) {
                return "Normal connection";
            } else {
                return "Slow connection";
            }
        } else {
            return "Connection failed";
        }
    }
    
    /**
     * 获取测试结果的状态描述（国际化版本）
     */
    public String getStatusDescription(android.content.Context context) {
        if (testTime == 0) {
            return context.getString(R.string.not_tested);
        }
        
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - testTime;
        
        if (timeDiff > RESULT_VALIDITY_DURATION) { // 超过30分钟
            return context.getString(R.string.result_expired);
        }
        
        if (connected) {
            if (delay < 100) {
                return context.getString(R.string.connection_good);
            } else if (delay < 500) {
                return context.getString(R.string.connection_normal);
            } else {
                return context.getString(R.string.connection_slow);
            }
        } else {
            return context.getString(R.string.connection_failed_text);
        }
    }
    
    /**
     * 获取测试时间的描述（多久之前测试的）
     */
    public String getTimeAgoDescription(android.content.Context context) {
        if (testTime == 0) {
            return "";
        }
        
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - testTime;
        
        if (timeDiff < 60 * 1000) { // 小于1分钟
            return context.getString(R.string.just_now);
        } else if (timeDiff < 60 * 60 * 1000) { // 小于1小时
            long minutes = timeDiff / (60 * 1000);
            return context.getString(R.string.minutes_ago, minutes);
        } else { // 大于1小时
            long hours = timeDiff / (60 * 60 * 1000);
            return context.getString(R.string.hours_ago, hours);
        }
    }
    
    /**
     * 获取带时间信息的格式化延迟结果
     */
    public String getFormattedResultWithTime(android.content.Context context) {
        if (testTime == 0) {
            return context.getString(R.string.not_tested);
        }
        
        String result;
        if (connected) {
            result = delay + "ms";
        } else {
            result = context.getString(R.string.connection_failed_text);
        }
        
        String timeAgo = getTimeAgoDescription(context);
        if (!timeAgo.isEmpty()) {
            return result + " (" + timeAgo + ")";
        } else {
            return result;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "TestResult{" +
                "serverIdentifier='" + serverIdentifier + '\'' +
                ", testTime=" + testTime +
                ", connected=" + connected +
                ", delay=" + delay +
                ", error='" + error + '\'' +
                '}';
    }
}