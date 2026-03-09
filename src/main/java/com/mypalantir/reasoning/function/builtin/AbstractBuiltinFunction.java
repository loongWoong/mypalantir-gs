package com.mypalantir.reasoning.function.builtin;

import com.mypalantir.reasoning.function.OntologyFunction;

import java.util.List;
import java.util.Map;

/**
 * 内置函数基类，提供参数提取工具方法。
 */
public abstract class AbstractBuiltinFunction implements OntologyFunction {

    @SuppressWarnings("unchecked")
    protected Map<String, Object> asInstance(Object arg) {
        if (arg instanceof Map) {
            return (Map<String, Object>) arg;
        }
        throw new IllegalArgumentException(getName() + ": expected Map instance, got " + arg.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> asList(Object arg) {
        if (arg instanceof List) {
            return (List<Map<String, Object>>) arg;
        }
        throw new IllegalArgumentException(getName() + ": expected List, got " + arg.getClass().getSimpleName());
    }

    protected String getString(Map<String, Object> instance, String field) {
        Object val = instance.get(field);
        return val != null ? val.toString() : null;
    }

    protected int getInt(Map<String, Object> instance, String field, int defaultValue) {
        Object val = instance.get(field);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    protected double getDouble(Map<String, Object> instance, String field, double defaultValue) {
        Object val = instance.get(field);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    protected long getLong(Map<String, Object> instance, String field, long defaultValue) {
        Object val = instance.get(field);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }
}
