package com.example.demo.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.demo.data.Persona;
import com.example.demo.data.PersonaDao;
import com.example.demo.data.Dynamic;
import com.example.demo.data.DynamicDao;
import com.example.demo.data.Message;
import com.example.demo.data.MessageDao;


/**
 * AppDatabase 是 Room 数据库的抽象类入口
 */
@Database(entities = {Persona.class, Dynamic.class, Message.class, KnowledgeChunk.class, Todo.class}, version = 4, exportSchema = false)
@TypeConverters({VectorConverters.class}) // 3. 挂载转换器
public abstract class AppDatabase extends RoomDatabase {

    public abstract PersonaDao personaDao();
    public abstract DynamicDao dynamicDao();
    public abstract MessageDao messageDao();
    public abstract TodoDao todoDao();

    public abstract KnowledgeChunkDao knowledgeChunkDao();

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "persona_social_db";

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}