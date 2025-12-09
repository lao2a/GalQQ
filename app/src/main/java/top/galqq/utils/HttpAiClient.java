package top.galqq.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Proxy;

import top.galqq.config.ConfigManager;

/**
 * AI客户端 - 支持多种模型和JSON格式响应
 */
public class HttpAiClient {

    private static final String TAG = "GalQQ.AI";
    private static final int MAX_RETRY_COUNT = 5; // 最大重试次数
    private static OkHttpClient client;
    private static OkHttpClient clientWithProxy;
    private static String lastProxyConfig = ""; // 用于检测代理配置变化
    private static int lastTimeout = 0; // 用于检测超时配置变化
    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 获取 OkHttpClient 实例
     * 根据代理配置自动选择是否使用代理
     */
    private static synchronized OkHttpClient getClient() {
        // 检查是否需要使用代理
        if (ConfigManager.isProxyEnabled() && ConfigManager.isProxyConfigValid()) {
            return getClientWithProxy();
        }
        
        // 获取配置的超时时间
        int timeout = ConfigManager.getAiTimeout();
        
        // 检查超时配置是否变化，需要重建客户端
        if (client != null && timeout != lastTimeout) {
            Log.d(TAG, "AI超时配置变化，重建客户端: " + lastTimeout + "s -> " + timeout + "s");
            client = null;
        }
        
        // 不使用代理的客户端
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout * 2, TimeUnit.SECONDS)  // 读取超时设为2倍，给AI足够的响应时间
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .build();
            lastTimeout = timeout;
            Log.d(TAG, "创建AI客户端，超时配置: connect=" + timeout + "s, read=" + (timeout * 2) + "s, write=" + timeout + "s");
        }
        return client;
    }
    
    /**
     * 获取带代理的 OkHttpClient 实例
     * 支持 HTTP 和 SOCKS 代理，以及用户名密码认证
     */
    private static synchronized OkHttpClient getClientWithProxy() {
        // 构建当前代理配置的唯一标识（包含超时配置）
        String currentProxyConfig = buildProxyConfigKey();
        
        // 如果代理配置没有变化，复用现有客户端
        if (clientWithProxy != null && currentProxyConfig.equals(lastProxyConfig)) {
            return clientWithProxy;
        }
        
        // 代理配置变化，重新创建客户端
        String proxyType = ConfigManager.getProxyType();
        String proxyHost = ConfigManager.getProxyHost();
        int proxyPort = ConfigManager.getProxyPort();
        int timeout = ConfigManager.getAiTimeout();
        
        Log.d(TAG, "创建代理客户端: " + proxyType + "://" + proxyHost + ":" + proxyPort + ", 超时: " + timeout + "s");
        
        // 创建代理对象
        Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
        
        // 代理模式下连接超时增加5秒余量
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(timeout + 5, TimeUnit.SECONDS)  // 代理可能需要更长时间
                .readTimeout(timeout * 2 + 10, TimeUnit.SECONDS)
                .writeTimeout(timeout + 5, TimeUnit.SECONDS);
        
        // 如果启用了代理认证
        if (ConfigManager.isProxyAuthEnabled()) {
            String username = ConfigManager.getProxyUsername();
            String password = ConfigManager.getProxyPassword();
            
            if (username != null && !username.isEmpty()) {
                Log.d(TAG, "代理认证已启用，用户名: " + username);
                
                // 添加代理认证器
                builder.proxyAuthenticator(new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(username, password);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    }
                });
            }
        }
        
        clientWithProxy = builder.build();
        lastProxyConfig = currentProxyConfig;
        
        return clientWithProxy;
    }
    
    /**
     * 构建代理配置的唯一标识，用于检测配置变化
     * 包含超时配置，确保超时变化时也会重建客户端
     */
    private static String buildProxyConfigKey() {
        return ConfigManager.getProxyType() + "://" +
               ConfigManager.getProxyHost() + ":" +
               ConfigManager.getProxyPort() + "@" +
               ConfigManager.isProxyAuthEnabled() + ":" +
               ConfigManager.getProxyUsername() + ":" +
               ConfigManager.getAiTimeout();
    }
    
    /**
     * 重置代理客户端（配置变化时调用）
     */
    public static synchronized void resetProxyClient() {
        clientWithProxy = null;
        lastProxyConfig = "";
        Log.d(TAG, "代理客户端已重置");
    }
    
    /**
     * 重置AI客户端（超时配置变化时调用）
     */
    public static synchronized void resetClient() {
        client = null;
        clientWithProxy = null;
        lastTimeout = 0;
        lastProxyConfig = "";
        Log.d(TAG, "AI客户端已重置");
    }
    
    /**
     * 测试代理连接（独立测试，不依赖AI API配置）
     * 通过访问一个简单的HTTPS网站来验证代理是否工作
     */
    public static void testProxyConnection(Context context, ProxyTestCallback callback) {
        if (!ConfigManager.isProxyEnabled()) {
            callback.onResult(false, "代理未启用");
            return;
        }
        
        String host = ConfigManager.getProxyHost();
        int port = ConfigManager.getProxyPort();
        
        if (host == null || host.trim().isEmpty()) {
            callback.onResult(false, "代理地址为空");
            return;
        }
        
        String proxyType = ConfigManager.getProxyType();
        String proxyInfo = proxyType + "://" + host + ":" + port;
        
        Log.d(TAG, "开始测试代理连接: " + proxyInfo);
        
        // 检查是否使用127.0.0.1，给出提示
        boolean isLocalhost = host.equals("127.0.0.1") || host.equals("localhost");
        
        try {
            // 创建专门用于测试的代理客户端
            Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            Proxy proxy = new Proxy(type, new InetSocketAddress(host, port));
            
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS);
            
            // 如果启用了代理认证
            if (ConfigManager.isProxyAuthEnabled()) {
                String username = ConfigManager.getProxyUsername();
                String password = ConfigManager.getProxyPassword();
                
                if (username != null && !username.isEmpty()) {
                    builder.proxyAuthenticator((route, response) -> {
                        String credential = Credentials.basic(username, password);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    });
                }
            }
            
            OkHttpClient testClient = builder.build();
            
            // 使用多个测试URL，增加成功率
            // 优先使用国内可访问的网站
            String[] testUrls = {
                "https://www.baidu.com",           // 百度，国内可直接访问
                "https://httpbin.org/ip",          // httpbin，返回IP
                "https://www.google.com/generate_204"  // Google连通性测试
            };
            
            // 先尝试第一个URL
            testProxyWithUrl(testClient, testUrls, 0, proxyInfo, isLocalhost, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "创建代理测试客户端失败", e);
            callback.onResult(false, "创建代理客户端失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用指定URL测试代理，失败时尝试下一个URL
     */
    private static void testProxyWithUrl(OkHttpClient testClient, String[] testUrls, int index, 
                                         String proxyInfo, boolean isLocalhost, ProxyTestCallback callback) {
        if (index >= testUrls.length) {
            // 所有URL都失败了
            String extraTip = isLocalhost ? 
                "\n\n提示: 你使用的是127.0.0.1，这指向手机本身。如果代理运行在电脑上，请使用电脑的局域网IP（如192.168.x.x）" : "";
            mainHandler.post(() -> callback.onResult(false, "所有测试URL均失败" + extraTip + "\n代理: " + proxyInfo));
            return;
        }
        
        String testUrl = testUrls[index];
        Log.d(TAG, "测试代理URL[" + index + "]: " + testUrl);
        
        Request request = new Request.Builder()
                .url(testUrl)
                .get()
                .build();
        
        testClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMsg = e.getMessage();
                Log.w(TAG, "代理测试URL[" + index + "]失败: " + errorMsg);
                
                // 尝试下一个URL
                testProxyWithUrl(testClient, testUrls, index + 1, proxyInfo, isLocalhost, callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    int code = response.code();
                    // 200, 204 都算成功
                    if (code == 200 || code == 204) {
                        String body = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "代理测试成功，URL: " + testUrl + ", 响应码: " + code);
                        
                        // 尝试解析返回的IP（如果是httpbin）
                        String resultMsg = "代理连接正常";
                        if (testUrl.contains("httpbin")) {
                            try {
                                JSONObject json = new JSONObject(body);
                                String origin = json.optString("origin", "");
                                if (!origin.isEmpty()) {
                                    resultMsg = "代理连接正常\n出口IP: " + origin;
                                }
                            } catch (Exception e) {
                                // 忽略解析错误
                            }
                        }
                        
                        final String finalMsg = resultMsg;
                        mainHandler.post(() -> callback.onResult(true, finalMsg + "\n代理: " + proxyInfo));
                    } else {
                        // 非成功状态码，尝试下一个URL
                        Log.w(TAG, "代理测试URL[" + index + "]返回非成功状态: " + code);
                        testProxyWithUrl(testClient, testUrls, index + 1, proxyInfo, isLocalhost, callback);
                    }
                } finally {
                    response.close();
                }
            }
        });
    }
    
    /**
     * 代理测试回调接口
     */
    public interface ProxyTestCallback {
        void onResult(boolean success, String message);
    }

    public interface AiCallback {
        void onSuccess(List<String> options);
        void onFailure(Exception e);
    }

    /**
     * 扩展回调接口 - 支持重试失败后显示重新加载按钮
     */
    public interface AiCallbackWithRetry extends AiCallback {
        /**
         * 所有重试都失败后调用，提供重新加载的Runnable
         * @param retryAction 点击"重新加载"按钮时执行的动作
         */
        void onAllRetriesFailed(Runnable retryAction);
    }

    /**
     * 获取AI生成的回复选项（无上下文和元数据，向后兼容）
     */
    public static void fetchOptions(Context context, String userMessage, AiCallback callback) {
        fetchOptions(context, userMessage, null, 0, null, callback);
    }

    /**
     * 获取AI生成的回复选项（带自动重试功能）
     * 格式错误时自动重试，最多重试MAX_RETRY_COUNT次
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param callback 支持重试的回调
     */
    public static void fetchOptionsWithRetry(Context context, String userMessage,
                                              String currentSenderName, long currentTimestamp,
                                              List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                              AiCallbackWithRetry callback) {
        fetchOptionsWithRetryInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                       contextMessages, callback, 0);
    }

    /**
     * 内部重试实现
     */
    private static void fetchOptionsWithRetryInternal(Context context, String userMessage,
                                                       String currentSenderName, long currentTimestamp,
                                                       List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                                       AiCallbackWithRetry callback, int retryCount) {
        
        // 创建重试动作
        Runnable retryAction = () -> {
            Log.d(TAG, "用户点击重新加载");
            fetchOptionsWithRetryInternal(context, userMessage, currentSenderName, currentTimestamp,
                                          contextMessages, callback, 0);
        };

        // 【关键】所有重试过程中都抑制Toast，只有最终失败时才显示
        // 第一次请求也抑制Toast，因为可能会自动重试
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, null, null, new AiCallback() {
            @Override
            public void onSuccess(List<String> options) {
                callback.onSuccess(options);
            }

            @Override
            public void onFailure(Exception e) {
                // 检查是否是格式错误（可重试的错误）
                // 扩展判断条件，包括更多可能的格式错误信息
                String errorMsg = e.getMessage();
                boolean isFormatError = errorMsg != null && 
                    (errorMsg.contains("格式") || 
                     errorMsg.contains("选项不足") ||
                     errorMsg.contains("无法识别") ||
                     errorMsg.contains("解析") ||
                     errorMsg.contains("parse"));
                
                if (isFormatError && retryCount < MAX_RETRY_COUNT - 1) {
                    // 还有重试机会，静默重试（不显示任何提示）
                    int nextRetry = retryCount + 1;
                    Log.d(TAG, "格式错误，静默重试 (" + nextRetry + "/" + MAX_RETRY_COUNT + "): " + errorMsg);
                    
                    // 延迟500ms后重试，避免请求过快
                    mainHandler.postDelayed(() -> {
                        fetchOptionsWithRetryInternal(context, userMessage, currentSenderName, 
                                                      currentTimestamp, contextMessages, callback, nextRetry);
                    }, 500);
                } else if (isFormatError) {
                    // 达到最大重试次数，通知显示重新加载按钮（不显示Toast）
                    Log.w(TAG, "达到最大重试次数 (" + MAX_RETRY_COUNT + ")，显示重新加载按钮");
                    logError(context, ConfigManager.getAiProvider(), ConfigManager.getAiModel(), 
                            ConfigManager.getApiUrl(), 
                            "AI返回格式错误，已重试" + MAX_RETRY_COUNT + "次仍失败");
                    callback.onAllRetriesFailed(retryAction);
                } else {
                    // 非格式错误（如网络错误），直接失败
                    callback.onFailure(e);
                }
            }
        }, true); // 【修改】始终抑制Toast，让重试逻辑决定是否显示
    }

    /**
     * 获取AI生成的回复选项（带上下文和当前消息元数据）
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param callback 回调
     */
    public static void fetchOptions(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    AiCallback callback) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, null, null, callback, false);
    }
    
    /**
     * 获取AI生成的回复选项（静默模式，不显示Toast）
     * 用于队列重试场景
     */
    public static void fetchOptionsSilent(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    AiCallback callback) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, null, null, null, null, callback, true);
    }
    
    /**
     * 获取AI生成的回复选项（带发送者QQ，静默模式）
     * 用于队列重试场景，支持好感度
     * 
     * @param senderUin 发送者QQ号（用于获取好感度）
     */
    public static void fetchOptionsSilent(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String senderUin,
                                    AiCallback callback) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, null, null, null, senderUin, callback, true);
    }
    
    /**
     * 获取AI生成的回复选项（带自定义提示词）
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param customPrompt 自定义提示词内容（如果为null则使用默认）
     * @param callback 回调
     */
    public static void fetchOptionsWithPrompt(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    AiCallback callback) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, customPrompt, null, callback, false);
    }
    
    /**
     * 获取AI生成的回复选项（带自定义提示词，静默模式）
     * 用于队列重试场景
     */
    public static void fetchOptionsWithPromptSilent(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    AiCallback callback) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, customPrompt, null, null, null, callback, true);
    }
    
    /**
     * 获取AI生成的回复选项（带自定义提示词、发送者QQ，静默模式）
     * 用于队列重试场景，支持好感度
     * 
     * @param senderUin 发送者QQ号（用于获取好感度）
     */
    public static void fetchOptionsWithPromptSilent(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    String senderUin,
                                    AiCallback callback) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                            contextMessages, customPrompt, null, null, senderUin, callback, true);
    }
    
    /**
     * 获取AI生成的回复选项（带图片信息）
     * 当消息包含图片时，先通过外挂AI获取图片描述，再调用主AI
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param customPrompt 自定义提示词内容（如果为null则使用默认）
     * @param imageElements 图片元素列表（可为null）
     * @param callback 回调
     */
    public static void fetchOptionsWithImages(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<ImageExtractor.ImageElement> imageElements,
                                    AiCallback callback) {
        fetchOptionsWithImages(context, userMessage, currentSenderName, currentTimestamp,
                              contextMessages, customPrompt, imageElements, null, null, callback);
    }
    
    /**
     * 获取AI生成的回复选项（带图片信息和缓存支持）
     * 当消息包含图片时，先通过外挂AI获取图片描述，再调用主AI
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param customPrompt 自定义提示词内容（如果为null则使用默认）
     * @param imageElements 图片元素列表（可为null）
     * @param conversationId 会话ID（用于缓存）
     * @param msgId 消息ID（用于缓存和标识）
     * @param callback 回调
     */
    public static void fetchOptionsWithImages(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<ImageExtractor.ImageElement> imageElements,
                                    String conversationId, String msgId,
                                    AiCallback callback) {
        fetchOptionsWithImages(context, userMessage, currentSenderName, currentTimestamp,
                              contextMessages, customPrompt, imageElements, conversationId, msgId, null, callback);
    }
    
    /**
     * 获取AI生成的回复选项（带图片信息、缓存支持和发送者QQ）
     * 当消息包含图片时，先通过外挂AI获取图片描述，再调用主AI
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param customPrompt 自定义提示词内容（如果为null则使用默认）
     * @param imageElements 图片元素列表（可为null）
     * @param conversationId 会话ID（用于缓存）
     * @param msgId 消息ID（用于缓存和标识）
     * @param senderUin 发送者QQ号（用于获取好感度）
     * @param callback 回调
     */
    public static void fetchOptionsWithImages(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<ImageExtractor.ImageElement> imageElements,
                                    String conversationId, String msgId,
                                    String senderUin,
                                    AiCallback callback) {
        // 如果没有图片或图片识别未启用
        if (imageElements == null || imageElements.isEmpty() || !ConfigManager.isImageRecognitionEnabled()) {
            // 检查是否需要处理上下文图片（不再要求必须启用外挂AI）
            boolean needContextImageRecognition = ConfigManager.isContextImageRecognitionEnabled()
                                                && ConfigManager.isImageRecognitionEnabled()
                                                && conversationId != null
                                                && contextMessages != null
                                                && hasContextImages(contextMessages);
            
            if (needContextImageRecognition) {
                // 有上下文图片需要处理，在后台线程处理
                Log.d(TAG, "当前消息无图片，但有上下文图片需要处理");
                final String finalSenderUin = senderUin;
                new Thread(() -> {
                    try {
                        // 根据是否启用外挂AI选择处理方式
                        if (ConfigManager.isVisionAiEnabled()) {
                            recognizeContextImages(context, conversationId, contextMessages);
                        } else {
                            processContextImagesForMainAi(context, conversationId, contextMessages);
                        }
                        mainHandler.post(() -> {
                            fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                                contextMessages, customPrompt, null, conversationId, finalSenderUin, callback, false);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "上下文图片识别失败: " + e.getMessage());
                        mainHandler.post(() -> {
                            fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                                contextMessages, customPrompt, null, conversationId, finalSenderUin, callback, false);
                        });
                    }
                }).start();
            } else {
                // 不需要处理上下文图片，直接调用
                fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                    contextMessages, customPrompt, null, null, senderUin, callback, false);
            }
            return;
        }
        
        // 检查是否启用外挂AI
        Log.d(TAG, "fetchOptionsWithImages: 图片数量=" + imageElements.size() + ", VisionAI启用=" + ConfigManager.isVisionAiEnabled());
        
        if (ConfigManager.isVisionAiEnabled()) {
            // 使用外挂AI获取图片描述
            Log.d(TAG, "使用外挂AI处理图片");
            processImagesWithVisionAi(context, userMessage, currentSenderName, currentTimestamp,
                                      contextMessages, customPrompt, imageElements, 
                                      conversationId, msgId, senderUin, callback);
        } else {
            // 未启用外挂AI，将图片Base64直接发送给主AI（如果主AI支持Vision）
            Log.d(TAG, "未启用外挂AI，尝试直接获取图片Base64");
            
            // 【上下文图片识别】如果启用了上下文图片识别，也需要处理上下文中的图片
            final boolean contextImageEnabled = ConfigManager.isContextImageRecognitionEnabled() 
                                              && conversationId != null 
                                              && contextMessages != null
                                              && hasContextImages(contextMessages);
            
            if (contextImageEnabled) {
                // 在后台线程处理上下文图片和当前图片
                Log.d(TAG, "启用了上下文图片识别，在后台线程处理所有图片");
                final String finalConversationId = conversationId;
                final String finalSenderUin = senderUin;
                new Thread(() -> {
                    try {
                        // 处理上下文图片
                        processContextImagesForMainAi(context, finalConversationId, contextMessages);
                        
                        // 处理当前消息图片
                        List<String> imageBase64List = new java.util.ArrayList<>();
                        for (int i = 0; i < imageElements.size(); i++) {
                            ImageExtractor.ImageElement img = imageElements.get(i);
                            String base64 = ImageBase64Helper.fromImageElement(img);
                            if (base64 != null) {
                                imageBase64List.add(base64);
                            }
                        }
                        
                        // 在主线程调用AI
                        mainHandler.post(() -> {
                            if (!imageBase64List.isEmpty()) {
                                fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                                    contextMessages, customPrompt, imageBase64List, finalConversationId, finalSenderUin, callback, false);
                            } else {
                                fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                                    contextMessages, customPrompt, null, finalConversationId, finalSenderUin, callback, false);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "处理图片失败: " + e.getMessage());
                        mainHandler.post(() -> {
                            fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                                contextMessages, customPrompt, null, null, finalSenderUin, callback, false);
                        });
                    }
                }).start();
                return;
            }
            
            // 不需要处理上下文图片，直接处理当前消息图片
            List<String> imageBase64List = new java.util.ArrayList<>();
            for (int i = 0; i < imageElements.size(); i++) {
                ImageExtractor.ImageElement img = imageElements.get(i);
                Log.d(TAG, "处理图片 " + (i + 1) + "/" + imageElements.size() + ": " + img);
                String base64 = ImageBase64Helper.fromImageElement(img);
                if (base64 != null) {
                    imageBase64List.add(base64);
                    Log.d(TAG, "图片 " + (i + 1) + " Base64获取成功，长度=" + base64.length());
                } else {
                    Log.w(TAG, "图片 " + (i + 1) + " Base64获取失败");
                }
            }
            
            Log.d(TAG, "成功获取 " + imageBase64List.size() + "/" + imageElements.size() + " 张图片的Base64");
            
            if (!imageBase64List.isEmpty()) {
                fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                    contextMessages, customPrompt, imageBase64List, null, senderUin, callback, false);
            } else {
                // 无法获取图片Base64，降级为普通请求
                Log.w(TAG, "无法获取任何图片Base64，降级为普通请求");
                fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                    contextMessages, customPrompt, null, null, senderUin, callback, false);
            }
        }
    }
    
    /**
     * 使用外挂AI处理图片，获取描述后再调用主AI
     * 支持缓存和速率限制
     * 
     * @param conversationId 会话ID（用于缓存）
     * @param msgId 消息ID（用于缓存和标识）
     */
    private static void processImagesWithVisionAi(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<ImageExtractor.ImageElement> imageElements,
                                    AiCallback callback) {
        processImagesWithVisionAi(context, userMessage, currentSenderName, currentTimestamp,
                                  contextMessages, customPrompt, imageElements, null, null, null, callback);
    }
    
    /**
     * 使用外挂AI处理图片，获取描述后再调用主AI
     * 支持缓存和速率限制
     * 
     * @param conversationId 会话ID（用于缓存）
     * @param msgId 消息ID（用于缓存和标识）
     */
    private static void processImagesWithVisionAi(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<ImageExtractor.ImageElement> imageElements,
                                    String conversationId, String msgId,
                                    String senderUin,
                                    AiCallback callback) {
        // 在后台线程处理图片
        new Thread(() -> {
            try {
                // 【上下文图片识别】先识别上下文中未识别的图片
                if (ConfigManager.isContextImageRecognitionEnabled() && conversationId != null && contextMessages != null) {
                    recognizeContextImages(context, conversationId, contextMessages);
                }
                
                List<String> imageDescriptions;
                
                // 如果有会话ID和消息ID，使用带缓存的队列
                if (conversationId != null && msgId != null) {
                    Log.d(TAG, "使用VisionAiQueue处理图片，conversationId=" + conversationId + ", msgId=" + msgId);
                    imageDescriptions = VisionAiQueue.getInstance().recognizeSync(
                        context, conversationId, msgId, imageElements);
                } else {
                    // 降级：直接处理（不缓存）
                    Log.d(TAG, "无会话/消息ID，直接处理图片");
                    imageDescriptions = new java.util.ArrayList<>();
                    
                    for (int i = 0; i < imageElements.size(); i++) {
                        ImageExtractor.ImageElement img = imageElements.get(i);
                        String base64 = ImageBase64Helper.fromImageElement(img);
                        
                        if (base64 != null) {
                            Log.d(TAG, "正在识别图片 " + (i + 1) + "/" + imageElements.size());
                            String description = VisionAiClient.analyzeImageSync(base64);
                            
                            if (description != null && !description.isEmpty()) {
                                imageDescriptions.add(description);
                                Log.d(TAG, "图片" + (i + 1) + "描述: " + description);
                            } else {
                                imageDescriptions.add("[图片识别失败]");
                                Log.w(TAG, "图片" + (i + 1) + "识别失败");
                            }
                        } else {
                            imageDescriptions.add("[无法读取图片]");
                            Log.w(TAG, "图片" + (i + 1) + "无法读取");
                        }
                    }
                }
                
                // 合并图片描述到消息内容
                String mergedMessage = ImageContextManager.mergeImageContext(
                    userMessage, imageDescriptions, null);
                
                // 记录图片识别日志
                AiLogManager.logImageRecognition(context, imageElements.size(), 0, 
                    imageDescriptions, System.currentTimeMillis());
                
                // 在主线程调用主AI（传递conversationId和senderUin用于上下文图片和好感度）
                final String finalConversationId = conversationId;
                final String finalSenderUin = senderUin;
                mainHandler.post(() -> {
                    fetchOptionsInternal(context, mergedMessage, currentSenderName, currentTimestamp, 
                                        contextMessages, customPrompt, null, finalConversationId, finalSenderUin, callback, false);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "图片处理失败", e);
                AiLogManager.logImageRecognitionError(context, imageElements.size(), e.getMessage());
                
                // 降级为不带图片的请求
                final String finalConversationId = conversationId;
                final String finalSenderUin = senderUin;
                mainHandler.post(() -> {
                    fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp, 
                                        contextMessages, customPrompt, null, finalConversationId, finalSenderUin, callback, false);
                });
            }
        }).start();
    }
    
    /**
     * 检查上下文消息中是否有图片
     * @param contextMessages 上下文消息列表
     * @return true 如果有图片
     */
    private static boolean hasContextImages(List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages) {
        if (contextMessages == null || contextMessages.isEmpty()) {
            return false;
        }
        for (top.galqq.utils.MessageContextManager.ChatMessage msg : contextMessages) {
            // 检查 hasImages 标记
            if (msg.hasImages && msg.imageCount > 0) {
                return true;
            }
            // 也检查消息内容中是否包含图片URL（兼容历史消息）
            if (msg.content != null && msg.content.contains("[图片:") && msg.content.contains("multimedia.nt.qq.com.cn")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 处理上下文图片（用于主AI直接识别，不使用外挂AI）
     * 将上下文消息中的图片转换为base64并缓存
     * 
     * @param context Android上下文
     * @param conversationId 会话ID
     * @param contextMessages 上下文消息列表
     */
    private static void processContextImagesForMainAi(Context context, String conversationId,
                                                      List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages) {
        if (contextMessages == null || contextMessages.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "开始处理上下文图片（主AI模式），消息数=" + contextMessages.size());
        
        for (top.galqq.utils.MessageContextManager.ChatMessage msg : contextMessages) {
            // 方式1：使用缓存的图片元素
            if (msg.hasImages && msg.msgId != null && msg.imageCount > 0) {
                // 检查是否已经全部处理过
                if (ImageDescriptionCache.hasAll(conversationId, msg.msgId, msg.imageCount)) {
                    Log.d(TAG, "消息 " + msg.msgId + " 的图片已全部处理，跳过");
                    continue;
                }
                
                // 获取缓存的图片元素
                java.util.List<ImageExtractor.ImageElement> imageElements = 
                    ImageDescriptionCache.getImageElements(conversationId, msg.msgId);
                
                if (imageElements != null && !imageElements.isEmpty()) {
                    Log.d(TAG, "处理消息 " + msg.msgId + " 的 " + imageElements.size() + " 张图片（使用缓存元素）");
                    
                    for (int i = 0; i < imageElements.size(); i++) {
                        if (ImageDescriptionCache.has(conversationId, msg.msgId, i)) {
                            continue;
                        }
                        
                        ImageExtractor.ImageElement img = imageElements.get(i);
                        String base64 = ImageBase64Helper.fromImageElement(img);
                        
                        if (base64 != null) {
                            ImageDescriptionCache.put(conversationId, msg.msgId, i, "BASE64:" + base64);
                            Log.d(TAG, "图片 " + (i + 1) + " base64获取成功，长度=" + base64.length());
                        } else {
                            ImageDescriptionCache.put(conversationId, msg.msgId, i, "[无法读取图片]");
                            Log.w(TAG, "图片 " + (i + 1) + " base64获取失败");
                        }
                    }
                    continue;
                }
            }
            
            // 方式2：从消息内容中提取图片URL（兼容历史消息）
            if (msg.content != null && msg.content.contains("[图片:") && msg.content.contains("multimedia.nt.qq.com.cn")) {
                Log.d(TAG, "从消息内容中提取图片URL: " + msg.msgId);
                
                // 使用正则表达式提取图片URL
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\[图片:\\s*(https?://[^\\s\\]]+)");
                java.util.regex.Matcher matcher = pattern.matcher(msg.content);
                
                int imageIndex = 0;
                while (matcher.find()) {
                    String imageUrl = matcher.group(1);
                    // 移除可能的尺寸信息
                    if (imageUrl.contains(" (")) {
                        imageUrl = imageUrl.substring(0, imageUrl.indexOf(" ("));
                    }
                    
                    String cacheKey = msg.msgId != null ? msg.msgId : ("url_" + imageUrl.hashCode());
                    
                    // 检查是否已处理
                    if (ImageDescriptionCache.has(conversationId, cacheKey, imageIndex)) {
                        imageIndex++;
                        continue;
                    }
                    
                    Log.d(TAG, "下载图片: " + imageUrl);
                    
                    // 下载图片并转换为base64
                    try {
                        String base64 = ImageDownloader.downloadAndConvertToBase64ByUrl(imageUrl, context);
                        if (base64 != null && !base64.isEmpty()) {
                            // 添加MIME前缀
                            String mimeType = ImageDownloader.getMimeTypeFromUrl(imageUrl);
                            String fullBase64 = "data:" + mimeType + ";base64," + base64;
                            ImageDescriptionCache.put(conversationId, cacheKey, imageIndex, "BASE64:" + fullBase64);
                            Log.d(TAG, "图片下载成功，base64长度=" + base64.length());
                        } else {
                            ImageDescriptionCache.put(conversationId, cacheKey, imageIndex, "[无法下载图片]");
                            Log.w(TAG, "图片下载失败");
                        }
                    } catch (Exception e) {
                        ImageDescriptionCache.put(conversationId, cacheKey, imageIndex, "[下载图片异常: " + e.getMessage() + "]");
                        Log.e(TAG, "下载图片异常: " + e.getMessage());
                    }
                    
                    imageIndex++;
                }
            }
        }
        
        Log.d(TAG, "上下文图片处理完成（主AI模式）");
    }
    
    /**
     * 识别上下文消息中的图片
     * 遍历上下文消息，对于有图片但未识别的消息，调用外挂AI进行识别
     * 
     * @param context Android上下文
     * @param conversationId 会话ID
     * @param contextMessages 上下文消息列表
     */
    private static void recognizeContextImages(Context context, String conversationId,
                                               List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages) {
        if (contextMessages == null || contextMessages.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "开始识别上下文图片，消息数=" + contextMessages.size());
        
        for (top.galqq.utils.MessageContextManager.ChatMessage msg : contextMessages) {
            if (!msg.hasImages || msg.msgId == null || msg.imageCount <= 0) {
                continue;
            }
            
            // 检查是否已经全部识别过
            if (ImageDescriptionCache.hasAll(conversationId, msg.msgId, msg.imageCount)) {
                Log.d(TAG, "消息 " + msg.msgId + " 的图片已全部识别，跳过");
                continue;
            }
            
            // 获取缓存的图片元素
            java.util.List<ImageExtractor.ImageElement> imageElements = 
                ImageDescriptionCache.getImageElements(conversationId, msg.msgId);
            
            if (imageElements == null || imageElements.isEmpty()) {
                Log.d(TAG, "消息 " + msg.msgId + " 没有缓存的图片元素，跳过");
                continue;
            }
            
            Log.d(TAG, "识别消息 " + msg.msgId + " 的 " + imageElements.size() + " 张图片");
            
            // 使用 VisionAiQueue 同步识别（会自动使用缓存和速率限制）
            try {
                VisionAiQueue.getInstance().recognizeSync(context, conversationId, msg.msgId, imageElements);
            } catch (Exception e) {
                Log.e(TAG, "识别上下文图片失败: " + e.getMessage());
            }
        }
        
        Log.d(TAG, "上下文图片识别完成");
    }

    /**
     * 内部实现 - 获取AI生成的回复选项
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param customPrompt 自定义提示词内容（如果为null则使用默认）
     * @param imageBase64List 图片Base64编码列表（可为null，用于直接发送图片给支持Vision的AI）
     * @param callback 回调
     * @param suppressToast 是否抑制Toast提示（重试时使用）
     */
    private static void fetchOptionsInternal(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<String> imageBase64List,
                                    AiCallback callback, boolean suppressToast) {
        fetchOptionsInternal(context, userMessage, currentSenderName, currentTimestamp,
                            contextMessages, customPrompt, imageBase64List, null, null, callback, suppressToast);
    }
    
    /**
     * 内部实现 - 获取AI生成的回复选项（带会话ID支持上下文图片）
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param customPrompt 自定义提示词内容（如果为null则使用默认）
     * @param imageBase64List 图片Base64编码列表（可为null，用于直接发送图片给支持Vision的AI）
     * @param conversationId 会话ID（用于上下文图片缓存）
     * @param senderUin 发送者QQ号（用于获取好感度，可为null）
     * @param callback 回调
     * @param suppressToast 是否抑制Toast提示（重试时使用）
     */
    private static void fetchOptionsInternal(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    String customPrompt,
                                    List<String> imageBase64List,
                                    String conversationId,
                                    String senderUin,
                                    AiCallback callback, boolean suppressToast) {
        String apiUrl = normalizeApiUrl(ConfigManager.getApiUrl());
        String apiKey = ConfigManager.getApiKey();
        // 使用自定义提示词或默认提示词
        String sysPrompt = (customPrompt != null && !customPrompt.isEmpty()) 
                ? customPrompt : ConfigManager.getSysPrompt();
        String model = ConfigManager.getAiModel();
        String provider = ConfigManager.getAiProvider();
        float temperature = ConfigManager.getAiTemperature();
        int maxTokens = ConfigManager.getAiMaxTokens();

        // 验证配置
        if (TextUtils.isEmpty(apiUrl) || TextUtils.isEmpty(apiKey)) {
            String error = "API配置不完整";
            logError(context, provider, model, apiUrl, error);
            showToast(context, "AI服务未配置 😢");
            callback.onFailure(new IllegalArgumentException(error));
            return;
        }

        try {
            // 构建请求体
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", model);
            
            // 可选参数：只在合理范围内添加
            if (temperature > 0 && temperature <= 2.0) {
                jsonBody.put("temperature", temperature);
            }
            if (maxTokens > 0 && maxTokens <= 4096) {
                jsonBody.put("max_tokens", maxTokens);
            }
            
            // 添加 reasoning_effort 参数（如果启用）
            if (ConfigManager.isReasoningEffortEnabled()) {
                String reasoningEffort = ConfigManager.getAiReasoningEffort();
                jsonBody.put("reasoning_effort", reasoningEffort);
                Log.d(TAG, "启用思考模式: reasoning_effort=" + reasoningEffort);
            }

            JSONArray messages = new JSONArray();
            
            // 系统提示词
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", sysPrompt);
            messages.put(sysMsg);

            // 添加历史上下文（如果有）
            if (contextMessages != null && !contextMessages.isEmpty()) {
                // 创建时间格式化器
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
                
                // 检查是否启用上下文图片识别（不再要求必须启用外挂AI）
                boolean contextImageEnabled = ConfigManager.isContextImageRecognitionEnabled() 
                                            && ConfigManager.isImageRecognitionEnabled()
                                            && conversationId != null;
                
                for (top.galqq.utils.MessageContextManager.ChatMessage msg : contextMessages) {
                    JSONObject ctxMsg = new JSONObject();
                    // 对方的消息作为"user"，自己的消息作为"assistant"
                    ctxMsg.put("role", msg.isSelf ? "assistant" : "user");
                    
                    // 格式化时间戳
                    String timeStr = timeFormat.format(new java.util.Date(msg.timestamp));
                    
                    // 获取消息内容
                    String msgContent = msg.content;
                    
                    // 【修复】如果图片识别关闭，过滤掉消息内容中的图片信息
                    if (!ConfigManager.isImageRecognitionEnabled() && msgContent != null) {
                        // 移除 [图片: URL (宽x高)] 格式的内容
                        msgContent = msgContent.replaceAll("\\[图片:[^\\]]*\\]", "").trim();
                        // 移除 [图片内容:\n  图1: ...\n  图2: ...] 格式的内容
                        msgContent = msgContent.replaceAll("\\[图片内容:[^\\]]*\\]", "").trim();
                    }
                    
                    // 如果启用上下文图片识别，尝试获取缓存的图片描述或base64
                    // 扩展条件：检查 hasImages 或消息内容中包含图片URL
                    boolean hasImageContent = msg.hasImages && msg.imageCount > 0;
                    boolean hasImageUrl = msgContent != null && msgContent.contains("[图片:") && msgContent.contains("multimedia.nt.qq.com.cn");
                    
                    if (contextImageEnabled && (hasImageContent || hasImageUrl)) {
                        java.util.List<String> base64Images = new java.util.ArrayList<>();
                        java.util.List<String> textDescriptions = new java.util.ArrayList<>();
                        boolean hasBase64Images = false;
                        
                        // 方式1：从 hasImages 标记的消息获取缓存
                        if (hasImageContent && msg.msgId != null) {
                            java.util.List<String> cachedDescriptions = ImageDescriptionCache.getAll(conversationId, msg.msgId, msg.imageCount);
                            for (String cached : cachedDescriptions) {
                                if (cached != null && cached.startsWith("BASE64:")) {
                                    hasBase64Images = true;
                                    base64Images.add(cached.substring(7)); // 去掉 "BASE64:" 前缀
                                } else if (cached != null) {
                                    textDescriptions.add(cached);
                                }
                            }
                        }
                        
                        // 方式2：从消息内容中提取的图片URL获取缓存
                        if (!hasBase64Images && hasImageUrl) {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                                "\\[图片:\\s*(https?://[^\\s\\]]+)");
                            java.util.regex.Matcher matcher = pattern.matcher(msg.content);
                            
                            int imageIndex = 0;
                            while (matcher.find()) {
                                String imageUrl = matcher.group(1);
                                if (imageUrl.contains(" (")) {
                                    imageUrl = imageUrl.substring(0, imageUrl.indexOf(" ("));
                                }
                                
                                String cacheKey = msg.msgId != null ? msg.msgId : ("url_" + imageUrl.hashCode());
                                String cached = ImageDescriptionCache.get(conversationId, cacheKey, imageIndex);
                                
                                if (cached != null && cached.startsWith("BASE64:")) {
                                    hasBase64Images = true;
                                    base64Images.add(cached.substring(7));
                                } else if (cached != null) {
                                    textDescriptions.add(cached);
                                }
                                
                                imageIndex++;
                            }
                        }
                        
                        if (hasBase64Images) {
                            // 有base64图片，需要构建带图片的content数组
                            JSONArray contentArray = new JSONArray();
                            
                            // 构建文本内容（使用新格式）
                            StringBuilder textContentBuilder = new StringBuilder();
                            
                            // 添加好感度（如果启用且有senderUin）
                            if (ConfigManager.isAffinityEnabled() && ConfigManager.isAiIncludeAffinity() 
                                && msg.senderUin != null && !msg.senderUin.isEmpty() && !msg.isSelf) {
                                try {
                                    AffinityManager affinityManager = AffinityManager.getInstance(context);
                                    int affinity = affinityManager.getAffinity(msg.senderUin);
                                    if (affinity >= 0) {
                                        textContentBuilder.append("[好感度:").append(affinity).append("]");
                                    }
                                } catch (Throwable t) {
                                    // 忽略好感度获取失败
                                }
                            }
                            
                            // 添加发送人名称
                            String displayName = (msg.senderName != null && !msg.senderName.isEmpty()) 
                                ? msg.senderName : "昵称获取失败";
                            textContentBuilder.append(displayName);
                            
                            // 添加[我]标记
                            if (msg.isSelf) {
                                textContentBuilder.append("[我]");
                            }
                            
                            // 添加QQ号
                            if (msg.senderUin != null && !msg.senderUin.isEmpty()) {
                                textContentBuilder.append("[").append(msg.senderUin).append("]");
                            }
                            
                            // 添加时间和内容
                            textContentBuilder.append(" [").append(timeStr).append("]: ").append(msgContent);
                            
                            if (!textDescriptions.isEmpty()) {
                                textContentBuilder.append("\n[图片描述: ").append(String.join(", ", textDescriptions)).append("]");
                            }
                            
                            JSONObject textObj = new JSONObject();
                            textObj.put("type", "text");
                            textObj.put("text", textContentBuilder.toString());
                            contentArray.put(textObj);
                            
                            // 添加图片
                            for (String base64 : base64Images) {
                                JSONObject imageContent = new JSONObject();
                                imageContent.put("type", "image_url");
                                JSONObject imageUrlObj = new JSONObject();
                                imageUrlObj.put("url", base64); // base64已经带有data:image前缀
                                imageUrlObj.put("detail", "low");
                                imageContent.put("image_url", imageUrlObj);
                                contentArray.put(imageContent);
                            }
                            
                            ctxMsg.put("content", contentArray);
                            messages.put(ctxMsg);
                            continue; // 跳过下面的普通处理
                        } else if (!textDescriptions.isEmpty()) {
                            // 只有文字描述（外挂AI识别的结果）
                            msgContent = msg.getContentWithImageDescriptions(conversationId);
                        }
                    }
                    
                    // 格式化为 "[好感度]发送人[我][qq号][时间]: 消息内容"
                    StringBuilder formattedContent = new StringBuilder();
                    
                    // 添加好感度（如果启用且有senderUin）
                    if (ConfigManager.isAffinityEnabled() && ConfigManager.isAiIncludeAffinity() 
                        && msg.senderUin != null && !msg.senderUin.isEmpty() && !msg.isSelf) {
                        try {
                            AffinityManager affinityManager = AffinityManager.getInstance(context);
                            int affinity = affinityManager.getAffinity(msg.senderUin);
                            if (affinity >= 0) {
                                formattedContent.append("[好感度:").append(affinity).append("]");
                            }
                        } catch (Throwable t) {
                            // 忽略好感度获取失败
                        }
                    }
                    
                    // 添加发送人名称（如果获取失败显示"昵称获取失败"）
                    String displayName = (msg.senderName != null && !msg.senderName.isEmpty()) 
                        ? msg.senderName : "昵称获取失败";
                    formattedContent.append(displayName);
                    
                    // 添加[我]标记（如果是自己发送的）
                    if (msg.isSelf) {
                        formattedContent.append("[我]");
                    }
                    
                    // 添加QQ号（如果有）
                    if (msg.senderUin != null && !msg.senderUin.isEmpty()) {
                        formattedContent.append("[").append(msg.senderUin).append("]");
                    }
                    
                    // 添加时间和内容
                    formattedContent.append(" [").append(timeStr).append("]: ").append(msgContent);
                    
                    ctxMsg.put("content", formattedContent.toString());
                    messages.put(ctxMsg);
                }
                Log.i(TAG, "Added " + contextMessages.size() + " context messages");
            }

            // 当前用户消息（添加特殊标注）
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            
            // 【修复】如果图片识别关闭，过滤掉当前消息中的图片信息
            String filteredUserMessage = userMessage;
            if (!ConfigManager.isImageRecognitionEnabled() && filteredUserMessage != null) {
                // 移除 [图片: URL (宽x高)] 格式的内容
                filteredUserMessage = filteredUserMessage.replaceAll("\\[图片:[^\\]]*\\]", "").trim();
                // 移除 [图片内容:\n  图1: ...\n  图2: ...] 格式的内容
                filteredUserMessage = filteredUserMessage.replaceAll("\\[图片内容:[^\\]]*\\]", "").trim();
            }
            
            // 格式化当前消息：添加[当前需添加选项信息]标签
            // 新格式：[好感度]昵称[我][qq号][时间]：信息
            String formattedCurrentMsg;
            if (currentTimestamp > 0) {
                // 创建时间格式化器
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
                String currentTimeStr = timeFormat.format(new java.util.Date(currentTimestamp));
                
                // 构建昵称部分（如果获取失败显示"昵称获取失败"）
                String displayName = (currentSenderName != null && !currentSenderName.isEmpty()) 
                    ? currentSenderName : "昵称获取失败";
                
                // 构建好感度部分（如果启用且有senderUin）
                String affinityPart = "";
                boolean affinityEnabled = ConfigManager.isAffinityEnabled();
                boolean aiIncludeAffinity = ConfigManager.isAiIncludeAffinity();
                if (ConfigManager.isVerboseLogEnabled()) {
                    Log.d(TAG, "好感度配置检查: affinityEnabled=" + affinityEnabled + ", aiIncludeAffinity=" + aiIncludeAffinity + ", senderUin=" + senderUin);
                }
                
                if (affinityEnabled && aiIncludeAffinity && senderUin != null) {
                    try {
                        AffinityManager affinityManager = AffinityManager.getInstance(context);
                        int affinity = affinityManager.getAffinity(senderUin);
                        if (ConfigManager.isVerboseLogEnabled()) {
                            Log.d(TAG, "获取到好感度: " + affinity + " for " + senderUin);
                        }
                        if (affinity >= 0) {
                            affinityPart = "[好感度:" + affinity + "]";
                            if (ConfigManager.isVerboseLogEnabled()) {
                                Log.d(TAG, "好感度部分: " + affinityPart);
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "获取好感度失败: " + t.getMessage(), t);
                    }
                }
                
                // 构建QQ号部分
                String qqPart = (senderUin != null && !senderUin.isEmpty()) ? "[" + senderUin + "]" : "";
                
                // 格式：[当前需添加选项信息] [好感度]昵称[qq号][时间]: 内容
                formattedCurrentMsg = "[当前需添加选项信息] " + affinityPart + displayName + qqPart + " [" + currentTimeStr + "]: " + filteredUserMessage;
            } else {
                // 降级：如果没有时间戳，仅添加标签和昵称
                String displayName = (currentSenderName != null && !currentSenderName.isEmpty()) 
                    ? currentSenderName : "昵称获取失败";
                formattedCurrentMsg = "[当前需添加选项信息] " + displayName + ": " + filteredUserMessage;
            }
            
            // 检查是否有图片需要发送（OpenAI Vision格式）
            if (imageBase64List != null && !imageBase64List.isEmpty()) {
                // 构建带图片的content数组（OpenAI Vision格式）
                JSONArray contentArray = new JSONArray();
                
                // 添加文本内容
                JSONObject textContent = new JSONObject();
                textContent.put("type", "text");
                textContent.put("text", formattedCurrentMsg);
                contentArray.put(textContent);
                
                // 添加图片内容
                for (String imageBase64 : imageBase64List) {
                    JSONObject imageContent = new JSONObject();
                    imageContent.put("type", "image_url");
                    
                    JSONObject imageUrlObj = new JSONObject();
                    // 确保Base64带有正确的前缀
                    if (imageBase64.startsWith("data:image")) {
                        imageUrlObj.put("url", imageBase64);
                    } else {
                        imageUrlObj.put("url", "data:image/png;base64," + imageBase64);
                    }
                    imageUrlObj.put("detail", "low"); // 使用低分辨率节省token
                    imageContent.put("image_url", imageUrlObj);
                    contentArray.put(imageContent);
                }
                
                userMsg.put("content", contentArray);
                Log.d(TAG, "构建带图片的请求，图片数: " + imageBase64List.size());
            } else {
                // 普通文本消息
                userMsg.put("content", formattedCurrentMsg);
            }
            
            messages.put(userMsg);

            jsonBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                    jsonBody.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // 记录完整的请求信息到日志（仅在启用详细日志时）
            if (ConfigManager.isVerboseLogEnabled()) {
                // 日志中截断base64内容（200字符），但实际请求包保持完整
                String jsonForLog = truncateBase64InJson(jsonBody.toString(), 200);
                String requestLog = buildRequestLog(provider, model, apiUrl, apiKey, jsonForLog);
                Log.d(TAG, "发送AI请求:\n" + requestLog);
                AiLogManager.addLog(context, "AI请求\n" + requestLog);
            } else {
                Log.d(TAG, "发送AI请求: " + provider + " / " + model);
            }

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String error = e.getMessage();
                    Log.e(TAG, "AI请求失败: " + error, e);
                    logError(context, provider, model, apiUrl, error);
                    if (!suppressToast) {
                        showToast(context, "网络连接失败 😢");
                    }
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = null;
                    try {
                        if (!response.isSuccessful()) {
                            int code = response.code();
                            String error = "HTTP " + code + ": " + response.message();
                            responseBody = response.body() != null ? response.body().string() : "";
                            
                            // 特殊处理429速率限制错误（静默处理，不显示Toast）
                            if (code == 429) {
                                Log.w(TAG, "速率限制: " + error);
                                logError(context, provider, model, apiUrl, "Rate Limit (429)\n" + responseBody);
                                // 不调用showToast，静默失败
                                callback.onFailure(new IOException("Rate limit reached"));
                                return;
                            }
                            
                            // 其他错误正常处理
                            logError(context, provider, model, apiUrl, error + "\n" + responseBody);
                            if (!suppressToast) {
                                showToast(context, "AI服务暂时不可用 😢");
                            }
                            callback.onFailure(new IOException(error));
                            return;
                        }

                        responseBody = response.body().string();
                        Log.d(TAG, "AI响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));

                        // 解析JSON格式的响应
                        List<String> options = parseJsonResponse(responseBody);
                        
                        if (options == null || options.size() < 3) {
                            // 改进的错误日志记录
                            int actualCount = options != null ? options.size() : 0;
                            String error;
                            if (options == null) {
                                error = "AI返回格式无法识别，请检查系统提示词配置";
                            } else {
                                error = "AI返回选项不足: 期望3个，实际" + actualCount + "个";
                            }
                            
                            // 重试时不记录详细日志，避免日志过多
                            if (!suppressToast) {
                                String fullLog = error + "\n" +
                                    "=== 原始响应内容 ===\n" + responseBody + "\n" +
                                    "=== 响应内容结束 ===\n" +
                                    "提示: 如果AI返回格式不正确，请检查系统提示词是否要求返回JSON格式";
                                logError(context, provider, model, apiUrl, fullLog);
                                showToast(context, "AI返回格式错误 😢");
                            }
                            callback.onFailure(new Exception(error));
                            return;
                        }

                        // 成功 - 如果启用了详细日志，记录完整响应
                        String fullResponse = ConfigManager.isVerboseLogEnabled() ? responseBody : null;
                        AiLogManager.logAiSuccess(context, provider, model, userMessage, options.size(), fullResponse);
                        callback.onSuccess(options);

                    } catch (Exception e) {
                        Log.e(TAG, "解析失败", e);
                        String error = "解析错误: " + e.getMessage();
                        if (!suppressToast) {
                            logError(context, provider, model, apiUrl, error + "\n响应: " + responseBody);
                            showToast(context, "AI返回格式错误 😢");
                        }
                        callback.onFailure(e);
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "请求构建失败", e);
            logError(context, provider, model, apiUrl, "请求构建失败: " + e.getMessage());
            if (!suppressToast) {
                showToast(context, "AI请求失败 😢");
            }
            callback.onFailure(e);
        }
    }

    /**
     * 解析JSON格式的AI响应（重构版）
     * 支持多种格式的智能解析，按优先级依次尝试：
     * 1. 直接JSON格式（响应本身就是options JSON）
     * 2. OpenAI标准格式（choices[0].message.content）
     * 3. 从content中提取：Markdown代码块、混合文本JSON、列表、纯文本
     * 4. 处理多个JSON对象拼接的情况（流式响应或重试响应）
     */
    private static List<String> parseJsonResponse(String responseBody) {
        // 边界情况处理
        if (responseBody == null || responseBody.trim().isEmpty()) {
            Log.w(TAG, "响应为空");
            return null;
        }
        
        List<String> result = null;
        
        // 预处理：处理多个JSON对象拼接的情况
        // 例如: {...}{...} 或 {...}\n{...}
        String cleanedResponse = preprocessMultipleJsonObjects(responseBody);
        
        try {
            JSONObject jsonResponse = new JSONObject(cleanedResponse);
            
            // 策略1: 直接包含options等字段
            result = parseOptionsJson(cleanedResponse);
            if (result != null && result.size() >= 3) {
                Log.d(TAG, "解析成功: 直接JSON格式");
                return result;
            }
            
            // 策略2: OpenAI标准格式
            result = parseOpenAiFormat(jsonResponse);
            if (result != null && result.size() >= 3) {
                return result;
            }
            
        } catch (Exception e) {
            // 响应本身不是有效JSON，尝试其他策略
            Log.d(TAG, "响应不是标准JSON，尝试其他解析策略: " + e.getMessage());
        }
        
        // 策略3: 尝试从原始响应中提取有效的JSON对象
        result = tryExtractValidJsonFromResponse(responseBody);
        if (result != null && result.size() >= 3) {
            return result;
        }
        
        // 【重要】不要在整个响应体上执行纯文本解析！
        // 这会导致JSON字段名被当作选项
        // 只有当响应明显不是JSON格式时才尝试纯文本解析
        if (!responseBody.trim().startsWith("{") && !responseBody.trim().startsWith("[")) {
            // 策略4: 作为纯文本解析（仅当响应不是JSON格式时）
            result = parseContentWithStrategies(responseBody);
            if (result != null && result.size() >= 3) {
                return result;
            }
        }
        
        Log.w(TAG, "所有解析策略均失败，请检查系统提示词配置");
        return null;
    }
    
    /**
     * 预处理多个JSON对象拼接的响应
     * 处理情况：{...}{...} 或 {...}\n{...}
     * 只保留第一个有效的JSON对象
     */
    private static String preprocessMultipleJsonObjects(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return responseBody;
        }
        
        String trimmed = responseBody.trim();
        
        // 检查是否以 { 开头
        if (!trimmed.startsWith("{")) {
            return responseBody;
        }
        
        // 找到第一个完整的JSON对象
        int depth = 0;
        int endIndex = -1;
        boolean inString = false;
        boolean escape = false;
        
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            
            if (escape) {
                escape = false;
                continue;
            }
            
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            
            if (c == '"' && !escape) {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }
        }
        
        if (endIndex > 0 && endIndex < trimmed.length() - 1) {
            // 检查后面是否还有内容（可能是另一个JSON对象）
            String remaining = trimmed.substring(endIndex + 1).trim();
            if (remaining.startsWith("{")) {
                Log.d(TAG, "检测到多个JSON对象拼接，只使用第一个");
                return trimmed.substring(0, endIndex + 1);
            }
        }
        
        return responseBody;
    }
    
    /**
     * 解析OpenAI标准格式响应
     * 处理choices数组，提取有效的content
     */
    private static List<String> parseOpenAiFormat(JSONObject jsonResponse) {
        try {
            if (!jsonResponse.has("choices")) {
                Log.d(TAG, "parseOpenAiFormat: 没有choices字段");
                return null;
            }
            
            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices.length() == 0) {
                Log.d(TAG, "parseOpenAiFormat: choices数组为空");
                return null;
            }
            
            Log.d(TAG, "parseOpenAiFormat: 找到 " + choices.length() + " 个choices");
            
            // 遍历所有choices，找到有有效content的那个
            for (int i = 0; i < choices.length(); i++) {
                JSONObject choice = choices.getJSONObject(i);
                
                // 检查finish_reason，跳过被截断的响应
                String finishReason = choice.optString("finish_reason", "");
                Log.d(TAG, "parseOpenAiFormat: choice[" + i + "] finish_reason=" + finishReason);
                
                if ("length".equals(finishReason)) {
                    Log.d(TAG, "跳过被截断的choice (finish_reason=length)");
                    continue;
                }
                
                // 获取message对象
                if (!choice.has("message")) {
                    Log.d(TAG, "parseOpenAiFormat: choice[" + i + "] 没有message字段");
                    continue;
                }
                
                JSONObject message = choice.getJSONObject("message");
                
                // 获取content - 尝试多种方式
                String content = message.optString("content", "");
                
                // 如果content为空，尝试其他可能的字段
                if (content.isEmpty()) {
                    content = message.optString("text", "");
                }
                
                if (content.isEmpty()) {
                    Log.d(TAG, "choice[" + i + "] content为空，跳过");
                    continue;
                }
                
                Log.d(TAG, "parseOpenAiFormat: choice[" + i + "] content长度=" + content.length());
                Log.d(TAG, "parseOpenAiFormat: content前100字符=" + content.substring(0, Math.min(100, content.length())));
                
                // 从content中尝试多种解析策略
                List<String> result = parseContentWithStrategies(content);
                if (result != null && result.size() >= 3) {
                    Log.d(TAG, "解析成功: OpenAI格式 choice[" + i + "], 选项数=" + result.size());
                    return result;
                } else {
                    Log.d(TAG, "parseOpenAiFormat: choice[" + i + "] parseContentWithStrategies返回null或不足3个");
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "parseOpenAiFormat失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 尝试从原始响应中提取有效的JSON对象并解析
     * 处理多个JSON对象拼接的情况
     */
    private static List<String> tryExtractValidJsonFromResponse(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        // 尝试找到所有可能的JSON对象
        List<String> jsonObjects = extractAllJsonObjects(responseBody);
        
        for (String jsonStr : jsonObjects) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                
                // 尝试作为OpenAI格式解析
                List<String> result = parseOpenAiFormat(json);
                if (result != null && result.size() >= 3) {
                    Log.d(TAG, "从拼接响应中提取成功");
                    return result;
                }
                
                // 尝试直接解析options
                result = parseOptionsJson(jsonStr);
                if (result != null && result.size() >= 3) {
                    return result;
                }
            } catch (Exception e) {
                // 继续尝试下一个
            }
        }
        
        return null;
    }
    
    /**
     * 从响应中提取所有JSON对象
     */
    private static List<String> extractAllJsonObjects(String responseBody) {
        List<String> result = new ArrayList<>();
        
        int index = 0;
        while (index < responseBody.length()) {
            int start = responseBody.indexOf('{', index);
            if (start == -1) {
                break;
            }
            
            // 找到匹配的闭合大括号
            int depth = 0;
            int end = -1;
            boolean inString = false;
            boolean escape = false;
            
            for (int i = start; i < responseBody.length(); i++) {
                char c = responseBody.charAt(i);
                
                if (escape) {
                    escape = false;
                    continue;
                }
                
                if (c == '\\' && inString) {
                    escape = true;
                    continue;
                }
                
                if (c == '"' && !escape) {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            end = i;
                            break;
                        }
                    }
                }
            }
            
            if (end > start) {
                result.add(responseBody.substring(start, end + 1));
                index = end + 1;
            } else {
                index = start + 1;
            }
        }
        
        return result;
    }

    /**
     * 使用多种策略解析content内容
     * @param content AI返回的content字符串
     * @return 解析出的选项列表
     */
    private static List<String> parseContentWithStrategies(String content) {
        if (content == null || content.trim().isEmpty()) {
            Log.d(TAG, "parseContentWithStrategies: content为空");
            return null;
        }
        
        Log.d(TAG, "parseContentWithStrategies: 开始解析，content长度=" + content.length());
        
        List<String> result = null;
        
        // 策略A: 直接作为JSON解析（支持多种字段名）
        result = parseOptionsJson(content);
        if (result != null && result.size() >= 3) {
            Log.d(TAG, "解析成功: content直接JSON, 选项数=" + result.size());
            return result;
        }
        
        // 策略B: 从Markdown代码块中提取JSON
        String markdownJson = extractJsonFromMarkdown(content);
        if (markdownJson != null) {
            Log.d(TAG, "parseContentWithStrategies: 找到Markdown代码块，长度=" + markdownJson.length());
            result = parseOptionsJson(markdownJson);
            if (result != null && result.size() >= 3) {
                Log.d(TAG, "解析成功: Markdown代码块, 选项数=" + result.size());
                return result;
            }
            // 尝试从不完整的JSON中提取选项
            result = extractOptionsFromIncompleteJson(markdownJson);
            if (result != null && result.size() >= 3) {
                Log.d(TAG, "解析成功: 不完整Markdown JSON, 选项数=" + result.size());
                return result;
            }
        }
        
        // 策略C: 从混合文本中提取JSON
        String textJson = extractJsonFromText(content);
        if (textJson != null) {
            result = parseOptionsJson(textJson);
            if (result != null && result.size() >= 3) {
                Log.d(TAG, "解析成功: 混合文本JSON");
                return result;
            }
            // 尝试从不完整的JSON中提取选项
            result = extractOptionsFromIncompleteJson(textJson);
            if (result != null && result.size() >= 3) {
                Log.d(TAG, "解析成功: 不完整混合文本JSON");
                return result;
            }
        }
        
        // 策略D: 尝试从整个content中提取不完整JSON的选项
        result = extractOptionsFromIncompleteJson(content);
        if (result != null && result.size() >= 3) {
            Log.d(TAG, "解析成功: 不完整JSON提取");
            return result;
        }
        
        // 策略E: 从任意代码块中提取（更宽松的匹配）
        result = extractFromAnyCodeBlock(content);
        if (result != null && result.size() >= 3) {
            Log.d(TAG, "解析成功: 任意代码块提取");
            return result;
        }
        
        // 策略G: 旧格式（|||分隔）
        result = parseLegacyFormat(content);
        if (result != null && result.size() >= 3) {
            Log.d(TAG, "解析成功: |||分隔格式");
            return result;
        }
        
        // 策略H: 编号/项目符号列表
        result = parseNumberedList(content);
        if (result != null && result.size() >= 3) {
            Log.d(TAG, "解析成功: 编号列表格式");
            return result;
        }
        
        // 策略I: 纯文本行（最后的备选方案）
        result = parsePlainLines(content);
        if (result != null && result.size() >= 3) {
            Log.d(TAG, "解析成功: 纯文本行格式");
            return result;
        }
        
        return null;
    }

    /**
     * 将JSONArray转换为List<String>
     */
    private static List<String> jsonArrayToList(JSONArray array) throws Exception {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String option = cleanOptionText(array.getString(i));
            if (!option.isEmpty()) {
                result.add(option);
            }
        }
        return result;
    }
    
    /**
     * 清理选项文本
     * 去除首尾空白、首尾引号等
     */
    private static String cleanOptionText(String text) {
        if (text == null) {
            return "";
        }
        
        String cleaned = text.trim();
        
        // 去除首尾的双引号
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        
        // 去除首尾的单引号
        if (cleaned.length() >= 2 && cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        
        // 去除首尾的中文引号
        if (cleaned.length() >= 2) {
             if ((cleaned.startsWith("“") && cleaned.endsWith("”"))){
                 cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
             }
         }
        
        return cleaned;
    }

    /**
     * 解析旧格式（|||分隔）
     */
    private static List<String> parseLegacyFormat(String content) {
        String[] parts = content.split("\\|\\|\\|");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String cleaned = cleanOptionText(part);
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result.size() >= 3 ? result : null;
    }

    // ==================== 新增解析辅助方法 ====================

    /**
     * 从markdown代码块中提取JSON
     * 支持格式：```json ... ``` 或 ``` ... ```
     * @param content 包含markdown代码块的内容
     * @return 提取的JSON字符串，如果没有找到则返回null
     */
    private static String extractJsonFromMarkdown(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        // 方法1：使用正则表达式匹配 ```json ... ``` 或 ``` ... ``` 格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String extracted = matcher.group(1);
            if (extracted != null && !extracted.trim().isEmpty()) {
                Log.d(TAG, "extractJsonFromMarkdown: 正则匹配成功，长度=" + extracted.length());
                return extracted.trim();
            }
        }
        
        // 方法2：手动查找 ```json 和 ``` 之间的内容（更健壮）
        String lowerContent = content.toLowerCase();
        int startIndex = lowerContent.indexOf("```json");
        if (startIndex == -1) {
            startIndex = lowerContent.indexOf("```");
        }
        
        if (startIndex != -1) {
            // 找到开始标记后的换行符
            int contentStart = content.indexOf('\n', startIndex);
            if (contentStart == -1) {
                contentStart = startIndex + 7; // "```json" 的长度
            } else {
                contentStart++; // 跳过换行符
            }
            
            // 找到结束的 ```
            int endIndex = content.indexOf("```", contentStart);
            if (endIndex != -1 && endIndex > contentStart) {
                String extracted = content.substring(contentStart, endIndex).trim();
                if (!extracted.isEmpty()) {
                    Log.d(TAG, "extractJsonFromMarkdown: 手动提取成功，长度=" + extracted.length());
                    return extracted;
                }
            }
        }
        
        Log.d(TAG, "extractJsonFromMarkdown: 未找到Markdown代码块");
        return null;
    }

    /**
     * 从任意代码块中提取内容并尝试解析
     * 更宽松的匹配方式，处理各种格式的代码块
     * @param content 包含代码块的内容
     * @return 解析出的选项列表
     */
    private static List<String> extractFromAnyCodeBlock(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        // 查找所有 ``` 包围的代码块
        int searchStart = 0;
        while (searchStart < content.length()) {
            // 找到开始的 ```
            int blockStart = content.indexOf("```", searchStart);
            if (blockStart == -1) {
                break;
            }
            
            // 跳过 ``` 后面可能的语言标识（如 json, javascript 等）
            int contentStart = blockStart + 3;
            // 找到换行符或直接开始内容
            int newlinePos = content.indexOf('\n', contentStart);
            if (newlinePos != -1 && newlinePos < contentStart + 20) {
                // 检查 ``` 和换行之间是否只有语言标识
                String langTag = content.substring(contentStart, newlinePos).trim();
                if (langTag.isEmpty() || langTag.matches("^[a-zA-Z]+$")) {
                    contentStart = newlinePos + 1;
                }
            }
            
            // 找到结束的 ```
            int blockEnd = content.indexOf("```", contentStart);
            if (blockEnd == -1) {
                break;
            }
            
            // 提取代码块内容
            String blockContent = content.substring(contentStart, blockEnd).trim();
            Log.d(TAG, "extractFromAnyCodeBlock: 找到代码块，长度=" + blockContent.length());
            
            if (!blockContent.isEmpty()) {
                // 尝试多种解析方式
                
                // 1. 直接作为JSON解析
                List<String> result = parseOptionsJson(blockContent);
                if (result != null && result.size() >= 3) {
                    Log.d(TAG, "extractFromAnyCodeBlock: JSON解析成功");
                    return result;
                }
                
                // 2. 从不完整JSON中提取
                result = extractOptionsFromIncompleteJson(blockContent);
                if (result != null && result.size() >= 3) {
                    Log.d(TAG, "extractFromAnyCodeBlock: 不完整JSON提取成功");
                    return result;
                }
                
                // 3. 作为编号列表解析
                result = parseNumberedList(blockContent);
                if (result != null && result.size() >= 3) {
                    Log.d(TAG, "extractFromAnyCodeBlock: 编号列表解析成功");
                    return result;
                }
                
                // 4. 作为纯文本行解析
                result = parsePlainLines(blockContent);
                if (result != null && result.size() >= 3) {
                    Log.d(TAG, "extractFromAnyCodeBlock: 纯文本行解析成功");
                    return result;
                }
            }
            
            // 继续查找下一个代码块
            searchStart = blockEnd + 3;
        }
        
        return null;
    }

    /**
     * 从混合文本中提取JSON对象
     * 查找第一个 { 和最后一个匹配的 } 之间的内容
     * @param content 可能包含JSON的混合文本
     * @return 提取的JSON字符串，如果没有找到则返回null
     */
    private static String extractJsonFromText(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        int firstBrace = content.indexOf('{');
        if (firstBrace == -1) {
            return null;
        }
        
        // 找到匹配的闭合大括号（处理嵌套）
        int depth = 0;
        int lastBrace = -1;
        for (int i = firstBrace; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    lastBrace = i;
                    break;
                }
            }
        }
        
        if (lastBrace == -1) {
            return null;
        }
        
        return content.substring(firstBrace, lastBrace + 1);
    }

    /**
     * 解析options JSON对象
     * 支持多种字段名：options, replies, answers, responses
     * 注意：不处理OpenAI格式的choices（那是包含message对象的数组）
     * @param jsonStr JSON字符串
     * @return 选项列表，如果解析失败返回null
     */
    private static List<String> parseOptionsJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }
        
        try {
            JSONObject json = new JSONObject(jsonStr);
            
            // 尝试多种字段名（不包括OpenAI格式的choices）
            String[] fieldNames = {"options", "replies", "answers", "responses"};
            for (String fieldName : fieldNames) {
                if (json.has(fieldName)) {
                    Object value = json.get(fieldName);
                    if (value instanceof JSONArray) {
                        JSONArray array = (JSONArray) value;
                        // 检查数组元素是否是字符串（而不是对象）
                        if (array.length() > 0) {
                            Object firstElement = array.get(0);
                            if (firstElement instanceof String) {
                                return jsonArrayToList(array);
                            }
                        }
                    }
                }
            }
            
            // 特殊处理：如果有choices字段，检查是否是简单字符串数组（而不是OpenAI格式）
            if (json.has("choices")) {
                Object choicesValue = json.get("choices");
                if (choicesValue instanceof JSONArray) {
                    JSONArray choices = (JSONArray) choicesValue;
                    if (choices.length() > 0) {
                        Object firstElement = choices.get(0);
                        // 只有当第一个元素是字符串时才处理（排除OpenAI格式的对象数组）
                        if (firstElement instanceof String) {
                            return jsonArrayToList(choices);
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.d(TAG, "parseOptionsJson失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析编号/项目符号列表
     * 支持格式：1. xxx, 1、xxx, 1) xxx, - xxx, * xxx, • xxx
     * @param content 列表文本
     * @return 选项列表，如果解析失败返回null
     */
    private static List<String> parseNumberedList(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        List<String> result = new ArrayList<>();
        String[] lines = content.split("\\n");
        
        // 匹配编号或项目符号的正则
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "^\\s*(?:\\d+[.、)\\]]|[-*•])\\s*(.+)$"
        );
        
        for (String line : lines) {
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String item = matcher.group(1);
                if (item != null) {
                    String cleaned = cleanOptionText(item);
                    if (!cleaned.isEmpty()) {
                        result.add(cleaned);
                    }
                }
            }
        }
        
        return result.size() >= 3 ? result : null;
    }

    /**
     * 解析纯文本行
     * 将非空行作为选项，但过滤掉JSON/代码格式的行
     * @param content 文本内容
     * @return 选项列表，如果行数不足返回null
     */
    private static List<String> parsePlainLines(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        List<String> result = new ArrayList<>();
        String[] lines = content.split("\\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && isValidOptionLine(trimmed)) {
                String cleaned = cleanOptionText(trimmed);
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }
        }
        
        return result.size() >= 3 ? result : null;
    }

    /**
     * 从不完整的JSON中提取选项
     * 用于处理AI返回被截断的JSON情况
     * @param content 可能不完整的JSON内容
     * @return 提取的选项列表
     */
    private static List<String> extractOptionsFromIncompleteJson(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        List<String> result = new ArrayList<>();
        
        // 使用正则匹配JSON数组中的字符串元素
        // 只匹配数组元素格式：  "内容"  或  "内容",  （前面不能是冒号，避免匹配字段值）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?<!:)\\s*\"([^\"]{5,})\"\\s*[,\\]]?",  // 至少5个字符，避免匹配短字段名
            java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        // 需要过滤的字段名和API元数据
        java.util.Set<String> skipValues = new java.util.HashSet<>();
        // JSON字段名
        skipValues.add("options");
        skipValues.add("choices");
        skipValues.add("replies");
        skipValues.add("answers");
        skipValues.add("responses");
        skipValues.add("message");
        skipValues.add("content");
        skipValues.add("role");
        skipValues.add("finish_reason");
        skipValues.add("index");
        skipValues.add("created");
        skipValues.add("model");
        skipValues.add("object");
        skipValues.add("usage");
        skipValues.add("completion_tokens");
        skipValues.add("prompt_tokens");
        skipValues.add("total_tokens");
        // API响应值
        skipValues.add("stop");
        skipValues.add("length");
        skipValues.add("assistant");
        skipValues.add("user");
        skipValues.add("system");
        skipValues.add("chat.completion");
        
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isEmpty()) {
                String lowerValue = value.toLowerCase().trim();
                
                // 跳过已知的字段名和元数据
                if (skipValues.contains(lowerValue)) {
                    continue;
                }
                
                // 跳过太短的内容（可能是JSON语法）
                if (value.length() < 5) {
                    continue;
                }
                
                // 跳过看起来像ID的字符串
                if (value.matches("^[A-Za-z0-9_-]{15,50}$")) {
                    continue;
                }
                
                // 跳过模型名称
                if (lowerValue.startsWith("gpt-") || lowerValue.startsWith("gemini-") ||
                    lowerValue.startsWith("claude-") || lowerValue.startsWith("deepseek-") ||
                    lowerValue.startsWith("qwen-") || lowerValue.startsWith("glm-")) {
                    continue;
                }
                
                // 跳过纯数字
                if (value.matches("^\\d+$")) {
                    continue;
                }
                
                // 跳过纯英文单词（可能是字段名）
                if (value.matches("^[a-zA-Z_]+$")) {
                    continue;
                }
                
                // 清理并添加
                String cleaned = cleanOptionText(value);
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }
        }
        
        return result.size() >= 3 ? result : null;
    }

    /**
     * 判断一行是否是有效的选项内容
     * 过滤掉JSON/代码格式的行和API响应元数据
     * @param line 要检查的行
     * @return 如果是有效选项返回true
     */
    private static boolean isValidOptionLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        
        // 过滤markdown代码块标记
        if (line.startsWith("```")) {
            return false;
        }
        
        // 过滤纯JSON语法字符的行
        String stripped = line.replaceAll("[\\s\\[\\]{}:,\"]", "");
        if (stripped.isEmpty()) {
            return false;
        }
        
        // 过滤JSON字段名行（如 "options": [ 或 "choices": [）
        if (line.matches("^\"?\\w+\"?\\s*:\\s*\\[?\\s*$")) {
            return false;
        }
        
        // 过滤只有单个大括号或方括号的行
        if (line.equals("{") || line.equals("}") || line.equals("[") || line.equals("]") ||
            line.equals("{,") || line.equals("},") || line.equals("[,") || line.equals("],")) {
            return false;
        }
        
        // 【重要】过滤所有常见的JSON字段名和API响应元数据
        String lowerLine = line.toLowerCase().trim();
        
        // 过滤常见的JSON字段名（这些是图片中显示的问题字段）
        java.util.Set<String> invalidValues = new java.util.HashSet<>();
        // API响应字段名
        invalidValues.add("finish_reason");
        invalidValues.add("length");
        invalidValues.add("index");
        invalidValues.add("message");
        invalidValues.add("role");
        invalidValues.add("assistant");
        invalidValues.add("created");
        invalidValues.add("id");
        invalidValues.add("model");
        invalidValues.add("object");
        invalidValues.add("chat.completion");
        invalidValues.add("usage");
        invalidValues.add("completion_tokens");
        invalidValues.add("prompt_tokens");
        invalidValues.add("total_tokens");
        // finish_reason 值
        invalidValues.add("stop");
        invalidValues.add("content_filter");
        invalidValues.add("tool_calls");
        invalidValues.add("function_call");
        // role 值
        invalidValues.add("user");
        invalidValues.add("system");
        invalidValues.add("function");
        invalidValues.add("tool");
        // 其他常见字段
        invalidValues.add("content");
        invalidValues.add("choices");
        invalidValues.add("options");
        invalidValues.add("text");
        invalidValues.add("data");
        invalidValues.add("error");
        invalidValues.add("status");
        invalidValues.add("code");
        invalidValues.add("type");
        invalidValues.add("name");
        invalidValues.add("value");
        
        if (invalidValues.contains(lowerLine)) {
            return false;
        }
        
        // 过滤看起来像ID的字符串（通常是随机字符串，如 _Zguae_rBpTSqfkPjrrksAQ）
        // 特征：只包含字母数字和下划线/横线，长度在10-60之间
        if (line.matches("^[A-Za-z0-9_-]{10,60}$")) {
            return false;
        }
        
        // 过滤模型名称（常见格式）
        if (lowerLine.startsWith("gpt-") || lowerLine.startsWith("gemini-") ||
            lowerLine.startsWith("claude-") || lowerLine.startsWith("deepseek-") ||
            lowerLine.startsWith("qwen-") || lowerLine.startsWith("glm-") ||
            lowerLine.startsWith("moonshot-") || lowerLine.startsWith("kimi-") ||
            lowerLine.startsWith("llama-") || lowerLine.startsWith("mistral-")) {
            return false;
        }
        
        // 过滤纯数字（可能是token计数、时间戳等）
        if (line.matches("^\\d+$")) {
            return false;
        }
        
        // 过滤JSON键值对格式（如 "key": value 或 "key": "value"）
        if (line.matches("^\"?\\w+\"?\\s*:\\s*.+$")) {
            return false;
        }
        
        // 过滤纯英文单词（可能是字段名，至少要有中文或特殊字符才是有效选项）
        if (line.matches("^[a-zA-Z_]+$")) {
            return false;
        }
        
        // 过滤下划线连接的英文单词（如 completion_tokens）
        if (line.matches("^[a-zA-Z]+(_[a-zA-Z]+)+$")) {
            return false;
        }
        
        return true;
    }

    /**
     * 记录错误日志
     */
    private static void logError(Context context, String provider, String model, String url, String error) {
        AiLogManager.logAiError(context, provider, model, url, error);
    }
    
    /**
     * 截断JSON中的Base64内容用于日志显示
     * 实际发送的请求包保持完整，只有日志中的内容被截断
     * @param json 原始JSON字符串
     * @param maxBase64Length Base64内容的最大显示长度
     * @return 截断后的JSON字符串（仅用于日志）
     */
    private static String truncateBase64InJson(String json, int maxBase64Length) {
        if (json == null) return "null";
        
        // 查找 data:image 开头的base64内容并截断
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            int dataStart = json.indexOf("data:image", i);
            if (dataStart == -1) {
                result.append(json.substring(i));
                break;
            }
            
            result.append(json.substring(i, dataStart));
            
            // 找到base64内容的结束位置（引号）
            int contentEnd = -1;
            boolean inEscape = false;
            for (int j = dataStart; j < json.length(); j++) {
                char c = json.charAt(j);
                if (inEscape) {
                    inEscape = false;
                    continue;
                }
                if (c == '\\') {
                    inEscape = true;
                    continue;
                }
                if (c == '"') {
                    contentEnd = j;
                    break;
                }
            }
            
            if (contentEnd == -1) {
                contentEnd = json.length();
            }
            
            String base64Content = json.substring(dataStart, contentEnd);
            if (base64Content.length() > maxBase64Length) {
                // 截断并添加提示
                result.append(base64Content.substring(0, maxBase64Length)).append("...[base64截断,原长度:").append(base64Content.length()).append("]");
            } else {
                result.append(base64Content);
            }
            
            i = contentEnd;
        }
        
        return result.toString();
    }

    /**
     * 构建请求日志（用于调试）
     */
    private static String buildRequestLog(String provider, String model, String url, String apiKey, String body) {
        StringBuilder log = new StringBuilder();
        log.append("Provider: ").append(provider).append("\n");
        log.append("Model: ").append(model).append("\n");
        log.append("URL: ").append(url).append("\n");
        log.append("Headers:\n");
        log.append("  Authorization: Bearer ").append(maskApiKey(apiKey)).append("\n");
        log.append("  Content-Type: application/json\n");
        log.append("Body:\n");
        
        // 格式化JSON body
        try {
            JSONObject jsonBody = new JSONObject(body);
            log.append(jsonBody.toString(2)); // 缩进2个空格
        } catch (Exception e) {
            log.append(body);
        }
        
        return log.toString();
    }

    /**
     * 遮蔽API Key（只显示前4位和后4位）
     */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 规范化API URL（防呆设计）
     * 如果用户只输入了域名，自动添加 /v1/chat/completions 路径
     * 
     * @param apiUrl 原始API URL
     * @return 规范化后的API URL
     */
    private static String normalizeApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            return apiUrl;
        }
        
        apiUrl = apiUrl.trim();
        
        // 移除末尾的斜杠
        while (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }
        
        try {
            java.net.URL url = new java.net.URL(apiUrl);
            String path = url.getPath();
            
            // 如果路径为空或只有 /，说明用户只输入了域名
            // 例如: https://api.example.com 或 https://api.example.com/
            if (path == null || path.isEmpty() || path.equals("/")) {
                String normalizedUrl = apiUrl + "/v1/chat/completions";
                Log.d(TAG, "API URL规范化: " + apiUrl + " -> " + normalizedUrl);
                return normalizedUrl;
            }
            
            // 如果路径只有版本号，如 /v1，添加 /chat/completions
            if (path.matches("/v\\d+/?")) {
                String normalizedUrl = apiUrl.replaceAll("/$", "") + "/chat/completions";
                Log.d(TAG, "API URL规范化: " + apiUrl + " -> " + normalizedUrl);
                return normalizedUrl;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "解析API URL失败: " + e.getMessage());
        }
        
        // 其他情况保持原样
        return apiUrl;
    }

    /**
     * 显示Toast提示
     */
    private static void showToast(Context context, String message) {
        mainHandler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 测试API连接
     */
    public static void testApiConnection(Context context, AiCallback callback) {
        fetchOptions(context, "你好", callback);
    }
}
