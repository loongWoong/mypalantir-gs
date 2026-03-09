package com.mypalantir.reasoning.function;

import java.util.List;
import java.util.Map;

/**
 * 本体函数接口。
 * 每个函数接收命名参数，返回计算结果。
 */
public interface OntologyFunction {

    /**
     * 执行函数
     *
     * @param args 参数列表，每个参数可以是单个实例 Map 或实例列表
     * @return 计算结果（通常为 boolean，也可以是其他类型）
     */
    Object execute(List<Object> args);

    /**
     * 函数名称（与 YAML 定义中的 name 对应）
     */
    String getName();
}
