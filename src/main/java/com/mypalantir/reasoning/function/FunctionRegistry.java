package com.mypalantir.reasoning.function;

import com.mypalantir.meta.FunctionDef;
import com.mypalantir.meta.OntologySchema;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数注册中心。
 * 启动时注册 builtin 实现；按当前 schema 动态注册 script 实现（从 functions/script/ 加载）。
 */
@Component
public class FunctionRegistry {

    private final Map<String, OntologyFunction> functions = new HashMap<>();
    private final Map<String, OntologyFunction> scriptFunctions = new HashMap<>();

    /**
     * 注册一个函数实现（builtin 等常驻）
     */
    public void register(OntologyFunction function) {
        functions.put(function.getName(), function);
    }

    /**
     * 注册脚本函数（按 schema 加载时调用，切换 schema 前会先 clearScriptFunctions）
     */
    public void registerScript(OntologyFunction function) {
        scriptFunctions.put(function.getName(), function);
    }

    /**
     * 清除所有脚本函数（切换 schema 时调用）
     */
    public void clearScriptFunctions() {
        scriptFunctions.clear();
    }

    /**
     * 获取函数（先查 script，再查 builtin）。
     * script 优先：当 schema 显式声明 implementation=script 时，脚本版本会注册到 scriptFunctions，
     * 优先于同名 builtin 被调用，确保 schema 专属逻辑覆盖通用 builtin 实现。
     */
    public OntologyFunction getFunction(String name) {
        OntologyFunction fn = scriptFunctions.get(name);
        if (fn != null) return fn;
        return functions.get(name);
    }

    /**
     * 调用函数
     */
    public Object call(String name, List<Object> args) {
        OntologyFunction fn = getFunction(name);
        if (fn == null) {
            throw new IllegalArgumentException("Function not found: " + name);
        }
        return fn.execute(args);
    }

    /**
     * 是否已注册（builtin 或 script）
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name) || scriptFunctions.containsKey(name);
    }

    /**
     * 获取所有已注册的函数名（builtin + script）
     */
    public java.util.Set<String> getFunctionNames() {
        java.util.Set<String> out = new java.util.HashSet<>(functions.keySet());
        out.addAll(scriptFunctions.keySet());
        return out;
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
