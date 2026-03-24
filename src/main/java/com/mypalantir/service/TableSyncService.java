package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TableSyncService {
    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private IInstanceStorage instanceStorage;

    @Autowired
    private LinkService linkService;

    public SyncResult syncTablesFromDatabase(String databaseId) throws IOException, SQLException {
        SyncResult result = new SyncResult();

        try (Connection conn = databaseMetadataService.getConnectionForDatabase(databaseId)) {
            // 单连接完成全流程，避免连接泄漏和多次建连
            List<Map<String, Object>> tables = databaseMetadataService.getTables(conn, databaseId);

            // 预先解析所有 tableId，避免主循环重复调用
            Map<String, String> tableKeyToId = new HashMap<>();
            for (Map<String, Object> t : tables) {
                String tableName = (String) t.get("name");
                String schemaName = (String) t.get("schema");
                String tableKey = (schemaName != null && !schemaName.isEmpty()) ? schemaName + "." + tableName : tableName;
                tableKeyToId.put(tableKey, databaseService.getOrCreateTable(databaseId, tableName, schemaName));
            }

            Set<String> existingTableTargetIds = loadExistingTableLinkTargets(databaseId);
            Map<String, Set<String>> existingColumnLinksByTable = loadExistingColumnLinksByTable(tableKeyToId);

            // Oracle 等支持时批量获取所有表列，减少往返
            Map<String, List<Map<String, Object>>> columnsByTable = databaseMetadataService.getAllColumnsBatch(conn, databaseId, tables);

            for (Map<String, Object> tableInfo : tables) {
                String tableName = (String) tableInfo.get("name");
                String schemaName = (String) tableInfo.get("schema");
                String tableKey = (schemaName != null && !schemaName.isEmpty()) ? schemaName + "." + tableName : tableName;

                String tableId = tableKeyToId.get(tableKey);
                if (!existingTableTargetIds.contains(tableId)) {
                    result.tablesCreated++;
                }

                try {
                    if (!existingTableTargetIds.contains(tableId)) {
                        createDatabaseTableLink(databaseId, tableId);
                        result.linksCreated++;
                        existingTableTargetIds.add(tableId);
                    }
                } catch (Exception e) {
                    // 链接创建失败，忽略错误
                }

                List<Map<String, Object>> columns = columnsByTable.getOrDefault(tableKey, List.of());

                for (Map<String, Object> columnInfo : columns) {
                    String columnName = (String) columnInfo.get("name");

                    Map<String, Object> filters = new HashMap<>();
                    filters.put("name", columnName);
                    filters.put("table_id", tableId);
                    List<Map<String, Object>> existingColumns = instanceStorage.searchInstances("column", filters);

                    String columnId;
                    Set<String> existingColsForTable = existingColumnLinksByTable.getOrDefault(tableId, Set.of());

                    if (existingColumns.isEmpty()) {
                        Map<String, Object> columnData = new HashMap<>();
                        columnData.put("name", columnName);
                        columnData.put("table_id", tableId);
                        columnData.put("table_name", tableName);
                        columnData.put("data_type", columnInfo.get("data_type"));
                        columnData.put("nullable", columnInfo.get("nullable"));
                        columnData.put("is_primary_key", columnInfo.get("is_primary_key"));
                        if (columnInfo.get("remarks") != null) {
                            columnData.put("description", columnInfo.get("remarks"));
                        }

                        columnId = instanceStorage.createInstance("column", columnData);
                        result.columnsCreated++;
                    } else {
                        Map<String, Object> existingColumn = existingColumns.get(0);
                        columnId = existingColumn.get("id").toString();

                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("table_name", tableName);
                        updateData.put("data_type", columnInfo.get("data_type"));
                        updateData.put("nullable", columnInfo.get("nullable"));
                        updateData.put("is_primary_key", columnInfo.get("is_primary_key"));
                        if (columnInfo.get("remarks") != null) {
                            updateData.put("description", columnInfo.get("remarks"));
                        }

                        instanceStorage.updateInstance("column", columnId, updateData);
                        result.columnsUpdated++;
                    }

                    try {
                        if (!existingColsForTable.contains(columnId)) {
                            createTableColumnLink(tableId, columnId);
                            result.linksCreated++;
                            existingColumnLinksByTable.computeIfAbsent(tableId, k -> new HashSet<>()).add(columnId);
                        }
                    } catch (Exception e) {
                        // 链接创建失败，忽略错误
                    }
                }
            }
        }

        return result;
    }

    private Set<String> loadExistingTableLinkTargets(String databaseId) {
        Set<String> targetIds = new HashSet<>();
        try {
            List<Map<String, Object>> links = linkService.getLinksBySource("database_has_table", databaseId);
            for (Map<String, Object> link : links) {
                Object tid = link.get("target_id");
                if (tid != null) targetIds.add(tid.toString());
            }
        } catch (Exception ignored) {
        }
        return targetIds;
    }

    private Map<String, Set<String>> loadExistingColumnLinksByTable(Map<String, String> tableKeyToId) {
        Map<String, Set<String>> byTable = new HashMap<>();
        for (String tableId : tableKeyToId.values()) {
            try {
                List<Map<String, Object>> links = linkService.getLinksBySource("table_has_column", tableId);
                Set<String> colIds = new HashSet<>();
                for (Map<String, Object> link : links) {
                    Object cid = link.get("target_id");
                    if (cid != null) colIds.add(cid.toString());
                }
                byTable.put(tableId, colIds);
            } catch (Exception ignored) {
            }
        }
        return byTable;
    }

    private boolean tableLinkExists(String databaseId, String tableId) {
        try {
            List<Map<String, Object>> existingLinks = linkService.getLinksBySource("database_has_table", databaseId);
            for (Map<String, Object> link : existingLinks) {
                if (tableId.equals(link.get("target_id"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean columnLinkExists(String tableId, String columnId) {
        try {
            List<Map<String, Object>> existingLinks = linkService.getLinksBySource("table_has_column", tableId);
            for (Map<String, Object> link : existingLinks) {
                if (columnId.equals(link.get("target_id"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void createDatabaseTableLink(String databaseId, String tableId) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 创建 database_has_table 链接
        linkService.createLink("database_has_table", databaseId, tableId, new HashMap<>());
    }

    private void createTableColumnLink(String tableId, String columnId) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 创建 table_has_column 链接
        linkService.createLink("table_has_column", tableId, columnId, new HashMap<>());
    }

    public static class SyncResult {
        public int tablesCreated = 0;
        public int columnsCreated = 0;
        public int columnsUpdated = 0;
        public int linksCreated = 0;
        
        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("tables_created", tablesCreated);
            result.put("columns_created", columnsCreated);
            result.put("columns_updated", columnsUpdated);
            result.put("links_created", linksCreated);
            return result;
        }
    }
}
