package com.game.component;

import java.time.LocalDateTime;
import java.util.Map;

public final class MapUtil {

    private MapUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 安全地从 Map 中获取一个值，并转换为指定的类型。
     * @param map 数据源 Map
     * @param key 要获取的键
     * @param clazz 期望的类型
     * @param <T> 泛型类型
     * @return 如果键存在且值类型匹配，则返回转换后的值；否则返回 null。
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Map<String, Object> map, String key, Class<T> clazz) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }

        // 直接类型匹配
        if (clazz.isInstance(value)) {
            return (T) value;
        }

        // 数字类型的智能转换
        if (value instanceof Number numValue) {
            if (clazz == Integer.class) {
                return (T) Integer.valueOf(numValue.intValue());
            }
            if (clazz == Long.class) {
                return (T) Long.valueOf(numValue.longValue());
            }
            if (clazz == Double.class) {
                return (T) Double.valueOf(numValue.doubleValue());
            }
            if (clazz == Float.class) {
                return (T) Float.valueOf(numValue.floatValue());
            }
        }

        // 字符串类型转换
        if (clazz == String.class) {
            return (T) value.toString();
        }

        return null;
    }

    /**
     * 获取 Integer 类型的值（带默认值）
     */
    public static Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Integer value = getValue(map, key, Integer.class);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Long 类型的值（带默认值）
     */
    public static Long getLong(Map<String, Object> map, String key, Long defaultValue) {
        Long value = getValue(map, key, Long.class);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 String 类型的值（带默认值）
     */
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        String value = getValue(map, key, String.class);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 LocalDateTime 类型的值
     */
    public static LocalDateTime getLocalDateTime(Map<String, Object> map, String key) {
        return getValue(map, key, LocalDateTime.class);
    }
}