package top.galqq.utils;

import android.content.Context;
import android.os.Bundle;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * QQNT 消息发送助手 - 基于真实API实现
 */
public class SendMessageHelper {
    private static final String TAG = "GalQQ.SendMessage";
    
    // 保存AIOSendMsgVMDelegate实例
    private static Object sAIOSendMsgVMDelegate = null;
    
    public static void setAIOSendMsgVMDelegate(Object vmDelegate) {
        sAIOSendMsgVMDelegate = vmDelegate;
        XposedBridge.log(TAG + ": 保存AIOSendMsgVMDelegate实例");
    }
    
    public static void sendMessageNT(Context context, Object msgRecord, String textToSend) {
        try {
            Object peerUid = XposedHelpers.getObjectField(msgRecord, "peerUid");
            String peerUidStr = String.valueOf(peerUid);
            
            XposedBridge.log(TAG + ": sendMessageNT called - peerUid=" + peerUidStr + ", text=" + textToSend);
            
            boolean success = sendTextMessage(context, peerUidStr, textToSend);
            if (!success) {
                android.widget.Toast.makeText(context, "发送失败", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": sendMessageNT 失败");
            XposedBridge.log(t);
            android.widget.Toast.makeText(context, "发送失败: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    public static boolean sendTextMessage(Context context, String peerUid, String messageText) {
        try {
            XposedBridge.log(TAG + ": 准备发送消息到 " + peerUid);
            XposedBridge.log(TAG + ": 消息内容: " + messageText);
            
            ClassLoader classLoader = context.getClassLoader();
            
            // 1. 创建 TextElement 对象
            Object textElement = createTextElement(classLoader, messageText);
            if (textElement == null) {
                return false;
            }
            
            // 2. 创建 msg.data.a 对象
            Object msgData = createMsgDataWithText(classLoader, textElement);
            if (msgData == null) {
                return false;
            }
            
            // 3. 创建消息列表
            List<Object> msgDataList = new ArrayList<>();
            msgDataList.add(msgData);
            
            // 4. 创建Bundle
            Bundle bundle = new Bundle();
            bundle.putString("input_text", messageText);
            bundle.putBoolean("from_send_btn", true);
            bundle.putInt("clearInputStatus", 1);
            
            // 5. 获取 AIOSendMsgVMDelegate 实例
            Object vmDelegate = getAIOSendMsgVMDelegate(context);
            if (vmDelegate == null) {
                return false;
            }
            
            // 6. 动态查找发送方法 (替代硬编码的 "n0")
            Class<?> vmDelegateClass = vmDelegate.getClass();
            Method sendMethod = findSendMethod(vmDelegateClass);
            
            if (sendMethod == null) {
                XposedBridge.log(TAG + ": 未找到符合特征的发送方法(List, Bundle, Long, String)");
                return false;
            }
            
            sendMethod.setAccessible(true);
            sendMethod.invoke(vmDelegate, msgDataList, bundle, null, "");
            
            XposedBridge.log(TAG + ": ✓ 消息发送成功！(Method: " + sendMethod.getName() + ")");
            return true;
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ✗ 发送消息失败");
            XposedBridge.log(t);
            return false;
        }
    }
    
    /**
     * 根据参数特征动态查找发送方法
     * 特征: (List, Bundle, Long, String)
     */
    private static Method findSendMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 4 && 
                paramTypes[0] == List.class && 
                paramTypes[1] == Bundle.class && 
                paramTypes[2] == Long.class && 
                paramTypes[3] == String.class) {
                return method;
            }
        }
        return null;
    }
    
    private static Object createTextElement(ClassLoader classLoader, String content) {
        try {
            Class<?> textElementClass = XposedHelpers.findClass(
                "com.tencent.qqnt.kernel.nativeinterface.TextElement", classLoader);
            
            Constructor<?> constructor = textElementClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object textElement = constructor.newInstance();
            
            XposedHelpers.setObjectField(textElement, "content", content);
            XposedHelpers.setIntField(textElement, "atType", 0);
            XposedHelpers.setLongField(textElement, "atUid", 0L);
            XposedHelpers.setLongField(textElement, "atTinyId", 0L);
            XposedHelpers.setObjectField(textElement, "atNtUid", "");
            
            XposedBridge.log(TAG + ": 创建TextElement成功: " + textElement);
            return textElement;
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 创建TextElement失败: " + t.getMessage());
            XposedBridge.log(t);
            return null;
        }
    }
    
    private static Object createMsgDataWithText(ClassLoader classLoader, Object textElement) {
        try {
            Class<?> msgDataClass = XposedHelpers.findClass(
                "com.tencent.mobileqq.aio.msg.data.a", classLoader);
            
            // 创建msg.data.a对象
            Constructor<?>[] constructors = msgDataClass.getDeclaredConstructors();
            XposedBridge.log(TAG + ": msg.data.a有 " + constructors.length + " 个构造函数");
            
            Object msgData = null;
            if (constructors.length > 0) {
                Constructor<?> constructor = constructors[0];
                constructor.setAccessible(true);
                
                Class<?>[] paramTypes = constructor.getParameterTypes();
                Object[] params = new Object[paramTypes.length];
                
                for (int i = 0; i < paramTypes.length; i++) {
                    if (paramTypes[i] == int.class) {
                        params[i] = 0;
                    } else if (paramTypes[i] == boolean.class) {
                        params[i] = false;
                    } else {
                        params[i] = null;
                    }
                }
                
                msgData = constructor.newInstance(params);
                XposedBridge.log(TAG + ": 使用构造函数创建msg.data.a成功 (" + paramTypes.length + "参数)");
            }
            
            if (msgData == null) {
                XposedBridge.log(TAG + ": 无法创建msg.data.a对象");
                return null;
            }
            
            // 创建 AIOElementType$h 包装对象
            Object hElement = createAIOElementTypeH(classLoader, textElement);
            if (hElement == null) {
                return null;
            }
            
            // 设置字段
            XposedHelpers.setIntField(msgData, "a", 1);
            XposedHelpers.setIntField(msgData, "b", 0);
            XposedHelpers.setObjectField(msgData, "c", hElement);
            
            XposedBridge.log(TAG + ": 创建msg.data.a成功: " + msgData);
            return msgData;
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 创建msg.data.a失败: " + t.getMessage());
            XposedBridge.log(t);
            return null;
        }
    }
    
    private static Object createAIOElementTypeH(ClassLoader classLoader, Object textElement) {
        try {
            Class<?> hClass = XposedHelpers.findClass(
                "com.tencent.qqnt.aio.msg.element.AIOElementType$h", classLoader);
            
            Constructor<?>[] hConstructors = hClass.getDeclaredConstructors();
            XposedBridge.log(TAG + ": AIOElementType$h有 " + hConstructors.length + " 个构造函数");
            
            for (int i = 0; i < hConstructors.length; i++) {
                Class<?>[] paramTypes = hConstructors[i].getParameterTypes();
                XposedBridge.log(TAG + ":   构造函数[" + i + "]: " + Arrays.toString(paramTypes));
            }
            
            // 使用第一个构造函数: (String content, int atType, long atUid, long atTinyId, String atNtUid)
            // 从TextElement对象中提取字段
            String content = (String) XposedHelpers.getObjectField(textElement, "content");
            int atType = XposedHelpers.getIntField(textElement, "atType");
            long atUid = XposedHelpers.getLongField(textElement, "atUid");
            long atTinyId = XposedHelpers.getLongField(textElement, "atTinyId");
            String atNtUid = (String) XposedHelpers.getObjectField(textElement, "atNtUid");
            
            XposedBridge.log(TAG + ": 提取TextElement字段: content=" + content + ", atType=" + atType + 
                ", atUid=" + atUid + ", atTinyId=" + atTinyId + ", atNtUid=" + atNtUid);
            
            if (hConstructors.length > 0) {
                Constructor<?> hConstructor = hConstructors[0];
                hConstructor.setAccessible(true);
                
                Object hElement = hConstructor.newInstance(content, atType, atUid, atTinyId, atNtUid);
                XposedBridge.log(TAG + ": 创建AIOElementType$h成功: " + hElement);
                return hElement;
            }
            
            XposedBridge.log(TAG + ": 无法创建AIOElementType$h，没有可用的构造函数");
            return null;
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 创建AIOElementType$h失败: " + t.getMessage());
            XposedBridge.log(t);
            return null;
        }
    }
    
    private static Object getAIOSendMsgVMDelegate(Context context) {
        if (sAIOSendMsgVMDelegate == null) {
            XposedBridge.log(TAG + ": AIOSendMsgVMDelegate实例为null，请确保已Hook");
        }
        return sAIOSendMsgVMDelegate;
    }
}