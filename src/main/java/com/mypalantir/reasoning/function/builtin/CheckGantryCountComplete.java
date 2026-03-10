package com.mypalantir.reasoning.function.builtin;

import java.util.*;

/**
 * 检查门架流水数量是否等于最后一条门架的顺序号（交易成功数）。
 * 输入：gantry_transactions (list)
 */
public class CheckGantryCountComplete extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "check_gantry_count_complete"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> gantryTxs = asList(args.get(0));
        if (gantryTxs.isEmpty()) return false;

        // 找到最大顺序号
        int maxOrder = gantryTxs.stream()
            .mapToInt(t -> getInt(t, "gantry_order_num", 0))
            .max()
            .orElse(0);

        return gantryTxs.size() == maxOrder;
    }
}
