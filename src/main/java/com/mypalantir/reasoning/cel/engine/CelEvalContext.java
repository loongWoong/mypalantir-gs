package com.mypalantir.reasoning.cel.engine;

import java.util.List;
import java.util.Map;

/**
 * CEL 求值上下文（增强版）。
 * 供 CEL 扩展函数获取当前求值时的 instance / links，与 rules-engine-V2 的 CelEvalContext 对齐但无 Ontology 依赖。
 */
public class CelEvalContext {

    private String objectType;
    private Object instanceId;
    private Map<String, Object> instance;
    private Map<String, List<Map<String, Object>>> links;

    private static final ThreadLocal<CelEvalContext> CURRENT = new ThreadLocal<>();

    public static void setCurrent(CelEvalContext context) {
        CURRENT.set(context);
    }

    public static CelEvalContext getCurrent() {
        return CURRENT.get();
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Object getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Object instanceId) {
        this.instanceId = instanceId;
    }

    public Map<String, Object> getInstance() {
        return instance;
    }

    public void setInstance(Map<String, Object> instance) {
        this.instance = instance;
    }

    public Map<String, List<Map<String, Object>>> getLinks() {
        return links;
    }

    public void setLinks(Map<String, List<Map<String, Object>>> links) {
        this.links = links;
    }
}
