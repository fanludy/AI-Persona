package com.example.demo;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.adapter.PersonaManagementAdapter;
import com.example.demo.data.Persona;
import com.example.demo.viewmodel.PersonaChatViewModel;

import java.util.List;

/**
 * Persona 管理界面
 */
public class PersonaManagementActivity extends AppCompatActivity
        implements PersonaManagementAdapter.PersonaActionListener {

    private PersonaChatViewModel viewModel;
    private PersonaManagementAdapter adapter;
    private ImageView btnBack;
    private RecyclerView recyclerView;
    // 【新增】用来临时记录用户是给哪个 Persona 添加文档
    private Persona pendingKnowledgePersona;

    // 【新增】文件选择器回调
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri documentUri = result.getData().getData();
                    if (documentUri != null && pendingKnowledgePersona != null) {
                        // 拿到文件 Uri 了！交给 ViewModel 去处理
                        Toast.makeText(this, "开始读取并消化文档，请稍候...", Toast.LENGTH_LONG).show();
                        viewModel.processAndSaveDocument(documentUri, pendingKnowledgePersona, this);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_persona_management);

        viewModel = new ViewModelProvider(this).get(PersonaChatViewModel.class);

        btnBack = findViewById(R.id.btn_management_back);
        recyclerView = findViewById(R.id.persona_management_recycler_view);

        setupRecyclerView();
        setupObservers();

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // 传入 this 作为操作监听器
        adapter = new PersonaManagementAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    private void setupObservers() {
        // 1. 观察角色列表
        viewModel.getAllPersonasLiveData().observe(this, personas -> {
            if (personas != null) {
                // 💡 【完美解决焦点丢失】：检查当前焦点是不是在 EditText 上
                // 如果用户正在打字，内存其实已经同步好了，我们直接拦截掉这次 UI 刷新，保护焦点不丢失！
                android.view.View focusedView = getCurrentFocus();
                if (focusedView instanceof android.widget.EditText) {
                    return;
                }

                adapter.setPersonaList(personas);
            }
        });

        // 2. 观察角色对应的文档知识库关系 (保持不变)
        viewModel.getAllPersonaDocumentsLiveData().observe(this, personaDocuments -> {
            // ... (保留你原来的代码) ...
            if (personaDocuments != null) {
                java.util.Map<Integer, List<String>> map = new java.util.HashMap<>();
                for (com.example.demo.data.PersonaDocument pd : personaDocuments) {
                    if (!map.containsKey(pd.personaId)) {
                        map.put(pd.personaId, new java.util.ArrayList<>());
                    }
                    map.get(pd.personaId).add(pd.docName);
                }
                adapter.setPersonaDocuments(map);
            }
        });
    }

    /**
     * 【删除操作】
     * 级联删除 Persona 及其关联的动态和聊天记录。
     */
    @Override
    public void onDeleteClick(Persona persona) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("您确定要永久删除 Persona: " + persona.getName() + " 吗？\n此操作将同时删除其在社交广场上的所有动态及其聊天记录！")
                .setPositiveButton("确定删除", (dialog, which) -> {
                    // 调用 ViewModel 执行级联删除
                    viewModel.deletePersona(persona);
                    Toast.makeText(this, persona.getName() + " 已被删除。", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 【切换启用状态操作】
     * 现在切换后，主界面的对话会加载该 Persona 的历史记录。
     */
    @Override
    public void onToggleActive(Persona persona, boolean isActive) {
        if (isActive) {
            new AlertDialog.Builder(this)
                    .setTitle("启用 Persona")
                    .setMessage("确定将 " + persona.getName() + " 设为当前聊天的 Persona 吗？\n启用后，主界面将加载该 Persona 的历史聊天记录。")
                    .setPositiveButton("确定", (dialog, which) -> {

                        viewModel.setActivePersona(persona.getId());

                        Toast.makeText(this, persona.getName() + " 已启用，将加载历史记录。", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        adapter.notifyDataSetChanged();
                    })
                    .show();
        } else {
            Toast.makeText(this, "必须启用另一个 Persona 才能禁用当前活跃 Persona。", Toast.LENGTH_LONG).show();
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * 【新增】实现 Adapter 中的点击事件
     */
    @Override
    public void onAddKnowledgeClick(Persona persona) {
        this.pendingKnowledgePersona = persona;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 设置允许选择所有类型，然后通过 MIME_TYPES 数组进行精确过滤
        intent.setType("*/*");
        String[] mimeTypes = {
                "text/plain",                                                               // .txt
                "application/pdf",                                                          // .pdf
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"   // .docx
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        filePickerLauncher.launch(intent);
    }

    @Override
    public void onDeleteDocumentClick(int personaId, String docName) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除文档")
                .setMessage("确定要从该角色的知识库中永久移除文档：\n\"" + docName + "\" 吗？\n删除后与之相关的日常检索记忆将同步失效。")
                .setPositiveButton("确认删除", (dialog, which) -> {
                    viewModel.deleteDocument(personaId, docName);
                    Toast.makeText(this, "文档及关联知识块已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 【新增】处理 Markdown 开关切换
     */
    @Override
    public void onToggleMarkdown(Persona persona, boolean isEnabled) {
        // 1. 修改对象的属性
        persona.setMarkdownEnabled(isEnabled);

        // 2. 存入数据库
        viewModel.updatePersona(persona);

        // 3. 给用户一个轻量提示
        String status = isEnabled ? "已开启" : "已关闭";
        Toast.makeText(this, persona.getName() + " 的 Markdown 渲染 " + status, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onToggleColloquial(Persona persona, boolean isEnabled) {
        persona.setColloquialEnabled(isEnabled);
        viewModel.updatePersona(persona);
    }

    @Override
    public void onWordLimitChanged(Persona persona, boolean isEnabled, int limit) {
        persona.setWordLimitEnabled(isEnabled);
        persona.setWordLimit(limit);
        viewModel.updatePersona(persona);
    }

    /**
     * 💡 点击头像，弹窗编辑角色基本信息
     */
    @Override
    public void onAvatarClick(Persona persona) {
        // 构建自定义弹窗布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("角色名称");
        etName.setText(persona.getName());
        layout.addView(etName);

        android.widget.EditText etPersonality = new android.widget.EditText(this);
        etPersonality.setHint("性格特征");
        etPersonality.setText(persona.getPersonality());
        layout.addView(etPersonality);

        android.widget.EditText etBackground = new android.widget.EditText(this);
        etBackground.setHint("背景故事");
        etBackground.setText(persona.getBackground());
        layout.addView(etBackground);

        new AlertDialog.Builder(this)
                .setTitle("编辑角色资料")
                .setView(layout)
                .setPositiveButton("保存更改", (dialog, which) -> {
                    persona.setName(etName.getText().toString());
                    persona.setPersonality(etPersonality.getText().toString());
                    persona.setBackground(etBackground.getText().toString());
                    viewModel.updatePersona(persona);
                    Toast.makeText(this, "资料已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}