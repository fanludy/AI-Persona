package com.example.demo.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.room.Ignore;

/**
 * Dynamic 类作为 Room 数据库的 Entity（实体）
 */
@Entity(tableName = "dynamic_table")
public class Dynamic {

    @PrimaryKey(autoGenerate = true)
    private int dynamicId;

    private int personaId;

    @NonNull
    private String personaName;
    private String personaAvatarUrl;

    @NonNull
    private String contentText;//动态文字内容
    private String imageUrl;//动态图片url

    private long timestamp;//动态发布时间
    private int likesCount;//点赞数

    @Ignore
    private boolean isLikedByCurrentUser = false;

    // 完整的带参构造函数
    public Dynamic(int personaId, @NonNull String personaName, String personaAvatarUrl, @NonNull String contentText, String imageUrl) {
        this.personaId = personaId;
        this.personaName = personaName;
        this.personaAvatarUrl = personaAvatarUrl != null ? personaAvatarUrl : "";
        this.contentText = contentText;
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.timestamp = System.currentTimeMillis();
        this.likesCount = 0;
    }

    public Dynamic() {}

    public int getDynamicId() { return dynamicId; }
    public void setDynamicId(int dynamicId) { this.dynamicId = dynamicId; }

    public int getPersonaId() { return personaId; }
    public void setPersonaId(int personaId) { this.personaId = personaId; }

    @NonNull
    public String getPersonaName() { return personaName; }
    public void setPersonaName(@NonNull String personaName) { this.personaName = personaName; }

    public String getPersonaAvatarUrl() { return personaAvatarUrl; }
    public void setPersonaAvatarUrl(String personaAvatarUrl) { this.personaAvatarUrl = personaAvatarUrl; }

    @NonNull
    public String getContentText() { return contentText; }
    public void setContentText(@NonNull String contentText) { this.contentText = contentText; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public boolean isLikedByCurrentUser() { return isLikedByCurrentUser; }
    public void setLikedByCurrentUser(boolean likedByCurrentUser) { isLikedByCurrentUser = likedByCurrentUser; }

    /**
     * 切换点赞状态和点赞数的方法 (业务逻辑)
     * 实现：只能点赞一次，再次点击则取消点赞。
     */
    public void toggleLikeStatus() {
        if (isLikedByCurrentUser) {
            // 如果已经点赞，则取消点赞
            likesCount = Math.max(0, likesCount - 1); // 确保点赞数不为负
            isLikedByCurrentUser = false;
        } else {
            // 如果未点赞，则点赞
            likesCount++;
            isLikedByCurrentUser = true;
        }
    }
}