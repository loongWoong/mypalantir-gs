package com.mypalantir.reasoning.cel.engine.impl;

import com.mypalantir.reasoning.cel.engine.CelEvalContext;
import com.mypalantir.reasoning.cel.engine.CelEvaluationService;
import com.mypalantir.reasoning.cel.engine.CelRuntimeFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * CEL 求值服务实现：委托 CelRuntimeFactory，并设置/清除线程局部上下文。
 */
@Service
public class CelEvaluationServiceImpl implements CelEvaluationService {

    /** 校验时声明的变量，使 size(links.xxx) 等表达式能通过编译 */
    private static final List<String> VALIDATION_VARS = List.of("links");

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
        runtimeFactory.compileOnly(expr, VALIDATION_VARS);
    }
}
