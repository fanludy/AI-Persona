package com.example.demo.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.demo.R;
import com.example.demo.data.Dynamic;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DynamicAdapter extends RecyclerView.Adapter<DynamicAdapter.DynamicViewHolder> {

    private List<Dynamic> dynamicList = new ArrayList<>();

    private final Set<Integer> likedDynamicIds = new HashSet<>();

    private final DynamicClickListener itemClickListener;
    private OnLikeClickListener likeClickListener;
    private OnEnablePersonaClickListener enablePersonaClickListener; //启用 Persona 监听器

    public interface OnEnablePersonaClickListener {
        /**
         * 当点击动态上的启用按钮时调用，并传入该动态所属的 Persona ID
         * @param personaId 要启用的 Persona 的 ID
         */
        void onEnablePersonaClick(int personaId);
    }

    public interface OnLikeClickListener {
        void onLikeClick(Dynamic dynamic);
    }

    public interface DynamicClickListener {
        void onDynamicClick(Dynamic dynamic);
    }

    public DynamicAdapter(DynamicClickListener listener) {
        this.itemClickListener = listener;
    }

    public DynamicAdapter() {
        this.itemClickListener = null;
    }

    public void setOnLikeClickListener(OnLikeClickListener listener) {
        this.likeClickListener = listener;
    }

    public void setOnEnablePersonaClickListener(OnEnablePersonaClickListener listener) { // 【新增】
        this.enablePersonaClickListener = listener;
    }

    public void setDynamicList(List<Dynamic> newDynamicList) {
        for (Dynamic dynamic : newDynamicList) {
            // 根据缓存的 ID 集合，恢复 isLikedByCurrentUser 的状态
            if (likedDynamicIds.contains(dynamic.getDynamicId())) {
                dynamic.setLikedByCurrentUser(true);
            } else {
                dynamic.setLikedByCurrentUser(false);
            }
        }
        this.dynamicList = newDynamicList;
        notifyDataSetChanged();
    }

    /**
     * 用于在用户点击点赞/取消点赞后，更新本地缓存。
     */
    public void updateLikedStatus(Dynamic dynamic) {
        if (dynamic.isLikedByCurrentUser()) {
            likedDynamicIds.add(dynamic.getDynamicId());
        } else {
            likedDynamicIds.remove(dynamic.getDynamicId());
        }

        notifyItemChanged(dynamicList.indexOf(dynamic));
    }


    @NonNull
    @Override
    public DynamicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dynamic, parent, false);
        return new DynamicViewHolder(view, this, itemClickListener, likeClickListener, enablePersonaClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DynamicViewHolder holder, int position) {
        Dynamic dynamic = dynamicList.get(position);
        holder.bind(dynamic);
    }

    @Override
    public int getItemCount() {
        return dynamicList.size();
    }

    public static class DynamicViewHolder extends RecyclerView.ViewHolder {
        private final TextView personaNameText;
        private final TextView contentText;
        private final ImageView dynamicImageView;
        private final TextView likesCountText;
        private final TextView dynamicTimestampText;
        private final ImageView personaAvatarView;
        private final ImageView likeButton;

        private final Button btnEnablePersona;

        public DynamicViewHolder(@NonNull View itemView, final DynamicAdapter adapter,
                                 final DynamicClickListener itemListener,
                                 final OnLikeClickListener likeListener,
                                 final OnEnablePersonaClickListener enablePersonaListener) {
            super(itemView);

            personaNameText = itemView.findViewById(R.id.persona_name_text);
            contentText = itemView.findViewById(R.id.dynamic_content_text);
            dynamicImageView = itemView.findViewById(R.id.dynamic_image_view);
            likesCountText = itemView.findViewById(R.id.likes_count_text);
            dynamicTimestampText = itemView.findViewById(R.id.dynamic_timestamp_text);
            personaAvatarView = itemView.findViewById(R.id.persona_avatar_view);
            likeButton = itemView.findViewById(R.id.like_button);
            btnEnablePersona = itemView.findViewById(R.id.btn_dynamic_enable_persona);

            //设置点赞按钮点击事件
            likeButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && likeListener != null) {
                    Dynamic dynamic = adapter.dynamicList.get(position);
                    likeListener.onLikeClick(dynamic);
                }
            });

            //设置启用 Persona 按钮点击事件
            if (btnEnablePersona != null) {
                btnEnablePersona.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && enablePersonaListener != null) {
                        Dynamic dynamic = adapter.dynamicList.get(position);
                        // 调用新的接口方法，传递 personaId
                        enablePersonaListener.onEnablePersonaClick(dynamic.getPersonaId());
                    }
                });
            }

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && itemListener != null) {
                    Dynamic dynamic = adapter.dynamicList.get(position);
                    itemListener.onDynamicClick(dynamic);
                }
            });
        }

        public void bind(Dynamic dynamic) {
            personaNameText.setText(dynamic.getPersonaName());
            contentText.setText(dynamic.getContentText());
            likesCountText.setText("❤ " + dynamic.getLikesCount());

            dynamicTimestampText.setText(formatTimeDifference(dynamic.getTimestamp()));

            if (dynamic.getPersonaAvatarUrl() != null && !dynamic.getPersonaAvatarUrl().isEmpty()) {
                Uri avatarUri = Uri.parse(dynamic.getPersonaAvatarUrl());
                Glide.with(itemView.getContext())
                        .load(avatarUri)
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .into(personaAvatarView);
            } else {
                personaAvatarView.setImageResource(R.drawable.default_avatar);
            }

            if (dynamic.isLikedByCurrentUser()) {
                likeButton.setImageResource(R.drawable.ic_like_filled);
            } else {
                likeButton.setImageResource(R.drawable.ic_like_outline);
            }

            if (dynamic.getImageUrl() != null && !dynamic.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(dynamic.getImageUrl())
                        .into(dynamicImageView);
                dynamicImageView.setVisibility(View.VISIBLE);
            } else {
                dynamicImageView.setVisibility(View.GONE);
            }
        }

        private static String formatTimeDifference(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 0) diff = 0;

            if (diff < TimeUnit.MINUTES.toMillis(1)) {
                return "刚刚";
            }

            if (diff < TimeUnit.HOURS.toMillis(1)) {
                long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
                return minutes + "分钟前";
            }

            if (diff < TimeUnit.DAYS.toMillis(1)) {
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
            }

            if (diff < TimeUnit.DAYS.toMillis(7)) {
                return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
            }

            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
        }
    }
}