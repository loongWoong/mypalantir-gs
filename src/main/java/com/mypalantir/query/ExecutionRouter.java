package com.mypalantir.query;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询执行路由器
 * 决定查询是走单源执行还是联邦执行
 */
@Component
public class ExecutionRouter {

    private final Loader loader;
    private final MappingService mappingService;
    private final IInstanceStorage instanceStorage;

    public enum ExecutionMode {
        SINGLE_SOURCE,
        FEDERATED
    }

    @Autowired
    public ExecutionRouter(Loader loader, MappingService mappingService, IInstanceStorage instanceStorage) {
        this.loader = loader;
        this.mappingService = mappingService;
        this.instanceStorage = instanceStorage;
    }

    /**
     * 路由查询
     */
    public ExecutionMode route(OntologyQuery query) {
        try {
            Set<String> databaseIds = new HashSet<>();
            
            // 1. 检查 FROM 对象
            collectDatabaseId(query.getFrom(), databaseIds);
            
            // 2. 检查 Link 对象
            if (query.getLinks() != null) {
                for (OntologyQuery.LinkQuery link : query.getLinks()) {
                    String targetType = getTargetTypeFromLink(link.getName());
                    if (targetType != null) {
                        collectDatabaseId(targetType, databaseIds);
                    }
                }
            }
            
            // 如果涉及多个数据库，走联邦执行
            if (databaseIds.size() > 1) {
                return ExecutionMode.FEDERATED;
            }
            
            return ExecutionMode.SINGLE_SOURCE;
            
        } catch (Exception e) {
            System.err.println("Error routing query: " + e.getMessage());
            // 默认回退到单源执行（如果出错，可能是元数据问题，让执行器去报错）
            return ExecutionMode.SINGLE_SOURCE;
        }
    }

    private void collectDatabaseId(String objectTypeName, Set<String> databaseIds) throws Exception {
        List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectTypeName);
        if (mappings != null && !mappings.isEmpty()) {
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            if (tableId != null) {
                Map<String, Object> table = instanceStorage.getInstance("table", tableId);
                if (table != null) {
                    String databaseId = (String) table.get("database_id");
                    if (databaseId != null) {
                        databaseIds.add(databaseId);
                    }
                }
            }
        }
    }

    private String getTargetTypeFromLink(String linkName) {
        try {
            return loader.getLinkType(linkName).getTargetType();
        } catch (Exception e) {
            return null;
        }
    }
}
