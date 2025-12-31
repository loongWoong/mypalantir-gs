package com.mypalantir.repository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 实例存储接口
 * 支持文件存储和 Neo4j 存储两种实现
 */
public interface IInstanceStorage {
    /**
     * 创建实例
     */
    String createInstance(String objectType, Map<String, Object> data) throws IOException;

    /**
     * 使用指定 ID 创建实例
     */
    String createInstanceWithId(String objectType, String id, Map<String, Object> data) throws IOException;

    /**
     * 获取实例
     */
    Map<String, Object> getInstance(String objectType, String id) throws IOException;

    /**
     * 更新实例
     */
    void updateInstance(String objectType, String id, Map<String, Object> data) throws IOException;

    /**
     * 删除实例
     */
    void deleteInstance(String objectType, String id) throws IOException;

    /**
     * 列出实例
     */
    InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException;

    /**
     * 搜索实例
     */
    List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException;

    /**
     * 批量获取实例
     */
    Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException;

    /**
     * 批量获取多个对象类型的实例
     */
    Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws IOException;
}
