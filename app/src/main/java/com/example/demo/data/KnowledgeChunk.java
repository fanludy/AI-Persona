package com.example.demo.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import java.util.List;

@Entity(tableName = "knowledge_chunk_table",
        foreignKeys = @ForeignKey(entity = Persona.class,
                parentColumns = "id",
                childColumns = "personaId",
                onDelete = ForeignKey.CASCADE))
public class KnowledgeChunk {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int personaId;
    public String docName;     // 来源文档名 (例如: "Android开发规范.txt")
    public String textContent; // 切片后的文本内容
    public List<Double> vector;// 文本对应的多维向量

    public KnowledgeChunk(int personaId, String docName, String textContent, List<Double> vector) {
        this.personaId = personaId;
        this.docName = docName;
        this.textContent = textContent;
        this.vector = vector;
    }
}