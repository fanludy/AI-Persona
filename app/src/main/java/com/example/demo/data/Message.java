package com.example.demo.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.room.ColumnInfo;
import org.jetbrains.annotations.Nullable;

/**
 * 聊天消息数据模型。
 */
@Entity(tableName = "message_table")
public class Message {

    @PrimaryKey(autoGenerate = true)
    public int id; // 唯一主键

    public int personaId;//外键，记录消息属于哪一个角色
    private String text;//消息内容
    private boolean isUser;//true 表示是用户发送的消息；false 表示是 Persona (AI) 发送的消息。用于在UI上区分左右气泡
    @ColumnInfo(name = "senderName")
    private String senderName;
    @Nullable
    private String imageUrl;//图片url

    public Message(int personaId, String text, boolean isUser, String senderName) {
        this.personaId = personaId;
        this.text = text;
        this.isUser = isUser;
        this.senderName = senderName;
        this.imageUrl = null;
    }

    @Ignore
    public Message(int personaId, String text, boolean isUser, String senderName, String imageUrl) {
        this.personaId = personaId;
        this.text = text;
        this.isUser = isUser;
        this.senderName = senderName;
        this.imageUrl = imageUrl;
    }

    public int getId() { return id; }

    public void setId(int id) {
        this.id = id;
    }

    public int getPersonaId() { return personaId; }
    public void setPersonaId(){
        this.personaId = personaId;
    }

    public String getText() { return text; }

    public void setText(String text) {
        this.text = text;
    }
    public boolean isUser() { return isUser; }
    public String getSenderName() { return senderName; }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }



}