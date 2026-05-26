package com.example.demo.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Persona 类作为 Room 数据库的 Entity（实体）
 */
@Entity(tableName = "persona_table")
public class Persona {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String name;//名称
    @NonNull
    private String personality;//性格
    @NonNull
    private String background;//背景
    private String avatarUrl;//头像的url地址
    private boolean isActive = false;//标记当前角色是否启用
    // 💡 新增：Markdown 渲染开关（默认设为 true）
    private boolean isMarkdownEnabled = true;
    // 💡 1. 字数限制开关
    private boolean isWordLimitEnabled = false;
    // 💡 2. 字数限制具体数量 (默认100字)
    private int wordLimit = 100;
    // 💡 3. 口语化表达开关
    private boolean isColloquialEnabled = false;

    // 💡 新增：随对话不断动态进化的长期记忆事实库
    private String longTermMemory = "暂无对用户的长期了解背景。";

    /**
     * 主构造函数，用于 Room 实体创建。
     */
    public Persona(@NonNull String name, @NonNull String personality, @NonNull String background, String avatarUrl) {
        this.name = name;
        this.personality = personality;
        this.background = background;
        this.avatarUrl = avatarUrl;
        // 默认创建时未启用
        this.isActive = false;
    }

    /**
     * 兼容构造函数：不带 avatarUrl 的情况，默认 null。
     */
    public Persona(@NonNull String name, @NonNull String personality, @NonNull String background) {
        this(name, personality, background, null);
    }

    /**
     * Room 可能需要的无参构造函数。
     */
    public Persona() {
        this.name = "";
        this.personality = "";
        this.background = "";
        this.avatarUrl = null;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    @NonNull
    public String getPersonality() { return personality; }
    public void setPersonality(@NonNull String personality) { this.personality = personality; }

    @NonNull
    public String getBackground() { return background; }
    public void setBackground(@NonNull String background) { this.background = background; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isMarkdownEnabled() {
        return isMarkdownEnabled;
    }

    public void setMarkdownEnabled(boolean markdownEnabled) {
        isMarkdownEnabled = markdownEnabled;
    }
    public boolean isWordLimitEnabled() { return isWordLimitEnabled; }
    public void setWordLimitEnabled(boolean wordLimitEnabled) { isWordLimitEnabled = wordLimitEnabled; }

    public int getWordLimit() { return wordLimit; }
    public void setWordLimit(int wordLimit) { this.wordLimit = wordLimit; }

    public boolean isColloquialEnabled() { return isColloquialEnabled; }
    public void setColloquialEnabled(boolean colloquialEnabled) { isColloquialEnabled = colloquialEnabled; }

    public String getLongTermMemory() { return longTermMemory; }
    public void setLongTermMemory(String longTermMemory) { this.longTermMemory = longTermMemory; }
}