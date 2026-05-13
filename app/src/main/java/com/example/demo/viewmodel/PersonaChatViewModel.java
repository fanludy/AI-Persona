package com.example.demo.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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

    private final String QWEN_API_KEY = "sk-08d5246bd6dc42d6ba075f8e2de2990b";
    private final String QWEN_CHAT_COMPLETION_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private final String TONGLYI_WANXIANG_IMAGE_GENERATION_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    private final String TONGLYI_WANXIANG_API_KEY ="sk-08d5246bd6dc42d6ba075f8e2de2990b";
    private final String TONGYI_TTS_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private final String TONGYI_TTS_API_KEY = "sk-08d5246bd6dc42d6ba075f8e2de2990b";
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

        httpClient = new OkHttpClient();
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

    public LiveData<String> getAudioFilePathLiveData() { return audioFilePathLiveData; }
    public LiveData<Boolean> getIsSpeakingLiveData() { return isSpeakingLiveData; }

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

    private static final long POLLING_INTERVAL_MS = 5000; // 轮询间隔 5 秒

    /**
     * 轮询查询图片生成任务状态
     */
    private void queryImageTask(String taskId, Persona persona) {
        // 提交任务到后台线程池（单线程，确保顺序执行）
        executorService.execute(() -> {
            boolean keepPolling = true;
            String status = "PENDING"; // 初始状态设为"等待中"

            while (keepPolling) {
                try {
                    // 非初始状态时，等待5秒再轮询（避免频繁请求）
                    if (!status.equals("PENDING")) {
                        Thread.sleep(POLLING_INTERVAL_MS);// POLLING_INTERVAL_MS = 5000（5秒）
                    }

                    // 构建查询任务状态的API地址（基于任务ID）
                    String queryUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
                    // 创建HTTP请求（带认证头）
                    Request request = new Request.Builder()
                            .url(queryUrl)
                            .header("Authorization", "Bearer " + TONGLYI_WANXIANG_API_KEY)
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        // 检查请求是否成功且响应体不为空
                        if (!response.isSuccessful() || response.body() == null) {
                            String errorBody = response.body() != null ? response.body().string() : "N/A";
                            Log.e(TAG, "图片查询 API 请求失败，代码: " + response.code() + ", 错误: " + errorBody);
                            throw new IOException("图片查询 API 请求失败，代码: " + response.code());
                        }

                        // 解析响应JSON
                        String jsonString = response.body().string();
                        JSONObject responseJson = new JSONObject(jsonString);

                        // 提取任务状态（从output字段中获取）
                        JSONObject output = responseJson.optJSONObject("output");

                        if (output != null) {
                            status = output.optString("task_status", "UNKNOWN");// 可能的值：PENDING/SUCCEEDED/FAILED/CANCELED
                        } else {
                            String errorCode = responseJson.optString("code", "UNKNOWN_ERROR");
                            String errorMsg = responseJson.optString("message", "API 返回结构异常，无 Output 字段。");
                            Log.e(TAG, "图片查询 API 返回异常，错误码: " + errorCode + ", 消息: " + errorMsg);
                            throw new Exception("API 任务查询失败: " + errorMsg);
                        }

                        if ("SUCCEEDED".equals(status)) {
                            JSONArray results = output.optJSONArray("results");// 图片结果数组

                            JSONObject input = responseJson.optJSONObject("input");
                            String prompt = (input != null) ? input.optString("prompt", "图片") : "图片";

                            // 解析图片URL（取第一个结果）
                            String imageUrl = "";
                            if (results != null && results.length() > 0) {
                                imageUrl = results.getJSONObject(0).optString("url", "");
                            }

                            // 图片URL有效时，通知UI更新
                            if (!imageUrl.isEmpty()) {
                                generatedImageUrl.postValue(imageUrl);
                            } else {
                                throw new JSONException("图片 URL 为空或解析失败。");
                            }

                            keepPolling = false;

                        } else if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                            Log.e(TAG, "图片任务失败或取消，状态: " + status);
                            Message errorMessage = new Message(persona.getId(), "图片生成失败：任务状态为 " + status + "。", false, "系统");
                            repository.insert(errorMessage);
                            keepPolling = false;

                        } else {
                            Log.d(TAG, "图片任务状态: " + status + "，继续轮询。");
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "图片查询轮询被中断", e);
                    keepPolling = false;
                } catch (Exception e) {
                    Log.e(TAG, "图片查询请求或解析异常: " + e.getMessage(), e);
                    Message errorMessage = new Message(persona.getId(), "图片生成失败：查询异常或 API 返回错误。", false, "系统");
                    repository.insert(errorMessage);
                    keepPolling = false;
                }
            }
            isGeneratingImage.postValue(false);// 通知UI：生成结束
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
            requestBodyJson.put("model", "qwen-turbo");// 指定使用的 AI 模型

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
            requestBodyJson.put("model", "qwen-turbo");

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
        if (persona == null) {
            Log.e(TAG, "无法发送消息：当前没有活跃的 Persona。");
            return;
        }

        //检测消息是否以 /imagine 开头（图片生成指令）
        if (message.trim().toLowerCase().startsWith("/imagine ")) {
            String prompt = message.trim().substring("/imagine ".length()).trim();

            repository.getExecutorService().execute(() -> {
                Message userCommandMessage = new Message(persona.getId(), message, true, "用户");
                repository.insert(userCommandMessage);
                generateImageRequest(prompt, persona); // 调用图片生成方法
            });
            return; // 阻止其进入正常的文本聊天流程
        }

        //若不是图片指令，则创建一条用户发送的文本消息
        Message userMessage = new Message(persona.getId(), message, true, "用户");

        repository.getExecutorService().execute(() -> {
            try {
                repository.insert(userMessage);//消息插入数据库

                //调用仓库的同步方法 getMessageHistorySync 获取当前 Persona 的所有历史消息
                List<Message> history = repository.getMessageHistorySync(persona.getId());

                if (history == null) history = new ArrayList<>();

                history = new ArrayList<>(history);

                if (!history.isEmpty()) {
                    //若历史消息的最后一条是 AI 回复（非用户消息），则移除该消息，避免后续请求中重复发送 AI 自身的回复，防止重复生成
                    Message lastMsgInDB = history.get(history.size() - 1);
                    if (!lastMsgInDB.isUser()) {
                        history.remove(history.size() - 1);
                        Log.w(TAG, "Strict cleanup: Removed lingering assistant response from chat history to avoid repetition.");
                    }
                }

                //检查历史消息的最后一条是否为当前发送的用户消息，若不是则手动添加，确保 AI 能获取到最新的用户输入。
                boolean isUserMessageAlreadyAtEnd = !history.isEmpty() &&
                        history.get(history.size() - 1).getText().equals(userMessage.getText()) &&
                        history.get(history.size() - 1).isUser();

                if (!isUserMessageAlreadyAtEnd) {
                    history.add(userMessage);
                    Log.d(TAG, "Forced addition of current user message to end of chat history.");
                }

                final int historyLimit = 1;

                List<Message> chatHistory = history.subList(
                        Math.max(0, history.size() - historyLimit),
                        history.size()
                );

                //开启流式输出
                isStreaming.postValue(true);
                sendStreamingAiRequest(chatHistory, persona);

            } catch (Exception e) {
                Log.e(TAG, "发送消息过程中发生错误: " + e.getMessage(), e);
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
            requestBodyJson.put("model", "qwen-turbo");
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
}