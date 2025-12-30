package com.mypalantir.service;

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
            result.tablesCreated++;
            
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
                
                if (existingColumns.isEmpty()) {
                    // 创建column实例
                    Map<String, Object> columnData = new HashMap<>();
                    columnData.put("name", columnName);
                    columnData.put("table_id", tableId);
                    columnData.put("data_type", columnInfo.get("data_type"));
                    columnData.put("nullable", columnInfo.get("nullable"));
                    columnData.put("is_primary_key", columnInfo.get("is_primary_key"));
                    if (columnInfo.get("remarks") != null) {
                        columnData.put("description", columnInfo.get("remarks"));
                    }
                    
                    instanceStorage.createInstance("column", columnData);
                    result.columnsCreated++;
                } else {
                    // 更新现有列信息
                    Map<String, Object> existingColumn = existingColumns.get(0);
                    String columnId = existingColumn.get("id").toString();
                    
                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("data_type", columnInfo.get("data_type"));
                    updateData.put("nullable", columnInfo.get("nullable"));
                    updateData.put("is_primary_key", columnInfo.get("is_primary_key"));
                    if (columnInfo.get("remarks") != null) {
                        updateData.put("description", columnInfo.get("remarks"));
                    }
                    
                    instanceStorage.updateInstance("column", columnId, updateData);
                    result.columnsUpdated++;
                }
            }
        }
        
        return result;
    }

    public static class SyncResult {
        public int tablesCreated = 0;
        public int columnsCreated = 0;
        public int columnsUpdated = 0;
        
        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("tables_created", tablesCreated);
            result.put("columns_created", columnsCreated);
            result.put("columns_updated", columnsUpdated);
            return result;
        }
    }
}
