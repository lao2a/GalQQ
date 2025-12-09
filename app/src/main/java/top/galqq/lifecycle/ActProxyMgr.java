package top.galqq.lifecycle;

import androidx.annotation.NonNull;

public class ActProxyMgr {

    public static final String STUB_DEFAULT_ACTIVITY = "com.tencent.mobileqq.activity.photo.CameraPreviewActivity";
    
    /**
     * Intent extra key，用于存储真实的 Intent
     * 注意：这个 key 必须是唯一的，不能使用 STUB_DEFAULT_ACTIVITY
     */
    public static final String ACTIVITY_PROXY_INTENT = "top.galqq.lifecycle.ActProxyMgr.ACTIVITY_PROXY_INTENT";

    private ActProxyMgr() {
        throw new AssertionError("No instance for you!");
    }

    public static boolean isModuleProxyActivity(@NonNull String className) {
        return className.startsWith("top.galqq.ui.");
    }
    
    public static boolean isModuleBundleClassLoaderRequired(@NonNull String className) {
        return isModuleProxyActivity(className);
    }
}
