package com.mypalantir.reasoning.function.builtin;

import java.util.*;

/**
 * 检查相邻门架的HEX编码是否首尾衔接。
 * 即：前一门架的 snapshot_gantry_hex == 后一门架的 snapshot_last_gantry_hex
 * 输入：gantry_transactions (list)
 */
public class CheckGantryHexContinuity extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "check_gantry_hex_continuity"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> gantryTxs = asList(args.get(0));
        if (gantryTxs.size() <= 1) return true;

        // 按 gantry_order_num 排序
        List<Map<String, Object>> sorted = new ArrayList<>(gantryTxs);
        sorted.sort(Comparator.comparingInt(t -> getInt(t, "gantry_order_num", 0)));

        for (int i = 1; i < sorted.size(); i++) {
            String prevHex = getString(sorted.get(i - 1), "snapshot_gantry_hex");
            String currLastHex = getString(sorted.get(i), "snapshot_last_gantry_hex");

            if (prevHex == null || !prevHex.equals(currLastHex)) {
                return false; // HEX 不连续
            }
        }
        return true;
    }
}
