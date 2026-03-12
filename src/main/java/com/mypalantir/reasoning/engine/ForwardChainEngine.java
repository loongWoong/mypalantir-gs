package com.mypalantir.reasoning.engine;

import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.reasoning.swrl.Atom;
import com.mypalantir.reasoning.swrl.FunctionArg;
import com.mypalantir.reasoning.swrl.SWRLRule;

import java.util.*;

/**
 * 前向链推理引擎。
 * 核心循环：match → fire → match → fire ... 直到无新事实产生。
 */
public class ForwardChainEngine {

    private static final int MAX_CYCLES = 100; // 防止无限循环

    private final FunctionRegistry functionRegistry;
    private final List<SWRLRule> rules;
    private final Map<String, String> functionDisplayNames;

    public ForwardChainEngine(FunctionRegistry functionRegistry, List<SWRLRule> rules) {
        this(functionRegistry, rules, Map.of());
    }

    public ForwardChainEngine(FunctionRegistry functionRegistry, List<SWRLRule> rules,
                               Map<String, String> functionDisplayNames) {
        this.functionRegistry = functionRegistry;
        this.rules = rules;
        this.functionDisplayNames = functionDisplayNames;
    }

    /**
     * 对单个实例执行前向链推理。
     *
     * @param instanceData  实例数据 (e.g. Passage 的属性)
     * @param linkedData    关联数据 (linkName → List<Map>)
     * @param derivedValues 衍生属性值 (propName → value)
     * @return 推理结果
     */
    public InferenceResult infer(Map<String, Object> instanceData,
                                  Map<String, List<Map<String, Object>>> linkedData,
                                  Map<String, Object> derivedValues) {
        WorkingMemory memory = new WorkingMemory();
        InferenceResult result = new InferenceResult();

        // 函数调用缓存：同一次推理中相同函数+相同参数只调用一次
        Map<String, Object> functionCallCache = new HashMap<>();

        // 主变量名（约定为 ?p）
        String mainVar = "?p";

        // 初始化：将衍生属性值作为初始事实
        if (derivedValues != null) {
            for (Map.Entry<String, Object> entry : derivedValues.entrySet()) {
                memory.addFact(new Fact(entry.getKey(), mainVar, entry.getValue()));
            }
        }

        // 前向链循环（严格分层：新事实在下一 cycle 才可见）
        int cycle = 0;
        boolean newFactsProduced;
        do {
            cycle++;
            newFactsProduced = false;
            InferenceResult.CycleDetail cycleDetail = new InferenceResult.CycleDetail(cycle);
            List<Fact> pendingFacts = new ArrayList<>();

            // 对当前工作内存做快照，本轮规则只匹配快照中的事实
            WorkingMemory snapshot = memory.snapshot();

            for (SWRLRule rule : rules) {
                snapshot.clearBindings();
                snapshot.bind(mainVar, instanceData);

                List<InferenceResult.MatchDetail> matchDetails = matchAntecedents(rule.getAntecedents(), snapshot, instanceData, linkedData, mainVar, functionCallCache, rule.getName());
                boolean matched = matchDetails != null;
                if (matched) {
                    // 触发规则：产生后件事实（暂存，不立即加入工作内存）
                    Fact newFact = fireConsequent(rule.getConsequent(), snapshot, mainVar);
                    boolean isNew = newFact != null && !memory.containsFact(newFact) && !pendingFacts.contains(newFact);
                    cycleDetail.addEvaluation(new InferenceResult.RuleEvaluation(
                        rule.getName(), rule.getDisplayName(), true, newFact, isNew, matchDetails));
                    if (isNew) {
                        pendingFacts.add(newFact);
                        result.addTraceEntry(cycle, rule.getName(), newFact);
                        newFactsProduced = true;
                    }
                } else {
                    cycleDetail.addEvaluation(new InferenceResult.RuleEvaluation(
                        rule.getName(), rule.getDisplayName(), false, null, false));
                }
            }

            // 轮次结束后，将本轮新事实合并到工作内存
            for (Fact fact : pendingFacts) {
                memory.addFact(fact);
            }

            cycleDetail.setNewFactsProduced(newFactsProduced);
            result.addCycleDetail(cycleDetail);
        } while (newFactsProduced && cycle < MAX_CYCLES);

        result.setCycleCount(cycle);
        return result;
    }

    /**
     * 检查规则的所有前件是否满足。
     * @return 匹配详情列表；若任一前件不满足返回 null
     */
    private List<InferenceResult.MatchDetail> matchAntecedents(List<Atom> antecedents, WorkingMemory memory,
                                      Map<String, Object> instanceData,
                                      Map<String, List<Map<String, Object>>> linkedData,
                                      String mainVar,
                                      Map<String, Object> functionCallCache,
                                      String ruleName) {
        List<InferenceResult.MatchDetail> details = new ArrayList<>();
        for (Atom atom : antecedents) {
            // TYPE_ASSERTION 总是满足，无需记录
            if (atom.getType() == Atom.Type.TYPE_ASSERTION) continue;

            AtomMatchResult amr = matchAtomWithDetail(atom, memory, instanceData, linkedData, mainVar, functionCallCache);
            details.add(new InferenceResult.MatchDetail(amr.condition, amr.matched, amr.actualValue, amr.description));
            if (!amr.matched) return null;
        }
        return details;
    }

    private record AtomMatchResult(String condition, boolean matched, String actualValue, String description) {
        AtomMatchResult(String condition, boolean matched, String actualValue) {
            this(condition, matched, actualValue, null);
        }
    }

    /**
     * 匹配单个原子并返回详情
     */
    private AtomMatchResult matchAtomWithDetail(Atom atom, WorkingMemory memory,
                                                 Map<String, Object> instanceData,
                                                 Map<String, List<Map<String, Object>>> linkedData,
                                                 String mainVar,
                                                 Map<String, Object> functionCallCache) {
        return switch (atom.getType()) {
            case TYPE_ASSERTION -> new AtomMatchResult(atom.toString(), true, null);

            case PROPERTY_MATCH -> {
                boolean result = matchPropertyAtom(atom, memory, mainVar);
                String condition = atom.getPredicate() + "(" + atom.getSubject() + ") == " +
                    (atom.getValueVariable() != null ? atom.getValueVariable() : atom.getValue());
                String actual = null;
                if (atom.getValueVariable() != null) {
                    List<Object> vals = memory.getValues(atom.getPredicate(), atom.getSubject());
                    actual = vals.isEmpty() ? "无" : String.valueOf(vals.get(0));
                } else {
                    actual = result ? String.valueOf(atom.getValue()) : "不匹配";
                }
                String desc = describePropertyMatch(atom, result);
                yield new AtomMatchResult(condition, result, actual, desc);
            }

            case FUNCTION_CALL -> {
                String funcName = atom.getFunctionName();
                if (!functionRegistry.hasFunction(funcName)) {
                    yield new AtomMatchResult(funcName + "(...) [未注册]", true, "跳过");
                }
                List<Object> resolvedArgs = resolveArguments(atom.getArguments(), instanceData, linkedData);
                String cacheKey = funcName + ":" + resolvedArgs.size() + ":" + Objects.hash(resolvedArgs.toArray());
                Object actualResult = functionCallCache.computeIfAbsent(cacheKey,
                        k -> functionRegistry.call(funcName, resolvedArgs));
                boolean result = Objects.equals(actualResult, atom.getExpectedValue());
                String condition = funcName + "(...) == " + atom.getExpectedValue();
                String desc = functionDisplayNames.getOrDefault(funcName, funcName);
                if (result) {
                    desc += Objects.equals(atom.getExpectedValue(), true) ? " — 是" : " — 否";
                } else {
                    desc += Objects.equals(atom.getExpectedValue(), true) ? " — 否" : " — 是";
                }
                yield new AtomMatchResult(condition, result, String.valueOf(actualResult), desc);
            }

            case DISJUNCTION -> {
                StringBuilder condition = new StringBuilder();
                boolean anyMatched = false;
                String matchedBranch = null;
                for (Atom disjunct : atom.getDisjuncts()) {
                    if (condition.length() > 0) condition.append(" | ");
                    condition.append(disjunct.toString());
                    if (!anyMatched && matchAtom(disjunct, memory, instanceData, linkedData, mainVar, functionCallCache)) {
                        anyMatched = true;
                        matchedBranch = disjunct.toString();
                    }
                }
                String desc = anyMatched ? "满足条件: " + matchedBranch : "所有分支均不满足";
                yield new AtomMatchResult(condition.toString(), anyMatched, matchedBranch, desc);
            }

            case INEQUALITY -> {
                boolean result = matchInequality(atom, memory);
                Object boundValue = memory.getBinding(atom.getInequalityVar());
                String condition = atom.getInequalityVar() + " != " + atom.getInequalityValue();
                String desc = result ? boundValue + " ≠ " + atom.getInequalityValue() : boundValue + " = " + atom.getInequalityValue();
                yield new AtomMatchResult(condition, result, String.valueOf(boundValue), desc);
            }

            case LINK_TRAVERSAL -> {
                boolean result = matchLinkTraversal(atom, memory, linkedData);
                String condition = atom.getLinkName() + "(" + atom.getSourceVar() + ", " + atom.getTargetVar() + ")";
                List<Map<String, Object>> targets = linkedData != null ? linkedData.get(atom.getLinkName()) : null;
                String actual = (targets != null ? targets.size() + "条记录" : "无数据");
                String desc = result ? "关联" + actual : "无关联数据";
                yield new AtomMatchResult(condition, result, actual, desc);
            }

            default -> new AtomMatchResult(atom.toString(), false, null);
        };
    }

    /**
     * 为属性匹配生成语义描述
     */
    private String describePropertyMatch(Atom atom, boolean matched) {
        String predicate = atom.getPredicate();
        if (atom.getValueVariable() != null) {
            return matched ? "绑定变量 " + atom.getValueVariable() : "无匹配事实";
        }
        Object value = atom.getValue();
        return matched ? predicate + " = " + value : predicate + " ≠ " + value;
    }

    /**
     * 匹配单个原子（简单版本，用于析取内部）
     */
    private boolean matchAtom(Atom atom, WorkingMemory memory,
                               Map<String, Object> instanceData,
                               Map<String, List<Map<String, Object>>> linkedData,
                               String mainVar,
                               Map<String, Object> functionCallCache) {
        return matchAtomWithDetail(atom, memory, instanceData, linkedData, mainVar, functionCallCache).matched;
    }

    /**
     * 匹配属性/事实
     */
    private boolean matchPropertyAtom(Atom atom, WorkingMemory memory, String mainVar) {
        String predicate = atom.getPredicate();
        String subject = atom.getSubject();

        if (atom.getValueVariable() != null) {
            // 变量绑定模式: prop(?p, ?status) — 查找任意匹配的事实并绑定变量
            List<Object> values = memory.getValues(predicate, subject);
            if (values.isEmpty()) return false;
            memory.bind(atom.getValueVariable(), values.get(0));
            return true;
        }

        // 值匹配模式: prop(?p, "value")
        return memory.hasFact(predicate, subject, atom.getValue());
    }

    /**
     * 解析函数参数为实际值
     */
    private List<Object> resolveArguments(List<FunctionArg> args,
                                           Map<String, Object> instanceData,
                                           Map<String, List<Map<String, Object>>> linkedData) {
        List<Object> resolved = new ArrayList<>();
        for (FunctionArg arg : args) {
            switch (arg.getType()) {
                case VARIABLE -> resolved.add(instanceData);
                case LINKS_EXPR -> {
                    List<Map<String, Object>> data = linkedData != null ? linkedData.get(arg.getLinkName()) : null;
                    resolved.add(data != null ? data : List.of());
                }
                case LINKS_PATH_EXPR -> {
                    // links(?p, linkName)[0].field — 取第一个元素的字段
                    List<Map<String, Object>> data = linkedData != null ? linkedData.get(arg.getLinkName()) : null;
                    if (data != null && !data.isEmpty()) {
                        String suffix = arg.getPathSuffix(); // e.g., "[0].pro_split_time"
                        // 简单解析: [0].fieldName
                        if (suffix.startsWith("[0].")) {
                            String field = suffix.substring(4);
                            resolved.add(data.get(0).get(field));
                        } else {
                            resolved.add(data);
                        }
                    } else {
                        resolved.add(null);
                    }
                }
                case LITERAL -> resolved.add(arg.getLiteralValue());
            }
        }
        return resolved;
    }

    /**
     * 匹配不等式
     */
    private boolean matchInequality(Atom atom, WorkingMemory memory) {
        Object boundValue = memory.getBinding(atom.getInequalityVar());
        if (boundValue == null) return false;
        return !Objects.equals(boundValue, atom.getInequalityValue());
    }

    /**
     * 匹配关系遍历：绑定目标变量
     */
    private boolean matchLinkTraversal(Atom atom, WorkingMemory memory,
                                        Map<String, List<Map<String, Object>>> linkedData) {
        String linkName = atom.getLinkName();
        List<Map<String, Object>> targets = linkedData != null ? linkedData.get(linkName) : null;
        if (targets == null || targets.isEmpty()) return false;

        // 绑定目标变量为第一个关联对象（简化：实际应对每个目标分别触发）
        memory.bind(atom.getTargetVar(), targets.get(0));
        return true;
    }

    /**
     * 触发后件：产生新事实
     */
    private Fact fireConsequent(Atom consequent, WorkingMemory memory, String mainVar) {
        String predicate = consequent.getPredicate();
        String subject = consequent.getSubject();

        Object value;
        if (consequent.getValueVariable() != null) {
            // 变量引用: prop(?v, true) — 从绑定中获取主语
            value = consequent.getValue();
            if (value == null) value = true; // 默认 true
            // 主语是变量引用，保持为变量名（在实际传播时会解析）
        } else {
            value = consequent.getValue();
        }

        return new Fact(predicate, subject, value);
    }
}
