package top.galqq.config;

import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置迁移器
 * 处理不同版本配置文件的兼容性
 */
public class ConfigMigrator {
    
    private static final String TAG = "GalQQ.ConfigMigrator";
    
    // 当前配置文件版本
    public static final int CURRENT_SCHEMA_VERSION = 1;
    
    /**
     * 兼容性结果
     */
    public static class CompatibilityResult {
        public boolean compatible;
        public boolean needsMigration;
        public String warningMessage;
        
        public CompatibilityResult(boolean compatible, boolean needsMigration, String warningMessage) {
            this.compatible = compatible;
            this.needsMigration = needsMigration;
            this.warningMessage = warningMessage;
        }
    }
    
    /**
     * 检查版本兼容性
     * @param schemaVersion 配置文件的 schema 版本
     * @return 兼容性结果
     */
    @NonNull
    public static CompatibilityResult checkCompatibility(int schemaVersion) {
        if (schemaVersion == CURRENT_SCHEMA_VERSION) {
            // 版本完全匹配
            return new CompatibilityResult(true, false, null);
        }
        
        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            // 旧版本，需要迁移
            return new CompatibilityResult(true, true, 
                "配置文件版本较旧 (v" + schemaVersion + ")，将自动迁移到当前版本 (v" + CURRENT_SCHEMA_VERSION + ")");
        }
        
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            // 新版本，可能不兼容
            return new CompatibilityResult(true, false,
                "配置文件版本较新 (v" + schemaVersion + ")，当前应用版本 (v" + CURRENT_SCHEMA_VERSION + ")，部分配置可能无法识别");
        }
        
        return new CompatibilityResult(true, false, null);
    }
    
    /**
     * 迁移配置到当前版本
     * @param oldConfig 旧配置
     * @param fromVersion 源版本
     * @return 迁移后的配置
     */
    @NonNull
    public static Map<String, Object> migrate(Map<String, Object> oldConfig, int fromVersion) {
        if (oldConfig == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> result = new HashMap<>(oldConfig);
        
        // 按版本逐步迁移
        if (fromVersion < 1) {
            result = migrateToV1(result);
        }
        
        // 未来版本迁移逻辑预留
        // if (fromVersion < 2) {
        //     result = migrateToV2(result);
        // }
        
        return result;
    }
    
    /**
     * 迁移到 V1 版本
     * 当前为初始版本，无需迁移
     */
    private static Map<String, Object> migrateToV1(Map<String, Object> config) {
        // V1 是初始版本，无需迁移
        return config;
    }
    
    /**
     * 处理未知键
     * @param key 未知的配置键
     * @return 警告信息
     */
    @NonNull
    public static String handleUnknownKey(String key) {
        return "未知配置键: " + key + "，已跳过";
    }
    
    /**
     * 获取配置键的默认值
     * @param key 配置键
     * @return 默认值，如果没有默认值返回 null
     */
    public static Object getDefaultValue(String key) {
        if (key == null) return null;
        
        switch (key) {
            // Boolean 默认值
            case ConfigManager.KEY_ENABLED:
                return true;
            case ConfigManager.KEY_AI_ENABLED:
                return false;
            case ConfigManager.KEY_CONTEXT_ENABLED:
                return ConfigManager.DEFAULT_CONTEXT_ENABLED;
            case ConfigManager.KEY_AUTO_SHOW_OPTIONS:
                return ConfigManager.DEFAULT_AUTO_SHOW_OPTIONS;
            case ConfigManager.KEY_AFFINITY_ENABLED:
                return false;
            case ConfigManager.KEY_VERBOSE_LOG:
                return false;
            case ConfigManager.KEY_DEBUG_HOOK_LOG:
                return false;
            case ConfigManager.KEY_PROXY_ENABLED:
                return false;
            case ConfigManager.KEY_PROXY_AUTH_ENABLED:
                return false;
            case ConfigManager.KEY_IMAGE_RECOGNITION_ENABLED:
                return ConfigManager.DEFAULT_IMAGE_RECOGNITION_ENABLED;
            case ConfigManager.KEY_EMOJI_RECOGNITION_ENABLED:
                return ConfigManager.DEFAULT_EMOJI_RECOGNITION_ENABLED;
            case ConfigManager.KEY_VISION_AI_ENABLED:
                return ConfigManager.DEFAULT_VISION_AI_ENABLED;
            case ConfigManager.KEY_VISION_USE_PROXY:
                return ConfigManager.DEFAULT_VISION_USE_PROXY;
            case ConfigManager.KEY_CONTEXT_IMAGE_RECOGNITION_ENABLED:
                return ConfigManager.DEFAULT_CONTEXT_IMAGE_RECOGNITION_ENABLED;
            case ConfigManager.KEY_DISABLE_GROUP_OPTIONS:
                return false;
                
            // String 默认值
            case ConfigManager.KEY_AI_MODEL:
                return ConfigManager.DEFAULT_MODEL;
            case ConfigManager.KEY_AI_PROVIDER:
                return ConfigManager.DEFAULT_PROVIDER;
            case ConfigManager.KEY_FILTER_MODE:
                return ConfigManager.DEFAULT_FILTER_MODE;
            case ConfigManager.KEY_GROUP_FILTER_MODE:
                return ConfigManager.DEFAULT_FILTER_MODE;
            case ConfigManager.KEY_PROXY_TYPE:
                return ConfigManager.DEFAULT_PROXY_TYPE;
            case ConfigManager.KEY_VISION_AI_MODEL:
                return ConfigManager.DEFAULT_VISION_AI_MODEL;
            case ConfigManager.KEY_VISION_AI_PROVIDER:
                return ConfigManager.DEFAULT_VISION_AI_PROVIDER;
            case ConfigManager.KEY_SYS_PROMPT:
                return ConfigManager.DEFAULT_SYS_PROMPT;
                
            // Int 默认值
            case ConfigManager.KEY_AI_MAX_TOKENS:
                return ConfigManager.DEFAULT_MAX_TOKENS;
            case ConfigManager.KEY_CONTEXT_MESSAGE_COUNT:
                return ConfigManager.DEFAULT_CONTEXT_MESSAGE_COUNT;
            case ConfigManager.KEY_HISTORY_THRESHOLD:
                return ConfigManager.DEFAULT_HISTORY_THRESHOLD;
            case ConfigManager.KEY_AFFINITY_MODEL:
                return ConfigManager.DEFAULT_AFFINITY_MODEL;
            case ConfigManager.KEY_PROXY_PORT:
                return ConfigManager.DEFAULT_PROXY_PORT;
            case ConfigManager.KEY_IMAGE_MAX_SIZE:
                return ConfigManager.DEFAULT_IMAGE_MAX_SIZE;
            case ConfigManager.KEY_IMAGE_DESCRIPTION_MAX_LENGTH:
                return ConfigManager.DEFAULT_IMAGE_DESCRIPTION_MAX_LENGTH;
            case ConfigManager.KEY_VISION_TIMEOUT:
                return ConfigManager.DEFAULT_VISION_TIMEOUT;
            case ConfigManager.KEY_AI_TIMEOUT:
                return ConfigManager.DEFAULT_AI_TIMEOUT;
            case ConfigManager.KEY_CURRENT_PROMPT_INDEX:
                return 0;
                
            // Float 默认值
            case ConfigManager.KEY_AI_TEMPERATURE:
                return ConfigManager.DEFAULT_TEMPERATURE;
            case ConfigManager.KEY_AI_QPS:
                return ConfigManager.DEFAULT_AI_QPS;
            case ConfigManager.KEY_VISION_AI_QPS:
                return ConfigManager.DEFAULT_VISION_AI_QPS;
                
            default:
                return null;
        }
    }
}
