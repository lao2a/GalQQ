package top.galqq.utils;

import de.robv.android.xposed.XposedBridge;

/**
 * Utility class for detecting and handling QQNT architecture
 * Implementation exactly matches QAuxiliary's QAppUtils.isQQnt()
 */
public class QQNTUtils {
    
    private static final String TAG = "GalQQ.QQNTUtils";
    private static Boolean sIsQQNT = null;
    
    /**
     * Check if current QQ uses QQNT architecture
     * QQNT is the new QQ architecture starting from version 9.x
     * This implementation exactly matches QAuxiliary's Initiator.load() and QAppUtils.isQQnt()
     */
    public static boolean isQQNT(ClassLoader classLoader) {
        if (sIsQQNT != null) {
            return sIsQQNT;
        }
        
        try {
            // Exactly match QAuxiliary's implementation:
            // return Initiator.load("com.tencent.qqnt.base.BaseActivity") != null;
            Class<?> baseActivity = classLoader.loadClass("com.tencent.qqnt.base.BaseActivity");
            sIsQQNT = (baseActivity != null);
            XposedBridge.log(TAG + ": Detected QQNT architecture");
            return sIsQQNT;
        } catch (ClassNotFoundException e) {
            sIsQQNT = false;
            XposedBridge.log(TAG + ": Detected legacy QQ architecture");
            return false;
        }
    }
}
