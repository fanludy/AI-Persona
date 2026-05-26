package com.example.demo.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TodoDao {
    @Insert
    void insert(Todo todo);

    // 查询某个角色记录的所有未完成任务
    @Query("SELECT * FROM todo_table WHERE personaId = :personaId AND isCompleted = 0")
    List<Todo> getPendingTodosSync(int personaId);

    @Update
    void update(Todo todo);
}