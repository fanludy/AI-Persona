package com.example.demo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.ChatActivity;
import com.example.demo.R;
import com.example.demo.data.Message;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import com.bumptech.glide.Glide;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    public interface OnTtsClickListener {
        void onTtsPlayClicked(String textToSpeak);
    }

    private List<Message> messages = new ArrayList<>();
    private final Markwon markwon;
    private final OnTtsClickListener ttsClickListener;

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_PERSONA = 2;
    private static final int VIEW_TYPE_IMAGE = 3;

    // 1. 新增一个全局状态变量
    private boolean isMarkdownEnabled = true;

    // 2. 提供一个给 ChatActivity 调用的设置方法
    public void setMarkdownEnabled(boolean enabled) {
        this.isMarkdownEnabled = enabled;
        // 注意：这里如果发生切换，可以调用 notifyDataSetChanged() 刷新全局，
        // 但通常我们是在进入聊天界面时就设置好的。
    }

    public MessageAdapter(Context context, OnTtsClickListener listener) {
        this.markwon = Markwon.create(context);
        this.ttsClickListener = listener;
    }

    /**
     * 提供 submitList 方法
     */
    public void submitList(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    public void setMessages(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    /**
     * 根据消息类型返回不同的布局类型
     */
    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
            return VIEW_TYPE_IMAGE;
        } else if (message.isUser()) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_PERSONA;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_USER) {
            view = inflater.inflate(R.layout.item_message_user, parent, false);
        } else if (viewType == VIEW_TYPE_PERSONA) {
            view = inflater.inflate(R.layout.item_message_persona, parent, false);
        } else { // 图片消息类型
            view = inflater.inflate(R.layout.item_message_image, parent, false);
        }
        return new MessageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);

        if(holder.viewType == VIEW_TYPE_IMAGE){
            // 图片消息逻辑
            Glide.with(holder.itemView.getContext())
                    .load(message.getImageUrl())
                    .placeholder(R.drawable.default_image_placeholder)
                    .error(R.drawable.image_load_error)
                    .into(holder.imageView);

            holder.messageTextView.setText(message.getText());
            holder.messageTextView.setVisibility(View.VISIBLE);

        } else if (holder.viewType == VIEW_TYPE_PERSONA) {
            // Persona 消息逻辑
            markwon.setMarkdown(holder.messageTextView, message.getText());

            // 💡 4. 核心修改：根据开关决定如何渲染文本
            if (isMarkdownEnabled) {
                markwon.setMarkdown(holder.messageTextView, message.getText());
            } else {
                holder.messageTextView.setText(message.getText());
            }

            if (holder.ttsButton != null && ttsClickListener != null) {
                // 文本消息显示按钮
                holder.ttsButton.setVisibility(View.VISIBLE);

                holder.ttsButton.setOnClickListener(v -> {
                    // 触发回调，将消息文本传给 ChatActivity
                    ttsClickListener.onTtsPlayClicked(message.getText());
                });
            }

        } else {
            // 用户消息逻辑
            holder.messageTextView.setText(message.getText());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    /**
     * 3. ViewHolder 类 (已更新)
     */
    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        public final TextView messageTextView;
        public final ImageView imageView;
        public final ImageButton ttsButton;
        public final int viewType;

        public MessageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            if (viewType == VIEW_TYPE_USER) {
                messageTextView = itemView.findViewById(R.id.message_text_user);
                imageView = null;
                ttsButton = null; // 用户消息无按钮
            } else if (viewType == VIEW_TYPE_PERSONA) {
                messageTextView = itemView.findViewById(R.id.message_text_persona);
                // 保持现有结构
                imageView = null;

                ttsButton = itemView.findViewById(R.id.btn_tts_play);
            } else { // 图片消息类型
                messageTextView = itemView.findViewById(R.id.message_text_image);
                imageView = itemView.findViewById(R.id.message_image_view);
                ttsButton = null; // 图片消息无按钮
            }
        }
    }


    /** 查找占位符在列表中的位置 */
    private int getPlaceholderPosition() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getId() == ChatActivity.STREAMING_PLACEHOLDER_ID) {
                return i;
            }
        }
        return -1;
    }

    public void addStreamingPlaceholder(Message placeholder) {
        removeStreamingPlaceholder(ChatActivity.STREAMING_PLACEHOLDER_ID);

        messages.add(placeholder);
        notifyItemInserted(messages.size() - 1);
    }

    public void appendContentToPlaceholder(String chunk) {
        int position = getPlaceholderPosition();
        if (position != -1) {
            Message placeholder = messages.get(position);
            String newContent = placeholder.getText() + chunk;
            placeholder.setText(newContent);
            notifyItemChanged(position);
        }
    }

    public void removeStreamingPlaceholder(int placeholderId) {
        int position = getPlaceholderPosition();
        if (position != -1) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * 【辅助方法 1】获取列表中的最后一条消息
     */
    public Message getLastMessage() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(messages.size() - 1);
        }
        return null;
    }

    /**
     * 【辅助方法 2】手动插入一条临时消息，并触发顺滑的插入动画
     */
    public void addMessage(Message message) {
        if (messages != null) {
            messages.add(message);
            notifyItemInserted(messages.size() - 1);
        }
    }

    /**
     * 【核心优化】专门用于流式打字机效果的局部刷新
     */
    public void updateStreamingText(String newText) {
        if (messages == null || messages.isEmpty()) return;

        int lastIndex = messages.size() - 1;
        Message lastMessage = messages.get(lastIndex);

        if (!lastMessage.isUser()) {
            String currentText = lastMessage.getText();

            // 💡 升级擦除逻辑：如果是初始占位符或者带有动作图标的状态文本，第一个字出来时直接覆盖擦除
            if ("正在思考...".equals(currentText) || currentText.startsWith("💻") || currentText.startsWith("🎨") || currentText.startsWith("🔍")) {
                lastMessage.setText(newText);
            } else {
                lastMessage.setText(currentText + newText);
            }
            notifyItemChanged(lastIndex);
        }
    }

    /**
     * 💡 新增：不刷新全屏，直接精准重写最后一条等待气泡的文字内容
     */
    public void changePlaceholderText(String text) {
        if (messages == null || messages.isEmpty()) return;
        int lastIndex = messages.size() - 1;
        Message lastMessage = messages.get(lastIndex);
        if (!lastMessage.isUser()) {
            lastMessage.setText(text);
            notifyItemChanged(lastIndex); // 局部高频重绘，不闪烁
        }
    }
}