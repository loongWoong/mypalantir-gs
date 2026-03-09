package com.mypalantir.reasoning.function.builtin;

import java.util.List;
import java.util.Map;

/**
 * 判定出口交易是否为本省单省ETC交易。
 * 输入：Passage 实例（含关联的 ExitTransaction 和 Media 数据）
 * 逻辑：卡网络号==本省 ∧ 省份标识==单省(multi_province==0) ∧ 支付方式==ETC(pay_type)
 */
public class IsSingleProvinceEtc extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "is_single_province_etc"; }

    @Override
    public Object execute(List<Object> args) {
        Map<String, Object> passage = asInstance(args.get(0));

        // 从 passage 的关联数据中获取出口交易
        @SuppressWarnings("unchecked")
        Map<String, Object> exitTx = (Map<String, Object>) passage.get("_exit_transaction");
        if (exitTx == null) return false;

        // multi_province == 0 表示单省
        int multiProvince = getInt(exitTx, "multi_province", -1);
        if (multiProvince != 0) return false;

        // 支付方式为 ETC（pay_type 通常为 1 表示 ETC）
        int payType = getInt(exitTx, "pay_type", -1);
        if (payType != 1) return false;

        // 从关联的 Media 获取卡网络号
        @SuppressWarnings("unchecked")
        Map<String, Object> media = (Map<String, Object>) passage.get("_media");
        if (media == null) return false;

        String cardNet = getString(media, "card_net");
        // 本省网络号判定（3701 为山东省示例，实际应可配置）
        return "3701".equals(cardNet);
    }
}
