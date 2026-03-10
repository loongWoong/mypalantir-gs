package com.mypalantir.reasoning.function.builtin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 比较拆分结果的收费单元组合与门架流水的收费单元组合是否一致。
 * 输入：split_details (list), gantry_transactions (list)
 */
public class CheckRouteConsistency extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "check_route_consistency"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> splitDetails = asList(args.get(0));
        List<Map<String, Object>> gantryTxs = asList(args.get(1));

        // 提取拆分明细的 interval_id 集合（排序后）
        List<String> splitIntervals = splitDetails.stream()
            .map(s -> getString(s, "interval_id"))
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());

        // 提取门架交易的 toll_interval_id 集合（排序后）
        List<String> gantryIntervals = gantryTxs.stream()
            .map(g -> getString(g, "toll_interval_id"))
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());

        return splitIntervals.equals(gantryIntervals);
    }
}
