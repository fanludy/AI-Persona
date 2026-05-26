package com.example.demo.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 待办事项实体类
 */
@Entity(tableName = "todo_table")
public class Todo {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int personaId; // 记录是哪个角色帮你记的
    public String taskName; // 任务名称
    public String dueDate; // 截止时间
    public boolean isCompleted; // 是否已完成

    public Todo(int personaId, String taskName, String dueDate, boolean isCompleted) {
        this.personaId = personaId;
        this.taskName = taskName;
        this.dueDate = dueDate;
        this.isCompleted = isCompleted;
    }
}