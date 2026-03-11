package com.mypalantir.reasoning.cel;

import com.mypalantir.reasoning.function.FunctionRegistry;

import java.util.List;

/**
 * CEL 与 FunctionRegistry 的桥接：将 CEL 表达式中的函数调用转发到 FunctionRegistry。
 * 用于在衍生属性表达式中调用与 SWRL 相同的 builtin 函数。
 */
public class CelFunctionAdapter {

    private final FunctionRegistry functionRegistry;

    public CelFunctionAdapter(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    /**
     * 是否已注册指定名称的函数
     */
    public boolean hasFunction(String name) {
        return functionRegistry != null && functionRegistry.hasFunction(name);
    }

    /**
     * 从 CEL 侧调用已注册函数，参数已求值为 Java 对象列表。
     *
     * @param name 函数名
     * @param args 参数列表（可为 Map 实例、List、字面值等）
     * @return 函数返回值
     */
    public Object call(String name, List<Object> args) {
        if (functionRegistry == null) {
            throw new IllegalStateException("FunctionRegistry not available for CEL function call: " + name);
        }
        return functionRegistry.call(name, args);
    }
}
