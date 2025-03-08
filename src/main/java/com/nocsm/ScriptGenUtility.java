package com.nocsm;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.StringJoiner;

public class ScriptGenUtility {
    public static String getInsertScripts(Object row, Field[] fields) {
        String compositeKey = getCompositeKey(fields);
        String tableName = Arrays.stream(row.getClass().getAnnotations())
                .filter(annotation -> annotation.toString().contains("schema="))
                .findFirst()
                .map(annotation -> {
                    String[] parts = annotation.toString().split(", ");
                    String schema = parts[1].split("=")[1];
                    String table = parts[0].split("\\(")[1].split("=")[1];
                    return schema + "." + table;
                })
                .orElse(null);
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner values = new StringJoiner(", ", "(", ")");
        if (compositeKey != null) {
            Field compositeKeyField = null;
            try {
                compositeKeyField = row.getClass().getDeclaredField(compositeKey);
                compositeKeyField.setAccessible(true);
                getColsAndValues(compositeKeyField.get(row), compositeKeyField.get(row).getClass().getDeclaredFields(), columns, values);
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        getColsAndValues(row, fields, columns, values);
        return String.format("INSERT INTO %s (%s) VALUES %s;", tableName, columns, values);
    }

    private static void getColsAndValues(Object row, Field[] fields, StringJoiner columns, StringJoiner values) {
        for (Field field: fields) {
            field.setAccessible(true);
            String columnName = field.getAnnotation(Column.class) != null ? field.getAnnotation(Column.class).name() : null;
            if (columnName == null) continue;
            columns.add(columnName);
            try {
                values.add(field.get(row).toString());
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String getCompositeKey(Field[] fields) {
        for (Field field: fields) {
            if (field.isAnnotationPresent(EmbeddedId.class)) {
                return field.getName();
            }
        }
        return null;
    }
}
