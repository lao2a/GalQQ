package top.galqq.lifecycle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import top.galqq.R;
import top.galqq.hook.GalqqHook;
import top.galqq.utils.HostInfo;

/**
 * Activity 代理和资源注入
 * 完全参照 QAuxiliary 的实现
 */
public class Parasitics {

    private Parasitics() {
    }

    private static final String TAG = "GalQQ.Parasitics";
    private static boolean __stub_hooked = false;
    private static String sModulePath = null;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static void log(Throwable t) {
        XposedBridge.log(t);
    }

    public static void setModulePath(String path) {
        sModulePath = path;
    }

    public static String getModulePath() {
        return sModulePath;
    }

    public static void injectModuleResources(Resources res) {
        if (res == null) {
            return;
        }
        try {
            res.getString(R.string.res_inject_success);
            return;
        } catch (Resources.NotFoundException ignored) {
        }
        if (sModulePath == null) {
            log("sModulePath is null, cannot inject resources");
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            injectResourcesAboveApi30(res, sModulePath);
        } else {
            injectResourcesBelowApi30(res, sModulePath);
        }
    }

    @RequiresApi(30)
    private static void injectResourcesAboveApi30(@NonNull Resources res, @NonNull String path) {
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            ResourcesProvider provider = ResourcesProvider.loadFromApk(pfd);
            ResourcesLoader loader = new ResourcesLoader();
            loader.addProvider(provider);
            res.addLoaders(loader);
        } catch (IOException e) {
            log("Failed to inject resources (API 30+): " + e.getMessage());
            // fallback
            injectResourcesBelowApi30(res, path);
        } catch (IllegalArgumentException e) {
            // fallback
            injectResourcesBelowApi30(res, path);
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("PrivateApi")
    private static void injectResourcesBelowApi30(@NonNull Resources res, @NonNull String path) {
        try {
            AssetManager assets = res.getAssets();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assets, path);
        } catch (Exception e) {
            log(e);
        }
    }


    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    public static void initForStubActivity(Context ctx) {
        if (__stub_hooked) {
            return;
        }
        try {
            // 动态获取模块路径（如果需要）
            if (sModulePath == null || !new File(sModulePath).exists()) {
                try {
                    Context moduleContext = ctx.createPackageContext("top.galqq", Context.CONTEXT_IGNORE_SECURITY);
                    sModulePath = moduleContext.getApplicationInfo().sourceDir;
                } catch (Exception e) {
                    log("Failed to resolve module path: " + e.getMessage());
                }
            }

            Class<?> clazz_ActivityThread = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = clazz_ActivityThread.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object sCurrentActivityThread = currentActivityThread.invoke(null);

            // Hook Instrumentation
            Field mInstrumentation = clazz_ActivityThread.getDeclaredField("mInstrumentation");
            mInstrumentation.setAccessible(true);
            Instrumentation instrumentation = (Instrumentation) mInstrumentation.get(sCurrentActivityThread);
            mInstrumentation.set(sCurrentActivityThread, new ProxyInstrumentation(instrumentation));

            // Hook Handler
            Field field_mH = clazz_ActivityThread.getDeclaredField("mH");
            field_mH.setAccessible(true);
            Handler oriHandler = (Handler) field_mH.get(sCurrentActivityThread);
            Field field_mCallback = Handler.class.getDeclaredField("mCallback");
            field_mCallback.setAccessible(true);
            Handler.Callback current = (Handler.Callback) field_mCallback.get(oriHandler);
            if (current == null || !current.getClass().getName().equals(ProxyHandlerCallback.class.getName())) {
                field_mCallback.set(oriHandler, new ProxyHandlerCallback(current));
            }

            // Hook IActivityManager
            Class<?> activityManagerClass;
            Field gDefaultField;
            try {
                activityManagerClass = Class.forName("android.app.ActivityManagerNative");
                gDefaultField = activityManagerClass.getDeclaredField("gDefault");
            } catch (Exception err1) {
                try {
                    activityManagerClass = Class.forName("android.app.ActivityManager");
                    gDefaultField = activityManagerClass.getDeclaredField("IActivityManagerSingleton");
                } catch (Exception err2) {
                    log("Unable to get IActivityManagerSingleton");
                    return;
                }
            }
            gDefaultField.setAccessible(true);
            Object gDefault = gDefaultField.get(null);
            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            Object mInstance = mInstanceField.get(gDefault);
            Object amProxy = Proxy.newProxyInstance(
                    Parasitics.class.getClassLoader(),
                    new Class[]{Class.forName("android.app.IActivityManager")},
                    new IActivityManagerHandler(mInstance));
            mInstanceField.set(gDefault, amProxy);

            // Hook IActivityTaskManager (Android 10+)
            try {
                Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
                Field fIActivityTaskManagerSingleton = activityTaskManagerClass.getDeclaredField("IActivityTaskManagerSingleton");
                fIActivityTaskManagerSingleton.setAccessible(true);
                Object singleton = fIActivityTaskManagerSingleton.get(null);
                singletonClass.getMethod("get").invoke(singleton);
                Object mDefaultTaskMgr = mInstanceField.get(singleton);
                Object proxy2 = Proxy.newProxyInstance(
                        Parasitics.class.getClassLoader(),
                        new Class[]{Class.forName("android.app.IActivityTaskManager")},
                        new IActivityManagerHandler(mDefaultTaskMgr));
                mInstanceField.set(singleton, proxy2);
            } catch (Exception ignored) {
            }

            // Hook PackageManager
            Field sPackageManagerField = clazz_ActivityThread.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            Object packageManagerImpl = sPackageManagerField.get(sCurrentActivityThread);
            Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
            PackageManager pm = ctx.getPackageManager();
            Field mPmField = pm.getClass().getDeclaredField("mPM");
            mPmField.setAccessible(true);
            Object pmProxy = Proxy.newProxyInstance(
                    iPackageManagerInterface.getClassLoader(),
                    new Class[]{iPackageManagerInterface},
                    new PackageManagerInvocationHandler(packageManagerImpl));
            sPackageManagerField.set(sCurrentActivityThread, pmProxy);
            mPmField.set(pm, pmProxy);

            __stub_hooked = true;
            log("Activity Proxy initialized successfully");
        } catch (Exception e) {
            log("Failed to init Activity Proxy: " + e.getMessage());
            log(e);
        }
    }


    /**
     * IActivityManager 代理处理器
     */
    public static class IActivityManagerHandler implements InvocationHandler {
        private final Object mOrigin;

        public IActivityManagerHandler(Object origin) {
            mOrigin = origin;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("startActivity".equals(method.getName())) {
                int index = -1;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        index = i;
                        break;
                    }
                }
                if (index != -1) {
                    Intent raw = (Intent) args[index];
                    ComponentName component = raw.getComponent();
                    Context hostApp = HostInfo.getApplication();
                    if (hostApp != null && component != null
                            && hostApp.getPackageName().equals(component.getPackageName())
                            && ActProxyMgr.isModuleProxyActivity(component.getClassName())) {
                        Intent wrapper = new Intent();
                        wrapper.setClassName(component.getPackageName(), ActProxyMgr.STUB_DEFAULT_ACTIVITY);
                        wrapper.putExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT, raw);
                        args[index] = wrapper;
                    }
                }
            }
            try {
                return method.invoke(mOrigin, args);
            } catch (InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }
    }

    /**
     * Handler.Callback 代理
     */
    public static class ProxyHandlerCallback implements Handler.Callback {
        private final Handler.Callback mNextCallbackHook;

        public ProxyHandlerCallback(Handler.Callback next) {
            mNextCallbackHook = next;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 100) {
                // LAUNCH_ACTIVITY - 旧版本 Android 和某些 ROM 使用
                onHandleLaunchActivity(msg);
            } else if (msg.what == 159) {
                // EXECUTE_TRANSACTION - 新版本 Android 使用
                onHandleExecuteTransaction(msg);
            }
            if (mNextCallbackHook != null) {
                return mNextCallbackHook.handleMessage(msg);
            }
            return false;
        }

        @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
        private void onHandleLaunchActivity(Message msg) {
            try {
                Object activityClientRecord = msg.obj;
                Field field_intent = activityClientRecord.getClass().getDeclaredField("intent");
                field_intent.setAccessible(true);
                Intent intent = (Intent) field_intent.get(activityClientRecord);
                if (intent == null) return;
                
                Bundle bundle = null;
                Intent cloneIntent = new Intent(intent);
                try {
                    Field fExtras = Intent.class.getDeclaredField("mExtras");
                    fExtras.setAccessible(true);
                    bundle = (Bundle) fExtras.get(cloneIntent);
                } catch (Exception e) {
                    log(e);
                }
                if (bundle != null) {
                    bundle.setClassLoader(Parasitics.class.getClassLoader());
                    if (cloneIntent.hasExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT)) {
                        Intent realIntent = cloneIntent.getParcelableExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT);
                        field_intent.set(activityClientRecord, realIntent);
                    }
                }
            } catch (Exception e) {
                log(e);
            }
        }

        @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
        private void onHandleExecuteTransaction(Message msg) {
            Object clientTransaction = msg.obj;
            try {
                if (clientTransaction != null) {
                    Method getCallbacks = Class.forName("android.app.servertransaction.ClientTransaction")
                            .getDeclaredMethod("getCallbacks");
                    getCallbacks.setAccessible(true);
                    List<?> clientTransactionItems = (List<?>) getCallbacks.invoke(clientTransaction);
                    if (clientTransactionItems != null && !clientTransactionItems.isEmpty()) {
                        for (Object item : clientTransactionItems) {
                            Class<?> c = item.getClass();
                            if (c.getName().contains("LaunchActivityItem")) {
                                processLaunchActivityItem(item, clientTransaction);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log(e);
            }
        }

        @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
        private void processLaunchActivityItem(Object item, Object clientTransaction) throws ReflectiveOperationException {
            Class<?> c = item.getClass();
            Field fmIntent = c.getDeclaredField("mIntent");
            fmIntent.setAccessible(true);
            Intent wrapper = (Intent) fmIntent.get(item);
            if (wrapper == null) return;
            
            Intent cloneIntent = (Intent) wrapper.clone();
            Bundle bundle = null;
            try {
                Field fExtras = Intent.class.getDeclaredField("mExtras");
                fExtras.setAccessible(true);
                bundle = (Bundle) fExtras.get(cloneIntent);
            } catch (Exception e) {
                log(e);
            }
            if (bundle != null) {
                bundle.setClassLoader(Parasitics.class.getClassLoader());
                if (cloneIntent.hasExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT)) {
                    Intent realIntent = cloneIntent.getParcelableExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT);
                    fmIntent.set(item, realIntent);
                    
                    // Android 12+ 需要额外处理
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            IBinder token = (IBinder) clientTransaction.getClass()
                                    .getMethod("getActivityToken").invoke(clientTransaction);
                            Class<?> clazz_ActivityThread = Class.forName("android.app.ActivityThread");
                            Method currentActivityThread = clazz_ActivityThread.getDeclaredMethod("currentActivityThread");
                            currentActivityThread.setAccessible(true);
                            Object activityThread = currentActivityThread.invoke(null);
                            if (activityThread != null) {
                                try {
                                    Object acr = activityThread.getClass()
                                            .getMethod("getLaunchingActivity", IBinder.class)
                                            .invoke(activityThread, token);
                                    if (acr != null) {
                                        Field fAcrIntent = acr.getClass().getDeclaredField("intent");
                                        fAcrIntent.setAccessible(true);
                                        fAcrIntent.set(acr, realIntent);
                                    }
                                } catch (NoSuchMethodException e) {
                                    // Android 13+ 可能没有这个方法
                                }
                            }
                        } catch (Exception e) {
                            // 忽略
                        }
                    }
                }
            }
        }
    }


    /**
     * Instrumentation 代理
     */
    public static class ProxyInstrumentation extends Instrumentation {
        private final Instrumentation mBase;

        public ProxyInstrumentation(Instrumentation base) {
            this.mBase = base;
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent)
                throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            try {
                return mBase.newActivity(cl, className, intent);
            } catch (Exception e) {
                if (ActProxyMgr.isModuleProxyActivity(className)) {
                    ClassLoader selfClassLoader = GalqqHook.class.getClassLoader();
                    if (selfClassLoader != null) {
                        return (Activity) selfClassLoader.loadClass(className).newInstance();
                    }
                }
                throw e;
            }
        }

        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle) {
            if (icicle != null) {
                String className = activity.getClass().getName();
                if (ActProxyMgr.isModuleBundleClassLoaderRequired(className)) {
                    icicle.setClassLoader(GalqqHook.class.getClassLoader());
                }
            }
            injectModuleResources(activity.getResources());
            mBase.callActivityOnCreate(activity, icicle);
        }

        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
            if (icicle != null) {
                String className = activity.getClass().getName();
                if (ActProxyMgr.isModuleBundleClassLoaderRequired(className)) {
                    icicle.setClassLoader(GalqqHook.class.getClassLoader());
                }
            }
            injectModuleResources(activity.getResources());
            mBase.callActivityOnCreate(activity, icicle, persistentState);
        }
    }

    /**
     * PackageManager 代理处理器
     */
    public static class PackageManagerInvocationHandler implements InvocationHandler {
        private final Object target;

        public PackageManagerInvocationHandler(Object target) {
            if (target == null) {
                throw new NullPointerException("IPackageManager == null");
            }
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if ("getActivityInfo".equals(method.getName())) {
                    ActivityInfo ai = (ActivityInfo) method.invoke(target, args);
                    if (ai != null) {
                        return ai;
                    }
                    ComponentName component = (ComponentName) args[0];
                    long flags = ((Number) args[1]).longValue();
                    Context hostApp = HostInfo.getApplication();
                    if (hostApp != null 
                            && hostApp.getPackageName().equals(component.getPackageName())
                            && ActProxyMgr.isModuleProxyActivity(component.getClassName())) {
                        return makeProxyActivityInfo(component.getClassName(), flags);
                    }
                    return null;
                }
                return method.invoke(target, args);
            } catch (InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }

        @Nullable
        private ActivityInfo makeProxyActivityInfo(String className, long flags) {
            try {
                Class.forName(className);
                Context ctx = HostInfo.getApplication();
                if (ctx == null) return null;
                
                String[] candidates = {
                    "com.tencent.mobileqq.activity.QQSettingSettingActivity",
                    "com.tencent.mobileqq.activity.QPublicFragmentActivity"
                };
                for (String activityName : candidates) {
                    try {
                        ActivityInfo proto = ctx.getPackageManager().getActivityInfo(
                            new ComponentName(ctx.getPackageName(), activityName),
                            (int) flags);
                        proto.targetActivity = null;
                        proto.taskAffinity = null;
                        proto.descriptionRes = 0;
                        proto.name = className;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            proto.splitName = null;
                        }
                        proto.configChanges |= ActivityInfo.CONFIG_UI_MODE;
                        return proto;
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
            return null;
        }
    }
}
