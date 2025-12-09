package top.galqq.config;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置导入器
 * 负责解析 JSON 并写入 MMKV
 */
public class ConfigImporter {
    
    private static final String TAG = "GalQQ.ConfigImporter";
    
    /**
     * 导入结果类
     */
    public static class ImportResult {
        public boolean success;
        public String errorMessage;
        public ConfigMetadata metadata;
        public Map<String, Map<String, Object>> configsByCategory;
        public int totalItems;
        public List<String> warnings;
        
        public ImportResult() {
            this.success = false;
            this.errorMessage = null;
            this.metadata = null;
            this.configsByCategory = new HashMap<>();
            this.totalItems = 0;
            this.warnings = new ArrayList<>();
        }
        
        /**
         * 创建失败结果
         */
        public static ImportResult failure(String errorMessage) {
            ImportResult result = new ImportResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }
        
        /**
         * 创建成功结果
         */
        public static ImportResult success(ConfigMetadata metadata, 
                                           Map<String, Map<String, Object>> configsByCategory,
                                           int totalItems,
                                           List<String> warnings) {
            ImportResult result = new ImportResult();
            result.success = true;
            result.metadata = metadata;
            result.configsByCategory = configsByCategory;
            result.totalItems = totalItems;
            result.warnings = warnings != null ? warnings : new ArrayList<>();
            return result;
        }
    }
    
    /**
     * 配置元数据类
     */
    public static class ConfigMetadata {
        public String exportTime;
        public String appVersion;
        public int schemaVersion;
        public String deviceInfo;
        
        public ConfigMetadata() {}
        
        public ConfigMetadata(String exportTime, String appVersion, int schemaVersion, String deviceInfo) {
            this.exportTime = exportTime;
            this.appVersion = appVersion;
            this.schemaVersion = schemaVersion;
            this.deviceInfo = deviceInfo;
        }
    }
    
    /**
     * 解析配置文件
     * @param jsonContent JSON 内容
     * @return 解析结果，包含元数据和配置项
     */
    @NonNull
    public static ImportResult parseConfig(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return ImportResult.failure("配置文件内容为空");
        }
        
        List<String> warnings = new ArrayList<>();
        
        try {
            JSONObject root = new JSONObject(jsonContent);
            
            // 1. 解析元数据
            ConfigMetadata metadata = new ConfigMetadata();
            if (root.has("_metadata")) {
                JSONObject metaObj = root.getJSONObject("_metadata");
                metadata.exportTime = metaObj.optString("exportTime", "");
                metadata.appVersion = metaObj.optString("appVersion", "");
                metadata.schemaVersion = metaObj.optInt("schemaVersion", 1);
                metadata.deviceInfo = metaObj.optString("deviceInfo", "");
            } else {
                warnings.add("配置文件缺少元数据，将使用默认值");
                metadata.schemaVersion = 1;
            }
            
            // 2. 检查版本兼容性
            ConfigMigrator.CompatibilityResult compatibility = 
                ConfigMigrator.checkCompatibility(metadata.schemaVersion);
            if (!compatibility.compatible) {
                return ImportResult.failure(compatibility.warningMessage);
            }
            if (compatibility.warningMessage != null && !compatibility.warningMessage.isEmpty()) {
                warnings.add(compatibility.warningMessage);
            }
            
            // 3. 按分类解析配置项
            Map<String, Map<String, Object>> configsByCategory = new HashMap<>();
            int totalItems = 0;
            
            for (String category : ConfigManager.ALL_CATEGORIES) {
                if (root.has(category)) {
                    JSONObject categoryObj = root.getJSONObject(category);
                    Map<String, Object> configs = new HashMap<>();
                    
                    Iterator<String> keys = categoryObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        
                        // 检查是否为已知配置键
                        String expectedCategory = ConfigManager.getCategoryForKey(key);
                        if (expectedCategory == null) {
                            warnings.add("未知配置键: " + key + "，将跳过");
                            continue;
                        }
                        
                        // 检查配置键是否属于正确的分类
                        if (!category.equals(expectedCategory)) {
                            warnings.add("配置键 " + key + " 应属于 " + expectedCategory + "，但在 " + category + " 中找到");
                        }
                        
                        Object value = categoryObj.get(key);
                        configs.put(key, value);
                        totalItems++;
                    }
                    
                    configsByCategory.put(category, configs);
                }
            }
            
            return ImportResult.success(metadata, configsByCategory, totalItems, warnings);
            
        } catch (JSONException e) {
            android.util.Log.e(TAG, "Failed to parse config JSON", e);
            return ImportResult.failure("JSON 解析失败: " + e.getMessage());
        }
    }

    
    /**
     * 导入配置
     * @param result 解析结果
     * @param selectedCategories 选中的分类（null 表示全部导入）
     * @return 是否成功
     */
    public static boolean importConfig(ImportResult result, @Nullable Set<String> selectedCategories) {
        if (result == null || !result.success || result.configsByCategory == null) {
            return false;
        }
        
        // 1. 备份当前配置（用于回滚）
        Map<String, Map<String, Object>> backup = ConfigExporter.getAllConfigsByCategory();
        
        try {
            // 2. 根据选中分类过滤并导入配置
            for (Map.Entry<String, Map<String, Object>> categoryEntry : result.configsByCategory.entrySet()) {
                String category = categoryEntry.getKey();
                
                // 如果指定了选中分类，检查是否在选中列表中
                if (selectedCategories != null && !selectedCategories.contains(category)) {
                    continue;
                }
                
                Map<String, Object> configs = categoryEntry.getValue();
                for (Map.Entry<String, Object> configEntry : configs.entrySet()) {
                    String key = configEntry.getKey();
                    Object value = configEntry.getValue();
                    
                    // 写入配置
                    writeConfigValue(key, value);
                }
            }
            
            return true;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to import config, rolling back", e);
            
            // 3. 发生错误，回滚到备份
            rollbackConfig(backup);
            return false;
        }
    }
    
    /**
     * 写入单个配置项的值
     * @param key 配置键
     * @param value 配置值
     */
    private static void writeConfigValue(String key, Object value) {
        if (key == null || value == null) return;
        
        try {
            // 根据配置键判断类型并写入
            switch (key) {
                // Boolean 类型
                case ConfigManager.KEY_ENABLED:
                    ConfigManager.setModuleEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_AI_ENABLED:
                    ConfigManager.setAiEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_CONTEXT_ENABLED:
                    ConfigManager.setContextEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_AUTO_SHOW_OPTIONS:
                    ConfigManager.setAutoShowOptionsEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_AFFINITY_ENABLED:
                    ConfigManager.setAffinityEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_VERBOSE_LOG:
                    ConfigManager.setVerboseLogEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_DEBUG_HOOK_LOG:
                    ConfigManager.setDebugHookLogEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_PROXY_ENABLED:
                    ConfigManager.setProxyEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_PROXY_AUTH_ENABLED:
                    ConfigManager.setProxyAuthEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_IMAGE_RECOGNITION_ENABLED:
                    ConfigManager.setImageRecognitionEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_EMOJI_RECOGNITION_ENABLED:
                    ConfigManager.setEmojiRecognitionEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_VISION_AI_ENABLED:
                    ConfigManager.setVisionAiEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_VISION_USE_PROXY:
                    ConfigManager.setVisionUseProxy(toBoolean(value));
                    break;
                case ConfigManager.KEY_CONTEXT_IMAGE_RECOGNITION_ENABLED:
                    ConfigManager.setContextImageRecognitionEnabled(toBoolean(value));
                    break;
                case ConfigManager.KEY_DISABLE_GROUP_OPTIONS:
                    ConfigManager.setDisableGroupOptions(toBoolean(value));
                    break;
                    
                // String 类型
                case ConfigManager.KEY_API_URL:
                    ConfigManager.setApiUrl(toString(value));
                    break;
                case ConfigManager.KEY_CUSTOM_API_URL:
                    ConfigManager.setCustomApiUrl(toString(value));
                    break;
                case ConfigManager.KEY_API_KEY:
                    ConfigManager.setApiKey(toString(value));
                    break;
                case ConfigManager.KEY_AI_MODEL:
                    ConfigManager.setAiModel(toString(value));
                    break;
                case ConfigManager.KEY_AI_PROVIDER:
                    ConfigManager.setAiProvider(toString(value));
                    break;
                case ConfigManager.KEY_AI_REASONING_EFFORT:
                    ConfigManager.setAiReasoningEffort(toString(value));
                    break;
                case ConfigManager.KEY_SYS_PROMPT:
                    ConfigManager.setSysPrompt(toString(value));
                    break;
                case ConfigManager.KEY_FILTER_MODE:
                    ConfigManager.setFilterMode(toString(value));
                    break;
                case ConfigManager.KEY_BLACKLIST:
                    ConfigManager.setBlacklist(toString(value));
                    break;
                case ConfigManager.KEY_WHITELIST:
                    ConfigManager.setWhitelist(toString(value));
                    break;
                case ConfigManager.KEY_GROUP_FILTER_MODE:
                    ConfigManager.setGroupFilterMode(toString(value));
                    break;
                case ConfigManager.KEY_GROUP_BLACKLIST:
                    ConfigManager.setGroupBlacklist(toString(value));
                    break;
                case ConfigManager.KEY_GROUP_WHITELIST:
                    ConfigManager.setGroupWhitelist(toString(value));
                    break;
                case ConfigManager.KEY_PROXY_TYPE:
                    ConfigManager.setProxyType(toString(value));
                    break;
                case ConfigManager.KEY_PROXY_HOST:
                    ConfigManager.setProxyHost(toString(value));
                    break;
                case ConfigManager.KEY_PROXY_USERNAME:
                    ConfigManager.setProxyUsername(toString(value));
                    break;
                case ConfigManager.KEY_PROXY_PASSWORD:
                    ConfigManager.setProxyPassword(toString(value));
                    break;
                case ConfigManager.KEY_VISION_API_URL:
                    ConfigManager.setVisionApiUrl(toString(value));
                    break;
                case ConfigManager.KEY_VISION_API_KEY:
                    ConfigManager.setVisionApiKey(toString(value));
                    break;
                case ConfigManager.KEY_VISION_AI_MODEL:
                    ConfigManager.setVisionAiModel(toString(value));
                    break;
                case ConfigManager.KEY_VISION_AI_PROVIDER:
                    ConfigManager.setVisionAiProvider(toString(value));
                    break;
                    
                // Int 类型
                case ConfigManager.KEY_AI_MAX_TOKENS:
                    ConfigManager.setAiMaxTokens(toInt(value));
                    break;
                case ConfigManager.KEY_CONTEXT_MESSAGE_COUNT:
                    ConfigManager.setContextMessageCount(toInt(value));
                    break;
                case ConfigManager.KEY_HISTORY_THRESHOLD:
                    ConfigManager.setHistoryThreshold(toInt(value));
                    break;
                case ConfigManager.KEY_AFFINITY_MODEL:
                    ConfigManager.setAffinityModel(toInt(value));
                    break;
                case ConfigManager.KEY_PROXY_PORT:
                    ConfigManager.setProxyPort(toInt(value));
                    break;
                case ConfigManager.KEY_IMAGE_MAX_SIZE:
                    ConfigManager.setImageMaxSize(toInt(value));
                    break;
                case ConfigManager.KEY_IMAGE_DESCRIPTION_MAX_LENGTH:
                    ConfigManager.setImageDescriptionMaxLength(toInt(value));
                    break;
                case ConfigManager.KEY_VISION_TIMEOUT:
                    ConfigManager.setVisionTimeout(toInt(value));
                    break;
                case ConfigManager.KEY_AI_TIMEOUT:
                    ConfigManager.setAiTimeout(toInt(value));
                    break;
                case ConfigManager.KEY_CURRENT_PROMPT_INDEX:
                    ConfigManager.setCurrentPromptIndex(toInt(value));
                    break;
                    
                // 按钮样式 - Int 类型
                case ConfigManager.KEY_BUTTON_FILL_COLOR:
                    ConfigManager.setButtonFillColor(toInt(value));
                    break;
                case ConfigManager.KEY_BUTTON_BORDER_COLOR:
                    ConfigManager.setButtonBorderColor(toInt(value));
                    break;
                case ConfigManager.KEY_BUTTON_BORDER_WIDTH:
                    ConfigManager.setButtonBorderWidth(toInt(value));
                    break;
                case ConfigManager.KEY_BUTTON_TEXT_COLOR:
                    ConfigManager.setButtonTextColor(toInt(value));
                    break;
                    
                // Float 类型
                case ConfigManager.KEY_AI_TEMPERATURE:
                    ConfigManager.setAiTemperature(toFloat(value));
                    break;
                case ConfigManager.KEY_AI_QPS:
                    ConfigManager.setAiQps(toFloat(value));
                    break;
                case ConfigManager.KEY_VISION_AI_QPS:
                    ConfigManager.setVisionAiQps(toFloat(value));
                    break;
                    
                // 特殊类型 - 提示词列表
                case ConfigManager.KEY_PROMPT_LIST:
                    importPromptList(toString(value));
                    break;
                    
                default:
                    // 尝试通用写入
                    ConfigManager.putString(key, toString(value));
                    break;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to write config: " + key, e);
        }
    }
    
    /**
     * 导入提示词列表
     */
    private static void importPromptList(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return;
        
        try {
            org.json.JSONArray arr = new org.json.JSONArray(jsonStr);
            List<ConfigManager.PromptItem> list = new ArrayList<>();
            
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", "");
                String content = obj.optString("content", "");
                String whitelist = obj.optString("whitelist", "");
                String blacklist = obj.optString("blacklist", "");
                boolean enabled = obj.optBoolean("enabled", true);
                boolean whitelistEnabled = obj.optBoolean("whitelistEnabled", false);
                boolean blacklistEnabled = obj.optBoolean("blacklistEnabled", false);
                String groupWhitelist = obj.optString("groupWhitelist", "");
                String groupBlacklist = obj.optString("groupBlacklist", "");
                boolean groupWhitelistEnabled = obj.optBoolean("groupWhitelistEnabled", false);
                boolean groupBlacklistEnabled = obj.optBoolean("groupBlacklistEnabled", false);
                
                list.add(new ConfigManager.PromptItem(name, content, whitelist, blacklist, 
                        enabled, whitelistEnabled, blacklistEnabled, groupWhitelist, groupBlacklist,
                        groupWhitelistEnabled, groupBlacklistEnabled));
            }
            
            ConfigManager.savePromptList(list);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to import prompt list", e);
        }
    }
    
    /**
     * 回滚配置到备份状态
     */
    private static void rollbackConfig(Map<String, Map<String, Object>> backup) {
        if (backup == null) return;
        
        for (Map.Entry<String, Map<String, Object>> categoryEntry : backup.entrySet()) {
            Map<String, Object> configs = categoryEntry.getValue();
            for (Map.Entry<String, Object> configEntry : configs.entrySet()) {
                writeConfigValue(configEntry.getKey(), configEntry.getValue());
            }
        }
    }
    
    /**
     * 从文件导入配置
     * @param context 上下文
     * @param uri 文件 URI
     * @return 解析结果
     */
    @NonNull
    public static ImportResult importFromFile(Context context, Uri uri) {
        if (context == null || uri == null) {
            return ImportResult.failure("参数无效");
        }
        
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return ImportResult.failure("无法打开文件");
            }
            
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                
                return parseConfig(sb.toString());
            } finally {
                inputStream.close();
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to import config from file", e);
            return ImportResult.failure("读取文件失败: " + e.getMessage());
        }
    }
    
    // ========== 类型转换辅助方法 ==========
    
    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return false;
    }
    
    private static String toString(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
    
    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    private static float toFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            try {
                return Float.parseFloat((String) value);
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }
}
