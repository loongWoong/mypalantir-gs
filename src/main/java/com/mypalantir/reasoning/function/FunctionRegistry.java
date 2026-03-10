package com.mypalantir.reasoning.function;

import com.mypalantir.meta.FunctionDef;
import com.mypalantir.meta.OntologySchema;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数注册中心。
 * 启动时扫描 OntologySchema 中的 function 定义，注册 builtin 实现。
 */
@Component
public class FunctionRegistry {

    private final Map<String, OntologyFunction> functions = new HashMap<>();

    /**
     * 注册一个函数实现
     */
    public void register(OntologyFunction function) {
        functions.put(function.getName(), function);
    }

    /**
     * 获取函数
     */
    public OntologyFunction getFunction(String name) {
        return functions.get(name);
    }

    /**
     * 调用函数
     */
    public Object call(String name, List<Object> args) {
        OntologyFunction fn = functions.get(name);
        if (fn == null) {
            throw new IllegalArgumentException("Function not found: " + name);
        }
        return fn.execute(args);
    }

    /**
     * 是否已注册
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    /**
     * 获取所有已注册的函数名
     */
    public java.util.Set<String> getFunctionNames() {
        return functions.keySet();
    }

    /**
     * 根据 schema 中的 function 定义，验证是否所有 builtin 函数都已注册
     */
    public List<String> getMissingBuiltins(OntologySchema schema) {
        if (schema.getFunctions() == null) return List.of();
        return schema.getFunctions().stream()
            .filter(f -> "builtin".equals(f.getImplementation()))
            .map(FunctionDef::getName)
            .filter(name -> !functions.containsKey(name))
            .toList();
    }
}
