package com.mypalantir.reasoning.swrl;

import java.util.List;

/**
 * SWRL 原子表达式。所有原子类型统一用这一个类表示，通过 type 区分语义。
 */
public class Atom {

    public enum Type {
        /** 类型断言: Type(?var) — 如 Passage(?p) */
        TYPE_ASSERTION,
        /** 属性/事实匹配: prop(?var, value) — 如 detail_count_matched(?p, true) */
        PROPERTY_MATCH,
        /** 函数调用比较: func(args) == value — 如 is_single_province_etc(?p) == true */
        FUNCTION_CALL,
        /** 析取(OR): (atom1 ∨ atom2) — 如 (cause(?p,"A") ∨ cause(?p,"B")) */
        DISJUNCTION,
        /** 不等式: (?var != value) — 如 (?status != "正常") */
        INEQUALITY,
        /** 关系遍历: linkName(?var1, ?var2) — 如 entry_involves_vehicle(?p, ?v) */
        LINK_TRAVERSAL,
        /** 事实断言(后件): prop(?var, value) */
        FACT_ASSERTION
    }

    private final Type type;

    // TYPE_ASSERTION: typeName + variable
    private String typeName;
    private String variable;

    // PROPERTY_MATCH / FACT_ASSERTION: predicate + subject(variable) + value/valueVariable
    private String predicate;
    private String subject;
    private Object value;            // 字面值 (string/boolean/number)
    private String valueVariable;    // 变量绑定 ?status

    // FUNCTION_CALL: functionName + arguments + expectedValue
    private String functionName;
    private List<FunctionArg> arguments;
    private Object expectedValue;

    // DISJUNCTION: disjuncts
    private List<Atom> disjuncts;

    // INEQUALITY: inequalityVar + inequalityValue
    private String inequalityVar;
    private Object inequalityValue;

    // LINK_TRAVERSAL: linkName + sourceVar + targetVar
    private String linkName;
    private String sourceVar;
    private String targetVar;

    private Atom(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }

    // ---- Factory methods ----

    public static Atom typeAssertion(String typeName, String variable) {
        Atom a = new Atom(Type.TYPE_ASSERTION);
        a.typeName = typeName;
        a.variable = variable;
        return a;
    }

    public static Atom propertyMatch(String predicate, String subject, Object value) {
        Atom a = new Atom(Type.PROPERTY_MATCH);
        a.predicate = predicate;
        a.subject = subject;
        a.value = value;
        return a;
    }

    public static Atom propertyMatchVar(String predicate, String subject, String valueVariable) {
        Atom a = new Atom(Type.PROPERTY_MATCH);
        a.predicate = predicate;
        a.subject = subject;
        a.valueVariable = valueVariable;
        return a;
    }

    public static Atom functionCall(String functionName, List<FunctionArg> arguments, Object expectedValue) {
        Atom a = new Atom(Type.FUNCTION_CALL);
        a.functionName = functionName;
        a.arguments = arguments;
        a.expectedValue = expectedValue;
        return a;
    }

    public static Atom disjunction(List<Atom> disjuncts) {
        Atom a = new Atom(Type.DISJUNCTION);
        a.disjuncts = disjuncts;
        return a;
    }

    public static Atom inequality(String variable, Object value) {
        Atom a = new Atom(Type.INEQUALITY);
        a.inequalityVar = variable;
        a.inequalityValue = value;
        return a;
    }

    public static Atom linkTraversal(String linkName, String sourceVar, String targetVar) {
        Atom a = new Atom(Type.LINK_TRAVERSAL);
        a.linkName = linkName;
        a.sourceVar = sourceVar;
        a.targetVar = targetVar;
        return a;
    }

    public static Atom factAssertion(String predicate, String subject, Object value) {
        Atom a = new Atom(Type.FACT_ASSERTION);
        a.predicate = predicate;
        a.subject = subject;
        a.value = value;
        return a;
    }

    public static Atom factAssertionVar(String predicate, String subject, String valueVariable) {
        Atom a = new Atom(Type.FACT_ASSERTION);
        a.predicate = predicate;
        a.subject = subject;
        a.valueVariable = valueVariable;
        return a;
    }

    // ---- Getters ----

    public String getTypeName() { return typeName; }
    public String getVariable() { return variable; }
    public String getPredicate() { return predicate; }
    public String getSubject() { return subject; }
    public Object getValue() { return value; }
    public String getValueVariable() { return valueVariable; }
    public String getFunctionName() { return functionName; }
    public List<FunctionArg> getArguments() { return arguments; }
    public Object getExpectedValue() { return expectedValue; }
    public List<Atom> getDisjuncts() { return disjuncts; }
    public String getInequalityVar() { return inequalityVar; }
    public Object getInequalityValue() { return inequalityValue; }
    public String getLinkName() { return linkName; }
    public String getSourceVar() { return sourceVar; }
    public String getTargetVar() { return targetVar; }

    @Override
    public String toString() {
        return switch (type) {
            case TYPE_ASSERTION -> typeName + "(" + variable + ")";
            case PROPERTY_MATCH -> predicate + "(" + subject + ", " + (valueVariable != null ? valueVariable : value) + ")";
            case FUNCTION_CALL -> functionName + "(...) == " + expectedValue;
            case DISJUNCTION -> "(" + disjuncts + ")";
            case INEQUALITY -> "(" + inequalityVar + " != " + inequalityValue + ")";
            case LINK_TRAVERSAL -> linkName + "(" + sourceVar + ", " + targetVar + ")";
            case FACT_ASSERTION -> predicate + "(" + subject + ", " + (valueVariable != null ? valueVariable : value) + ")";
        };
    }
}
