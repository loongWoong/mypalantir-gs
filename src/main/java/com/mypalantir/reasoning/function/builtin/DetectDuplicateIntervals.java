package com.mypalantir.reasoning.function.builtin;

import java.util.*;

/**
 * 检测门架流水中是否存在重复的收费单元（同一门架重复上报）。
 * 输入：gantry_transactions (list)
 */
public class DetectDuplicateIntervals extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "detect_duplicate_intervals"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> gantryTxs = asList(args.get(0));

        Set<String> seen = new HashSet<>();
        for (Map<String, Object> tx : gantryTxs) {
            String intervalId = getString(tx, "toll_interval_id");
            if (intervalId != null && !seen.add(intervalId)) {
                return true; // 发现重复
            }
        }
        return false;
    }
}
