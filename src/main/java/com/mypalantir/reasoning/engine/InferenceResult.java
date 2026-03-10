package com.mypalantir.reasoning.engine;

import java.util.*;

/**
 * 推理结果：包含所有产生的事实和推理轨迹。
 */
public class InferenceResult {

    private final List<TraceEntry> trace = new ArrayList<>();
    private final List<CycleDetail> cycleDetails = new ArrayList<>();
    private final Set<Fact> producedFacts = new LinkedHashSet<>();
    private int cycleCount;

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

    public List<TraceEntry> getTrace() { return trace; }
    public List<CycleDetail> getCycleDetails() { return cycleDetails; }
    public Set<Fact> getProducedFacts() { return producedFacts; }
    public int getCycleCount() { return cycleCount; }

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
     * 单条规则在某轮中的求值结果
     */
    public record RuleEvaluation(String ruleName, String displayName, boolean matched, Fact producedFact,
                                  boolean factIsNew) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rule", ruleName);
            m.put("displayName", displayName);
            m.put("matched", matched);
            if (producedFact != null) {
                m.put("fact", producedFact.toString());
                m.put("factIsNew", factIsNew);
            }
            return m;
        }
    }
}
