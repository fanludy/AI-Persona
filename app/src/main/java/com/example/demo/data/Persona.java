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
}