package com.mypalantir.reasoning.cel.engine.config;

import com.mypalantir.reasoning.cel.engine.CelFunctionRegistry;
import com.mypalantir.reasoning.cel.engine.impl.ListSumFunction;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 注册 CEL 引擎内置扩展函数（如 list.sum()）。
 */
@Component
public class CelEngineInitializer {

    private final CelFunctionRegistry celFunctionRegistry;
    private final ListSumFunction listSumFunction;

    public CelEngineInitializer(CelFunctionRegistry celFunctionRegistry, ListSumFunction listSumFunction) {
        this.celFunctionRegistry = celFunctionRegistry;
        this.listSumFunction = listSumFunction;
    }

    @PostConstruct
    public void registerBuiltinCelFunctions() {
        celFunctionRegistry.register("sum", listSumFunction);
    }
}
