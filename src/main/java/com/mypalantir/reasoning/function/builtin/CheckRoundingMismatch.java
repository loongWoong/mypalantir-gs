package com.mypalantir.reasoning.function.builtin;

import java.util.*;

/**
 * 检测是否因四舍五入取整产生金额差异。
 * 计算：分省实收 = min(round(门架累计应收 * discount_rate), 门架累计实收)
 * 输入：gantry_transactions (list), discount_rate (float, 默认0.95)
 */
public class CheckRoundingMismatch extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "check_rounding_mismatch"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> gantryTxs = asList(args.get(0));
        double discountRate = args.size() > 1 && args.get(1) instanceof Number
            ? ((Number) args.get(1)).doubleValue()
            : 0.95;

        // 累计应收和实收
        long totalPayFee = 0;  // 应收
        long totalFee = 0;     // 实收

        for (Map<String, Object> tx : gantryTxs) {
            totalPayFee += getLong(tx, "pay_fee", 0);
            totalFee += getLong(tx, "fee", 0);
        }

        // 计算取整后的金额
        long roundedFee = Math.round(totalPayFee * discountRate);
        long expectedFee = Math.min(roundedFee, totalFee);

        // 判断实际金额与预期金额是否因取整产生差异
        return expectedFee != totalFee && Math.abs(expectedFee - totalFee) <= Math.abs(roundedFee - totalPayFee);
    }
}
