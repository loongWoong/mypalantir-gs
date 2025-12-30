package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@Service
public class MappedDataService {
    @Autowired
    private MappingService mappingService;

    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private InstanceStorage instanceStorage;

    @Autowired
    private Loader loader;

    public InstanceStorage.ListResult queryMappedInstances(String objectType, String mappingId, int offset, int limit) throws IOException, SQLException, Loader.NotFoundException {
        // 获取映射关系
        Map<String, Object> mapping = mappingService.getMapping(mappingId);
        String tableId = (String) mapping.get("table_id");
        
        // 获取表信息
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String tableName = (String) table.get("name");
        
        // 获取数据库ID
        String databaseId = (String) table.get("database_id");
        
        // 获取列到属性的映射
        @SuppressWarnings("unchecked")
        Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
        
        // 构建查询SQL
        String sql = buildSelectQuery(tableName, columnPropertyMappings, offset, limit);
        
        // 执行查询
        List<Map<String, Object>> dbRows = databaseMetadataService.executeQuery(sql, databaseId);
        
        // 转换为实例对象
        List<Map<String, Object>> instances = new ArrayList<>();
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        for (Map<String, Object> row : dbRows) {
            Map<String, Object> instance = new HashMap<>();
            
            // 使用主键列作为ID，如果没有则生成UUID
            if (primaryKeyColumn != null && row.containsKey(primaryKeyColumn)) {
                instance.put("id", String.valueOf(row.get(primaryKeyColumn)));
            } else {
                instance.put("id", UUID.randomUUID().toString());
            }
            
            // 映射列到属性
            for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                String columnName = entry.getKey();
                String propertyName = entry.getValue();
                if (row.containsKey(columnName)) {
                    instance.put(propertyName, row.get(columnName));
                }
            }
            
            // 添加时间戳
            String now = java.time.Instant.now().toString();
            instance.put("created_at", now);
            instance.put("updated_at", now);
            
            instances.add(instance);
        }
        
        // 获取总数（需要执行COUNT查询）
        String countSql = "SELECT COUNT(*) as total FROM `" + tableName + "`";
        List<Map<String, Object>> countResult = databaseMetadataService.executeQuery(countSql, databaseId);
        long total = countResult.isEmpty() ? 0 : ((Number) countResult.get(0).get("total")).longValue();
        
        return new InstanceStorage.ListResult(instances, total);
    }

    private String buildSelectQuery(String tableName, Map<String, String> columnPropertyMappings, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // 添加所有映射的列
        List<String> columns = new ArrayList<>(columnPropertyMappings.keySet());
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            // 转义列名，防止SQL注入
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("`").append(columns.get(i)).append("`");
            }
        }
        
        // 转义表名
        sql.append(" FROM `").append(tableName).append("`");
        
        // MySQL分页语法：LIMIT offset, limit
        sql.append(" LIMIT ").append(offset).append(", ").append(limit);
        
        return sql.toString();
    }

    public void syncMappedDataToInstances(String objectType, String mappingId) throws IOException, SQLException, Loader.NotFoundException {
        // 获取映射关系
        Map<String, Object> mapping = mappingService.getMapping(mappingId);
        String tableId = (String) mapping.get("table_id");
        
        // 获取表信息
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String tableName = (String) table.get("name");
        
        // 获取数据库ID
        String databaseId = (String) table.get("database_id");
        
        // 获取列到属性的映射
        @SuppressWarnings("unchecked")
        Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
        
        // 构建查询SQL（查询所有数据，不使用分页）
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> columns = new ArrayList<>(columnPropertyMappings.keySet());
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("`").append(columns.get(i)).append("`");
            }
        }
        sql.append(" FROM `").append(tableName).append("`");
        
        // 执行查询
        List<Map<String, Object>> dbRows = databaseMetadataService.executeQuery(sql.toString(), databaseId);
        
        // 转换为实例并保存
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        for (Map<String, Object> row : dbRows) {
            Map<String, Object> instanceData = new HashMap<>();
            
            // 映射列到属性
            for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                String columnName = entry.getKey();
                String propertyName = entry.getValue();
                if (row.containsKey(columnName)) {
                    instanceData.put(propertyName, row.get(columnName));
                }
            }
            
            // 如果指定了主键列，使用它作为ID，否则创建新实例
            if (primaryKeyColumn != null && row.containsKey(primaryKeyColumn)) {
                String instanceId = String.valueOf(row.get(primaryKeyColumn));
                try {
                    // 尝试更新现有实例
                    Map<String, Object> existing = instanceStorage.getInstance(objectType, instanceId);
                    instanceStorage.updateInstance(objectType, instanceId, instanceData);
                } catch (IOException e) {
                    // 不存在则创建新实例
                    instanceStorage.createInstance(objectType, instanceData);
                }
            } else {
                // 创建新实例
                instanceStorage.createInstance(objectType, instanceData);
            }
        }
    }
}
