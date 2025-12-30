package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableSyncService {
    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private InstanceStorage instanceStorage;

    @Autowired
    private LinkService linkService;

    public SyncResult syncTablesFromDatabase(String databaseId) throws IOException, SQLException {
        SyncResult result = new SyncResult();
        
        // 获取数据库实例
        Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
        String dbName = (String) database.get("database_name");
        
        // 获取所有表
        List<Map<String, Object>> tables = databaseMetadataService.getTables(databaseId);
        
        for (Map<String, Object> tableInfo : tables) {
            String tableName = (String) tableInfo.get("name");
            String schemaName = (String) tableInfo.get("schema");
            
            // 创建或获取table实例
            String tableId = databaseService.getOrCreateTable(databaseId, tableName, schemaName);
            
            // 检查表是否是新创建的（通过检查是否已有链接）
            boolean isNewTable = !tableLinkExists(databaseId, tableId);
            if (isNewTable) {
                result.tablesCreated++;
            }
            
            // 创建或检查 database_has_table 链接
            try {
                if (!tableLinkExists(databaseId, tableId)) {
                    createDatabaseTableLink(databaseId, tableId);
                    result.linksCreated++;
                }
            } catch (Exception e) {
                // 链接创建失败，忽略错误
            }
            
            // 获取表的列信息
            List<Map<String, Object>> columns = databaseMetadataService.getColumns(databaseId, tableName);
            
            // 为每个列创建column实例
            for (Map<String, Object> columnInfo : columns) {
                String columnName = (String) columnInfo.get("name");
                
                // 检查列是否已存在
                Map<String, Object> filters = new HashMap<>();
                filters.put("name", columnName);
                filters.put("table_id", tableId);
                List<Map<String, Object>> existingColumns = instanceStorage.searchInstances("column", filters);
                
                String columnId;
                boolean isNewColumn = false;
                
                if (existingColumns.isEmpty()) {
                    // 创建column实例
                    Map<String, Object> columnData = new HashMap<>();
                    columnData.put("name", columnName);
                    columnData.put("table_id", tableId);
                    columnData.put("table_name", tableName); // 添加table_name属性
                    columnData.put("data_type", columnInfo.get("data_type"));
                    columnData.put("nullable", columnInfo.get("nullable"));
                    columnData.put("is_primary_key", columnInfo.get("is_primary_key"));
                    if (columnInfo.get("remarks") != null) {
                        columnData.put("description", columnInfo.get("remarks"));
                    }
                    
                    columnId = instanceStorage.createInstance("column", columnData);
                    result.columnsCreated++;
                    isNewColumn = true;
                } else {
                    // 更新现有列信息
                    Map<String, Object> existingColumn = existingColumns.get(0);
                    columnId = existingColumn.get("id").toString();
                    
                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("table_name", tableName); // 更新table_name属性
                    updateData.put("data_type", columnInfo.get("data_type"));
                    updateData.put("nullable", columnInfo.get("nullable"));
                    updateData.put("is_primary_key", columnInfo.get("is_primary_key"));
                    if (columnInfo.get("remarks") != null) {
                        updateData.put("description", columnInfo.get("remarks"));
                    }
                    
                    instanceStorage.updateInstance("column", columnId, updateData);
                    result.columnsUpdated++;
                }
                
                // 创建或检查 table_has_column 链接
                try {
                    if (!columnLinkExists(tableId, columnId)) {
                        createTableColumnLink(tableId, columnId);
                        result.linksCreated++;
                    }
                } catch (Exception e) {
                    // 链接创建失败，忽略错误
                }
            }
        }
        
        return result;
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
