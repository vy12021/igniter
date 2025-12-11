package io.github.trojan_gfw.igniter;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 测试结果管理器
 * 负责保存和加载服务器测试结果到独立的JSON文件
 */
public class TestResultManager {
    private static final String TAG = "TestResultManager";
    private static TestResultManager instance;
    private final String testResultFilePath;
    private final Map<String, TestResult> testResults = new HashMap<>();

    private TestResultManager(String filePath) {
        this.testResultFilePath = filePath;
        loadTestResults();
    }

    public static synchronized TestResultManager getInstance() {
        if (instance == null) {
            String filePath = getTestResultsFilePath();
            instance = new TestResultManager(filePath);
        }
        return instance;
    }

    /**
     * 获取测试结果文件路径
     */
    private static String getTestResultsFilePath() {
        // 使用与其他配置文件相同的目录
        return PathHelper.combine(getFilesDir(), "test_results.json");
    }

    /**
     * 获取应用文件目录（通过反射获取Globals中的filesDir）
     */
    private static String getFilesDir() {
        try {
            java.lang.reflect.Field field = Globals.class.getDeclaredField("filesDir");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (Exception e) {
            LogHelper.e("TestResultManager", "Failed to get filesDir, using fallback");
            // 回退方案：使用已知的配置文件路径推导
            String configPath = Globals.getTrojanConfigPath();
            return new File(configPath).getParent();
        }
    }

    /**
     * 保存测试结果
     */
    public synchronized void saveTestResult(@NonNull String serverIdentifier, boolean connected, long delay, @Nullable String error) {
        TestResult result = new TestResult(
            serverIdentifier,
            System.currentTimeMillis(),
            connected,
            delay,
            error != null ? error : ""
        );
        
        testResults.put(serverIdentifier, result);
        saveTestResultsToFile();
        
        LogHelper.i(TAG, "Saved test result for " + serverIdentifier + ": " + 
                   (connected ? delay + "ms" : "failed"));
    }

    /**
     * 获取测试结果
     */
    @Nullable
    public synchronized TestResult getTestResult(@NonNull String serverIdentifier) {
        return testResults.get(serverIdentifier);
    }

    /**
     * 获取格式化的测试结果字符串
     * @deprecated Use getFormattedTestResult(String, Context) for internationalization
     */
    @Deprecated
    @NonNull
    public synchronized String getFormattedTestResult(@NonNull String serverIdentifier) {
        TestResult result = testResults.get(serverIdentifier);
        if (result == null) {
            return "Not tested";
        }
        return result.getFormattedResult();
    }
    
    /**
     * 获取格式化的测试结果字符串（国际化版本）
     */
    @NonNull
    public synchronized String getFormattedTestResult(@NonNull String serverIdentifier, android.content.Context context) {
        TestResult result = testResults.get(serverIdentifier);
        if (result == null) {
            return context.getString(R.string.not_tested);
        }
        return result.getFormattedResult(context);
    }

    /**
     * 检查是否有有效的测试结果
     */
    public synchronized boolean hasValidTestResult(@NonNull String serverIdentifier) {
        TestResult result = testResults.get(serverIdentifier);
        return result != null && result.isValid();
    }

    /**
     * 清除指定服务器的测试结果
     */
    public synchronized void clearTestResult(@NonNull String serverIdentifier) {
        testResults.remove(serverIdentifier);
        saveTestResultsToFile();
    }

    /**
     * 清除所有测试结果
     */
    public synchronized void clearAllTestResults() {
        testResults.clear();
        saveTestResultsToFile();
    }

    /**
     * 清除过期的测试结果（超过24小时）
     */
    public synchronized void clearExpiredResults() {
        long currentTime = System.currentTimeMillis();
        long twentyFourHours = 24 * 60 * 60 * 1000; // 24小时
        
        Iterator<Map.Entry<String, TestResult>> iterator = testResults.entrySet().iterator();
        boolean hasChanges = false;
        
        while (iterator.hasNext()) {
            Map.Entry<String, TestResult> entry = iterator.next();
            TestResult result = entry.getValue();
            
            if (currentTime - result.getTestTime() > twentyFourHours) {
                iterator.remove();
                hasChanges = true;
                LogHelper.d(TAG, "Removed expired test result for " + entry.getKey());
            }
        }
        
        if (hasChanges) {
            saveTestResultsToFile();
        }
    }

    /**
     * 从文件加载测试结果
     */
    private void loadTestResults() {
        File file = new File(testResultFilePath);
        if (!file.exists()) {
            LogHelper.d(TAG, "Test results file does not exist, starting with empty results");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String jsonString = new String(data, StandardCharsets.UTF_8);
            
            if (TextUtils.isEmpty(jsonString)) {
                return;
            }

            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keys = jsonObject.keys();
            
            while (keys.hasNext()) {
                String serverIdentifier = keys.next();
                JSONObject resultJson = jsonObject.getJSONObject(serverIdentifier);
                
                TestResult result = new TestResult();
                result.setServerIdentifier(serverIdentifier);
                result.setTestTime(resultJson.getLong("test_time"));
                result.setConnected(resultJson.getBoolean("connected"));
                result.setDelay(resultJson.getLong("delay"));
                result.setError(resultJson.optString("error", ""));
                
                testResults.put(serverIdentifier, result);
            }
            
            LogHelper.i(TAG, "Loaded " + testResults.size() + " test results from file");
            
            // 清理过期结果
            clearExpiredResults();
            
        } catch (IOException | JSONException e) {
            LogHelper.e(TAG, "Failed to load test results: " + e.getMessage());
        }
    }

    /**
     * 保存测试结果到文件
     */
    private void saveTestResultsToFile() {
        try {
            JSONObject jsonObject = new JSONObject();
            
            for (Map.Entry<String, TestResult> entry : testResults.entrySet()) {
                TestResult result = entry.getValue();
                JSONObject resultJson = new JSONObject();
                resultJson.put("test_time", result.getTestTime());
                resultJson.put("connected", result.isConnected());
                resultJson.put("delay", result.getDelay());
                resultJson.put("error", result.getError());
                
                jsonObject.put(entry.getKey(), resultJson);
            }
            
            String jsonString = jsonObject.toString(2); // 格式化输出
            
            File file = new File(testResultFilePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(jsonString.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            
            LogHelper.d(TAG, "Saved " + testResults.size() + " test results to file");
            
        } catch (IOException | JSONException e) {
            LogHelper.e(TAG, "Failed to save test results: " + e.getMessage());
        }
    }

    /**
     * 获取所有测试结果的数量
     */
    public synchronized int getTestResultCount() {
        return testResults.size();
    }

    /**
     * 获取有效测试结果的数量
     */
    public synchronized int getValidTestResultCount() {
        int count = 0;
        for (TestResult result : testResults.values()) {
            if (result.isValid()) {
                count++;
            }
        }
        return count;
    }
}