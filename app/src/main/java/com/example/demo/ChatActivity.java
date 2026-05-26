package com.example.demo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.adapter.MessageAdapter;
import com.example.demo.data.Message;
import com.example.demo.data.Persona;
import com.example.demo.viewmodel.PersonaChatViewModel;
import com.bumptech.glide.Glide;
import android.media.MediaPlayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.List;

/**
 * 主要聊天界面 (ChatActivity)
 */
public class ChatActivity extends AppCompatActivity implements View.OnClickListener {

    // 日志标签和常量定义
    private static final String TAG = "ChatActivity";
    public static final int STREAMING_PLACEHOLDER_ID = -1;
    public static final String EXTRA_PERSONA_ID = "com.example.demo.PERSONA_ID";

    private PersonaChatViewModel viewModel;// 数据与业务逻辑处理的ViewModel
    private RecyclerView recyclerView;// 聊天消息列表
    private MessageAdapter adapter;// 消息列表适配器
    private EditText messageEditText;// 输入框
    private Button sendButton;// 发送按钮

    private View btnGoToFeed;// 跳转到社交广场
    private View btnGoToCreation;// 跳转到角色创作
    private View btnPublishDynamic;// 发布动态
    private View btnGoToManagement;// 跳转到角色管理

    private TextView tvPersonaName;// 角色名称
    private TextView tvPersonaDescription;// 角色描述
    private ImageView ivPersonaAvatar;// 角色头像

    private Persona activePersona;// 当前活跃的角色
    private MediaPlayer mediaPlayer;// 音频播放器（用于TTS）
    private List<Message> pendingMessages = null; // 💡【新增】用于缓存打字期间数据库的更新

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);// 加载布局

        // 初始化ViewModel
        viewModel = new ViewModelProvider(this).get(PersonaChatViewModel.class);

        initializeUI();// 初始化UI组件

        setClickListeners();// 设置点击监听

        setupRecyclerView(); // 初始化RecyclerView

        // 初始化各种观察者（监听数据变化）
        setupPersonaObserver();
        setupMessageObserver();
        setupDynamicPublishObserver();
        setupGeneratedImageObserver();
        setupImageGenerationObserver();
        setupStreamingObservers();

        // 初始化音频播放器
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            //播放完成后，重置 ViewModel 状态
            viewModel.setSpeakingStatus(false);
        });

        // 设置TTS音频URL观察者
        setupAudioPathObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();// 释放播放器资源
            mediaPlayer = null;
        }
    }

    /**
     *  设置 TTS 音频 URL 的观察者
     */
    private void setupAudioPathObserver() {
        viewModel.getAudioFilePathLiveData().observe(this, audioUrl -> {
            if (audioUrl != null && !audioUrl.isEmpty()) {
                playAudio(audioUrl);
                // 播放路径用完即清除
                viewModel.clearAudioFilePath();
            }
        });

        viewModel.getIsSpeakingLiveData().observe(this, isSpeaking -> {
            Log.d(TAG, "TTS 播放状态更新: " + isSpeaking);
        });
    }

    /**
     * 播放指定 URL 的音频文件
     */
    // ChatActivity.java 中的修改
    private void playAudio(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) return;

        // 1. 规范化 URL
        final String secureUrl = audioUrl.startsWith("http://") ?
                audioUrl.replace("http://", "https://") : audioUrl;

        // 2. 清理逻辑：删除之前的临时音频文件
        File cacheDir = getCacheDir();
        File[] oldFiles = cacheDir.listFiles((dir, name) -> name.startsWith("tts_cache") && name.endsWith(".wav"));
        if (oldFiles != null) {
            for (File oldFile : oldFiles) {
                boolean deleted = oldFile.delete();
                Log.d(TAG, "清理旧音频文件: " + oldFile.getName() + " -> " + deleted);
            }
        }

        // 3. 使用 OkHttp 下载
        okhttp3.Request request = new okhttp3.Request.Builder().url(secureUrl).build();
        new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                Log.e(TAG, "下载音频失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;

                try {
                    // 4. 创建新的临时文件
                    final File tempFile = File.createTempFile("tts_cache", ".wav", getCacheDir());
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        fos.write(response.body().bytes());
                    }

                    runOnUiThread(() -> {
                        try {
                            if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
                            mediaPlayer.reset();

                            // 5. 播放文件描述符
                            FileInputStream fis = new FileInputStream(tempFile);
                            mediaPlayer.setDataSource(fis.getFD());

                            mediaPlayer.prepareAsync();
                            mediaPlayer.setOnPreparedListener(mp -> {
                                mp.start();
                                viewModel.setSpeakingStatus(true);
                                try { fis.close(); } catch (IOException ignored) {}
                            });

                            // 6. 播放完成后立即尝试删除当前文件（可选，或者留给下次播放前清理）
                            mediaPlayer.setOnCompletionListener(mp -> {
                                viewModel.setSpeakingStatus(false);
                                if (tempFile.exists()) {
                                    tempFile.delete();
                                    Log.d(TAG, "播放完成，已删除当前临时文件");
                                }
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "MediaPlayer 播放出错: " + e.getMessage());
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "处理音频文件出错: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 初始化所有 UI 组件
     */
    private void initializeUI() {
        tvPersonaName = findViewById(R.id.current_persona_name);
        tvPersonaDescription = findViewById(R.id.current_persona_description);
        ivPersonaAvatar = findViewById(R.id.current_persona_avatar);

        recyclerView = findViewById(R.id.chat_recycler_view);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_button);

        btnGoToCreation = findViewById(R.id.btn_go_to_creation);
        btnGoToFeed = findViewById(R.id.btn_go_to_feed);
        btnPublishDynamic = findViewById(R.id.btn_publish_dynamic);
        btnGoToManagement = findViewById(R.id.btn_go_to_management);
    }

    /**
     * 设置所有按钮的点击监听器
     */
    private void setClickListeners() {
        sendButton.setOnClickListener(this);
        btnGoToFeed.setOnClickListener(this);
        btnGoToCreation.setOnClickListener(this);
        btnPublishDynamic.setOnClickListener(this);
        btnGoToManagement.setOnClickListener(this);
    }

    /**
     * 【修改】设置 RecyclerView 和 Adapter，并实现 TTS 监听器
     */
    private void setupRecyclerView() {
        adapter = new MessageAdapter(this, messageText -> {
            if (viewModel.getIsSpeakingLiveData().getValue() != Boolean.TRUE) {
                viewModel.synthesizeAndPlay(messageText);
            } else {
                Toast.makeText(this, "正在播放中，请稍候。", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // 💡 顺手优化：保证不管文本怎么变长，列表总是以底部为基准
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // ==========================================
        // 💡 【彻底解决抖动和遮挡的核心代码】
        // 关闭 RecyclerView 默认的局部刷新（Change）动画
        // ==========================================
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
    }

    /**
     * 设置当前活跃 Persona 的 LiveData 观察者
     */
    private void setupPersonaObserver() {
        viewModel.getActivePersonaLiveData().observe(this, persona -> {
            if (persona != null) {
                activePersona = persona;

                // 💡 在这里把当前角色的 Markdown 偏好传递给适配器
                if (adapter != null) {
                    adapter.setMarkdownEnabled(persona.isMarkdownEnabled());
                }

                updatePersonaUI(persona);// 更新角色信息UI
            } else {
                activePersona = null;
                tvPersonaName.setText("未设置 Persona");
                tvPersonaDescription.setText("点击【创作 Persona】按钮设置你的 AI 伙伴。");
                ivPersonaAvatar.setImageResource(R.drawable.default_avatar);
            }
        });
    }

    /**
     * 设置消息列表 LiveData 的观察者
     */
    private void setupMessageObserver() {
        viewModel.getMessagesLiveData().observe(this, (List<Message> messages) -> {
            if (viewModel.getIsStreaming().getValue() == Boolean.TRUE) {
                // 💡 终极修复 1：如果 AI 正在打字，千万不要用数据库的新列表去冲刷屏幕！
                // 把它悄悄缓存在 pendingMessages 里，等打字机表演完再用。
                pendingMessages = messages;
            } else {
                // 没在打字时，正常全局刷新
                updateChatUI(messages);
            }
        });
    }

    /**
     * 设置流式输出 LiveData 的观察者
     */
    private void setupStreamingObservers() {
        // 1. 监听流式输出状态的变化
        viewModel.getIsStreaming().observe(this, isStreaming -> {
            if (activePersona == null) return;

            if (isStreaming) {
                // 开始打字：仅禁用输入控件
                sendButton.setEnabled(false);
                messageEditText.setEnabled(false);
            } else {
                // 结束打字：恢复输入控件
                sendButton.setEnabled(true);
                messageEditText.setEnabled(true);

                // 💡 终极修复 2：打字彻底结束后，把刚才缓存的数据库真实记录刷上屏幕，完美交接！
                if (pendingMessages != null) {
                    updateChatUI(pendingMessages);
                    pendingMessages = null;
                }
            }
        });

        // 2. 监听真实流式文本的下发
        viewModel.getCurrentStreamingText().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                // 调用我们在 MessageAdapter 里写的丝滑局部刷新
                adapter.updateStreamingText(text);
                recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    /**
     * 设置图片生成状态的观察者
     */
    private void setupImageGenerationObserver() {
        viewModel.getIsGeneratingImage().observe(this, isGenerating -> {
            // 生成图片时禁用输入控件
            sendButton.setEnabled(!isGenerating);
            messageEditText.setEnabled(!isGenerating);

            if (isGenerating) {
                Toast.makeText(this, "正在让 Persona 生成图片...", Toast.LENGTH_SHORT).show();
            } else {
                if (adapter.getItemCount() > 0) {
                    recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                }
            }
        });
    }

    /**
     * 设置生成图片 URL 的观察者
     */
    private void setupGeneratedImageObserver() {
        viewModel.getGeneratedImageUrl().observe(this, imageUrl -> {
            if (activePersona == null) return;

            if (imageUrl != null && !imageUrl.isEmpty()) {
                // 创建一个包含图片 URL 的 Message
                Message imageMessage = new Message(
                        activePersona.getId(),
                        "[AI生成图片]", // 消息文本作为占位符
                        false, // AI 消息 (助手的回复)
                        activePersona.getName()
                );

                imageMessage.setImageUrl(imageUrl);
                viewModel.insertMessage(imageMessage);

                viewModel.clearGeneratedImageUrl();
            }
        });
    }


    /**
     * 设置动态发布成功 LiveData 的观察者
     */
    private void setupDynamicPublishObserver() {
        viewModel.getDynamicPublishSuccess().observe(this, isSuccess -> {
            if (isSuccess != null && isSuccess) {
                String name = (activePersona != null) ? activePersona.getName() : "Persona";
                Toast.makeText(this, name + " 的社交动态已发布成功！", Toast.LENGTH_LONG).show();
                viewModel.resetDynamicPublishSuccessStatus();
            }
        });
    }

    /**
     * 更新主界面 Persona 信息的私有方法
     */
    private void updatePersonaUI(Persona persona) {
        tvPersonaName.setText(persona.getName());
        tvPersonaDescription.setText(persona.getPersonality());

        if (persona.getAvatarUrl() != null && !persona.getAvatarUrl().isEmpty()) {
            Uri avatarUri = Uri.parse(persona.getAvatarUrl());

            Glide.with(this)
                    .load(avatarUri)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .into(ivPersonaAvatar);
        } else {
            ivPersonaAvatar.setImageResource(R.drawable.default_avatar);
        }
    }


    /**
     * LiveData 观察者回调，更新 RecyclerView
     */
    private void updateChatUI(List<Message> messages) {
        if (messages != null) {
            adapter.setMessages(messages);
            adapter.notifyDataSetChanged();
            if (!messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        }
    }

    /**
     * 处理发送消息的逻辑
     */
    private void sendMessage() {
        String messageText = messageEditText.getText().toString().trim();

        if (activePersona == null) {
            Toast.makeText(this, "请先创建一个 Persona 才能开始聊天！", Toast.LENGTH_SHORT).show();
            return;
        }

        if (viewModel.getIsStreaming().getValue() == Boolean.TRUE) {
            Toast.makeText(this, "Persona 正在回复，请稍候！", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!messageText.isEmpty()) {
            // 💡 终极修复 3：在点击发送的【瞬间】，在主线程立刻死锁打字状态，绝不给数据库抢跑的机会！
            viewModel.setChatStreaming(true);

            // 瞬间把你的问题上屏（绝对不会再被吞了）
            Message tempUserMsg = new Message(activePersona.getId(), messageText, true, "用户");
            adapter.addMessage(tempUserMsg);

            // 瞬间追加一个且只有【唯一一个】AI 的思考占位符
            Message typingPlaceholder = new Message(activePersona.getId(), "正在思考...", false, activePersona.getName());
            adapter.addMessage(typingPlaceholder);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // 后台默默发网络请求并写入数据库
            viewModel.sendMessage(messageText, activePersona);
            messageEditText.setText("");
        } else {
            Toast.makeText(this, "不能发送空消息", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理发布动态的逻辑
     */
    private void publishDynamic() {
        if (activePersona == null) {
            Toast.makeText(this, "请先创建一个 Persona！", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在让 " + activePersona.getName() + " 发布一条社交动态...", Toast.LENGTH_LONG).show();

        String topic = "今日日常或感受";
        viewModel.generateAndPostDynamic(activePersona, topic);
    }

    @Override
    public void onClick(@NonNull View v) {
        int id = v.getId();
        if (id == R.id.send_button) {
            sendMessage();
        }
        else if (id == R.id.btn_go_to_creation) {
            Intent intent = new Intent(ChatActivity.this, CreationActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.btn_go_to_feed) {
            Intent intent = new Intent(ChatActivity.this, FeedActivity.class);
            startActivity(intent);
        } else if (id == R.id.btn_publish_dynamic) {
            publishDynamic();
        } else if (id == R.id.btn_go_to_management) {
            Intent intent = new Intent(ChatActivity.this, PersonaManagementActivity.class);
            startActivity(intent);
        }
    }
}