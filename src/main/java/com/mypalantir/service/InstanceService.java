package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class InstanceService {
    private final InstanceStorage storage;
    private final Loader loader;
    private final DataValidator validator;

    public InstanceService(InstanceStorage storage, Loader loader, DataValidator validator) {
        this.storage = storage;
        this.loader = loader;
        this.validator = validator;
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
        return storage.getInstance(objectType, id);
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

    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit, Map<String, Object> filters) throws Loader.NotFoundException, IOException {
        loader.getObjectType(objectType);

        if (filters != null && !filters.isEmpty()) {
            List<Map<String, Object>> instances = storage.searchInstances(objectType, filters);
            int total = instances.size();
            int end = Math.min(offset + limit, instances.size());
            if (offset >= instances.size()) {
                return new InstanceStorage.ListResult(List.of(), total);
            }
            return new InstanceStorage.ListResult(instances.subList(offset, end), total);
        }

        return storage.listInstances(objectType, offset, limit);
    }
}

