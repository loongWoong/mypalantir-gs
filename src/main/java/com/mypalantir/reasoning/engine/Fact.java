package com.mypalantir.reasoning.engine;

import java.util.Objects;

/**
 * 工作内存中的事实。
 * 三元组：(predicate, subject, value)
 * 例如：("obu_split_status", "?p", "路径不一致")
 */
public class Fact {
    private final String predicate;
    private final String subject;
    private final Object value;

    public Fact(String predicate, String subject, Object value) {
        this.predicate = predicate;
        this.subject = subject;
        this.value = value;
    }

    public String getPredicate() { return predicate; }
    public String getSubject() { return subject; }
    public Object getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fact fact)) return false;
        return Objects.equals(predicate, fact.predicate)
            && Objects.equals(subject, fact.subject)
            && Objects.equals(value, fact.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicate, subject, value);
    }

    @Override
    public String toString() {
        return predicate + "(" + subject + ", " + value + ")";
    }
}
