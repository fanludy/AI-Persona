package com.example.demo.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DynamicDao {

    /**
     * 插入新的动态
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Dynamic dynamic);

    /**
     * 更新动态 (用于点赞等互动)
     */
    @Update
    void update(Dynamic dynamic);

    /**
     * 获取所有动态的 LiveData，按时间倒序排列
     */
    @Query("SELECT * FROM dynamic_table ORDER BY timestamp DESC")
    LiveData<List<Dynamic>> getAllDynamicsLiveData();

    /**
     * 根据 Persona ID 删除所有相关动态 (用于级联删除)
     */
    @Query("DELETE FROM dynamic_table WHERE personaId = :personaId")
    void deleteDynamicsByPersonaId(String personaId);

    /**
     * 根据 Persona ID 获取某个角色的所有动态
     */
    @Query("SELECT * FROM dynamic_table WHERE personaId = :personaId ORDER BY timestamp DESC")
    LiveData<List<Dynamic>> getDynamicsByPersona(String personaId);

    /**
     * 通过动态ID获取动态
     */
    @Query("SELECT * FROM dynamic_table WHERE dynamicId = :dynamicId LIMIT 1")
    Dynamic getDynamicById(String dynamicId); //

}