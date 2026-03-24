package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class MappingService {
    @Autowired
    @Qualifier("instanceStorage")
    private IInstanceStorage instanceStorage;

    @Autowired
    private Loader loader;

    public String createMapping(String objectType, String tableId, Map<String, String> columnPropertyMappings, List<String> primaryKeyColumns) throws Loader.NotFoundException, IOException {
        // 验证对象类型存在
        loader.getObjectType(objectType);

        // 获取表信息以获取表名
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String tableName = (String) table.get("name");

        Map<String, Object> mappingData = new HashMap<>();
        mappingData.put("object_type", objectType);
        mappingData.put("table_id", tableId);
        mappingData.put("table_name", tableName); // 添加table_name属性
        mappingData.put("column_property_mappings", columnPropertyMappings);
        if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
            mappingData.put("primary_key_columns", primaryKeyColumns);
            // 保留旧字段以兼容旧代码（使用第一个主键列）
            mappingData.put("primary_key_column", primaryKeyColumns.get(0));
            System.out.println("[MappingService.createMapping] Saving primary_key_columns: " + primaryKeyColumns);
            System.out.println("[MappingService.createMapping] Saving primary_key_column (first): " + primaryKeyColumns.get(0));
        } else {
            System.out.println("[MappingService.createMapping] No primary key columns provided");
        }

        String mappingId = instanceStorage.createInstance("mapping", mappingData);
        
        // 验证保存的数据
        try {
            Map<String, Object> saved = instanceStorage.getInstance("mapping", mappingId);
            System.out.println("[MappingService.createMapping] Saved mapping - primary_key_columns: " + saved.get("primary_key_columns"));
            System.out.println("[MappingService.createMapping] Saved mapping - primary_key_column: " + saved.get("primary_key_column"));
        } catch (Exception e) {
            System.err.println("[MappingService.createMapping] Failed to verify saved data: " + e.getMessage());
        }
        
        return mappingId;
    }

    public Map<String, Object> getMapping(String mappingId) throws IOException {
        return instanceStorage.getInstance("mapping", mappingId);
    }

    public List<Map<String, Object>> getMappingsByObjectType(String objectType) throws IOException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("object_type", objectType);
        return instanceStorage.searchInstances("mapping", filters);
    }

    public List<Map<String, Object>> getMappingsByTable(String tableNameOrId) throws IOException {
        Map<String, Object> filters = new HashMap<>();
        // 先尝试按table_name查询
        filters.put("table_name", tableNameOrId);
        List<Map<String, Object>> result = instanceStorage.searchInstances("mapping", filters);
        // 如果没找到，再尝试按table_id查询
        if (result.isEmpty()) {
            filters.clear();
            filters.put("table_id", tableNameOrId);
            result = instanceStorage.searchInstances("mapping", filters);
        }
        return result;
    }

    /**
     * 通过 tableId 获取 table 实例（包含 database_id、name 等字段）
     */
    public Map<String, Object> getTableInstance(String tableId) throws IOException {
        return instanceStorage.getInstance("table", tableId);
    }

    public void updateMapping(String mappingId, Map<String, String> columnPropertyMappings, List<String> primaryKeyColumns) throws IOException {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("column_property_mappings", columnPropertyMappings);
        if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
            updateData.put("primary_key_columns", primaryKeyColumns);
            // 保留旧字段以兼容旧代码（使用第一个主键列）
            updateData.put("primary_key_column", primaryKeyColumns.get(0));
            System.out.println("[MappingService.updateMapping] Updating primary_key_columns: " + primaryKeyColumns);
            System.out.println("[MappingService.updateMapping] Updating primary_key_column (first): " + primaryKeyColumns.get(0));
        } else {
            // 如果 primaryKeyColumns 为空，清除主键列字段
            updateData.put("primary_key_columns", null);
            updateData.put("primary_key_column", null);
            System.out.println("[MappingService.updateMapping] Clearing primary key columns");
        }
        instanceStorage.updateInstance("mapping", mappingId, updateData);
        
        // 验证更新的数据
        try {
            Map<String, Object> saved = instanceStorage.getInstance("mapping", mappingId);
            System.out.println("[MappingService.updateMapping] Updated mapping - primary_key_columns: " + saved.get("primary_key_columns"));
            System.out.println("[MappingService.updateMapping] Updated mapping - primary_key_column: " + saved.get("primary_key_column"));
        } catch (Exception e) {
            System.err.println("[MappingService.updateMapping] Failed to verify updated data: " + e.getMessage());
        }
    }

    public void deleteMapping(String mappingId) throws IOException {
        instanceStorage.deleteInstance("mapping", mappingId);
    }
}
