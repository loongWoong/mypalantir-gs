package com.mypalantir.service;

import com.mypalantir.config.Config;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {
    @Autowired
    private IInstanceStorage instanceStorage;

    @Autowired
    private Config config;

    @Autowired
    private Loader loader;

    public String getOrCreateDefaultDatabase() throws IOException {
        // 查找是否已存在默认数据库
        Map<String, Object> filters = new HashMap<>();
        filters.put("name", "default");
        List<Map<String, Object>> databases = instanceStorage.searchInstances("database", filters);
        
        if (!databases.isEmpty()) {
            return databases.get(0).get("id").toString();
        }

        // 创建默认数据库实例
        Map<String, Object> dbData = new HashMap<>();
        dbData.put("name", "default");
        dbData.put("type", config.getDbType());
        dbData.put("host", config.getDbHost());
        dbData.put("port", config.getDbPort());
        dbData.put("database_name", config.getDbName());
        dbData.put("username", config.getDbUser());
        // 不存储密码到实例中，从配置读取

        return instanceStorage.createInstance("database", dbData);
    }

    public Map<String, Object> getDatabase(String databaseId) throws IOException {
        return instanceStorage.getInstance("database", databaseId);
    }

    public String createTable(String databaseId, String tableName, String schemaName) throws IOException {
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("name", tableName);
        tableData.put("database_id", databaseId);
        if (schemaName != null) {
            tableData.put("schema_name", schemaName);
        }
        return instanceStorage.createInstance("table", tableData);
    }

    public String getOrCreateTable(String databaseId, String tableName, String schemaName) throws IOException {
        // 查找是否已存在表
        Map<String, Object> filters = new HashMap<>();
        filters.put("name", tableName);
        filters.put("database_id", databaseId);
        List<Map<String, Object>> tables = instanceStorage.searchInstances("table", filters);
        
        if (!tables.isEmpty()) {
            return tables.get(0).get("id").toString();
        }

        // 创建表实例
        return createTable(databaseId, tableName, schemaName);
    }

    public List<Map<String, Object>> getAllDatabases() throws IOException {
        return instanceStorage.searchInstances("database", new HashMap<>());
    }
}
