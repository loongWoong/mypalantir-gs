package com.mypalantir.reasoning.cel.engine;

import dev.cel.common.CelFunctionDecl;
import dev.cel.runtime.CelRuntimeBuilder;

import java.util.Collection;

/**
 * CEL 扩展函数注册表：声明与运行时绑定。
 */
public interface CelFunctionRegistry {

    void register(String name, CelFunction function);

    /** 同一名下可注册多个重载（不同参数个数），此处为追加一个重载 */
    void registerOverload(String name, CelFunction overload);

    Collection<CelFunctionDecl> getFunctionDeclarations();

    void addFunctionBindings(CelRuntimeBuilder runtimeBuilder);

    /** 清除所有已注册的函数声明与绑定 */
    default void clear() {}
}
