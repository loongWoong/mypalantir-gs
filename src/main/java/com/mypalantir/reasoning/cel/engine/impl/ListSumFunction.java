package com.mypalantir.reasoning.cel.engine.impl;

import com.mypalantir.reasoning.cel.engine.CelEvalContext;
import com.mypalantir.reasoning.cel.engine.CelFunction;
import com.mypalantir.reasoning.cel.engine.CelFunctionMetadata;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CEL 扩展：list.sum()，对列表数值元素求和。
 */
@Component
public class ListSumFunction implements CelFunction {

    private static final String OVERLOAD_ID = "list_sum";

    @Override
    public Object evaluate(Object[] args, CelEvalContext context) {
        Object listObj = args != null && args.length > 0 ? args[0] : null;
        if (listObj == null) return 0.0;
        if (!(listObj instanceof List)) return 0.0;
        List<?> list = (List<?>) listObj;
        double sum = 0.0;
        for (Object e : list) {
            if (e instanceof Number) {
                sum += ((Number) e).doubleValue();
            } else if (e != null) {
                try {
                    sum += Double.parseDouble(e.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return sum;
    }

    @Override
    public CelFunctionMetadata getMetadata() {
        return new CelFunctionMetadata() {
            @Override
            public String getCelName() {
                return "sum";
            }

            @Override
            public String getOverloadId() {
                return OVERLOAD_ID;
            }

            @Override
            public List<dev.cel.common.types.CelType> getParameterTypes() {
                return List.of(ListType.create(SimpleType.DYN));
            }

            @Override
            public dev.cel.common.types.CelType getResultType() {
                return SimpleType.DOUBLE;
            }

            @Override
            public boolean isMember() {
                return true;
            }

            @Override
            public String getDoc() {
                return "Sum numeric elements of a list.";
            }
        };
    }
}
