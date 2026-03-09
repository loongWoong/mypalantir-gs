package com.mypalantir.reasoning.function.builtin;

import java.util.*;

/**
 * 检查相邻门架交易的交易后余额是否等于下一笔的交易前余额。
 * 输入：gantry_transactions (list)
 */
public class CheckBalanceContinuity extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "check_balance_continuity"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> gantryTxs = asList(args.get(0));
        if (gantryTxs.size() <= 1) return true;

        // 按 gantry_order_num 排序
        List<Map<String, Object>> sorted = new ArrayList<>(gantryTxs);
        sorted.sort(Comparator.comparingInt(t -> getInt(t, "gantry_order_num", 0)));

        for (int i = 1; i < sorted.size(); i++) {
            long prevAfter = getLong(sorted.get(i - 1), "balance_after", Long.MIN_VALUE);
            long currBefore = getLong(sorted.get(i), "balance_before", Long.MAX_VALUE);

            if (prevAfter != currBefore) {
                return false; // 余额不连续
            }
        }
        return true;
    }
}
