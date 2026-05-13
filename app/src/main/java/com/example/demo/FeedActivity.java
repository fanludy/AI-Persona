package com.example.demo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.adapter.DynamicAdapter;
import com.example.demo.data.Dynamic;
import com.example.demo.viewmodel.PersonaChatViewModel;

import java.util.List;

public class FeedActivity extends AppCompatActivity
        implements DynamicAdapter.DynamicClickListener,
        DynamicAdapter.OnLikeClickListener,
        DynamicAdapter.OnEnablePersonaClickListener {

    private PersonaChatViewModel viewModel;
    private DynamicAdapter adapter;
    private RecyclerView recyclerView;
    private ImageView btnFeedBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        viewModel = new ViewModelProvider(this).get(PersonaChatViewModel.class);

        // 初始化 UI 组件
        recyclerView = findViewById(R.id.feed_recycler_view);
        btnFeedBack = findViewById(R.id.btn_feed_back);

        // 设置 RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Adapter 初始化时传入 this 作为 DynamicClickListener
        adapter = new DynamicAdapter(this);

        adapter.setOnEnablePersonaClickListener(this);

        adapter.setOnLikeClickListener(this);

        recyclerView.setAdapter(adapter);

        btnFeedBack.setOnClickListener(v -> {
            finish();
        });

        // 观察动态列表 LiveData
        viewModel.getAllDynamicsLiveData().observe(this, (List<Dynamic> dynamics) -> {
            if (dynamics != null) {
                adapter.setDynamicList(dynamics);
            }
        });
    }

    /**
     * 当用户点击动态项上的“启用/聊聊”按钮时触发。
     */
    @Override
    public void onEnablePersonaClick(int personaId) {
        // 1. 调用 ViewModel 启用该 Persona
        viewModel.setActivePersona(personaId);

        // 2. 给出反馈
        Toast.makeText(this, "Persona 已切换，正在返回聊天界面。", Toast.LENGTH_SHORT).show();

        finish();
    }

    /**
     * 实现 DynamicAdapter.DynamicClickListener 接口的方法
     */
    @Override
    public void onDynamicClick(Dynamic dynamic) {
        Toast.makeText(this, "点击了 " + dynamic.getPersonaName() + " 的动态项", Toast.LENGTH_SHORT).show();
    }

    /**
     * 实现 DynamicAdapter.OnLikeClickListener 接口的方法
     */
    @Override
    public void onLikeClick(Dynamic dynamic) {
        // 1. 调用 ViewModel 的业务逻辑处理点赞/取消点赞
        viewModel.toggleLike(dynamic);

        // 2. 更新 Adapter 内部的 isLikedByCurrentUser 缓存，并局部刷新 UI
        adapter.updateLikedStatus(dynamic);

        // 给出反馈，确认点赞状态变化
        if (dynamic.isLikedByCurrentUser()) {
            Toast.makeText(this, "成功点赞！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "取消点赞", Toast.LENGTH_SHORT).show();
        }
    }
}