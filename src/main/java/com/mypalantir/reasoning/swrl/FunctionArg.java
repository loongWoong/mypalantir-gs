package com.mypalantir.reasoning.swrl;

/**
 * 函数调用参数。
 * 支持三种形式：
 * 1. 变量引用: ?p
 * 2. links 表达式: links(?p, passage_has_gantry_transactions)
 * 3. 字面值: 0.95, "string"
 * 4. 复杂路径: links(?p, linkName)[0].field
 */
public class FunctionArg {

    public enum Type {
        VARIABLE,       // ?p
        LINKS_EXPR,     // links(?p, linkName)
        LITERAL,        // 0.95
        LINKS_PATH_EXPR // links(?p, linkName)[0].field
    }

    private final Type type;
    private String variable;
    private String linkName;
    private Object literalValue;
    private String pathSuffix;  // [0].pro_split_time

    private FunctionArg(Type type) {
        this.type = type;
    }

    public static FunctionArg variable(String variable) {
        FunctionArg arg = new FunctionArg(Type.VARIABLE);
        arg.variable = variable;
        return arg;
    }

    public static FunctionArg linksExpr(String variable, String linkName) {
        FunctionArg arg = new FunctionArg(Type.LINKS_EXPR);
        arg.variable = variable;
        arg.linkName = linkName;
        return arg;
    }

    public static FunctionArg literal(Object value) {
        FunctionArg arg = new FunctionArg(Type.LITERAL);
        arg.literalValue = value;
        return arg;
    }

    public static FunctionArg linksPathExpr(String variable, String linkName, String pathSuffix) {
        FunctionArg arg = new FunctionArg(Type.LINKS_PATH_EXPR);
        arg.variable = variable;
        arg.linkName = linkName;
        arg.pathSuffix = pathSuffix;
        return arg;
    }

    public Type getType() { return type; }
    public String getVariable() { return variable; }
    public String getLinkName() { return linkName; }
    public Object getLiteralValue() { return literalValue; }
    public String getPathSuffix() { return pathSuffix; }

    @Override
    public String toString() {
        return switch (type) {
            case VARIABLE -> variable;
            case LINKS_EXPR -> "links(" + variable + ", " + linkName + ")";
            case LITERAL -> String.valueOf(literalValue);
            case LINKS_PATH_EXPR -> "links(" + variable + ", " + linkName + ")" + pathSuffix;
        };
    }
}
