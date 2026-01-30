package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mypalantir.service.QueryService;
@Service
public class InstanceService {
    private final IInstanceStorage storage;
    private final Loader loader;
    private final DataValidator validator;
    private final QueryService queryService;
    private static final Logger logger = LoggerFactory.getLogger(InstanceService.class);

    public InstanceService(IInstanceStorage storage, Loader loader, DataValidator validator, QueryService queryService) {
        this.storage = storage;
        this.loader = loader;
        this.validator = validator;
        this.queryService = queryService;
    }

    public String createInstance(String objectType, Map<String, Object> data) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证对象类型存在
        loader.getObjectType(objectType);

        // 验证数据
        validator.validateInstanceData(objectType, data);

        // 创建实例
        return storage.createInstance(objectType, data);
    }

    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        // 首先尝试使用原始名称
        try {
            return storage.getInstance(objectType, id);
        } catch (IOException e) {
            // 如果失败，尝试查找schema中定义的正确对象类型名称（忽略大小写）
            try {
                // 查找所有对象类型，找到匹配的（忽略大小写）
                List<com.mypalantir.meta.ObjectType> allTypes = loader.listObjectTypes();
                com.mypalantir.meta.ObjectType matchedType = null;
                for (com.mypalantir.meta.ObjectType ot : allTypes) {
                    if (ot.getName().equalsIgnoreCase(objectType)) {
                        matchedType = ot;
                        break;
                    }
                }
                
                if (matchedType != null && !matchedType.getName().equals(objectType)) {
                    // 使用schema中定义的准确名称重试
                    try {
                        return storage.getInstance(matchedType.getName(), id);
                    } catch (IOException ex) {
                        // 如果还是失败，抛出原始异常
                        throw e;
                    }
                } else {
                    // 如果找不到匹配的对象类型，或者名称已经匹配，抛出原始异常
                    throw e;
                }
            } catch (IOException ex) {
                // 如果是IOException，直接抛出
                throw ex;
            } catch (Exception ex) {
                // 如果查找过程中出错，抛出原始异常
                throw e;
            }
        }
    }

    public void updateInstance(String objectType, String id, Map<String, Object> data) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证对象类型存在
        loader.getObjectType(objectType);

        // 验证数据
        validator.validateInstanceData(objectType, data);

        // 更新实例
        storage.updateInstance(objectType, id, data);
    }

    public void deleteInstance(String objectType, String id) throws IOException {
        storage.deleteInstance(objectType, id);
    }

    /**
     * 查询实例存储（同步数据）
     * 严格查询界限：只查询同步表和Neo4j数据，不查询原始表
     * 
     * @param objectType 对象类型
     * @param offset 偏移量
     * @param limit 限制数量
     * @param filters 过滤条件
     * @return 查询结果
     */
    public com.mypalantir.repository.InstanceStorage.ListResult listInstances(String objectType, int offset, int limit, Map<String, Object> filters) throws Loader.NotFoundException, IOException {
        logger.info("[InstanceService] ========== INSTANCE STORAGE QUERY (同步数据查询) ==========");
        logger.info("[InstanceService] Query mode: INSTANCE_STORAGE (同步表和Neo4j查询)");
        logger.info("[InstanceService] listInstances called: objectType={}, offset={}, limit={}, filters={}", 
            objectType, offset, limit, filters);
        logger.info("[InstanceService] Storage implementation: {}", storage.getClass().getSimpleName());
        logger.info("[InstanceService] Data source: SYNC TABLE (同步表) + NEO4J (图数据库)，不查询原始表");
        
        // 实例存储查询：直接使用 storage.listInstances()，它会查询同步表和Neo4j
        // 严格查询界限：
        // 1. 不查询原始表（原始表查询应该通过 MappedDataService.queryMappedInstances() 进行）
        // 2. 只查询同步表（表名 = 模型名，在默认数据库中）和Neo4j
        
        com.mypalantir.repository.InstanceStorage.ListResult result;
        
        if (filters != null && !filters.isEmpty()) {
            logger.info("[InstanceService] Using searchInstances with filters (querying SYNC TABLE or NEO4J)");
            List<Map<String, Object>> instances = storage.searchInstances(objectType, filters);
            int total = instances.size();
            int end = Math.min(offset + limit, instances.size());
            if (offset >= instances.size()) {
                result = new com.mypalantir.repository.InstanceStorage.ListResult(List.of(), total);
            } else {
                result = new com.mypalantir.repository.InstanceStorage.ListResult(instances.subList(offset, end), total);
            }
        } else {
            logger.info("[InstanceService] Using listInstances without filters (querying SYNC TABLE or NEO4J)");
            result = storage.listInstances(objectType, offset, limit);
        }
        
        logger.info("[InstanceService] Instance storage query result: objectType={}, itemsCount={}, total={}, storageClass={}", 
            objectType, result.getItems().size(), result.getTotal(), storage.getClass().getSimpleName());
        
        // 最终数据源分析
        if (result.getItems().isEmpty()) {
            logger.info("[InstanceService] ========== DATA SOURCE ANALYSIS: Query returned EMPTY - sync table and Neo4j both have no data for objectType={} ==========", objectType);
        } else {
            logger.info("[InstanceService] ========== DATA SOURCE ANALYSIS: Query returned {} instances for objectType={} - data from SYNC TABLE or NEO4J ==========", 
                result.getItems().size(), objectType);
            logger.info("[InstanceService] First instance sample keys: {}", 
                result.getItems().isEmpty() ? "N/A" : result.getItems().get(0).keySet());
        }
        logger.info("[InstanceService] ========== INSTANCE STORAGE QUERY END ==========");
        
        return result;
    }

    /**
     * 批量获取实例
     * @param objectType 对象类型
     * @param ids 实例ID列表
     * @return 实例Map，key为ID，value为实例数据（如果不存在则为null）
     */
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws Loader.NotFoundException, IOException {
        loader.getObjectType(objectType);
        return storage.getInstancesBatch(objectType, ids);
    }

    /**
     * 批量获取多个对象类型的实例
     * @param typeIdMap key为对象类型，value为该类型的ID列表
     * @return Map，key为"objectType:id"，value为实例数据（如果不存在则为null）
     */
    public Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws Loader.NotFoundException, IOException {
        // 验证所有对象类型存在
        for (String objectType : typeIdMap.keySet()) {
            loader.getObjectType(objectType);
        }
        return storage.getInstancesBatchMultiType(typeIdMap);
    }
}

