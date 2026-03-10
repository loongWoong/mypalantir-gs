package com.mypalantir.reasoning.function.builtin;

import java.util.*;

/**
 * 逐收费单元比较拆分金额与门架流水金额是否一致。
 * 输入：split_details (list), gantry_transactions (list)
 */
public class CheckFeeDetailConsistency extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "check_fee_detail_consistency"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> splitDetails = asList(args.get(0));
        List<Map<String, Object>> gantryTxs = asList(args.get(1));

        // 按收费单元建立门架交易费用 map
        Map<String, Double> gantryFeeMap = new HashMap<>();
        for (Map<String, Object> tx : gantryTxs) {
            String intervalId = getString(tx, "toll_interval_id");
            double fee = getDouble(tx, "fee", 0.0);
            if (intervalId != null) {
                gantryFeeMap.merge(intervalId, fee, Double::sum);
            }
        }

        // 逐收费单元对比
        for (Map<String, Object> split : splitDetails) {
            String intervalId = getString(split, "interval_id");
            double splitFee = getDouble(split, "toll_interval_fee", 0.0);
            Double gantryFee = gantryFeeMap.get(intervalId);

            if (gantryFee == null || Math.abs(splitFee - gantryFee) > 0.01) {
                return false;
            }
        }
        return true;
    }
}
