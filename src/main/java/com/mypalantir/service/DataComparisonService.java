package com.mypalantir.service;

import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
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

        // 2. 使用 Calcite 执行跨库查询 (按主键排序)
        String sourceKey = request.getSourceKey();
        String targetKey = request.getTargetKey();
        
        List<Map<String, Object>> sourceRows = executeWithCalcite(sourceDbId, sourceTableName, sourceKey);
        List<Map<String, Object>> targetRows = executeWithCalcite(targetDbId, targetTableName, targetKey);

        // 3. 执行对比 (双指针算法)
        return compareRows(sourceRows, targetRows, request);
    }

    /**
     * 使用 Calcite 执行查询
     * 自动处理不同数据库的方言和引号问题
     */
    private List<Map<String, Object>> executeWithCalcite(String databaseId, String tableName, String sortKey) throws SQLException, IOException {
        Connection calciteConnection = null;
        try {
            // 1. 创建 Calcite 根 Schema
            calciteConnection = DriverManager.getConnection("jdbc:calcite:");
            CalciteConnection calciteConn = calciteConnection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConn.getRootSchema();

            // 2. 获取目标数据库的 DataSource
            DataSource dataSource = databaseMetadataService.getDataSourceForDatabase(databaseId);

            // 3. 将目标数据库挂载为 Calcite 的子 Schema (命名为 "DB")
            // null, null 表示加载 catalog/schema 下的所有表 (或者默认 schema)
            // JdbcSchema 会自动扫描表并处理方言
            SchemaPlus dbSchema = rootSchema.add("DB", JdbcSchema.create(rootSchema, "DB", dataSource, null, null));

            // 4. 构建 Calcite SQL
            // 注意：Calcite 中引用表名需要用双引号，且大小写敏感（取决于 JdbcSchema 的配置）
            // 这里假设 JdbcSchema 能正确识别表名
            // 如果 tableName 在 MySQL 中是小写，Calcite 可能也需要小写引用
            // 我们尝试使用 quoteIdentifier
            
            // 为了确保表名正确，我们可以先检查 schema 中的表名
            // Set<String> tableNames = dbSchema.getTableNames();
            // ... 但为了简单，先假设名称匹配
            
            String sql = String.format("SELECT * FROM \"DB\".\"%s\" ORDER BY \"%s\"", tableName, sortKey);
            
            System.out.println("[DataComparison] Executing Calcite SQL: " + sql);

            // 5. 执行查询
            List<Map<String, Object>> results = new ArrayList<>();
            try (Statement stmt = calciteConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        // Calcite 返回的列名可能大写或保持原样
                        // 我们使用 getColumnLabel 获取别名或列名
                        String colName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(colName, value);
                    }
                    results.add(row);
                }
            }
            return results;

        } finally {
            if (calciteConnection != null) {
                calciteConnection.close();
            }
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
