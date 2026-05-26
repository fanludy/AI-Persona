package com.example.demo.data;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class VectorConverters {
    @TypeConverter
    public static String fromVector(List<Double> vector) {
        if (vector == null) return null;
        return new Gson().toJson(vector);
    }

    @TypeConverter
    public static List<Double> toVector(String vectorString) {
        if (vectorString == null) return null;
        Type listType = new TypeToken<List<Double>>() {}.getType();
        return new Gson().fromJson(vectorString, listType);
    }
}