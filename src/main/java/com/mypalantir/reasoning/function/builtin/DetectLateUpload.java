package com.mypalantir.reasoning.function.builtin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 检测是否存在门架流水接收时间晚于拆分时间的情况。
 * 输入：gantry_transactions (list), split_time (date string)
 */
public class DetectLateUpload extends AbstractBuiltinFunction {

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    @Override
    public String getName() { return "detect_late_upload"; }

    @Override
    public Object execute(List<Object> args) {
        List<Map<String, Object>> gantryTxs = asList(args.get(0));
        Object splitTimeArg = args.get(1);

        LocalDateTime splitTime = parseDateTime(splitTimeArg);
        if (splitTime == null) return false;

        for (Map<String, Object> tx : gantryTxs) {
            LocalDateTime receiveTime = parseDateTime(tx.get("receive_time"));
            if (receiveTime != null && receiveTime.isAfter(splitTime)) {
                return true; // 接收时间晚于拆分时间
            }
        }
        return false;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        String str = value.toString();
        for (DateTimeFormatter fmt : FORMATTERS) {
            try { return LocalDateTime.parse(str, fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
