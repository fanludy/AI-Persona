package com.example.demo;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.adapter.PersonaManagementAdapter;
import com.example.demo.data.Persona;
import com.example.demo.viewmodel.PersonaChatViewModel;

/**
 * Persona 管理界面
 */
public class PersonaManagementActivity extends AppCompatActivity
        implements PersonaManagementAdapter.PersonaActionListener {

    private PersonaChatViewModel viewModel;
    private PersonaManagementAdapter adapter;
    private ImageView btnBack;
    private RecyclerView recyclerView;

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
        viewModel.getAllPersonasLiveData().observe(this, personas -> {
            if (personas != null) {
                adapter.setPersonaList(personas);
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
}