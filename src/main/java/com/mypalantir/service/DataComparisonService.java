package com.mypalantir.service;

import com.mypalantir.repository.IInstanceStorage;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@Service
public class DataComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(DataComparisonService.class);

    @Autowired
    private IInstanceStorage instanceStorage;

    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    public ComparisonResult runComparison(ComparisonRequest request) throws IOException, SQLException {
        // 1. 获取源表和目标表信息（通过 Primary instanceStorage，hybrid 模式下会路由到图数据库）
        Map<String, Object> sourceTable = instanceStorage.getInstance("table", request.getSourceTableId());
        Map<String, Object> targetTable = instanceStorage.getInstance("table", request.getTargetTableId());

        if (sourceTable == null || targetTable == null) {
            throw new IllegalArgumentException("Source or Target table not found");
        }

        String sourceDbId = (String) sourceTable.get("database_id");
        String targetDbId = (String) targetTable.get("database_id");
        String sourceTableName = (String) sourceTable.get("name");
        String targetTableName = (String) targetTable.get("name");

        // 2. 使用 Calcite 联邦查询执行跨库查询（按主键排序）
        String sourceKey = request.getSourceKey();
        String targetKey = request.getTargetKey();

        List<Map<String, Object>> sourceRows = executeWithCalcite(sourceDbId, sourceTableName, sourceKey);
        List<Map<String, Object>> targetRows = executeWithCalcite(targetDbId, targetTableName, targetKey);

        // 3. 执行对比（双指针算法）
        return compareRows(sourceRows, targetRows, request);
    }

    /**
     * 使用 Calcite 联邦查询执行单库查询，按主键排序。
     *
     * <p>关键设计：
     * <ul>
     *   <li>复用 databaseMetadataService 的 HikariCP 连接池作为底层 DataSource，
     *       Calcite JdbcSchema 只做 SQL 翻译和执行，不维护独立连接池。</li>
     *   <li>JdbcSchema.create 第4参数传入实际 catalog（MySQL 中即数据库名），
     *       第5参数传 null（使用默认 schema），避免全库扫描。</li>
     *   <li>Calcite 默认将列名转大写，查询结果用大小写不敏感的 Map 存储，
     *       保证后续用原始列名取值时不会因大小写不一致而失败。</li>
     * </ul>
     */
    private List<Map<String, Object>> executeWithCalcite(
            String databaseId, String tableName, String sortKey) throws SQLException, IOException {

        // 从 databaseMetadataService 获取该库的实际数据库名（用作 Calcite catalog）
        String catalogName = databaseMetadataService.getDatabaseNameById(databaseId);
        DataSource dataSource = databaseMetadataService.getDataSourceForDatabase(databaseId);

        Connection calciteConnection = null;
        try {
            calciteConnection = DriverManager.getConnection("jdbc:calcite:");
            CalciteConnection calciteConn = calciteConnection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConn.getRootSchema();

            // 挂载目标数据库为 Calcite 子 Schema
            // catalog = 实际数据库名（MySQL 中 catalog == schema），schema = null（使用默认）
            // 这样 JdbcSchema 只扫描该数据库下的表，不会全库扫描
            rootSchema.add("DB", JdbcSchema.create(rootSchema, "DB", dataSource, catalogName, null));

            // Calcite 对标识符大小写敏感，表名和列名用双引号包裹保留原始大小写
            String sql = String.format(
                "SELECT * FROM \"DB\".\"%s\" ORDER BY \"%s\"", tableName, sortKey);

            logger.debug("[DataComparison] Executing Calcite SQL: {}", sql);

            List<Map<String, Object>> results = new ArrayList<>();
            try (Statement stmt = calciteConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 构建列名映射：Calcite 可能将列名转大写，记录原始列名 -> Calcite列名 的对应关系
                // 使用大小写不敏感的 TreeMap 存储每行数据，保证后续用任意大小写取值都能命中
                while (rs.next()) {
                    Map<String, Object> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    for (int i = 1; i <= columnCount; i++) {
                        String colName = metaData.getColumnLabel(i);
                        row.put(colName, rs.getObject(i));
                    }
                    results.add(row);
                }
            }
            return results;

        } finally {
            if (calciteConnection != null) {
                try { calciteConnection.close(); } catch (SQLException ignored) {}
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
