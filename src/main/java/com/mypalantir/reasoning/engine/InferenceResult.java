package com.mypalantir.reasoning.engine;

import java.util.*;

/**
 * 推理结果：包含所有产生的事实和推理轨迹。
 *
 * V2.1 新增层级后处理结果：
 * - rootCauseFacts: 未被子规则抑制的根本原因事实（has_abnormal_root_cause）
 * - contextFacts:   被子规则抑制的父节点上下文事实（has_abnormal_context）
 */
public class InferenceResult {

    private final List<TraceEntry> trace = new ArrayList<>();
    private final List<CycleDetail> cycleDetails = new ArrayList<>();
    private final Set<Fact> producedFacts = new LinkedHashSet<>();
    private int cycleCount;

    /** 层级后处理：根本原因事实（叶节点，未被抑制），key = ruleName */
    private final Map<String, Fact> rootCauseFacts = new LinkedHashMap<>();
    /** 层级后处理：上下文事实（父节点，被子规则抑制降级），key = ruleName */
    private final Map<String, Fact> contextFacts = new LinkedHashMap<>();

    public void addTraceEntry(int cycle, String ruleName, Fact producedFact) {
        trace.add(new TraceEntry(cycle, ruleName, producedFact));
        producedFacts.add(producedFact);
    }

    public void addCycleDetail(CycleDetail detail) {
        cycleDetails.add(detail);
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public void addRootCauseFact(String ruleName, Fact fact) {
        rootCauseFacts.put(ruleName, fact);
        producedFacts.add(fact);
    }

    public void addContextFact(String ruleName, Fact fact) {
        contextFacts.put(ruleName, fact);
        producedFacts.add(fact);
    }

    public List<TraceEntry> getTrace() { return trace; }
    public List<CycleDetail> getCycleDetails() { return cycleDetails; }
    public Set<Fact> getProducedFacts() { return producedFacts; }
    public int getCycleCount() { return cycleCount; }
    public Map<String, Fact> getRootCauseFacts() { return rootCauseFacts; }
    public Map<String, Fact> getContextFacts() { return contextFacts; }

    /**
     * 获取指定谓词的事实值
     */
    public Object getFactValue(String predicate, String subject) {
        for (Fact f : producedFacts) {
            if (f.getPredicate().equals(predicate) && f.getSubject().equals(subject)) {
                return f.getValue();
            }
        }
        return null;
    }

    /**
     * 转为可序列化的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cycleCount", cycleCount);

        // 按轮次分组的完整推理轨迹
        List<Map<String, Object>> cycles = new ArrayList<>();
        for (CycleDetail detail : cycleDetails) {
            cycles.add(detail.toMap());
        }
        result.put("cycles", cycles);

        // 保留扁平 trace 兼容旧接口
        List<Map<String, Object>> traceList = new ArrayList<>();
        for (TraceEntry entry : trace) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cycle", entry.cycle());
            m.put("rule", entry.ruleName());
            m.put("fact", entry.fact().toString());
            traceList.add(m);
        }
        result.put("trace", traceList);

        Map<String, Object> facts = new LinkedHashMap<>();
        for (Fact f : producedFacts) {
            facts.put(f.getPredicate(), f.getValue());
        }
        result.put("facts", facts);

        // 层级后处理结果
        if (!rootCauseFacts.isEmpty()) {
            List<String> rootCauses = new ArrayList<>();
            for (Fact f : rootCauseFacts.values()) {
                rootCauses.add(String.valueOf(f.getValue()));
            }
            result.put("has_abnormal_root_cause", rootCauses);
        }
        if (!contextFacts.isEmpty()) {
            List<String> contexts = new ArrayList<>();
            for (Fact f : contextFacts.values()) {
                contexts.add(String.valueOf(f.getValue()));
            }
            result.put("has_abnormal_context", contexts);
        }

        return result;
    }

    public record TraceEntry(int cycle, String ruleName, Fact fact) {}

    /**
     * 单轮迭代的详细信息
     */
    public static class CycleDetail {
        private final int cycle;
        private final List<RuleEvaluation> evaluations = new ArrayList<>();
        private boolean newFactsProduced;

        public CycleDetail(int cycle) {
            this.cycle = cycle;
        }

        public void addEvaluation(RuleEvaluation eval) {
            evaluations.add(eval);
        }

        public void setNewFactsProduced(boolean produced) {
            this.newFactsProduced = produced;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cycle", cycle);
            m.put("newFactsProduced", newFactsProduced);

            List<Map<String, Object>> evalList = new ArrayList<>();
            for (RuleEvaluation eval : evaluations) {
                evalList.add(eval.toMap());
            }
            m.put("rules", evalList);
            return m;
        }
    }

    /**
     * 单条前件条件的匹配详情
     */
    public record MatchDetail(String condition, boolean matched, String actualValue, String description) {
        public MatchDetail(String condition, boolean matched, String actualValue) {
            this(condition, matched, actualValue, null);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("condition", condition);
            m.put("matched", matched);
            if (actualValue != null) {
                m.put("actualValue", actualValue);
            }
            if (description != null) {
                m.put("description", description);
            }
            return m;
        }
    }

    /**
     * 单条规则在某轮中的求值结果
     */
    public record RuleEvaluation(String ruleName, String displayName, boolean matched, Fact producedFact,
                                  boolean factIsNew, List<MatchDetail> matchDetails) {
        public RuleEvaluation(String ruleName, String displayName, boolean matched, Fact producedFact,
                              boolean factIsNew) {
            this(ruleName, displayName, matched, producedFact, factIsNew, List.of());
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rule", ruleName);
            m.put("displayName", displayName);
            m.put("matched", matched);
            if (producedFact != null) {
                m.put("fact", producedFact.toString());
                m.put("factIsNew", factIsNew);
            }
            if (matchDetails != null && !matchDetails.isEmpty()) {
                List<Map<String, Object>> details = new ArrayList<>();
                for (MatchDetail detail : matchDetails) {
                    details.add(detail.toMap());
                }
                m.put("matchDetails", details);
            }
            return m;
        }
    }
}
