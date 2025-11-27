package top.galqq.utils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

/**
 * 类加载工具类，模仿QAuxiliary的实现
 * 用于安全地加载宿主应用的类，避免ClassNotFoundException
 */
public class Initiator {

    // 类缓存，提高性能
    private static final Map<String, Class<?>> sClassCache = new HashMap<>();
    
    // 宿主类加载器
    private static ClassLoader sHostClassLoader;
    
    // 预定义的常用类引用
    public static Class<?> _QQAppInterface;
    public static Class<?> _AppRuntime;
    public static Class<?> _MobileQQ;
    public static Class<?> _MessageRecord;
    
    /**
     * 初始化类加载器和常用类引用
     * @param classLoader 宿主应用的类加载器
     */
    public static void init(ClassLoader classLoader) {
        sHostClassLoader = classLoader;
        
        try {
            // 预加载常用类，但不预加载AppRuntime类
            _QQAppInterface = load("com.tencent.mobileqq.app.QQAppInterface");
            // 不预加载AppRuntime类，避免NoClassDefFoundError
            // _AppRuntime = load("mqq.app.AppRuntime");
            _MobileQQ = load("mqq.app.MobileQQ");
            _MessageRecord = load("com.tencent.mobileqq.data.MessageRecord");
            
            XposedBridge.log("GalQQ.Initiator: 初始化完成，未预加载AppRuntime类");
        } catch (Exception e) {
            XposedBridge.log("GalQQ.Initiator: Failed to preload classes: " + e.getMessage());
        }
    }
    
    /**
     * 安全地加载类
     * @param className 类名
     * @return 加载的类对象
     */
    public static Class<?> load(String className) {
        XposedBridge.log("GalQQ.Initiator: 尝试加载类: " + className);
        
        if (sClassCache.containsKey(className)) {
            XposedBridge.log("GalQQ.Initiator: 从缓存获取类: " + className);
            return sClassCache.get(className);
        }
        
        try {
            // 检查是否是AppRuntime类，如果是，使用特殊处理
            if ("mqq.app.AppRuntime".equals(className)) {
                XposedBridge.log("GalQQ.Initiator: 检测到AppRuntime类，使用特殊处理");
                
                // 尝试直接从宿主类加载器获取，避免初始化
                try {
                    // 使用forName但不初始化类
                    Class<?> clazz = Class.forName(className, false, sHostClassLoader);
                    sClassCache.put(className, clazz);
                    XposedBridge.log("GalQQ.Initiator: 成功加载AppRuntime类(不初始化): " + clazz.getName());
                    XposedBridge.log("GalQQ.Initiator: 类的ClassLoader: " + clazz.getClassLoader().getClass().getName());
                    return clazz;
                } catch (ClassNotFoundException e) {
                    XposedBridge.log("GalQQ.Initiator: AppRuntime类未找到，尝试其他方式");
                    // 不抛出异常，继续下面的尝试
                } catch (NoClassDefFoundError e) {
                    XposedBridge.log("GalQQ.Initiator: AppRuntime类NoClassDefFoundError，尝试其他方式");
                    // 不抛出异常，继续下面的尝试
                } catch (LinkageError e) {
                    XposedBridge.log("GalQQ.Initiator: AppRuntime类LinkageError，尝试其他方式");
                    // 不抛出异常，继续下面的尝试
                }
                
                // 如果直接加载失败，返回null，让调用方处理
                XposedBridge.log("GalQQ.Initiator: 无法加载AppRuntime类，返回null");
                return null;
            }
            
            XposedBridge.log("GalQQ.Initiator: 使用ClassLoader加载类: " + className);
            XposedBridge.log("GalQQ.Initiator: ClassLoader类型: " + sHostClassLoader.getClass().getName());
            
            Class<?> clazz = Class.forName(className, false, sHostClassLoader);
            sClassCache.put(className, clazz);
            XposedBridge.log("GalQQ.Initiator: 成功加载类: " + className + ", 实际类名: " + clazz.getName());
            XposedBridge.log("GalQQ.Initiator: 类的ClassLoader: " + clazz.getClassLoader().getClass().getName());
            return clazz;
        } catch (ClassNotFoundException e) {
            XposedBridge.log("GalQQ.Initiator: 类未找到: " + className);
            XposedBridge.log("GalQQ.Initiator: 错误信息: " + e.getMessage());
            
            // 尝试打印更多调试信息
            XposedBridge.log("GalQQ.Initiator: 当前ClassLoader: " + (sHostClassLoader != null ? sHostClassLoader.getClass().getName() : "null"));
            
            return null;
        } catch (NoClassDefFoundError e) {
            XposedBridge.log("GalQQ.Initiator: NoClassDefFoundError: " + className);
            XposedBridge.log("GalQQ.Initiator: 错误信息: " + e.getMessage());
            
            // 尝试打印更多调试信息
            XposedBridge.log("GalQQ.Initiator: 当前ClassLoader: " + (sHostClassLoader != null ? sHostClassLoader.getClass().getName() : "null"));
            
            return null;
        } catch (LinkageError e) {
            XposedBridge.log("GalQQ.Initiator: LinkageError: " + className);
            XposedBridge.log("GalQQ.Initiator: 错误信息: " + e.getMessage());
            
            // 尝试打印更多调试信息
            XposedBridge.log("GalQQ.Initiator: 当前ClassLoader: " + (sHostClassLoader != null ? sHostClassLoader.getClass().getName() : "null"));
            
            return null;
        } catch (Exception e) {
            XposedBridge.log("GalQQ.Initiator: 加载类时发生未知错误: " + className);
            XposedBridge.log("GalQQ.Initiator: 错误类型: " + e.getClass().getName());
            XposedBridge.log("GalQQ.Initiator: 错误信息: " + e.getMessage());
            
            return null;
        }
    }
    
    /**
     * 加载类并初始化
     * @param className 类名
     * @return 加载的类对象
     */
    public static Class<?> loadClass(String className) {
        if (sClassCache.containsKey(className)) {
            return sClassCache.get(className);
        }
        
        try {
            Class<?> clazz = Class.forName(className, true, sHostClassLoader);
            sClassCache.put(className, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            XposedBridge.log("GalQQ: Class not found: " + className + ", error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取静态字段值
     * @param className 类名
     * @param fieldName 字段名
     * @return 字段值
     */
    public static Object getStaticObject(String className, String fieldName) {
        try {
            Class<?> clazz = load(className);
            if (clazz == null) {
                return null;
            }
            
            Field field = XposedHelpers.findField(clazz, fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            XposedBridge.log("GalQQ: Failed to get static field " + fieldName + " from " + className + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 调用静态方法
     * @param className 类名
     * @param methodName 方法名
     * @param args 参数
     * @return 方法返回值
     */
    public static Object callStaticMethod(String className, String methodName, Object... args) {
        try {
            Class<?> clazz = load(className);
            if (clazz == null) {
                return null;
            }
            
            return XposedHelpers.callStaticMethod(clazz, methodName, args);
        } catch (Exception e) {
            XposedBridge.log("GalQQ: Failed to call static method " + methodName + " from " + className + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取类加载器
     * @return 宿主应用的类加载器
     */
    public static ClassLoader getHostClassLoader() {
        return sHostClassLoader;
    }
    
    /**
     * 清除类缓存
     */
    public static void clearCache() {
        sClassCache.clear();
    }
}