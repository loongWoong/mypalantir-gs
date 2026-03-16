package com.mypalantir.reasoning.cel.engine;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationResult;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * CEL 运行时工厂：编译表达式并求值。
 * 使用 CelStandardLibrary 与 CelFunctionRegistry 构建编译器和运行时。
 */
public class CelRuntimeFactory {

    private final CelStandardLibrary standardLibrary;
    private final CelFunctionRegistry registry;

    public CelRuntimeFactory(CelStandardLibrary standardLibrary, CelFunctionRegistry registry) {
        this.standardLibrary = standardLibrary;
        this.registry = registry;
    }

    /**
     * 仅编译不求值，用于校验表达式是否合法。
     * 声明给定变量名为 DYN，编译通过则视为合法。
     *
     * @param expr     表达式
     * @param varNames 需声明的变量名（如 "links"）
     * @throws RuntimeException 编译错误时
     */
    public void compileOnly(String expr, Collection<String> varNames) {
        CelCompilerBuilder compilerBuilder = standardLibrary.newCompilerBuilder();
        for (String key : varNames) {
            compilerBuilder.addVar(key, SimpleType.DYN);
        }
        compilerBuilder.addFunctionDeclarations(registry.getFunctionDeclarations());
        CelCompiler compiler = compilerBuilder.build();
        CelValidationResult result = compiler.compile(expr);
        if (result.hasError()) {
            String msg = result.getErrors().isEmpty()
                    ? "Compilation failed"
                    : result.getErrors().get(0).getMessage();
            throw new RuntimeException(msg);
        }
    }

    public Object evaluate(String expr, Map<String, Object> env) {
        try {
            CelCompilerBuilder compilerBuilder = standardLibrary.newCompilerBuilder();
            for (String key : env.keySet()) {
                compilerBuilder.addVar(key, SimpleType.DYN);
            }
            compilerBuilder.addFunctionDeclarations(registry.getFunctionDeclarations());
            CelCompiler compiler = compilerBuilder.build();
            CelValidationResult result = compiler.compile(expr);
            if (result.hasError()) {
                String errMsg = result.getErrors().isEmpty() ? "unknown" : result.getErrors().get(0).getMessage();
                System.err.println("[CEL] Compilation failed for expr: " + expr + " | error: " + errMsg);
                return null;
            }
            CelAbstractSyntaxTree ast = result.getAst();
            CelRuntimeBuilder runtimeBuilder = standardLibrary.newRuntimeBuilder();
            registry.addFunctionBindings(runtimeBuilder);
            CelRuntime runtime = runtimeBuilder.build();
            CelRuntime.Program program = runtime.createProgram(ast);
            return program.eval(env);
        } catch (CelValidationException | CelEvaluationException e) {
            throw new RuntimeException("CEL evaluate failed: " + expr, e);
        }
    }
}
