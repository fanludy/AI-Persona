package com.example.demo.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

import com.example.demo.data.AppDatabase;
import com.example.demo.data.Persona;
import com.example.demo.data.PersonaDao;
import com.example.demo.data.Dynamic;
import com.example.demo.data.DynamicDao;
import com.example.demo.data.Message;
import com.example.demo.data.MessageDao;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PersonaRepository {

    private final PersonaDao personaDao;
    private final DynamicDao dynamicDao;
    private final MessageDao messageDao;
    private final ExecutorService executorService; // 保持为单线程执行器

    private static volatile AppDatabase INSTANCE;
    private final LiveData<Persona> activePersonaLiveData;
    private final LiveData<List<Persona>> allPersonasLiveData;
    private final LiveData<List<Dynamic>> allDynamicsLiveData;

    public static AppDatabase getDatabase(final Application application) {
        if (INSTANCE == null) {
            synchronized (PersonaRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(application.getApplicationContext(),
                                    AppDatabase.class, "persona_database")
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
        }
    };


    public PersonaRepository(Application application) {
        AppDatabase db = getDatabase(application);

        personaDao = db.personaDao();
        dynamicDao = db.dynamicDao();
        messageDao = db.messageDao();

        executorService = Executors.newSingleThreadExecutor();

        this.activePersonaLiveData = personaDao.getActivePersonaLiveData();
        this.allPersonasLiveData = personaDao.getAllPersonasLiveData();
        this.allDynamicsLiveData = dynamicDao.getAllDynamicsLiveData();
    }

    public LiveData<Persona> getActivePersonaLiveData() { return activePersonaLiveData; }
    public LiveData<List<Persona>> getAllPersonasLiveData() { return allPersonasLiveData; }
    public LiveData<List<Dynamic>> getAllDynamicsLiveData() { return allDynamicsLiveData; }
    public LiveData<List<Message>> getMessagesByPersonaId(int personaId) { return messageDao.getMessagesByPersonaId(personaId); }
    public ExecutorService getExecutorService() { return executorService; }

    /**
     * 【同步】获取指定 Persona 的所有消息历史。
     */
    public List<Message> getMessageHistorySync(int personaId) {
        // 由于 ViewModel 已经使用 executorService.execute() 包裹了调用，
        // 我们在这里可以直接调用 DAO 的同步方法，不需要再嵌套 Future.get()。
        // 否则会造成死锁。
        return messageDao.getMessagesByPersonaIdSync(personaId);
    }

    /**
     * 【插入 Message】
     */
    public void insert(Message message) {
        // 提交任务到单线程执行器，确保所有数据库写入操作的原子性和顺序性。
        executorService.execute(() -> {
            messageDao.insert(message);
        });
    }


    public void insert(Persona persona) {
        executorService.execute(() -> {
            personaDao.insert(persona);
        });
    }

    public void insert(Dynamic dynamic) {
        executorService.execute(() -> {
            dynamicDao.insert(dynamic);
        });
    }

    public void update(Persona persona) {
        executorService.execute(() -> {
            personaDao.update(persona);
        });
    }

    public void update(Dynamic dynamic) {
        executorService.execute(() -> {
            dynamicDao.update(dynamic);
        });
    }

    public void deletePersonaAndDynamics(Persona persona) {
        executorService.execute(() -> {
            personaDao.deleteDynamicsByPersonaId(String.valueOf(persona.getId()));
            messageDao.deleteMessagesByPersonaId(persona.getId());
            personaDao.delete(persona);
        });
    }

    public void setActivePersona(int personaId) {
        // 确保数据库操作在后台线程执行
        executorService.execute(() -> {
            personaDao.disableAllPersonas();

            personaDao.activatePersonaQuery(personaId);
        });
    }
}