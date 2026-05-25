package com.lamiplus_common_api.common;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Generic utility to convert between DTOs (plain POJOs) and Map<String, Object>.
 *
 * This is the core of the "local DTO" pattern:
 *   - Calling plugin defines a DTO with only the fields it needs
 *   - DtoMapper serializes it to a Map for transport through PluginBridge
 *   - Target plugin's service receives the Map and can reconstruct its own model
 *
 * Supports:
 *   - Nested objects (flattened or kept as sub-maps)
 *   - UUID → String auto-conversion for safe serialization
 *   - Null-safe: null fields are excluded from the map
 *   - Map → DTO reconstruction for response handling
 */
public final class DtoMapper {

    private DtoMapper() {}

    /**
     * Convert any DTO object to a Map<String, Object>.
     * - Null fields are skipped
     * - UUID fields are converted to String
     * - All declared fields (including inherited) are included
     */
    public static Map<String, Object> toMap(Object dto) {
        if (dto == null) {
            return Collections.emptyMap();
        }
        if (dto instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) dto;
            return cast;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        Class<?> clazz = dto.getClass();

        // Walk up the class hierarchy to include inherited fields
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(dto);
                    if (value != null) {
                        map.put(field.getName(), convertValue(value));
                    }
                } catch (IllegalAccessException e) {
                    // skip inaccessible fields
                }
            }
            clazz = clazz.getSuperclass();
        }
        return map;
    }

    /**
     * Reconstruct a DTO from a Map.
     * Useful for reading cross-plugin responses into a typed object.
     *
     * @param map   the data map
     * @param clazz the target DTO class (must have a no-arg constructor)
     * @return a populated instance of T
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            for (Field field : getAllFields(clazz)) {
                field.setAccessible(true);
                String fieldName = field.getName();
                if (map.containsKey(fieldName)) {
                    Object value = map.get(fieldName);
                    Object converted = convertToFieldType(value, field.getType());
                    if (converted != null) {
                        field.set(instance, converted);
                    }
                }
            }
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to map data to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Extract a single typed value from a result map.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Map<String, Object> map, String key, Class<T> type) {
        Object value = map.get(key);
        if (value == null) return null;
        Object converted = convertToFieldType(value, type);
        return (T) converted;
    }

    /**
     * Extract UUID from a result map (common pattern).
     */
    public static UUID getUuid(Map<String, Object> map) {
        return getValue(map, "uuid", UUID.class);
    }

    // ========================
    //  INTERNAL HELPERS
    // ========================

    private static Object convertValue(Object value) {
        if (value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Date) {
            return value.toString();
        }
        return value;
    }

    private static Object convertToFieldType(Object value, Class<?> targetType) {
        if (value == null) return null;

        String str = value.toString();

        if (targetType == UUID.class) {
            return UUID.fromString(str);
        }
        if (targetType == String.class) {
            return str;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(str);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(str);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(str);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(str);
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumVal = Enum.valueOf((Class<Enum>) targetType, str);
            return enumVal;
        }

        // Fallback: return as-is if types are compatible
        if (targetType.isInstance(value)) {
            return value;
        }

        return value;
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
