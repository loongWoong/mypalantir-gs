package com.mypalantir.reasoning.cel.engine;

import com.mypalantir.reasoning.function.FunctionRegistry;
import dev.cel.common.types.SimpleType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 {@link FunctionRegistry} 中已注册的本体函数暴露为 CEL 可调用的扩展函数。
 * 按参数个数注册多个重载（如 1..5），便于 CEL 按实参个数匹配。
 */
public class RegistryBackedCelFunction implements CelFunction {

    private final String functionName;
    private final FunctionRegistry functionRegistry;
    private final int paramCount;
    private final CelFunctionMetadata metadata;

    /** 最大支持的参数个数（含 instance 等） */
    public static final int MAX_ARITY = 5;

    public RegistryBackedCelFunction(String functionName, FunctionRegistry functionRegistry, int paramCount) {
        if (paramCount < 1 || paramCount > MAX_ARITY) {
            throw new IllegalArgumentException("paramCount must be 1.." + MAX_ARITY);
        }
        this.functionName = functionName;
        this.functionRegistry = functionRegistry;
        this.paramCount = paramCount;
        this.metadata = new Metadata();
    }

    @Override
    public Object evaluate(Object[] args, CelEvalContext context) {
        List<Object> list = args == null ? Collections.emptyList() : Arrays.asList(args);
        return functionRegistry.call(functionName, list);
    }

    @Override
    public CelFunctionMetadata getMetadata() {
        return metadata;
    }

    private class Metadata implements CelFunctionMetadata {
        @Override
        public String getCelName() {
            return functionName;
        }

        @Override
        public String getOverloadId() {
            return functionName + "_" + paramCount;
        }

        @Override
        public List<dev.cel.common.types.CelType> getParameterTypes() {
            return Collections.nCopies(paramCount, SimpleType.DYN);
        }

        @Override
        public dev.cel.common.types.CelType getResultType() {
            return SimpleType.DYN;
        }

        @Override
        public boolean isMember() {
            return false;
        }

        @Override
        public String getDoc() {
            return "Ontology function: " + functionName;
        }
    }
}
