package com.mypalantir.reasoning.cel;

import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.reasoning.function.OntologyFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CEL 求值器测试：内置语法与通过 FunctionRegistry 的自定义函数调用。
 */
class CelEvaluatorTest {

    private CelEvaluator evaluatorWithoutRegistry;
    private CelEvaluator evaluatorWithRegistry;
    private FunctionRegistry registry;

    @BeforeEach
    void setUp() {
        evaluatorWithoutRegistry = new CelEvaluator();
        registry = new FunctionRegistry();
        registry.register(new OntologyFunction() {
            @Override
            public String getName() { return "returns_true"; }

            @Override
            public Object execute(List<Object> args) {
                return Boolean.TRUE;
            }
        });
        registry.register(new OntologyFunction() {
            @Override
            public String getName() { return "echo_first"; }

            @Override
            public Object execute(List<Object> args) {
                return args.isEmpty() ? null : args.get(0);
            }
        });
        evaluatorWithRegistry = new CelEvaluator(new CelFunctionAdapter(registry));
    }

    @Test
    void size_withoutLinks_returnsZero() {
        Object result = evaluatorWithoutRegistry.evaluate("size(links.missing)", Map.of(), Map.of());
        assertEquals(0, result);
    }

    @Test
    void size_withLinks_returnsCount() {
        Map<String, List<Map<String, Object>>> linked = Map.of("items", List.of(Map.of("a", 1), Map.of("b", 2)));
        Object result = evaluatorWithoutRegistry.evaluate("size(links.items)", Map.of(), linked);
        assertEquals(2, result);
    }

    @Test
    void equals_comparison() {
        Object result = evaluatorWithoutRegistry.evaluate("1 == 1", Map.of(), Map.of());
        assertEquals(Boolean.TRUE, result);
        result = evaluatorWithoutRegistry.evaluate("\"x\" == \"x\"", Map.of(), Map.of());
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void customFunction_callViaAdapter_returnsResult() {
        Map<String, Object> props = Map.of();
        Map<String, List<Map<String, Object>>> links = Map.of();
        Object result = evaluatorWithRegistry.evaluate("returns_true()", props, links);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void customFunction_withLiteralArg_echoes() {
        Object result = evaluatorWithRegistry.evaluate("echo_first(42)", Map.of(), Map.of());
        assertEquals(42, result);
    }

    @Test
    void customFunction_withPathArg_usesResolvedValue() {
        Map<String, Object> props = Map.of("score", 99);
        Object result = evaluatorWithRegistry.evaluate("echo_first(score)", props, Map.of());
        assertEquals(99, result);
    }

    @Test
    void customFunction_withLinksArg_passesList() {
        Map<String, List<Map<String, Object>>> linked = Map.of("data", List.of(Map.of("x", 1)));
        Object result = evaluatorWithRegistry.evaluate("echo_first(links.data)", Map.of(), linked);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(1, ((List<?>) result).size());
    }

    @Test
    void builtinSize_notRoutedToRegistry() {
        // size(links.x) 应走内置 size，不交给 FunctionRegistry
        Map<String, List<Map<String, Object>>> linked = Map.of("x", List.of(Map.of()));
        Object result = evaluatorWithRegistry.evaluate("size(links.x)", Map.of(), linked);
        assertEquals(1, result);
    }

    @Test
    void noAdapter_unknownFuncTreatedAsPath() {
        // 无 adapter 时，unknownFunc(1) 会先被 parseFunctionCall 解析，但无 adapter 不会调用；
        // 整体会落到 evaluateSimple，最终 resolvePath("unknownFunc(1)") 返回 null
        Object result = evaluatorWithoutRegistry.evaluate("unknownFunc(1)", Map.of(), Map.of());
        assertNull(result);
    }
}
