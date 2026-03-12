package com.mypalantir.reasoning.cel.engine;

import java.util.Map;

/**
 * CEL 求值服务接口（增强版）。
 * 使用 Google CEL 运行时编译并执行表达式，支持完整 CEL 语法与自定义函数绑定。
 */
public interface CelEvaluationService {

    /**
     * 对 CEL 表达式求值。
     *
     * @param expr    表达式字符串
     * @param env     变量环境（通常包含 instance 属性与 "links" 关联数据）
     * @param context 求值上下文（可为 null，用于扩展函数获取当前 instance/links）
     * @return 求值结果，编译/求值失败时返回 null 或抛异常
     */
    Object evaluate(String expr, Map<String, Object> env, CelEvalContext context);

    /**
     * 仅做编译校验，不求值。用于验证表达式语法及变量/函数是否合法（声明 links 等常用变量）。
     *
     * @param expr 表达式字符串
     * @throws RuntimeException 编译失败时
     */
    void validateCompile(String expr);
}
