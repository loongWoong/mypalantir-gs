package com.mypalantir.reasoning.cel.engine;

/**
 * CEL 扩展函数接口。
 * 实现类在 CEL 表达式中可被调用，通过 CelFunctionRegistry 注册。
 */
public interface CelFunction {

    Object evaluate(Object[] args, CelEvalContext context);

    CelFunctionMetadata getMetadata();
}
