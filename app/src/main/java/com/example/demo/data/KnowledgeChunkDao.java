package com.example.demo.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface KnowledgeChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(KnowledgeChunk chunk);

    // 同步获取某个 Persona 的所有知识块
    @Query("SELECT * FROM knowledge_chunk_table WHERE personaId = :personaId")
    List<KnowledgeChunk> getChunksByPersonaIdSync(int personaId);

    // 清空某个 Persona 的知识库
    @Query("DELETE FROM knowledge_chunk_table WHERE personaId = :personaId")
    void deleteChunksByPersonaId(int personaId);

    // 1. 响应式获取所有角色各自拥有的唯一文档列表组合
    @Query("SELECT DISTINCT personaId, docName FROM knowledge_chunk_table")
    androidx.lifecycle.LiveData<List<PersonaDocument>> getAllPersonaDocumentsLiveData();

    // 2. 根据角色 ID 和文档名称，级联删除该文档切分出的所有向量知识块
    @Query("DELETE FROM knowledge_chunk_table WHERE personaId = :personaId AND docName = :docName")
    void deleteChunksByDocName(int personaId, String docName);
}