package com.mypalantir.repository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 关系存储接口
 * 支持文件存储和 Neo4j 存储两种实现
 */
public interface ILinkStorage {
    /**
     * 创建关系
     */
    String createLink(String linkType, String sourceID, String targetID, Map<String, Object> properties) throws IOException;

    /**
     * 获取关系
     */
    Map<String, Object> getLink(String linkType, String id) throws IOException;

    /**
     * 更新关系
     */
    void updateLink(String linkType, String id, Map<String, Object> properties) throws IOException;

    /**
     * 删除关系
     */
    void deleteLink(String linkType, String id) throws IOException;

    /**
     * 根据源节点获取关系
     */
    List<Map<String, Object>> getLinksBySource(String linkType, String sourceID) throws IOException;

    /**
     * 根据目标节点获取关系
     */
    List<Map<String, Object>> getLinksByTarget(String linkType, String targetID) throws IOException;

    /**
     * 列出关系
     */
    InstanceStorage.ListResult listLinks(String linkType, int offset, int limit) throws IOException;
}
