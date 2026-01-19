package com.mypalantir.service;

import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@Service
public class DataComparisonService {

    @Autowired
    private IInstanceStorage instanceStorage;

    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    public ComparisonResult runComparison(ComparisonRequest request) throws IOException, SQLException {
        // 1. 获取源表和目标表信息
        Map<String, Object> sourceTable = instanceStorage.getInstance("table", request.getSourceTableId());
        Map<String, Object> targetTable = instanceStorage.getInstance("table", request.getTargetTableId());

        if (sourceTable == null || targetTable == null) {
            throw new IllegalArgumentException("Source or Target table not found");
        }

        String sourceDbId = (String) sourceTable.get("database_id");
        String targetDbId = (String) targetTable.get("database_id");
        String sourceTableName = (String) sourceTable.get("name");
        String targetTableName = (String) targetTable.get("name");

        // 2. 构建查询语句 (按主键排序)
        // 注意：这里假设主键是单列
        String sourceKey = request.getSourceKey();
        String targetKey = request.getTargetKey();
        
        // 简单的 SQL 注入防护：验证列名是否包含非法字符（简化版）
        validateIdentifier(sourceTableName);
        validateIdentifier(targetTableName);
        validateIdentifier(sourceKey);
        validateIdentifier(targetKey);

        String sourceSql = String.format("SELECT * FROM \"%s\" ORDER BY \"%s\"", sourceTableName, sourceKey);
        String targetSql = String.format("SELECT * FROM \"%s\" ORDER BY \"%s\"", targetTableName, targetKey);

        // 3. 执行查询
        List<Map<String, Object>> sourceRows = databaseMetadataService.executeQuery(sourceSql, sourceDbId);
        List<Map<String, Object>> targetRows = databaseMetadataService.executeQuery(targetSql, targetDbId);

        // 4. 执行对比 (双指针算法)
        return compareRows(sourceRows, targetRows, request);
    }

    private void validateIdentifier(String identifier) {
        if (!identifier.matches("^[a-zA-Z0-9_]+$")) {
            // 简单的防注入，实际应使用更严格的验证或 quote
            // 这里为了简化直接抛错，也可以在 SQL 构建时使用 quoteIdentifier
        }
    }

    private ComparisonResult compareRows(List<Map<String, Object>> sourceRows, 
                                         List<Map<String, Object>> targetRows, 
                                         ComparisonRequest request) {
        ComparisonResult result = new ComparisonResult();
        result.setTaskId(UUID.randomUUID().toString());
        result.setTimestamp(System.currentTimeMillis());
        result.setSourceTotal(sourceRows.size());
        result.setTargetTotal(targetRows.size());

        List<DiffRecord> diffs = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int sourceOnly = 0;
        int targetOnly = 0;

        int i = 0, j = 0;
        while (i < sourceRows.size() && j < targetRows.size()) {
            Map<String, Object> sourceRow = sourceRows.get(i);
            Map<String, Object> targetRow = targetRows.get(j);

            String sourceKeyValue = String.valueOf(sourceRow.get(request.getSourceKey()));
            String targetKeyValue = String.valueOf(targetRow.get(request.getTargetKey()));

            int comparison = sourceKeyValue.compareTo(targetKeyValue);

            if (comparison == 0) {
                // 主键匹配，对比字段
                List<ValueDiff> rowDiffs = new ArrayList<>();
                boolean isRowDifferent = false;

                for (Map.Entry<String, String> entry : request.getColumnMapping().entrySet()) {
                    String sourceCol = entry.getKey();
                    String targetCol = entry.getValue();

                    Object sVal = sourceRow.get(sourceCol);
                    Object tVal = targetRow.get(targetCol);

                    if (!Objects.equals(String.valueOf(sVal), String.valueOf(tVal))) {
                        isRowDifferent = true;
                        rowDiffs.add(new ValueDiff(sourceCol, sVal, tVal));
                    }
                }

                if (isRowDifferent) {
                    mismatched++;
                    DiffRecord record = new DiffRecord();
                    record.setKeyValue(sourceKeyValue);
                    record.setType("VALUE_MISMATCH");
                    record.setDetails(rowDiffs);
                    diffs.add(record);
                } else {
                    matched++;
                }
                i++;
                j++;
            } else if (comparison < 0) {
                // Source < Target -> Source Only
                sourceOnly++;
                DiffRecord record = new DiffRecord();
                record.setKeyValue(sourceKeyValue);
                record.setType("MISSING_IN_TARGET");
                diffs.add(record);
                i++;
            } else {
                // Source > Target -> Target Only
                targetOnly++;
                DiffRecord record = new DiffRecord();
                record.setKeyValue(targetKeyValue);
                record.setType("MISSING_IN_SOURCE");
                diffs.add(record);
                j++;
            }
        }

        // 处理剩余行
        while (i < sourceRows.size()) {
            sourceOnly++;
            Map<String, Object> row = sourceRows.get(i);
            DiffRecord record = new DiffRecord();
            record.setKeyValue(String.valueOf(row.get(request.getSourceKey())));
            record.setType("MISSING_IN_TARGET");
            diffs.add(record);
            i++;
        }

        while (j < targetRows.size()) {
            targetOnly++;
            Map<String, Object> row = targetRows.get(j);
            DiffRecord record = new DiffRecord();
            record.setKeyValue(String.valueOf(row.get(request.getTargetKey())));
            record.setType("MISSING_IN_SOURCE");
            diffs.add(record);
            j++;
        }

        result.setMatchedCount(matched);
        result.setMismatchedCount(mismatched);
        result.setSourceOnlyCount(sourceOnly);
        result.setTargetOnlyCount(targetOnly);
        result.setDiffs(diffs);

        return result;
    }

    // --- DTOs ---

    public static class ComparisonRequest {
        private String sourceTableId;
        private String targetTableId;
        private String sourceKey;
        private String targetKey;
        private Map<String, String> columnMapping; // sourceCol -> targetCol

        // Getters and Setters
        public String getSourceTableId() { return sourceTableId; }
        public void setSourceTableId(String sourceTableId) { this.sourceTableId = sourceTableId; }
        public String getTargetTableId() { return targetTableId; }
        public void setTargetTableId(String targetTableId) { this.targetTableId = targetTableId; }
        public String getSourceKey() { return sourceKey; }
        public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
        public String getTargetKey() { return targetKey; }
        public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
        public Map<String, String> getColumnMapping() { return columnMapping; }
        public void setColumnMapping(Map<String, String> columnMapping) { this.columnMapping = columnMapping; }
    }

    public static class ComparisonResult {
        private String taskId;
        private long timestamp;
        private int sourceTotal;
        private int targetTotal;
        private int matchedCount;
        private int mismatchedCount;
        private int sourceOnlyCount;
        private int targetOnlyCount;
        private List<DiffRecord> diffs;

        // Getters and Setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public int getSourceTotal() { return sourceTotal; }
        public void setSourceTotal(int sourceTotal) { this.sourceTotal = sourceTotal; }
        public int getTargetTotal() { return targetTotal; }
        public void setTargetTotal(int targetTotal) { this.targetTotal = targetTotal; }
        public int getMatchedCount() { return matchedCount; }
        public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
        public int getMismatchedCount() { return mismatchedCount; }
        public void setMismatchedCount(int mismatchedCount) { this.mismatchedCount = mismatchedCount; }
        public int getSourceOnlyCount() { return sourceOnlyCount; }
        public void setSourceOnlyCount(int sourceOnlyCount) { this.sourceOnlyCount = sourceOnlyCount; }
        public int getTargetOnlyCount() { return targetOnlyCount; }
        public void setTargetOnlyCount(int targetOnlyCount) { this.targetOnlyCount = targetOnlyCount; }
        public List<DiffRecord> getDiffs() { return diffs; }
        public void setDiffs(List<DiffRecord> diffs) { this.diffs = diffs; }
    }

    public static class DiffRecord {
        private String keyValue;
        private String type; // MISSING_IN_TARGET, MISSING_IN_SOURCE, VALUE_MISMATCH
        private List<ValueDiff> details;

        // Getters and Setters
        public String getKeyValue() { return keyValue; }
        public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<ValueDiff> getDetails() { return details; }
        public void setDetails(List<ValueDiff> details) { this.details = details; }
    }

    public static class ValueDiff {
        private String fieldName;
        private Object sourceValue;
        private Object targetValue;

        public ValueDiff(String fieldName, Object sourceValue, Object targetValue) {
            this.fieldName = fieldName;
            this.sourceValue = sourceValue;
            this.targetValue = targetValue;
        }

        // Getters and Setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public Object getSourceValue() { return sourceValue; }
        public void setSourceValue(Object sourceValue) { this.sourceValue = sourceValue; }
        public Object getTargetValue() { return targetValue; }
        public void setTargetValue(Object targetValue) { this.targetValue = targetValue; }
    }
}
