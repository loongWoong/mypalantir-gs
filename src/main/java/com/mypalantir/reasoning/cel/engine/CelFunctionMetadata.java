package com.mypalantir.reasoning.cel.engine;

import dev.cel.common.types.CelType;

import java.util.List;

/**
 * CEL 扩展函数的元数据（声明名、重载、参数与返回类型）。
 */
public interface CelFunctionMetadata {

    String getCelName();

    String getOverloadId();

    List<CelType> getParameterTypes();

    CelType getResultType();

    boolean isMember();

    String getDoc();
}
