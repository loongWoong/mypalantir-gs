package com.mypalantir.reasoning.engine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作内存：存储推理过程中的事实集合。
 */
public class WorkingMemory {

    private final Set<Fact> facts = new LinkedHashSet<>();
    private final Map<String, Object> bindings = new HashMap<>();

    /**
     * 添加事实。返回 true 表示新事实（之前不存在）。
     */
    public boolean addFact(Fact fact) {
        return facts.add(fact);
    }

    /**
     * 查询是否存在匹配的事实
     */
    public boolean hasFact(String predicate, String subject, Object value) {
        return facts.contains(new Fact(predicate, subject, value));
    }

    /**
     * 查询谓词+主语的所有事实值
     */
    public List<Object> getValues(String predicate, String subject) {
        return facts.stream()
            .filter(f -> f.getPredicate().equals(predicate) && f.getSubject().equals(subject))
            .map(Fact::getValue)
            .collect(Collectors.toList());
    }

    /**
     * 查询谓词的所有事实
     */
    public List<Fact> getFacts(String predicate) {
        return facts.stream()
            .filter(f -> f.getPredicate().equals(predicate))
            .collect(Collectors.toList());
    }

    /**
     * 绑定变量值
     */
    public void bind(String variable, Object value) {
        bindings.put(variable, value);
    }

    /**
     * 获取变量绑定值
     */
    public Object getBinding(String variable) {
        return bindings.get(variable);
    }

    /**
     * 清除变量绑定（每条规则匹配前重置）
     */
    public void clearBindings() {
        bindings.clear();
    }

    /**
     * 获取所有事实
     */
    public Set<Fact> getAllFacts() {
        return Collections.unmodifiableSet(facts);
    }

    public int size() {
        return facts.size();
    }

    /**
     * 检查是否包含某个事实（不添加）
     */
    public boolean containsFact(Fact fact) {
        return facts.contains(fact);
    }

    /**
     * 创建当前工作内存的只读快照（共享事实集合的副本，独立的绑定空间）
     */
    public WorkingMemory snapshot() {
        WorkingMemory copy = new WorkingMemory();
        copy.facts.addAll(this.facts);
        return copy;
    }
}
