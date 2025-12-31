package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.ILinkStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LinkService {
    private final ILinkStorage storage;
    private final IInstanceStorage instanceStorage;
    private final Loader loader;
    private final DataValidator validator;

    public LinkService(ILinkStorage storage, IInstanceStorage instanceStorage, Loader loader, DataValidator validator) {
        this.storage = storage;
        this.instanceStorage = instanceStorage;
        this.loader = loader;
        this.validator = validator;
    }

    public String createLink(String linkType, String sourceID, String targetID, Map<String, Object> properties) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证关系类型存在
        loader.getLinkType(linkType);

        // 验证关系数据
        validator.validateLinkData(linkType, sourceID, targetID, properties);

        // 创建关系
        return storage.createLink(linkType, sourceID, targetID, properties);
    }

    public Map<String, Object> getLink(String linkType, String id) throws IOException {
        return storage.getLink(linkType, id);
    }

    public void updateLink(String linkType, String id, Map<String, Object> properties) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证关系类型存在
        loader.getLinkType(linkType);

        // 验证关系属性
        Map<String, Object> link = storage.getLink(linkType, id);
        String sourceID = (String) link.get("source_id");
        String targetID = (String) link.get("target_id");

        validator.validateLinkData(linkType, sourceID, targetID, properties);

        storage.updateLink(linkType, id, properties);
    }

    public void deleteLink(String linkType, String id) throws IOException {
        storage.deleteLink(linkType, id);
    }

    public List<Map<String, Object>> getLinksBySource(String linkType, String sourceID) throws IOException {
        return storage.getLinksBySource(linkType, sourceID);
    }

    public List<Map<String, Object>> getLinksByTarget(String linkType, String targetID) throws IOException {
        return storage.getLinksByTarget(linkType, targetID);
    }

    public List<Map<String, Object>> getConnectedInstances(String objectType, String linkType, String instanceID, String direction) throws Loader.NotFoundException, IOException {
        LinkType linkTypeDef = loader.getLinkType(linkType);

        List<Map<String, Object>> links = new ArrayList<>();
        if ("outgoing".equals(direction) || direction == null || direction.isEmpty()) {
            if (objectType.equals(linkTypeDef.getSourceType())) {
                links = storage.getLinksBySource(linkType, instanceID);
            }
        } else if ("incoming".equals(direction)) {
            if (objectType.equals(linkTypeDef.getTargetType())) {
                links = storage.getLinksByTarget(linkType, instanceID);
            }
        }

        // 获取关联的实例
        List<Map<String, Object>> instances = new ArrayList<>();
        String targetObjectType;

        if ("outgoing".equals(direction) || direction == null || direction.isEmpty()) {
            targetObjectType = linkTypeDef.getTargetType();
            for (Map<String, Object> link : links) {
                String targetID = (String) link.get("target_id");
                if (targetID != null) {
                    try {
                        Map<String, Object> instance = instanceStorage.getInstance(targetObjectType, targetID);
                        instances.add(instance);
                    } catch (IOException e) {
                        // 忽略不存在的实例
                    }
                }
            }
        } else if ("incoming".equals(direction)) {
            targetObjectType = linkTypeDef.getSourceType();
            for (Map<String, Object> link : links) {
                String sourceID = (String) link.get("source_id");
                if (sourceID != null) {
                    try {
                        Map<String, Object> instance = instanceStorage.getInstance(targetObjectType, sourceID);
                        instances.add(instance);
                    } catch (IOException e) {
                        // 忽略不存在的实例
                    }
                }
            }
        }

        return instances;
    }

    public com.mypalantir.repository.InstanceStorage.ListResult listLinks(String linkType, int offset, int limit) throws Loader.NotFoundException, IOException {
        loader.getLinkType(linkType);
        return storage.listLinks(linkType, offset, limit);
    }
}

