package com.example.demo.viewmodel;

import android.app.Application;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.example.demo.data.KnowledgeChunk;
import com.example.demo.data.Persona;
import com.example.demo.data.Message;
import com.example.demo.data.Dynamic;
import com.example.demo.repository.PersonaRepository;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PersonaChatViewModel extends AndroidViewModel {

    private final PersonaRepository repository;//数据仓库实例
    private final ExecutorService executorService;//线程执行器

    private final LiveData<List<Message>> messagesLiveData;//聊天记录
    private final MutableLiveData<Persona> generatedPersonaLiveData = new MutableLiveData<>();// AI生成的角色数据
    private final LiveData<Persona> activePersonaLiveData;//活跃角色
    private final LiveData<List<Dynamic>> dynamicFeedLiveData;//动态
    private final MutableLiveData<Boolean> dynamicPublishSuccess = new MutableLiveData<>();// 动态发布成功状态

    private final MutableLiveData<String> streamingTextChunk = new MutableLiveData<>();//流式传输核心，用于接收 AI 响应时实时返回的文本片段。
    private final MutableLiveData<Boolean> isStreaming = new MutableLiveData<>(false);

    // 图片生成相关
    private final MutableLiveData<String> generatedImageUrl = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGeneratingImage = new MutableLiveData<>(false);

    // 语音合成相关
    private final MutableLiveData<String> audioFilePathLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSpeakingLiveData = new MutableLiveData<>(false);

    private static final String TAG = "PersonaChatViewModel";

    private final String QWEN_API_KEY = "sk-c5677a8b2c77473fab746ba32308bf10";
    private final String QWEN_CHAT_COMPLETION_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private final String TONGLYI_WANXIANG_IMAGE_GENERATION_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    private final String TONGLYI_WANXIANG_API_KEY ="sk-c5677a8b2c77473fab746ba32308bf10";
    private final String TONGYI_TTS_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private final String TONGYI_TTS_API_KEY = "sk-c5677a8b2c77473fab746ba32308bf10";

    private final String QWEN_EMBEDDING_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    private final String QWEN_MODEL = "qwen-plus";//对话模型

    private final OkHttpClient httpClient;

    public PersonaChatViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonaRepository(application);
        executorService = repository.getExecutorService();

        this.dynamicFeedLiveData = repository.getAllDynamicsLiveData();
        this.activePersonaLiveData = repository.getActivePersonaLiveData();

        // 关键：根据活跃角色动态切换聊天记录
        messagesLiveData = Transformations.switchMap(
                activePersonaLiveData,
                persona -> {
                    if (persona != null) {
                        return repository.getMessagesByPersonaId(persona.getId());
                    } else {
                        return new MutableLiveData<>(new ArrayList<>());
                    }
                }
        );

        // 💡 针对大模型请求的特点，放宽网络超时限制
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // 连接超时：30秒
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)   // 读取超时：120秒 (大模型生成文本和推理非常耗时)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)   // 写入超时：60秒
                .build();
    }

    // --- Getter 和辅助方法 ---

    public LiveData<Boolean> getDynamicPublishSuccess() {
        return dynamicPublishSuccess;
    }

    public void resetDynamicPublishSuccessStatus() {
        dynamicPublishSuccess.postValue(false);
    }

    public LiveData<Persona> getActivePersonaLiveData() { return activePersonaLiveData; }
    public LiveData<List<Persona>> getAllPersonasLiveData() { return repository.getAllPersonasLiveData(); }
    public LiveData<Persona> getGeneratedPersonaLiveData() { return generatedPersonaLiveData; }
    public LiveData<List<Message>> getMessagesLiveData() { return messagesLiveData; }
    public LiveData<List<Dynamic>> getAllDynamicsLiveData() { return dynamicFeedLiveData; }

    public LiveData<String> getStreamingTextChunk() {
        return streamingTextChunk;
    }
    public LiveData<Boolean> getIsStreaming() {
        return isStreaming;
    }

    // 供外部主线程立刻锁定打字状态，防止数据库抢跑
    public void setChatStreaming(boolean isStreamingVal) {
        isStreaming.setValue(isStreamingVal);
    }

    public LiveData<String> getAudioFilePathLiveData() { return audioFilePathLiveData; }
    public LiveData<Boolean> getIsSpeakingLiveData() { return isSpeakingLiveData; }
    public LiveData<List<com.example.demo.data.PersonaDocument>> getAllPersonaDocumentsLiveData() {
        return repository.getAllPersonaDocumentsLiveData();
    }

    // 用于向 UI 实时发射流式打字机文本
    private final MutableLiveData<String> currentStreamingText = new MutableLiveData<>();

    public LiveData<String> getCurrentStreamingText() {
        return currentStreamingText;
    }

    // 💡 新增：用于向前台动态同步 Agent 当前动作状态的 LiveData
    private final MutableLiveData<String> agentStatusText = new MutableLiveData<>();

    public LiveData<String> getAgentStatusText() {
        return agentStatusText;
    }

    public void deleteDocument(int personaId, String docName) {
        repository.deleteDocument(personaId, docName);
    }

    /**
     * 重置图片 URL LiveData，防止重复处理
     */
    public void clearGeneratedImageUrl() {
        generatedImageUrl.postValue(null);
    }

    public LiveData<String> getGeneratedImageUrl() {
        return generatedImageUrl;
    }

    public LiveData<Boolean> getIsGeneratingImage() {
        return isGeneratingImage;
    }

    public void clearAudioFilePath() {
        audioFilePathLiveData.postValue(null);
    }

    public void setSpeakingStatus(boolean isSpeaking) {
        isSpeakingLiveData.postValue(isSpeaking);
    }

    public void savePersona(Persona persona) {
        repository.insert(persona);
    }

    public void clearGeneratedPersona() {
        generatedPersonaLiveData.postValue(null);
    }

    public void deletePersona(Persona persona) {
        repository.deletePersonaAndDynamics(persona);
    }

    public void setActivePersona(int personaId) {
        repository.setActivePersona(personaId);
    }

    public void insertMessage(Message message) {
        repository.insert(message);
    }

    /**
     * 【同步调用】通义千问 Embedding API，将文本转为向量
     */
    private List<Double> getEmbeddingSync(String text) {
        try {
            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("model", "text-embedding-v2");
            JSONObject input = new JSONObject();
            input.put("texts", new JSONArray().put(text));
            requestBodyJson.put("input", input);

            RequestBody requestBody = RequestBody.create(
                    requestBodyJson.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(QWEN_EMBEDDING_URL)
                    .post(requestBody)
                    .header("Authorization", "Bearer " + QWEN_API_KEY)
                    .build();

            // 注意：这里用 execute() 同步阻塞，因为我们将把它放在后台线程池运行
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject resJson = new JSONObject(response.body().string());
                    JSONArray embeddingsArray = resJson.optJSONObject("output")
                            .optJSONArray("embeddings");
                    if (embeddingsArray != null && embeddingsArray.length() > 0) {
                        JSONArray vectorArray = embeddingsArray.getJSONObject(0).getJSONArray("embedding");
                        List<Double> vector = new ArrayList<>();
                        for (int i = 0; i < vectorArray.length(); i++) {
                            vector.add(vectorArray.getDouble(i));
                        }
                        return vector;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取向量失败", e);
        }
        return null;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) return 0.0;
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }


    private static final long POLLING_INTERVAL_MS = 5000; // 轮询间隔 5 秒

    /**
     * 轮询查询图片生成任务状态 (具备防网络抖动与超时熔断机制)
     */
    private void queryImageTask(String taskId, Persona persona) {
        // 提交任务到后台线程池
        repository.getExecutorService().execute(() -> {
            boolean keepPolling = true;

            // 💡 改进 1：增加安全边界，防止无限死循环 (比如最多轮询 24 次，约 2 分钟)
            int maxAttempts = 24;
            int currentAttempt = 0;

            // 💡 改进 2：容忍偶发的网络断流 (最多容忍连续 3 次网络错误)
            int maxNetworkFailures = 3;
            int networkFailureCount = 0;

            while (keepPolling && currentAttempt < maxAttempts) {
                try {
                    // 💡 改进 3：无条件先休眠。因为任务刚提交，云端肯定需要几秒钟画图。
                    Thread.sleep(POLLING_INTERVAL_MS);
                    currentAttempt++;

                    String queryUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
                    Request request = new Request.Builder()
                            .url(queryUrl)
                            .header("Authorization", "Bearer " + TONGLYI_WANXIANG_API_KEY)
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        // 如果网络请求成功打通，清零网络失败计数器
                        networkFailureCount = 0;

                        if (!response.isSuccessful() || response.body() == null) {
                            String errorBody = response.body() != null ? response.body().string() : "N/A";
                            Log.e(TAG, "图片查询 API 返回异常状态码: " + response.code() + ", 详情: " + errorBody);
                            // 这里不直接抛出异常终止，而是继续下一轮循环等恢复，除非达到了重试上限
                            continue;
                        }

                        String jsonString = response.body().string();
                        JSONObject responseJson = new JSONObject(jsonString);
                        JSONObject output = responseJson.optJSONObject("output");

                        if (output == null) {
                            throw new JSONException("API 返回结构异常，无 Output 字段");
                        }

                        String status = output.optString("task_status", "UNKNOWN");

                        if ("SUCCEEDED".equals(status)) {
                            JSONArray results = output.optJSONArray("results");
                            String imageUrl = "";
                            if (results != null && results.length() > 0) {
                                imageUrl = results.getJSONObject(0).optString("url", "");
                            }

                            if (!imageUrl.isEmpty()) {
                                // 成功拿到图片！
                                // 注意：如果你希望这张图片像聊天记录一样保存在界面上，
                                // 建议像处理报错消息那样，把它封装成 Message 写入 Room 数据库。
                                generatedImageUrl.postValue(imageUrl);
                            } else {
                                throw new JSONException("返回状态成功，但图片 URL 解析为空");
                            }
                            keepPolling = false; // 结束轮询

                        } else if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                            Log.e(TAG, "图片任务失败或取消，状态: " + status);
                            // 从响应中提取阿里云给出的具体失败原因（比如提示词违规）
                            String failedReason = output.optString("code", status) + ": " + output.optString("message", "未知原因");
                            Message errorMessage = new Message(persona.getId(), "图片生成失败：\n" + failedReason, false, "系统");
                            repository.insert(errorMessage);
                            keepPolling = false;

                        } else {
                            // PENDING 或 RUNNING 状态，什么都不做，安心进入下一次 while 循环
                            Log.d(TAG, "图片任务状态: " + status + "，正在尝试第 " + currentAttempt + " 次轮询。");
                        }
                    }

                } catch (java.io.IOException e) {
                    // 💡 针对网络异常 (如 Socket Closed, Timeout) 进行容错拦截
                    networkFailureCount++;
                    Log.w(TAG, "轮询图片时发生网络异常 (已累计 " + networkFailureCount + " 次): " + e.getMessage());

                    if (networkFailureCount >= maxNetworkFailures) {
                        Log.e(TAG, "网络连接彻底断开，放弃轮询图片。");
                        Message errorMessage = new Message(persona.getId(), "网络连接断开，无法获取图片结果，请检查网络。", false, "系统");
                        repository.insert(errorMessage);
                        keepPolling = false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "图片查询轮询被中断", e);
                    keepPolling = false;
                } catch (Exception e) {
                    // JSON 解析等其他硬性错误，直接放弃
                    Log.e(TAG, "图片查询解析异常: " + e.getMessage(), e);
                    Message errorMessage = new Message(persona.getId(), "图片数据解析失败。", false, "系统");
                    repository.insert(errorMessage);
                    keepPolling = false;
                }
            }

            // 💡 改进 4：超时兜底
            if (keepPolling && currentAttempt >= maxAttempts) {
                Log.e(TAG, "图片生成等待超时");
                Message errorMessage = new Message(persona.getId(), "大模型画图太慢，等待超时了，请稍后再试。", false, "系统");
                repository.insert(errorMessage);
            }

            // 最终无论成功失败，关闭 UI 加载动画
            isGeneratingImage.postValue(false);
        });
    }

    /**
     * 调用通义万相 API 生成图片
     */
    public void generateImageRequest(String prompt, Persona persona) {
        // 参数校验：Persona为空或提示词为空时直接返回
        if (persona == null || prompt.trim().isEmpty()) {
            Log.e(TAG, "无法生成图片：Persona 为空或提示词为空。");
            return;
        }

        isGeneratingImage.postValue(true);// 通知UI：开始生成图片

        // API地址和请求格式
        String url = TONGLYI_WANXIANG_IMAGE_GENERATION_URL;
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        // 构建请求参数（JSON格式）
        JSONObject requestBodyJson = new JSONObject();
        try {
            requestBodyJson.put("model", "wan2.5-t2i-preview");// 使用的模型版本
            // 输入参数：提示词和反向提示词
            JSONObject input = new JSONObject();
            input.put("prompt", prompt);// 生成图片的描述文本
            input.put("negative_prompt", "");// 不希望出现的内容（为空表示无限制）
            requestBodyJson.put("input", input);
            JSONObject parameters = new JSONObject();
            parameters.put("size", "1024*1024");
            parameters.put("n", 1);
            parameters.put("prompt_extend", true);
            parameters.put("watermark", false);
            parameters.put("seed", 12345);
            requestBodyJson.put("parameters", parameters);

        } catch (JSONException e) {
            // JSON构建失败时，通知UI结束生成
            Log.e(TAG, "构造图片生成 JSON Payload 失败", e);
            isGeneratingImage.postValue(false);
            return;
        }

        // 创建请求体
        RequestBody requestBody = RequestBody.create(requestBodyJson.toString(), JSON);

        // 发送POST请求
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("X-DashScope-Async", "enable")// 启用异步模式（适合耗时任务
                .header("Authorization", "Bearer " + TONGLYI_WANXIANG_API_KEY)
                .build();

        // 发送异步网络请求
        httpClient.newCall(request).enqueue(new Callback() {
            // 请求成功回调
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String jsonString = "";
                try {
                    // 检查响应体是否为空
                    if (response.body() == null) {
                        throw new IOException("图片生成 API 提交请求失败：响应体为空。");
                    }
                    jsonString = response.body().string();

                    // 检查HTTP状态码（200-299为成功）
                    if (!response.isSuccessful()) {
                        throw new IOException("图片生成 API 提交请求失败，代码: " + response.code() + ", 错误: " + jsonString);
                    }

                    // 解析响应，提取任务ID
                    JSONObject responseJson = new JSONObject(jsonString);
                    JSONObject output = responseJson.optJSONObject("output");
                    String taskId = "";
                    if (output != null) {
                        taskId = output.optString("task_id", "");
                    }

                    // 任务ID有效时，启动轮询
                    if (!taskId.isEmpty()) {
                        Log.i(TAG, "图片生成任务提交成功，Task ID: " + taskId);
                        //开始轮询 (它会在后台线程中循环)
                        queryImageTask(taskId, persona);
                    } else {
                        Log.e(TAG, "图片生成任务提交失败：未找到 task_id。完整响应: " + jsonString);
                        Message errorMessage = new Message(persona.getId(), "图片生成失败：API返回中没有任务ID。", false, "系统");
                        repository.insert(errorMessage);
                        isGeneratingImage.postValue(false);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "图片生成请求失败或解析异常: " + e.getMessage(), e);
                    String displayMessage = (e instanceof IOException) ? e.getMessage() : "解析响应时发生错误。";
                    Message errorMessage = new Message(persona.getId(), "图片生成失败：" + displayMessage, false, "系统");
                    repository.insert(errorMessage);
                    isGeneratingImage.postValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "图片生成网络请求失败: " + e.getMessage());
                isGeneratingImage.postValue(false);
                Message errorMessage = new Message(persona.getId(), "图片生成失败：网络请求错误。", false, "系统");
                repository.insert(errorMessage);
            }
        });
    }

    /**
     * 调用通义 TTS API 生成语音 URL，并通知 UI 播放。
     * @param text 要合成的文本
     */
    /**
     * 调用通义 TTS API 生成语音 URL，并通知 UI 播放。
     * @param text 要合成的文本
     */
    public void synthesizeAndPlay(String text) {
        //获取当前活跃的 Persona 实例，检查输入文本是否为空或 Persona 是否存在
        final Persona currentPersona = activePersonaLiveData.getValue();

        if (text.trim().isEmpty() || currentPersona == null) {
            Log.w(TAG, "TTS 请求失败：文本为空或 Persona 不存在。");
            return;
        }

        //检查 TTS API 密钥是否配置，若未配置则打印错误日志
        if (TONGYI_TTS_API_KEY == null || TONGYI_TTS_API_KEY.isEmpty()) {
            Log.e(TAG, "FATAL ERROR: TONGYI_TTS_API_KEY is not set.");
            Message errorMessage = new Message(currentPersona.getId(), "语音合成失败：API Key 配置缺失。", false, "系统");
            repository.insert(errorMessage);
            return;
        }

        //按 API 要求构造 JSON 格式的请求体
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        JSONObject requestBodyJson = new JSONObject();
        try {
            requestBodyJson.put("task", "text-to-speech");
            requestBodyJson.put("model", "qwen3-tts-flash");

            JSONObject input = new JSONObject();
            input.put("text", text); // 文本必须在这里
            requestBodyJson.put("input", input);

            JSONObject parameters = new JSONObject();
            // 选用文档中支持的音色，例如 Cherry
            parameters.put("voice", "Cherry");
            parameters.put("language_type", "Chinese");
            parameters.put("format", "wav");

            requestBodyJson.put("parameters", parameters);

        } catch (JSONException e) {
            Log.e(TAG, "构造 TTS JSON Payload 失败", e);
            Message errorMessage = new Message(currentPersona.getId(), "语音合成请求构建失败。", false, "系统");
            repository.insert(errorMessage);
            return;
        }

        //将 JSON 转为请求体，创建 HTTP 请求并添加认证头和内容类型头，同时打印请求体用于调试
        RequestBody requestBody = RequestBody.create(requestBodyJson.toString(), JSON);
        Log.d(TAG, "TTS Request JSON: " + requestBodyJson.toString()); // 打印请求体进行调试

        Request request = new Request.Builder()
                .url(TONGYI_TTS_API_URL)
                .post(requestBody)
                .header("Authorization", "Bearer " + TONGYI_TTS_API_KEY)
                .header("Content-Type", "application/json")
                .build();

        //使用 OkHttp 异步发送请求
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String jsonString = "";
                try {
                    jsonString = response.body() != null ? response.body().string() : "N/A";

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "TTS API 错误响应: " + jsonString);
                        throw new IOException("TTS 请求失败，代码: " + response.code() + ", 错误: " + jsonString);
                    }

                    JSONObject responseJson = new JSONObject(jsonString);

                    String audioUrl = responseJson
                            .optJSONObject("output")
                            .optJSONObject("audio")
                            .optString("url", "");

                    if (!audioUrl.isEmpty()) {
                        audioFilePathLiveData.postValue(audioUrl);// 通知 UI 播放音频
                        Log.i(TAG, "TTS 音频 URL 获取成功: " + audioUrl);
                    } else {
                        Log.e(TAG, "TTS 音频 URL 为空，完整响应: " + jsonString);
                        throw new Exception("API 返回的音频 URL 为空或 JSON 结构异常。");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "TTS 请求或解析异常: " + e.getMessage(), e);
                    Message errorMessage = new Message(currentPersona.getId(), "语音合成失败：" + e.getMessage(), false, "系统");
                    repository.insert(errorMessage);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "TTS 网络请求失败: " + e.getMessage());
                setSpeakingStatus(false);

                Message errorMessage = new Message(currentPersona.getId(), "语音合成失败：网络错误。", false, "系统");
                repository.insert(errorMessage);
            }
        });
    }


    public void toggleLike(Dynamic dynamic) {
        dynamic.toggleLikeStatus();
        repository.update(dynamic);
    }

    /**
     * 【AI 辅助生成 Persona 设定】
     */
    public void generatePersona(String prompt) {
        //指定调用的 AI 接口地址
        String url = QWEN_CHAT_COMPLETION_URL;
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        JSONObject requestBodyJson = new JSONObject();
        try {
            requestBodyJson.put("model", QWEN_MODEL);// 指定使用的 AI 模型

            JSONArray messagesArray = new JSONArray();

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");// 角色为"用户"
            userMessage.put("content", prompt);// 传入用户提供的提示词
            messagesArray.put(userMessage);

            requestBodyJson.put("messages", messagesArray);

        } catch (JSONException e) {
            Log.e(TAG, "构造 Persona 生成请求 JSON Payload 失败", e);
            return;
        }

        //将 JSON 转为请求体，创建 POST 请求并添加认证头
        RequestBody requestBody = RequestBody.create(requestBodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer " + QWEN_API_KEY)
                .build();

        //使用 OkHttp 异步发送请求
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonString = response.body().string();
                        JSONObject responseJson = new JSONObject(jsonString);

                        JSONArray choices = responseJson.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            // 解析 AI 返回的 JSON 内容
                            JSONObject firstChoice = choices.getJSONObject(0);
                            JSONObject message = firstChoice.getJSONObject("message");
                            String contentString = message.getString("content");

                            String cleanedContentString = contentString.replaceAll("```json", "").replaceAll("```", "").trim();
                            JSONObject personaJson = new JSONObject(cleanedContentString);

                            // 提取角色信息并创建 Persona 对象
                            String name = personaJson.optString("name", "");
                            String personality = personaJson.optString("personality", "");
                            String background = personaJson.optString("background", "");

                            if (!name.isEmpty() && !personality.isEmpty() && !background.isEmpty()) {
                                Persona generatedPersona = new Persona(name, personality, background);
                                generatedPersonaLiveData.postValue(generatedPersona);
                                Log.i(TAG, "AI Persona 生成成功: " + name);
                            } else {
                                Log.e(TAG, "AI 响应 JSON 解析失败：字段缺失。");
                            }

                        } else {
                            Log.e(TAG, "AI 响应中未找到 choices 或 choices 为空。");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Persona 生成或解析失败: " + e.getMessage(), e);
                    }
                } else {
                    Log.e(TAG, "AI API 请求失败，代码: " + response.code());
                    try (Response originalResponse = response) {
                        if (originalResponse.body() != null) {
                            Log.e(TAG, "失败响应体: " + originalResponse.body().string());
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Persona 生成网络请求失败: " + e.getMessage());
            }
        });
    }

    /**
     * 通过 AI 生成并发布动态 (使用通义千问 API)
     */
    public void generateAndPostDynamic(Persona persona, String topic) {
        //检查 Persona 是否为空，避免空指针异常
        if (persona == null) {
            Log.e(TAG, "Persona 为空，无法发布动态。");
            return;
        }

        //构建系统提示词
        String systemPrompt = String.format(
                "你是一个名为 \"%s\" 的角色，性格特征是 \"%s\"。你的背景故事是 \"%s\"。请严格扮演该角色，根据主题 \"%s\" 以该角色的口吻发布一条图文社交动态。你的回复必须是**严格的 JSON 格式**，不包含任何额外文字，包含两个字段: " +
                        "{\"text\": \"[动态的文字内容，请在100字以内]\", \"image_url\": \"[一个随机的、描述性的图片链接，如果没有图片则为空字符串。请使用真实的图片链接，例如 Unsplash 或 PicSum 的链接]\"}",
                persona.getName(), persona.getPersonality(), persona.getBackground(), topic);


        String url = QWEN_CHAT_COMPLETION_URL;
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        //构建请求体 JSON
        JSONObject requestBodyJson = new JSONObject();
        try {
            requestBodyJson.put("model", QWEN_MODEL);

            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesArray.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "请立即根据我的要求和主题发布动态。");
            messagesArray.put(userMessage);

            requestBodyJson.put("messages", messagesArray);

        } catch (JSONException e) {
            Log.e(TAG, "构造动态发布 JSON Payload失败", e);
            return;
        }

        //创建并发送 HTTP 请求
        RequestBody requestBody = RequestBody.create(requestBodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer " + QWEN_API_KEY)
                .build();

        //处理响应并发布动态
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonString = response.body().string();
                        JSONObject responseJson = new JSONObject(jsonString);

                        JSONArray choices = responseJson.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject firstChoice = choices.getJSONObject(0);
                            JSONObject message = firstChoice.getJSONObject("message");
                            String contentString = message.getString("content");

                            String cleanedContentString = contentString.replaceAll("```json", "").replaceAll("```", "").trim();
                            JSONObject dynamicJson = new JSONObject(cleanedContentString);

                            String content = dynamicJson.optString("text", "AI生成动态失败，请检查API响应格式。");
                            String imageUrl = dynamicJson.optString("image_url", "");

                            Dynamic newDynamic = new Dynamic(
                                    persona.getId(),
                                    persona.getName(),
                                    persona.getAvatarUrl(),
                                    content,
                                    imageUrl
                            );

                            repository.insert(newDynamic);
                            Log.i(TAG, "动态发布成功: " + content);

                            dynamicPublishSuccess.postValue(true);

                        } else {
                            Log.e(TAG, "AI响应中未找到 choices 或 choices 为空。");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "动态生成或解析失败: " + e.getMessage(), e);
                    }
                } else {
                    Log.e(TAG, "API 请求失败，代码: " + response.code());
                    try (Response originalResponse = response) {
                        if (originalResponse.body() != null) {
                            Log.e(TAG, "失败响应体: " + originalResponse.body().string());
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "网络请求失败: " + e.getMessage());
            }
        });
    }

    /**
     * 发送消息并切换为流式请求。
     */
    public void sendMessage(String message, Persona persona) {
        if (persona == null || message == null || message.trim().isEmpty()) return;

        // 1. 瞬间上屏
        Message userMessage = new Message(persona.getId(), message, true, "用户");
        repository.getExecutorService().execute(() -> repository.insert(userMessage));

        // 2. 异步启动 Agent 决策链
        repository.getExecutorService().execute(() -> {
            try {
                JSONObject probeBody = new JSONObject();
                probeBody.put("model", QWEN_MODEL);
                probeBody.put("stream", false);
                probeBody.put("temperature", 0.1);

                JSONArray messagesArray = new JSONArray();

                // 💡 记忆组装 1：将【长期进化记忆】刻入潜意识
                StringBuilder sbProbePrompt = new StringBuilder();
                sbProbePrompt.append("你现在的身份是 \"").append(persona.getName())
                        .append("\"，性格特点是：\"").append(persona.getPersonality())
                        .append("\"，背景故事是：\"").append(persona.getBackground()).append("\"。\n\n");

                sbProbePrompt.append("【你对该用户的长期记忆与历史了解】\n")
                        .append(persona.getLongTermMemory()).append("\n\n");

                sbProbePrompt.append("【输出格式约束】\n");
                if (persona.isColloquialEnabled()) {
                    sbProbePrompt.append("- 语气约束：请务必使用极其口语化、接地气的表达方式，多用简短对话，并且多加一些生动的 Emoji 表情（😊、🤔等）。\n");
                }
                if (persona.isWordLimitEnabled() && persona.getWordLimit() > 0) {
                    sbProbePrompt.append("- 字数约束：你的回答必须极其精炼，废话少说，绝对不能超过 ").append(persona.getWordLimit()).append(" 个字！\n");
                }
                if (!persona.isColloquialEnabled() && !persona.isWordLimitEnabled()) {
                    sbProbePrompt.append("- 无特殊字数和语气格式约束。\n");
                }

                sbProbePrompt.append("\n【智能体核心行为准则】\n")
                        .append("1. 日常闲聊（如问候、天气）：保持沉浸感，用第一人称简短、口语化回复。绝对不要提“作为一个AI”。\n")
                        .append("2. 专业/事实问题：遇到询问具体知识时，调用 search_local_docs 工具进行资料检索。\n")
                        .append("3. 绘画/视觉请求：当用户想要看图片、要求画画时，调用 generate_image 工具。\n")
                        .append("4. 日程管理：当用户要求记录任务或查询待办时，调用 add_todo 或 query_todos 工具。\n")
                        // 💡 新增：告诉它遇到算术和逻辑问题时，用代码解决！
                        .append("5. 计算与分析：当用户要求计算复杂数学公式、分析数据流时，【必须】调用 python_interpreter 工具写代码解决。\n")
                        // 💡 绝对不能丢的极速通道，保证闲聊秒回！
                        .append("6. 日常闲聊高速通道：如果用户的输入不需要调用上述任何工具，请你【必须且只能】回复字母：『PASS』，绝对不要生成任何实际回复！\n")
                        .append("请严格判断应回复 PASS 还是调用合适的工具。");

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", sbProbePrompt.toString());
                messagesArray.put(systemMsg);

                // 💡 记忆组装 2：调取【短期滑动记忆】（最近6条=3轮交互），让AI拥有连贯上下文
                List<Message> history = repository.getRecentMessagesSync(persona.getId(), 6);
                if (history != null) {
                    for (Message msg : history) {
                        JSONObject histMsg = new JSONObject();
                        histMsg.put("role", msg.isUser() ? "user" : "assistant");
                        histMsg.put("content", msg.getText());
                        messagesArray.put(histMsg);
                    }
                }

                // 注入当前提问
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", message);
                messagesArray.put(userMsg);

                probeBody.put("messages", messagesArray);
                probeBody.put("tools", buildAgentTools());

                RequestBody body = RequestBody.create(probeBody.toString(), MediaType.get("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(QWEN_CHAT_COMPLETION_URL)
                        .post(body)
                        .header("Authorization", "Bearer " + QWEN_API_KEY)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("探针请求失败，状态码: " + response.code());
                    }

                    JSONObject resJson = new JSONObject(response.body().string());
                    JSONObject choice = resJson.getJSONArray("choices").getJSONObject(0);
                    JSONObject responseMessage = choice.getJSONObject("message");

                    if (responseMessage.has("tool_calls")) {
                        JSONArray toolCalls = responseMessage.getJSONArray("tool_calls");
                        JSONObject toolCall = toolCalls.getJSONObject(0);
                        String callId = toolCall.getString("id");
                        String functionName = toolCall.getJSONObject("function").getString("name");
                        String argumentsStr = toolCall.getJSONObject("function").getString("arguments");
                        JSONObject argsObj = new JSONObject(argumentsStr);

                        if ("search_local_docs".equals(functionName)) {
                            // ===== 触发知识库检索 =====
                            String searchQuery = argsObj.optString("search_query", message);
                            Log.i(TAG, "Agent 判定触发知识库！提取的搜索词为: " + searchQuery);

                            List<Double> queryVector = getEmbeddingSync(searchQuery);
                            String localContext = "没有在本地记忆库中找到相关的参考文档。";
                            com.example.demo.data.KnowledgeChunk bestChunk = null;

                            if (queryVector != null) {
                                List<com.example.demo.data.KnowledgeChunk> allChunks = repository.getKnowledgeChunksSync(persona.getId());
                                double maxSimilarity = -1.0;
                                for (com.example.demo.data.KnowledgeChunk chunk : allChunks) {
                                    double sim = calculateCosineSimilarity(queryVector, chunk.vector);
                                    if (sim > maxSimilarity) {
                                        maxSimilarity = sim;
                                        bestChunk = chunk;
                                    }
                                }
                                if (bestChunk != null && maxSimilarity > 0.35) {
                                    localContext = bestChunk.textContent;
                                }
                            }

                            JSONArray followUpMessages = new JSONArray();
                            followUpMessages.put(systemMsg);

                            // 💡 注意：为了节省 Token，RAG 时暂不注入历史闲聊，只保留最新提问
                            followUpMessages.put(userMsg);
                            followUpMessages.put(responseMessage);

                            JSONObject toolResultMsg = new JSONObject();
                            toolResultMsg.put("role", "tool");
                            toolResultMsg.put("name", "search_local_docs");
                            toolResultMsg.put("tool_call_id", callId);
                            toolResultMsg.put("content", "这是从本地文档《" + (bestChunk != null ? bestChunk.docName : "未知") + "》中为您检索到的核心真实事实：\n" + localContext);
                            followUpMessages.put(toolResultMsg);

                            // 💡 记忆组装 3：即便查文档，也不能忘了【长期记忆】
                            StringBuilder sbAgentPrompt = new StringBuilder();
                            sbAgentPrompt.append("你必须严格基于给定的 [tool] 角色提供的事实内容，使用符合你本身人设(名称:").append(persona.getName())
                                    .append(", 性格:").append(persona.getPersonality()).append(")的语气，提炼并回答用户的问题。如果内容中未提及，请委婉表达你不知道，绝不可胡编乱造。\n\n");

                            sbAgentPrompt.append("【你对该用户的长期记忆与历史了解】\n")
                                    .append(persona.getLongTermMemory()).append("\n\n");

                            sbAgentPrompt.append("【必须严格遵守的高级输出约束】\n");
                            if (persona.isColloquialEnabled()) {
                                sbAgentPrompt.append("- 请务必用极其口语化、接地气的口吻组织语言，多用短句，且必须带有丰富的 Emoji 表情！\n");
                            }
                            if (persona.isWordLimitEnabled() && persona.getWordLimit() > 0) {
                                sbAgentPrompt.append("- 你的提炼回答必须极度精炼，直接切入核心，总字数绝对不能超过 ").append(persona.getWordLimit()).append(" 个字！\n");
                            }

                            List<Message> mockHistory = new ArrayList<>();
                            for (int k = 1; k < followUpMessages.length(); k++) { // 跳过 system
                                JSONObject m = followUpMessages.getJSONObject(k);
                                String textContent = m.has("content") ? m.getString("content") : m.toString();
                                boolean isUserRole = "user".equals(m.optString("role"));
                                mockHistory.add(new Message(persona.getId(), textContent, isUserRole, isUserRole ? "用户" : persona.getName()));
                            }

                            sendStreamingAiRequestWithCustomSystem(mockHistory, persona, sbAgentPrompt.toString());

                        }
                        else if ("generate_image".equals(functionName)) {
                            // ===== 触发画图工具 =====
                            String imagePrompt = argsObj.optString("image_prompt", message);
                            agentStatusText.postValue("🎨 正在为你绘制图像，请稍候...");
                            generateImageRequest(imagePrompt, persona);
                        }
                        else if ("add_todo".equals(functionName)) {
                            // ===== 触发添加待办 =====
                            String taskName = argsObj.optString("task_name", "未知任务");
                            String dueDate = argsObj.optString("due_date", "待定");
                            Log.i(TAG, "Agent 调用添加待办: " + taskName + ", 时间: " + dueDate);

                            // 1. 瞬间写入本地数据库
                            com.example.demo.data.Todo newTodo = new com.example.demo.data.Todo(persona.getId(), taskName, dueDate, false);
                            repository.insertTodo(newTodo);

                            // 2. 告诉二次流式它成功了
                            JSONArray followUpMessages = new JSONArray();
                            followUpMessages.put(systemMsg);
                            followUpMessages.put(userMsg);
                            followUpMessages.put(responseMessage);
                            JSONObject toolResultMsg = new JSONObject();
                            toolResultMsg.put("role", "tool");
                            toolResultMsg.put("name", "add_todo");
                            toolResultMsg.put("tool_call_id", callId);
                            toolResultMsg.put("content", "系统返回：已成功将任务【" + taskName + "】(时间：" + dueDate + ") 写入用户的日程数据库。");
                            followUpMessages.put(toolResultMsg);

                            // 3. 复用你最丝滑的流式打字机来告诉用户
                            StringBuilder sbAgentPrompt = new StringBuilder();
                            sbAgentPrompt.append("你现在的身份是 \"").append(persona.getName()).append("\"。刚才你调用了待办添加工具，并得到了系统成功的反馈。\n")
                                    .append("请使用符合你人设的语气，告诉用户任务已成功记录，可以带点鼓励。\n\n");
                            // (可在此处判断追加口语化等约束)

                            List<Message> mockHistory = new ArrayList<>();
                            for (int k = 1; k < followUpMessages.length(); k++) {
                                JSONObject m = followUpMessages.getJSONObject(k);
                                boolean isUserRole = "user".equals(m.optString("role"));
                                mockHistory.add(new Message(persona.getId(), m.has("content") ? m.getString("content") : m.toString(), isUserRole, isUserRole ? "用户" : persona.getName()));
                            }
                            sendStreamingAiRequestWithCustomSystem(mockHistory, persona, sbAgentPrompt.toString());

                        }
                        else if ("query_todos".equals(functionName)) {
                            // ===== 触发查询待办 =====
                            Log.i(TAG, "Agent 调用查询待办");

                            // 1. 从底层数据库捞出数据
                            List<com.example.demo.data.Todo> pendingTodos = repository.getPendingTodosSync(persona.getId());
                            StringBuilder todoListStr = new StringBuilder();
                            if (pendingTodos == null || pendingTodos.isEmpty()) {
                                todoListStr.append("当前数据库中没有未完成的任务。");
                            } else {
                                todoListStr.append("当前未完成任务列表：\n");
                                for (int i = 0; i < pendingTodos.size(); i++) {
                                    com.example.demo.data.Todo t = pendingTodos.get(i);
                                    todoListStr.append(i + 1).append(". ").append(t.taskName).append(" (时间: ").append(t.dueDate).append(")\n");
                                }
                            }

                            // 2. 将真实数据交给二次流式
                            JSONArray followUpMessages = new JSONArray();
                            followUpMessages.put(systemMsg);
                            followUpMessages.put(userMsg);
                            followUpMessages.put(responseMessage);
                            JSONObject toolResultMsg = new JSONObject();
                            toolResultMsg.put("role", "tool");
                            toolResultMsg.put("name", "query_todos");
                            toolResultMsg.put("tool_call_id", callId);
                            toolResultMsg.put("content", "系统返回的真实数据：\n" + todoListStr.toString() + "\n请根据此数据，用你的人设口吻向用户汇报日程。");
                            followUpMessages.put(toolResultMsg);

                            // 3. 复用你最丝滑的流式打字机来播报
                            StringBuilder sbAgentPrompt = new StringBuilder();
                            sbAgentPrompt.append("你现在的身份是 \"").append(persona.getName()).append("\"。刚才你调用了查询待办工具，拿到了系统返回的日程数据。\n")
                                    .append("请使用符合你人设的语气，自然地向用户播报这些任务。如果没有任务，可以顺势夸奖用户效率高。\n\n");

                            List<Message> mockHistory = new ArrayList<>();
                            for (int k = 1; k < followUpMessages.length(); k++) {
                                JSONObject m = followUpMessages.getJSONObject(k);
                                boolean isUserRole = "user".equals(m.optString("role"));
                                mockHistory.add(new Message(persona.getId(), m.has("content") ? m.getString("content") : m.toString(), isUserRole, isUserRole ? "用户" : persona.getName()));
                            }
                            sendStreamingAiRequestWithCustomSystem(mockHistory, persona, sbAgentPrompt.toString());
                        }
                        else if ("python_interpreter".equals(functionName)) {
                            // ===== 触发代码沙盒与数据计算 =====
                            String script = argsObj.optString("script", "");
                            Log.i(TAG, "Agent 编写了 Python 脚本准备计算:\n" + script);

                            agentStatusText.postValue("💻 正在沙盒中执行计算，请稍候...");

                            // 2. 模拟沙盒执行（未来你可以将 script 发送到你的 Spring Boot 后端进行真实计算并返回结果）
                            // 这里我们先做一个前端模拟拦截，让 Agent 把生成的代码原样解释给用户听
                            String executionResult = "【沙盒模拟返回】代码已成功运行。\n生成的脚本如下：\n```python\n" + script + "\n```\n(注：真实执行需接入后端计算节点)";

                            // 3. 将执行结果塞回给大模型
                            JSONArray followUpMessages = new JSONArray();
                            followUpMessages.put(systemMsg);
                            followUpMessages.put(userMsg);
                            followUpMessages.put(responseMessage);
                            JSONObject toolResultMsg = new JSONObject();
                            toolResultMsg.put("role", "tool");
                            toolResultMsg.put("name", "python_interpreter");
                            toolResultMsg.put("tool_call_id", callId);
                            toolResultMsg.put("content", executionResult);
                            followUpMessages.put(toolResultMsg);

                            // 4. 复用丝滑的流式打字机进行播报
                            StringBuilder sbAgentPrompt = new StringBuilder();
                            sbAgentPrompt.append("你现在的身份是 \"").append(persona.getName()).append("\"。刚才你编写了 Python 代码并在沙盒中运行了。\n")
                                    .append("请使用符合你人设的语气，把代码逻辑和执行结果自然地告诉用户。\n\n");

                            List<Message> mockHistory = new ArrayList<>();
                            for (int k = 1; k < followUpMessages.length(); k++) {
                                JSONObject m = followUpMessages.getJSONObject(k);
                                boolean isUserRole = "user".equals(m.optString("role"));
                                mockHistory.add(new Message(persona.getId(), m.has("content") ? m.getString("content") : m.toString(), isUserRole, isUserRole ? "用户" : persona.getName()));
                            }
                            sendStreamingAiRequestWithCustomSystem(mockHistory, persona, sbAgentPrompt.toString());
                        }
                    } else {
                        // ==========================================
                        // 分支 3：未触发工具调用，大模型判定为日常轻松闲聊
                        // ==========================================
                        Log.i(TAG, "Agent 判定为日常闲聊，放弃探针结果，发起真实的流式请求...");

                        // 重新组装专门用于流式闲聊的 System Prompt，带上长期记忆
                        StringBuilder sbAgentPrompt = new StringBuilder();
                        sbAgentPrompt.append("你现在的身份是 \"").append(persona.getName())
                                .append("\"，性格特点是：\"").append(persona.getPersonality())
                                .append("\"，背景故事是：\"").append(persona.getBackground()).append("\"。\n\n");

                        sbAgentPrompt.append("【你对该用户的长期记忆与历史了解】\n")
                                .append(persona.getLongTermMemory()).append("\n\n");

                        sbAgentPrompt.append("【必须严格遵守的高级输出约束】\n");
                        if (persona.isColloquialEnabled()) {
                            sbAgentPrompt.append("- 请务必用极其口语化、接地气的口吻组织语言，多用短句，且必须带有丰富的 Emoji 表情！\n");
                        }
                        if (persona.isWordLimitEnabled() && persona.getWordLimit() > 0) {
                            sbAgentPrompt.append("- 你的回答必须极其精炼，废话少说，绝对不能超过 ").append(persona.getWordLimit()).append(" 个字！\n");
                        }

                        // 构造发送给流式接口的历史记录（包含之前查出的近期历史 + 本次的新问题）
                        List<Message> mockHistory = new ArrayList<>();
                        if (history != null) {
                            for (Message msg : history) {
                                mockHistory.add(new Message(persona.getId(), msg.getText(), msg.isUser(), msg.isUser() ? "用户" : persona.getName()));
                            }
                        }
                        // 追加当前的新问题
                        mockHistory.add(new Message(persona.getId(), message, true, "用户"));

                        // 💡 丢弃第一阶段探针的生硬结果，发起真实的云端流式请求！
                        sendStreamingAiRequestWithCustomSystem(mockHistory, persona, sbAgentPrompt.toString());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Agent 链条推理执行失败", e);
                repository.insert(new Message(persona.getId(), "网络迷路了，请稍后再试。", false, "系统"));
                isStreaming.postValue(false);
            }
        });
    }

    /**
     * 封装流式 AI 请求逻辑 (SSE 解析)。
     */
    private void sendStreamingAiRequest(List<Message> chatHistory, Persona persona) {
        //根据当前 Persona 的属性（名称、性格、背景）生成系统提示，要求 AI 严格扮演该角色，并使用 Markdown 格式回复
        String systemPrompt = String.format(
                "你是一个名为 \"%s\" 的角色，性格特征是 \"%s\"。你的背景故事是 \"%s\"。请严格扮演该角色，根据聊天记录回复用户。你的回复应简洁自然，**并且要求回复中必须使用 Markdown 格式，例如粗体和列表**。",
                persona.getName(), persona.getPersonality(), persona.getBackground());

        //指定 AI 接口地址
        String url = QWEN_CHAT_COMPLETION_URL;
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        JSONObject requestBodyJson = new JSONObject();
        try {
            requestBodyJson.put("model", QWEN_MODEL);
            requestBodyJson.put("stream", true); //开启流式输出

            //将系统提示词包装为 system 角色的消息，放入请求的消息列表中
            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesArray.put(systemMessage);

            //遍历处理后的历史消息，转换为 AI 接口要求的格式
            for (Message msg : chatHistory) {
                JSONObject chatMessage = new JSONObject();
                chatMessage.put("role", msg.isUser() ? "user" : "assistant");
                chatMessage.put("content", msg.getText());
                messagesArray.put(chatMessage);
            }

            requestBodyJson.put("messages", messagesArray);

        } catch (JSONException e) {
            Log.e(TAG, "构造流式请求 JSON Payload 失败", e);
            isStreaming.postValue(false);
            return;
        }

        //创建包含请求体、URL 和授权头（API Key）的 POST 请求
        RequestBody requestBody = RequestBody.create(requestBodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer " + QWEN_API_KEY)
                .build();

        //使用 OkHttpClient 异步发送请求，在 onResponse 中处理返回结果
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String fullResponse = "";
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("Unexpected code " + response);
                    }

                    try (okhttp3.ResponseBody responseBody = response.body()) {

                        //用 BufferedReader 逐行读取流式响应
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));

                        String line;
                        //若读取到 [DONE] 标记，说明流式传输结束，退出循环。
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if (data.equals("[DONE]")) {
                                    break;
                                }

                                try {
                                    //解析每行 data: 后的 JSON 数据，提取 AI 回复的内容片段
                                    JSONObject jsonChunk = new JSONObject(data);
                                    JSONArray choices = jsonChunk.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                        String contentChunk = delta.optString("content", "");

                                        //将片段追加到完整响应中
                                        if (!contentChunk.isEmpty()) {
                                            fullResponse += contentChunk;
                                            streamingTextChunk.postValue(contentChunk);
                                            try {
                                                Thread.sleep(50); // 引入延迟，模拟打字速度
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                                Log.e(TAG, "流式传输线程休眠时被中断", e);
                                            }
                                            streamingTextChunk.postValue(null);
                                        }
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "解析流式 JSON 块失败: " + e.getMessage());
                                }
                            }
                        }
                    }

                    if (!fullResponse.isEmpty()) {
                        Message aiMessage = new Message(persona.getId(), fullResponse, false, persona.getName());
                        repository.insert(aiMessage);
//
//                        // 核心修改：利用正则表达式去掉 Markdown 符号（星号、井号、列表符号）
//                        String rawText = fullResponse; // AI 返回的原始带 Markdown 的字符串
//                        String cleanText = rawText.replaceAll("\\*\\*", "") // 去除粗体 **
//                                .replaceAll("- ", "")     // 去除列表横杠
//                                .replaceAll("#", "")      // 去除标题 #
//                                .trim();
//
//                        synthesizeAndPlay(cleanText);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "流式请求网络或IO错误: " + e.getMessage());
                    // 保存错误消息
                    Message errorMessage = new Message(persona.getId(), "网络错误，请检查网络连接。", false, "系统");
                    repository.insert(errorMessage);
                } finally {
                    // 确保在任何情况下都关闭流式状态
                    isStreaming.postValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "流式请求失败: " + e.getMessage());
                isStreaming.postValue(false);
                // 仅保存一条系统错误消息
                Message errorMessage = new Message(persona.getId(), "网络错误，请检查网络连接。", false, "系统");
                repository.insert(errorMessage);
            }
        });
    }

    /**
     * 发起流式网络请求（专用于 Agent 模式，支持自定义 System Prompt 和历史伪造）
     */
    private void sendStreamingAiRequestWithCustomSystem(List<Message> history, Persona persona, String customSystemPrompt) {
        currentStreamingText.postValue("");

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", QWEN_MODEL);
            requestBody.put("stream", true);

            JSONArray messagesArray = new JSONArray();

            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", customSystemPrompt);
            messagesArray.put(systemMsg);

            if (history != null) {
                for (Message m : history) {
                    JSONObject msg = new JSONObject();
                    msg.put("role", m.isUser() ? "user" : "assistant");
                    msg.put("content", m.getText());
                    messagesArray.put(msg);
                }
            }
            requestBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(QWEN_CHAT_COMPLETION_URL)
                    .header("Authorization", "Bearer " + QWEN_API_KEY)
                    .header("Accept", "text/event-stream")
                    .post(body)
                    .build();

            // ==========================================
            // 3. 处理流式返回 (完美照搬原版极致丝滑的打字机逻辑！)
            // ==========================================
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("API 流式请求失败，状态码: " + response.code());
                }

                String fullResponse = ""; // 恢复原版的拼接变量
                try (okhttp3.ResponseBody responseBody = response.body()) {

                    // 恢复原版的 BufferedReader 逐行读取机制
                    BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (data.equals("[DONE]")) {
                                break;
                            }

                            try {
                                JSONObject jsonChunk = new JSONObject(data);
                                JSONArray choices = jsonChunk.optJSONArray("choices");
                                if (choices != null && choices.length() > 0) {
                                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                    if (delta != null && delta.has("content")) {
                                        String contentChunk = delta.optString("content", "");

                                        // 💡 完全使用你原版的增量更新策略！
                                        if (!contentChunk.isEmpty()) {
                                            fullResponse += contentChunk;

                                            // 💡 修复断流：ChatActivity 监听的是 currentStreamingText，必须把碎片发给它！
                                            currentStreamingText.postValue(contentChunk);
                                            try {
                                                Thread.sleep(50); // 恢复原版的 50ms 优雅延迟
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                                Log.e(TAG, "流式传输线程休眠时被中断", e);
                                            }
                                            // 触发 Adapter 里的状态重置
                                            currentStreamingText.postValue(null);
                                        }
                                    }
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "解析流式 JSON 块失败: " + e.getMessage());
                            }
                        }
                    }
                }

                // ==========================================
                // 4. 流式彻底接收完毕！统一写入 Room 数据库
                // ==========================================
                String finalContent = fullResponse.trim();
                if (!finalContent.isEmpty()) {
                    Message aiMessage = new Message(persona.getId(), finalContent, false, persona.getName());
                    repository.insert(aiMessage);

                    // 💡 进化触发 2：完全保留新增的长期记忆萃取功能，丝毫不受影响！
                    if (history != null && !history.isEmpty()) {
                        String userQuestion = "";
                        // 倒序查找最后一条属于用户的消息，作为提取记忆的 "最新提问"
                        for (int i = history.size() - 1; i >= 0; i--) {
                            if (history.get(i).isUser()) {
                                userQuestion = history.get(i).getText();
                                break;
                            }
                        }
                        // 如果找到了用户的提问，启动后台潜意识萃取
                        if (!userQuestion.isEmpty()) {
                            extractAndEvolutionMemory(persona, userQuestion, finalContent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "流式请求过程中发生异常", e);
            repository.insert(new Message(persona.getId(), "抱歉，我的网络连接似乎断开了，没能把话说完。", false, "系统"));
        } finally {
            isStreaming.postValue(false);
        }
    }

    /**
     * 处理用户上传的文档：读取纯文本 -> 文本切片 -> 向量化 -> 存入 Room 数据库
     */
    /**
     * 【全格式支持版】处理用户上传的文档：支持 TXT / PDF / DOCX
     */
    public void processAndSaveDocument(Uri documentUri, Persona persona, Context context) {
        // 【关键初始化】如果你使用了 PDFBox，必须在使用前初始化它的资源加载器
        PDFBoxResourceLoader.init(context);

        repository.getExecutorService().execute(() -> {
            try {
                // 1. 获取真实文件名
                String fileName = "未知文档.txt";
                android.database.Cursor cursor = context.getContentResolver().query(documentUri, null, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                    cursor.close();
                }

                // 2. 根据文件后缀名，智能选择解析方案抽取纯文本
                String fullText = "";
                String lowerFileName = fileName.toLowerCase();
                InputStream inputStream = context.getContentResolver().openInputStream(documentUri);

                if (inputStream == null) {
                    throw new Exception("无法读取文件内容");
                }

                if (lowerFileName.endsWith(".pdf")) {
                    // 【解析 PDF】
                    PDDocument document = PDDocument.load(inputStream);
                    PDFTextStripper stripper = new PDFTextStripper();
                    fullText = stripper.getText(document);
                    document.close();

                } else if (lowerFileName.endsWith(".docx")) {
                    // 【解析 DOCX】
                    XWPFDocument document = new XWPFDocument(inputStream);
                    XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                    fullText = extractor.getText();
                    extractor.close();

                } else {
                    // 【解析默认的 TXT 或其他文本】
                    StringBuilder textBuilder = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        textBuilder.append(line).append("\n");
                    }
                    fullText = textBuilder.toString();
                }

                inputStream.close(); // 统一关闭流

                fullText = fullText.trim();
                if (fullText.isEmpty()) {
                    Log.e(TAG, "文档提取后内容为空，跳过处理");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        android.widget.Toast.makeText(context, "文档似乎是空的或者全为图片，无法提取文字", android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // 3. 文本滑动切片 (Chunking) - 与之前逻辑保持一致
                int chunkSize = 300;
                int overlap = 50;
                List<String> chunks = new ArrayList<>();
                int i = 0;
                while (i < fullText.length()) {
                    int end = Math.min(i + chunkSize, fullText.length());
                    chunks.add(fullText.substring(i, end));
                    if (end == fullText.length()) break;
                    i += (chunkSize - overlap);
                }

                // 4. 遍历每个文本块，调用 Embedding API，存入数据库
                int successCount = 0;
                for (String chunkText : chunks) {
                    List<Double> vector = getEmbeddingSync(chunkText);
                    if (vector != null) {
                        KnowledgeChunk chunkEntity = new KnowledgeChunk(persona.getId(), fileName, chunkText, vector);
                        repository.insertKnowledgeChunk(chunkEntity);
                        successCount++;
                    } else {
                        Thread.sleep(500); // 应对限流
                    }
                }

                Log.i(TAG, "知识库文档吸收完毕！文件名：" + fileName + "，共保存了 " + successCount + " 个知识块。");

                final String finalFileName = fileName;
                final int finalSuccessCount = successCount;

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(
                            context,
                            "上传成功！《" + finalFileName + "》已转化为 " + finalSuccessCount + " 个知识记忆",
                            android.widget.Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "处理文档并保存时发生异常: " + e.getMessage(), e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(context, "文档解析失败，可能是文件损坏或格式不受支持", android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 【新增】更新 Persona 的属性（比如开关 Markdown）
     */
    public void updatePersona(Persona persona) {
        repository.getExecutorService().execute(() -> {
            // 假设你的 PersonaRepository 里有 update 方法。
            // 如果没有，直接用 repository.insert(persona) 通常也能覆盖更新（取决于你的 DAO 策略）
            repository.update(persona);
        });
    }

    /**
     * 💡 【记忆进化引擎】异步无感萃取长期核心记忆，并增量更新到角色事实库中
     */
    private void extractAndEvolutionMemory(Persona persona, String userQuestion, String aiResponse) {
        repository.getExecutorService().execute(() -> {
            try {
                JSONObject memoryBody = new JSONObject();
                memoryBody.put("model", QWEN_MODEL);
                memoryBody.put("stream", false);
                memoryBody.put("temperature", 0.0); // 绝对理智，不瞎编

                JSONArray messages = new JSONArray();

                JSONObject systemRole = new JSONObject();
                String extractionPrompt = "你是一个智能体后台潜意识记忆萃取器。\n" +
                        "【⚠️ 实体防混淆警告】当前 AI 扮演的虚拟角色名字是『" + persona.getName() + "』。当用户的话语中出现这个名字时，是在呼唤 AI，绝对不是用户本人的名字！\n\n" +
                        "请仔细阅读用户与AI的最新一轮对话，专门提炼出关于【用户本人】的长期核心静态事实（例如：用户的真实称呼、职业身份、正在研究的专业领域、明确表达的个人偏好、当前的长期核心任务等）。\n" +
                        "【萃取要求】\n" +
                        "1. 必须是长期的背景事实，绝对不要记录临时状态（如心情好、单纯的指令等）。\n" +
                        "2. 归纳语言必须极其精炼，用分号隔开，只提炼新出现的事实。\n" +
                        "3. 如果这段对话里完全没有提及任何关于【用户自身】的新硬核信息，你必须且只能回复两个字：\"无\"。";

                systemRole.put("role", "system");
                systemRole.put("content", extractionPrompt);
                messages.put(systemRole);

                JSONObject userContext = new JSONObject();
                userContext.put("role", "user");
                userContext.put("content", "【最新交互记录】\n用户问: \"" + userQuestion + "\"\nAI答: \"" + aiResponse + "\"");
                messages.put(userContext);
                memoryBody.put("messages", messages);

                RequestBody body = RequestBody.create(memoryBody.toString(), MediaType.get("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(QWEN_CHAT_COMPLETION_URL)
                        .post(body)
                        .header("Authorization", "Bearer " + QWEN_API_KEY)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject resJson = new JSONObject(response.body().string());
                        String extractedInfo = resJson.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").optString("content", "").trim();

                        // 如果提炼出了新记忆，且大模型没有判定为“无”，执行进化融合
                        if (!extractedInfo.isEmpty() && !extractedInfo.contains("无")) {
                            Log.i(TAG, "🧠 长期记忆萃取成功！捕获到用户新事实: " + extractedInfo);

                            String currentMemory = persona.getLongTermMemory();
                            if (currentMemory == null || currentMemory.contains("暂无")) {
                                currentMemory = "";
                            }
                            String evolvedMemory = currentMemory + (currentMemory.isEmpty() ? "" : "\n") + "- " + extractedInfo;
                            persona.setLongTermMemory(evolvedMemory);

                            updatePersona(persona); // 写入底层数据库，实现永久记忆
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "记忆进化引擎在后台开小差了", e);
            }
        });
    }


    /**
     * 【新增】组装 Function Calling 工具箱声明的 JSON 数组
     */
    private JSONArray buildAgentTools() {
        JSONArray toolsArray = new JSONArray();
        try {
            // ==========================================
            // 工具 1：本地知识库检索 (search_local_docs)
            // ==========================================
            JSONObject docTool = new JSONObject();
            docTool.put("type", "function");
            JSONObject docFunc = new JSONObject();
            docFunc.put("name", "search_local_docs");
            docFunc.put("description", "当用户询问特定专业知识、查阅资料、或者问及你过去记忆库中可能存在的文档文件时，必须调用此工具进行本地检索。");
            JSONObject docParams = new JSONObject();
            docParams.put("type", "object");
            JSONObject docProps = new JSONObject();
            JSONObject queryProp = new JSONObject();
            queryProp.put("type", "string");
            queryProp.put("description", "用于在本地向量知识库中搜索的关键词或问题摘要");
            docProps.put("search_query", queryProp);
            docParams.put("properties", docProps);
            docParams.put("required", new JSONArray().put("search_query"));
            docFunc.put("parameters", docParams);
            docTool.put("function", docFunc);
            toolsArray.put(docTool);

            // ==========================================
            // 工具 2：AI 绘画生成 (generate_image)
            // ==========================================
            JSONObject drawTool = new JSONObject();
            drawTool.put("type", "function");
            JSONObject drawFunc = new JSONObject();
            drawFunc.put("name", "generate_image");
            drawFunc.put("description", "当用户明确要求画画、生成图片、或者需要视觉图像来展示某物时，必须调用此工具。");
            JSONObject drawParams = new JSONObject();
            drawParams.put("type", "object");
            JSONObject drawProps = new JSONObject();
            JSONObject promptProp = new JSONObject();
            promptProp.put("type", "string");
            promptProp.put("description", "提炼出的画面描述词(Prompt)。必须转换为英文，用于喂给文生图大模型，描述要尽可能详细且包含画风设定。");
            drawProps.put("image_prompt", promptProp);
            drawParams.put("properties", drawProps);
            drawParams.put("required", new JSONArray().put("image_prompt"));
            drawFunc.put("parameters", drawParams);
            drawTool.put("function", drawFunc);
            toolsArray.put(drawTool);

            // ==========================================
            // 工具 3：添加待办事项 (add_todo)
            // ==========================================
            JSONObject addTodoTool = new JSONObject();
            addTodoTool.put("type", "function");
            JSONObject addTodoFunc = new JSONObject();
            addTodoFunc.put("name", "add_todo");
            addTodoFunc.put("description", "当用户要求你记住一个任务、安排日程、提醒他做某事时，调用此工具将任务添加到数据库。");
            JSONObject addTodoParams = new JSONObject();
            addTodoParams.put("type", "object");
            JSONObject addTodoProps = new JSONObject();

            JSONObject taskNameProp = new JSONObject();
            taskNameProp.put("type", "string");
            taskNameProp.put("description", "任务的具体内容摘要");
            addTodoProps.put("task_name", taskNameProp);

            JSONObject dueDateProp = new JSONObject();
            dueDateProp.put("type", "string");
            dueDateProp.put("description", "任务的截止时间（如'明天上午'，'2026-06-01'等，如果没有具体时间请填'待定'）");
            addTodoProps.put("due_date", dueDateProp);

            addTodoParams.put("properties", addTodoProps);
            addTodoParams.put("required", new JSONArray().put("task_name").put("due_date"));
            addTodoFunc.put("parameters", addTodoParams);
            addTodoTool.put("function", addTodoFunc);
            toolsArray.put(addTodoTool);

            // ==========================================
            // 工具 4：查询待办事项 (query_todos)
            // ==========================================
            JSONObject queryTodoTool = new JSONObject();
            queryTodoTool.put("type", "function");
            JSONObject queryTodoFunc = new JSONObject();
            queryTodoFunc.put("name", "query_todos");
            queryTodoFunc.put("description", "当用户问'我今天有什么安排'、'我的待办事项是什么'、'提醒我一下要做什么'时，调用此工具查询未完成任务列表。");
            JSONObject queryTodoParams = new JSONObject();
            queryTodoParams.put("type", "object");
            queryTodoParams.put("properties", new JSONObject());
            queryTodoFunc.put("parameters", queryTodoParams);
            queryTodoTool.put("function", queryTodoFunc);
            toolsArray.put(queryTodoTool);

            // ==========================================
            // 工具 5：代码沙盒与数据计算器 (python_interpreter)
            // ==========================================
            JSONObject pythonTool = new JSONObject();
            pythonTool.put("type", "function");
            JSONObject pythonFunc = new JSONObject();
            pythonFunc.put("name", "python_interpreter");
            pythonFunc.put("description", "当用户要求你进行复杂的数学计算、数据分析、或者编写并运行代码来解决问题时，必须调用此工具生成代码。");
            JSONObject pythonParams = new JSONObject();
            pythonParams.put("type", "object");
            JSONObject pythonProps = new JSONObject();

            JSONObject scriptProp = new JSONObject();
            scriptProp.put("type", "string");
            scriptProp.put("description", "需要执行的 Python 3 脚本代码，用于解决用户的计算或分析需求。");
            pythonProps.put("script", scriptProp);

            pythonParams.put("properties", pythonProps);
            pythonParams.put("required", new JSONArray().put("script"));
            pythonFunc.put("parameters", pythonParams);
            pythonTool.put("function", pythonFunc);
            toolsArray.put(pythonTool);
        } catch (JSONException e) {
            Log.e(TAG, "构建工具箱 JSON 失败", e);
        }
        return toolsArray;
    }
}