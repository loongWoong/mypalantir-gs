package com.mypalantir.reasoning.cel.engine.impl;

import com.mypalantir.reasoning.cel.engine.CelEvalContext;
import com.mypalantir.reasoning.cel.engine.CelEvaluationService;
import com.mypalantir.reasoning.cel.engine.CelRuntimeFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CEL 求值服务实现：委托 CelRuntimeFactory，并设置/清除线程局部上下文。
 */
@Service
public class CelEvaluationServiceImpl implements CelEvaluationService {

    private static final List<String> BASE_VALIDATION_VARS = List.of("links");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    private static final Set<String> CEL_KEYWORDS = Set.of(
            "true", "false", "null", "in", "size", "map", "filter", "exists", "all",
            "sum", "sort", "double", "int", "string", "bool", "type", "has", "dyn",
            "links", "if", "else", "for"
    );

    private final CelRuntimeFactory runtimeFactory;

    public CelEvaluationServiceImpl(CelRuntimeFactory runtimeFactory) {
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public Object evaluate(String expr, Map<String, Object> env, CelEvalContext context) {
        try {
            if (context != null) {
                CelEvalContext.setCurrent(context);
            }
            return runtimeFactory.evaluate(expr, env);
        } finally {
            CelEvalContext.clearCurrent();
        }
    }

    @Override
    public void validateCompile(String expr) {
        Set<String> vars = new LinkedHashSet<>(BASE_VALIDATION_VARS);
        Matcher m = IDENTIFIER_PATTERN.matcher(expr);
        while (m.find()) {
            String id = m.group(1);
            if (!CEL_KEYWORDS.contains(id) && !id.matches("^\\d")) {
                vars.add(id);
            }
        }
        runtimeFactory.compileOnly(expr, vars);
    }
}
