package com.example.demo.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Room Data Access Object (DAO) for the Message entity.
 * 负责处理聊天记录的数据库操作。
 */
@Dao
public interface MessageDao {

    /**
     * 插入新的聊天消息。
     * @param message 要插入的消息对象。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);

    /**
     * 【核心】根据 Persona ID 获取 LiveData 聊天记录。
     * 当该 Persona 有新消息插入时，LiveData 会自动通知 UI 刷新。
     * @param personaId 要查询的 Persona 的 ID。
     * @return 属于该 Persona 的所有消息的 LiveData 列表，按 ID 升序排列（即按时间顺序）。
     */
    @Query("SELECT * FROM message_table WHERE personaId = :personaId ORDER BY id ASC") // 修正为按 id 排序
    LiveData<List<Message>> getMessagesByPersonaId(int personaId);

    /**
     * 删除指定 Persona 的所有聊天记录。
     * 通常在删除 Persona 时调用此方法进行级联清理。
     * @param personaId 要删除消息的 Persona 的 ID。
     */
    @Query("DELETE FROM message_table WHERE personaId = :personaId")
    void deleteMessagesByPersonaId(int personaId);

    /**
     * 【同步】获取指定 Persona 的所有消息历史。
     * 在非主线程中调用，用于即时获取上下文。
     * @return 属于该 Persona 的所有消息的同步列表，按 ID 升序排列。
     */
    @Query("SELECT * FROM message_table WHERE personaId = :personaId ORDER BY id ASC") // 修正为按 id 排序
    List<Message> getMessagesByPersonaIdSync(int personaId);

    // 查出某个角色名下，排除掉占位符（id=-1）的最近几条历史消息，并按时间正序排列（旧消息在前，新消息在后）
    @Query("SELECT * FROM (SELECT * FROM message_table WHERE personaId = :personaId AND id != -1 ORDER BY id DESC LIMIT :limit) ORDER BY id ASC")
    List<Message> getRecentMessagesSync(int personaId, int limit);
}