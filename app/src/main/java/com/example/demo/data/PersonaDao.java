package com.example.demo.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PersonaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Persona persona);

    @Query("SELECT * FROM persona_table ORDER BY id DESC LIMIT 1")
    LiveData<Persona> getLatestPersonaLiveData();

    @Query("SELECT * FROM persona_table ORDER BY id DESC")
    LiveData<List<Persona>> getAllPersonasLiveData();

    @Query("SELECT * FROM persona_table WHERE isActive = 1 LIMIT 1")
    LiveData<Persona> getActivePersonaLiveData();

    @Delete
    void delete(Persona persona);

    @Update
    void update(Persona persona);

    @Query("UPDATE persona_table SET isActive = 0")
    void disableAllPersonas();

    @Query("SELECT * FROM persona_table WHERE id = :personaId LIMIT 1")
    Persona getPersonaById(int personaId);

    @Query("DELETE FROM dynamic_table WHERE personaId = :personaId")
    void deleteDynamicsByPersonaId(String personaId);

    @Query("UPDATE persona_table SET isActive = 1 WHERE id = :personaId")
    void activatePersonaQuery(int personaId);
}