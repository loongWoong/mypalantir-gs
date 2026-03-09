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

    public ForwardChainEngine(FunctionRegistry functionRegistry, List<SWRLRule> rules) {
        this.functionRegistry = functionRegistry;
        this.rules = rules;
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

        // 主变量名（约定为 ?p）
        String mainVar = "?p";

        // 初始化：将衍生属性值作为初始事实
        if (derivedValues != null) {
            for (Map.Entry<String, Object> entry : derivedValues.entrySet()) {
                memory.addFact(new Fact(entry.getKey(), mainVar, entry.getValue()));
            }
        }

        // 前向链循环
        int cycle = 0;
        boolean newFactsProduced;
        do {
            cycle++;
            newFactsProduced = false;
            InferenceResult.CycleDetail cycleDetail = new InferenceResult.CycleDetail(cycle);

            for (SWRLRule rule : rules) {
                memory.clearBindings();
                memory.bind(mainVar, instanceData);

                boolean matched = matchAntecedents(rule.getAntecedents(), memory, instanceData, linkedData, mainVar);
                if (matched) {
                    // 触发规则：产生后件事实
                    Fact newFact = fireConsequent(rule.getConsequent(), memory, mainVar);
                    boolean isNew = newFact != null && memory.addFact(newFact);
                    cycleDetail.addEvaluation(new InferenceResult.RuleEvaluation(
                        rule.getName(), rule.getDisplayName(), true, newFact, isNew));
                    if (isNew) {
                        result.addTraceEntry(cycle, rule.getName(), newFact);
                        newFactsProduced = true;
                    }
                } else {
                    cycleDetail.addEvaluation(new InferenceResult.RuleEvaluation(
                        rule.getName(), rule.getDisplayName(), false, null, false));
                }
            }

            cycleDetail.setNewFactsProduced(newFactsProduced);
            result.addCycleDetail(cycleDetail);
        } while (newFactsProduced && cycle < MAX_CYCLES);

        result.setCycleCount(cycle);
        return result;
    }

    /**
     * 检查规则的所有前件是否满足
     */
    private boolean matchAntecedents(List<Atom> antecedents, WorkingMemory memory,
                                      Map<String, Object> instanceData,
                                      Map<String, List<Map<String, Object>>> linkedData,
                                      String mainVar) {
        for (Atom atom : antecedents) {
            if (!matchAtom(atom, memory, instanceData, linkedData, mainVar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 匹配单个原子
     */
    private boolean matchAtom(Atom atom, WorkingMemory memory,
                               Map<String, Object> instanceData,
                               Map<String, List<Map<String, Object>>> linkedData,
                               String mainVar) {
        return switch (atom.getType()) {
            case TYPE_ASSERTION -> true; // 类型断言总是满足（实例已确定类型）

            case PROPERTY_MATCH -> matchPropertyAtom(atom, memory, mainVar);

            case FUNCTION_CALL -> matchFunctionCall(atom, instanceData, linkedData, mainVar);

            case DISJUNCTION -> matchDisjunction(atom, memory, instanceData, linkedData, mainVar);

            case INEQUALITY -> matchInequality(atom, memory);

            case LINK_TRAVERSAL -> matchLinkTraversal(atom, memory, linkedData);

            default -> false;
        };
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
     * 匹配函数调用
     */
    private boolean matchFunctionCall(Atom atom, Map<String, Object> instanceData,
                                       Map<String, List<Map<String, Object>>> linkedData,
                                       String mainVar) {
        String funcName = atom.getFunctionName();

        if (!functionRegistry.hasFunction(funcName)) {
            // 未注册的函数（如 external），跳过（视为满足）
            System.err.println("Function not registered, skipping: " + funcName);
            return true;
        }

        // 解析参数
        List<Object> resolvedArgs = resolveArguments(atom.getArguments(), instanceData, linkedData);

        // 调用函数
        Object actualResult = functionRegistry.call(funcName, resolvedArgs);

        // 比较结果
        return Objects.equals(actualResult, atom.getExpectedValue());
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
     * 匹配析取 (OR)
     */
    private boolean matchDisjunction(Atom atom, WorkingMemory memory,
                                      Map<String, Object> instanceData,
                                      Map<String, List<Map<String, Object>>> linkedData,
                                      String mainVar) {
        for (Atom disjunct : atom.getDisjuncts()) {
            if (matchAtom(disjunct, memory, instanceData, linkedData, mainVar)) {
                return true;
            }
        }
        return false;
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
