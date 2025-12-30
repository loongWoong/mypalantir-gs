package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class MappingService {
    @Autowired
    private InstanceStorage instanceStorage;

    @Autowired
    private Loader loader;

    public String createMapping(String objectType, String tableId, Map<String, String> columnPropertyMappings, String primaryKeyColumn) throws Loader.NotFoundException, IOException {
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
        if (primaryKeyColumn != null) {
            mappingData.put("primary_key_column", primaryKeyColumn);
        }

        return instanceStorage.createInstance("mapping", mappingData);
    }

    public Map<String, Object> getMapping(String mappingId) throws IOException {
        return instanceStorage.getInstance("mapping", mappingId);
    }

    public List<Map<String, Object>> getMappingsByObjectType(String objectType) throws IOException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("object_type", objectType);
        return instanceStorage.searchInstances("mapping", filters);
    }

    public List<Map<String, Object>> getMappingsByTable(String tableId) throws IOException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("table_id", tableId);
        return instanceStorage.searchInstances("mapping", filters);
    }

    public void updateMapping(String mappingId, Map<String, String> columnPropertyMappings, String primaryKeyColumn) throws IOException {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("column_property_mappings", columnPropertyMappings);
        if (primaryKeyColumn != null) {
            updateData.put("primary_key_column", primaryKeyColumn);
        }
        instanceStorage.updateInstance("mapping", mappingId, updateData);
    }

    public void deleteMapping(String mappingId) throws IOException {
        instanceStorage.deleteInstance("mapping", mappingId);
    }
}
