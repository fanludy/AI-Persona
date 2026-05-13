package com.example.demo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.demo.data.Persona;
import com.example.demo.viewmodel.PersonaChatViewModel;
import com.bumptech.glide.Glide;

/**
 * Persona 创作界面 (CreationActivity)
 * 负责收集用户的输入，包括调用 AI 辅助生成内容，并最终将 Persona 保存到数据库。
 */
public class CreationActivity extends AppCompatActivity implements View.OnClickListener {

    private PersonaChatViewModel viewModel;

    // UI 控件
    private EditText etName;
    private EditText etPersonality;
    private EditText etBackground;
    private Button btnGenerate; // 一键 AI 辅助生成
    private Button btnSave;     // 保存 Persona
    private ImageView btnCreationBack;
    private ImageView personaAvatarImage;
    private Button btnSelectAvatar;

    // 用于存储用户选择的头像 URI
    private Uri selectedAvatarUri;

    // 图片选择器启动器
    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // 获取选中的图片的 URI
                    selectedAvatarUri = result.getData().getData();
                    if (selectedAvatarUri != null) {
                        // 使用 Glide 加载图片并显示在 ImageView 上
                        Glide.with(this)
                                .load(selectedAvatarUri)
                                .placeholder(R.drawable.default_avatar)
                                .error(R.drawable.default_avatar)
                                .into(personaAvatarImage);

                        try {
                            getContentResolver().takePersistableUriPermission(
                                    selectedAvatarUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_creation);

        viewModel = new ViewModelProvider(this).get(PersonaChatViewModel.class);

        // 1. 初始化 UI 控件
        initializeUI();

        // 2. 设置点击事件监听
        setClickListeners();

        // 3. 设置 LiveData 观察者
        setupObserver();
    }

    /**
     * 初始化 UI 控件
     */
    private void initializeUI() {
        etName = findViewById(R.id.et_persona_name);
        etPersonality = findViewById(R.id.et_persona_personality);
        etBackground = findViewById(R.id.et_persona_background);
        btnGenerate = findViewById(R.id.btn_generate_by_ai);
        btnSave = findViewById(R.id.btn_save_persona);
        btnCreationBack = findViewById(R.id.btn_creation_back);
        personaAvatarImage = findViewById(R.id.persona_avatar_image);
        // 【修正点 2】使用 XML 中正确的 ID：R.id.btn_select_avatar
        btnSelectAvatar = findViewById(R.id.btn_select_avatar);
    }

    /**
     * 设置所有按钮的点击监听器
     */
    private void setClickListeners() {
        btnSave.setOnClickListener(this);
        btnGenerate.setOnClickListener(this);
        btnSelectAvatar.setOnClickListener(this);
        personaAvatarImage.setOnClickListener(this); // 点击头像也能触发选择
        btnCreationBack.setOnClickListener(this);
    }

    /**
     * 观察 LiveData 变化，并在 AI 生成内容后更新 UI
     */
    private void setupObserver() {
        // 观察 ViewModel 中 AI 生成的 Persona 数据
        viewModel.getGeneratedPersonaLiveData().observe(this, persona -> {
            if (persona != null) {
                // 将 AI 生成的内容填充到 EditText 中
                etName.setText(persona.getName());
                etPersonality.setText(persona.getPersonality());
                etBackground.setText(persona.getBackground());
                Toast.makeText(this, "AI 角色人设已生成并填充！", Toast.LENGTH_SHORT).show();

                viewModel.clearGeneratedPersona();
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_save_persona) {
            savePersona();
        } else if (id == R.id.btn_generate_by_ai) {
            generatePersonaByAi();
        }
        else if (id == R.id.persona_avatar_image || id == R.id.btn_select_avatar) {
            openImagePicker();
        } else if (id == R.id.btn_creation_back) {
            finish();
        }
    }

    /**
     * 启动图片选择器
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        // 确保能获取到长期读权限
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pickImageLauncher.launch(intent);
    }

    /**
     * 触发 AI 辅助生成 Persona 设定
     */
    private void generatePersonaByAi() {
        // 收集用户已输入的内容作为 AI 提示的关键词
        String promptText = etName.getText().toString().trim() +
                etPersonality.getText().toString().trim() +
                etBackground.getText().toString().trim();

        // 构造 AI 提示语，明确要求 JSON 格式输出
        String prompt = "请根据我提供的关键词（如果没有关键词，请自由发挥）为我设计一个二次元角色的人设。关键词是：" +
                (promptText.isEmpty() ? "幻想小说主角" : promptText) +
                "。你的回复必须是**严格的 JSON 格式**，不包含任何额外文字，并包含三个字段返回结果: " +
                "{\"name\": \"[角色名称]\", \"personality\": \"[10字以内的性格特点]\", \"background\": \"[100字以内的背景故事]\"}";

        // 提示用户正在等待 AI 结果
        Toast.makeText(this, "正在调用 AI 生成角色人设...", Toast.LENGTH_LONG).show();

        // 调用 ViewModel 的 AI 生成方法
        viewModel.generatePersona(prompt);
    }


    /**
     * 保存 Persona 到数据库
     */
    private void savePersona() {
        String name = etName.getText().toString().trim();
        String personality = etPersonality.getText().toString().trim();
        String background = etBackground.getText().toString().trim();

        if (name.isEmpty() || personality.isEmpty() || background.isEmpty()) {
            Toast.makeText(this, "请填写完整的 Persona 设定！", Toast.LENGTH_SHORT).show();
            return;
        }

        String avatarUrlString = (selectedAvatarUri != null) ? selectedAvatarUri.toString() : "";

        Persona newPersona = new Persona(name, personality, background, avatarUrlString);

        // 调用 ViewModel 保存数据
        viewModel.savePersona(newPersona);

        // 保存成功后，给出提示并关闭当前 Activity
        Toast.makeText(this, "Persona 已成功保存！", Toast.LENGTH_SHORT).show();

        // 跳转回 ChatActivity (或关闭当前页)
        finish();
    }
}