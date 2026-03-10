package com.mypalantir.reasoning.function.builtin;

import java.util.List;
import java.util.Map;

/**
 * 判定通行介质为OBU且计费方式为1。
 * 输入：Passage 实例（含关联的 Media 数据）
 */
public class IsObuBillingMode1 extends AbstractBuiltinFunction {

    @Override
    public String getName() { return "is_obu_billing_mode1"; }

    @Override
    public Object execute(List<Object> args) {
        Map<String, Object> passage = asInstance(args.get(0));

        @SuppressWarnings("unchecked")
        Map<String, Object> media = (Map<String, Object>) passage.get("_media");
        if (media == null) return false;

        // media_type == 1 表示 OBU
        int mediaType = getInt(media, "media_type", -1);
        return mediaType == 1;
    }
}
